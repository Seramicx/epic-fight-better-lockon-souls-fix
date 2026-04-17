package com.lockonfix.handler;

import com.lockonfix.FixConfig;
import com.lockonfix.LockOnMovementFix;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import yesman.epicfight.api.animation.types.EntityState;
import yesman.epicfight.api.client.camera.EpicFightCameraAPI;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.client.world.capabilites.entitypatch.player.LocalPlayerPatch;

import java.lang.reflect.Field;

/**
 * Core movement fix handler for souls-like lock-on with 360 smooth movement.
 *
 * KEY DESIGN INSIGHT:
 * Epic Fight's postClientTick() forcibly rotates player.yRot toward the target
 * at 30°/tick. If we read player.getYRot() as our "current" angle, Epic Fight
 * corrupts our smoothing every tick. So we maintain our OWN tracked angle
 * (smoothedYRot) that is independent of what Epic Fight writes to the player.
 *
 * We re-apply our yRot in BOTH:
 * 1. MovementInputUpdateEvent (LOW priority) for correct movement direction
 * 2. PlayerTickEvent (Phase.END, LOW priority) to override Epic Fight's postClientTick
 */
@Mod.EventBusSubscriber(modid = LockOnMovementFix.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class MovementFixHandler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Minecraft MC = Minecraft.getInstance();

    // Safe defaults
    private static final float DEFAULT_TURN_SPEED = 0.45F;
    private static final float DEFAULT_IDLE_TURN_SPEED = 0.7F;
    private static final int DEFAULT_LOCK_ON_RANGE = 64;
    private static final boolean DEFAULT_AUTO_FACE_TARGET = true;

    // Cached API
    private static EpicFightCameraAPI cachedAPI = null;

    // Lock-on range override
    private static boolean rangeOverrideApplied = false;

    // =====================================================================
    // OUR OWN tracked rotation state (immune to Epic Fight's overrides)
    // =====================================================================
    private static float smoothedYRot = Float.NaN;
    private static boolean wasLockedOn = false;

    // =====================================================================
    // Safe config reads
    // =====================================================================

    private static float getTurnSpeed() {
        try { return (float) FixConfig.TURN_SPEED.get().doubleValue(); }
        catch (Exception e) { return DEFAULT_TURN_SPEED; }
    }

    private static float getIdleTurnSpeed() {
        try { return (float) FixConfig.IDLE_TURN_SPEED.get().doubleValue(); }
        catch (Exception e) { return DEFAULT_IDLE_TURN_SPEED; }
    }

    private static int getLockOnRange() {
        try { return FixConfig.LOCK_ON_RANGE.get(); }
        catch (Exception e) { return DEFAULT_LOCK_ON_RANGE; }
    }

    private static boolean getAutoFaceTarget() {
        try { return FixConfig.AUTO_FACE_TARGET.get(); }
        catch (Exception e) { return DEFAULT_AUTO_FACE_TARGET; }
    }

    // =====================================================================
    // API access
    // =====================================================================

    private static EpicFightCameraAPI getAPI() {
        if (cachedAPI == null) {
            try { cachedAPI = EpicFightCameraAPI.getInstance(); }
            catch (Exception e) { return null; }
        }
        return cachedAPI;
    }

    // =====================================================================
    // Lock-on range override (one-shot reflection)
    // =====================================================================

    private static void applyLockOnRangeOverride() {
        if (rangeOverrideApplied) return;
        rangeOverrideApplied = true;

        try {
            int desiredRange = getLockOnRange();
            Class<?> configClass = Class.forName("yesman.epicfight.config.ClientConfig");

            for (String name : new String[]{"lockOnRange", "LOCK_ON_RANGE"}) {
                try {
                    Field field = configClass.getDeclaredField(name);
                    field.setAccessible(true);
                    Object val = field.get(null);
                    if (val instanceof Integer && (Integer) val < desiredRange) {
                        field.setInt(null, desiredRange);
                        LOGGER.info("Lock-On range overridden: {} -> {} blocks", val, desiredRange);
                    }
                    return;
                } catch (NoSuchFieldException ignored) {}
            }
            LOGGER.warn("Could not find lockOnRange field in Epic Fight ClientConfig.");
        } catch (Exception e) {
            LOGGER.warn("Failed to override lock-on range: {}", e.getMessage());
        }
    }

    // =====================================================================
    // Math helpers
    // =====================================================================

    private static float getYawToTarget(LocalPlayer player, LivingEntity target) {
        double dx = target.getX() - player.getX();
        double dz = target.getZ() - player.getZ();
        return (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
    }

    /**
     * Smooth interpolation between angles.
     * Uses our tracked angle as the starting point instead of player.getYRot(),
     * which gets corrupted by Epic Fight's postClientTick().
     */
    private static float smoothAngle(float from, float to, float factor) {
        float delta = Mth.wrapDegrees(to - from);
        return from + delta * factor;
    }

    // =====================================================================
    // Attack state detection via Epic Fight capabilities
    // =====================================================================

    /**
     * Check if the player is in a state where movement should be suppressed.
     * Uses Epic Fight's EntityState.movementLocked() flag, which is set by
     * attack animations, guard (blocking), and parry animations.
     * Sprint/dash attacks do NOT set this flag, so they keep their momentum.
     */
    private static boolean isMovementLocked(LocalPlayer player) {
        try {
            LocalPlayerPatch patch = EpicFightCapabilities.getLocalPlayerPatch(player);
            if (patch == null) return false;

            EntityState state = patch.getEntityState();
            if (state == null) return false;

            // If Epic Fight says movement is locked (attack animations that
            // should root the player), suppress our movement override.
            // Sprint/dash attacks do NOT set movementLocked, so they keep momentum.
            return state.movementLocked();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if the player is holding a guard/parry skill.
     * Used to defer rotation to Better Lock On during blocking.
     */
    private static boolean isHoldingGuard(LocalPlayer player) {
        try {
            LocalPlayerPatch patch = EpicFightCapabilities.getLocalPlayerPatch(player);
            return patch != null && patch.isHoldingAny();
        } catch (Exception e) {
            return false;
        }
    }

    // =====================================================================
    // Input reading — keyboard + controller fallback
    // =====================================================================

    /**
     * Read directional input from keyboard keys first; if nothing pressed,
     * fall back to the Input values already set (by Controllable or other mods).
     */
    private static float[] readDirectionalInput(Input input) {
        // Try keyboard first
        float rawForward = 0;
        if (MC.options.keyUp.isDown()) rawForward += 1.0F;
        if (MC.options.keyDown.isDown()) rawForward -= 1.0F;

        float rawStrafe = 0;
        if (MC.options.keyLeft.isDown()) rawStrafe += 1.0F;
        if (MC.options.keyRight.isDown()) rawStrafe -= 1.0F;

        // If no keyboard input, use values already written to Input by Controllable
        // or any other input source. These are the pre-existing values from earlier
        // event handlers (Controllable fires at default priority, we fire at LOW).
        if (rawForward == 0 && rawStrafe == 0) {
            rawForward = input.forwardImpulse;
            rawStrafe = input.leftImpulse;
        }

        return new float[]{rawForward, rawStrafe};
    }

    // =====================================================================
    // PRIMARY: MovementInputUpdateEvent (after BetterLockOn)
    // Sets movement direction and yRot for the movement calculation
    // =====================================================================

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onMovementInput(MovementInputUpdateEvent event) {
        LocalPlayer player = MC.player;
        if (player == null) return;

        EpicFightCameraAPI api = getAPI();
        if (api == null || !api.isLockingOnTarget()) {
            // Reset tracking state if not locked on
            if (wasLockedOn) {
                smoothedYRot = Float.NaN;
                wasLockedOn = false;
            }
            return;
        }

        LivingEntity target = api.getFocusingEntity();
        if (target == null || !target.isAlive()) return;

        wasLockedOn = true;
        Input input = event.getInput();

        // Initialize our tracked angle on first lock-on frame
        if (Float.isNaN(smoothedYRot)) {
            smoothedYRot = player.getYRot();
        }

        // When the player is holding guard/parry and auto-facing is enabled,
        // defer entirely to Better Lock On which handles guard-facing rotation.
        // Keep smoothedYRot synced so the transition back to movement is seamless.
        if (isHoldingGuard(player) && getAutoFaceTarget()) {
            smoothedYRot = player.getYRot();
            return;
        }

        // If Epic Fight has locked movement for an attack animation, don't inject
        // any movement. This prevents sliding while attacking.
        // Sprint/dash attacks don't set movementLocked, so they keep their momentum.
        if (isMovementLocked(player)) {
            input.forwardImpulse = 0;
            input.leftImpulse = 0;
            input.up = false;
            input.down = false;
            input.left = false;
            input.right = false;
            return;
        }

        // Read input from keyboard OR controller
        float[] dir = readDirectionalInput(input);
        float rawForward = dir[0];
        float rawStrafe = dir[1];

        float targetYaw = getYawToTarget(player, target);
        boolean isMoving = rawForward != 0 || rawStrafe != 0;

        float turnSpeed = getTurnSpeed();
        float idleTurnSpeed = getIdleTurnSpeed();

        if (isMoving) {
            // Moving: 360 directional movement with smooth turns
            float offsetAngle = -(float) Math.toDegrees(Math.atan2(rawStrafe, rawForward));
            float desiredYRot = Mth.wrapDegrees(targetYaw + offsetAngle);

            smoothedYRot = smoothAngle(smoothedYRot, desiredYRot, turnSpeed);
            player.setYRot(smoothedYRot);

            // Impulse trick: rotate yRot to face movement direction, then set all
            // movement as "forward" at that angle. Booleans must be consistent with
            // the impulse values — contradictory state (up=true + down=true) breaks
            // EF's MovementDirection.vertical() and other systems.
            float speed = input.shiftKeyDown ? 0.3F : 1.0F;
            input.forwardImpulse = speed;
            input.leftImpulse = 0;
            input.up = true;
            input.down = false;
            input.left = false;
            input.right = false;

        } else {
            // Standing still: only auto-face target if config allows it.
            // Otherwise hold the last movement direction so attacks go that way.
            if (getAutoFaceTarget()) {
                float desiredYRot = Mth.wrapDegrees(targetYaw);
                smoothedYRot = smoothAngle(smoothedYRot, desiredYRot, idleTurnSpeed);
            }

            player.setYRot(smoothedYRot);

            input.forwardImpulse = 0;
            input.leftImpulse = 0;
            input.up = false;
            input.down = false;
            input.left = false;
            input.right = false;
        }

        // Sync body and head
        player.yBodyRot = player.getYRot();
        player.yHeadRot = player.getYRot();
    }

    // =====================================================================
    // SECONDARY: PlayerTickEvent.END (after Epic Fight's postClientTick)
    // Re-applies our yRot to override Epic Fight's forced rotation
    // =====================================================================

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!event.side.isClient()) return;

        LocalPlayer player = MC.player;
        if (player == null || event.player != player) return;

        // One-shot range override
        applyLockOnRangeOverride();

        EpicFightCameraAPI api = getAPI();
        if (api == null || !api.isLockingOnTarget()) return;

        LivingEntity target = api.getFocusingEntity();
        if (target == null || !target.isAlive()) return;

        // During guard/parry with auto-facing enabled, defer to Better Lock On.
        // Don't overwrite the rotation it set in clientTick.
        if (isHoldingGuard(player) && getAutoFaceTarget()) {
            smoothedYRot = player.getYRot();
            return;
        }

        // Re-apply our tracked yRot AFTER Epic Fight's postClientTick
        // has overwritten it. Also set yRotO/yBodyRotO/yHeadRotO to prevent
        // interpolation jitter that causes the lock-on icon to flicker.
        if (!Float.isNaN(smoothedYRot)) {
            player.setYRot(smoothedYRot);
            player.yRotO = smoothedYRot;
            player.yBodyRot = smoothedYRot;
            player.yBodyRotO = smoothedYRot;
            player.yHeadRot = smoothedYRot;
            player.yHeadRotO = smoothedYRot;
        }
    }
}
