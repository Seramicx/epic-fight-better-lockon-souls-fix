package com.lockonfix.handler;

import com.lockonfix.FixConfig;
import com.lockonfix.LockOnMovementFix;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import yesman.epicfight.api.animation.types.EntityState;
import yesman.epicfight.api.client.camera.EpicFightCameraAPI;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.client.world.capabilites.entitypatch.player.LocalPlayerPatch;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Elden Ring-style auto lock-on: when the current target dies, automatically
 * selects the next best target using cone-based scoring. Mouse flick switches
 * targets directionally. Toggled via a configurable keybind.
 *
 * Flick uses raw {@link MouseHandler} cursor deltas read in {@link TickEvent.Phase#START}
 * so we do not read {@link LocalPlayer#getYRot()} (MovementFixHandler rewrites yaw every
 * tick while locked on, which falsely looked like constant mouse flicks).
 *
 * Integration with Epic Fight is done via its public camera API plus reflection
 * for the private setFocusingEntity/sendTargeting methods. No mixins needed.
 */
@Mod.EventBusSubscriber(modid = LockOnMovementFix.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class AutoLockOnHandler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Minecraft MC = Minecraft.getInstance();

    // =====================================================================
    // State
    // =====================================================================

    private static boolean autoLockOnEnabled = false;

    /** Track lock-on state across ticks — only used for death detection */
    private static boolean wasLockedOn = false;
    private static LivingEntity lastKnownTarget = null;

    /** For continuity scoring (favors candidates near the previous target direction) */
    private static LivingEntity previousTarget = null;

    /** After a target switch, ignore further switches for N ticks */
    private static int settlingDelay = 0;
    private static final int SETTLING_TICKS = 5;

    // =====================================================================
    // Flick detection (raw mouse, not player yaw)
    // =====================================================================

    private static double flickAccum = 0.0;
    private static int flickCooldown = 0;

    private static final int FLICK_COOLDOWN_TICKS = 15;
    /** Per-tick contribution below this is treated as tremor (see mouseDxToYawDegrees). */
    private static final double MIN_TICK_DELTA_DEGREES = 3.0;
    /** Ignore single-frame spikes larger than this (raw accumulatedDX pixels). */
    private static final double MAX_MOUSE_DX_PER_TICK = 400.0;

    // =====================================================================
    // Scoring weights
    // =====================================================================

    private static final double CONE_WEIGHT = 0.5;
    private static final double DIST_WEIGHT = 0.3;
    private static final double CONT_WEIGHT = 0.2;
    private static final double MAX_CONE_ANGLE = 90.0;

    // =====================================================================
    // Reflection: EpicFightCameraAPI
    // =====================================================================

    private static Field focusingEntityField = null;
    private static Method sendTargetingMethod = null;
    private static boolean reflectionInitialized = false;

    // =====================================================================
    // Reflection: MouseHandler accumulated cursor deltas (mapping fallbacks)
    // =====================================================================

    private static Field mouseAccumDXField = null;
    private static boolean mouseReflectionInitialized = false;

    // =====================================================================
    // API cache
    // =====================================================================

    private static EpicFightCameraAPI cachedAPI = null;

    private static EpicFightCameraAPI getAPI() {
        if (cachedAPI == null) {
            try { cachedAPI = EpicFightCameraAPI.getInstance(); }
            catch (Exception e) { return null; }
        }
        return cachedAPI;
    }

    // =====================================================================
    // Safe config reads
    // =====================================================================

    private static boolean getFilterPlayers() {
        try { return FixConfig.FILTER_PLAYERS_FROM_AUTO_LOCKON.get(); }
        catch (Exception e) { return true; }
    }

    private static double getFlickSensitivity() {
        try { return FixConfig.FLICK_SENSITIVITY.get(); }
        catch (Exception e) { return 15.0; }
    }

    private static int getLockOnRange() {
        try { return FixConfig.LOCK_ON_RANGE.get(); }
        catch (Exception e) { return 64; }
    }

    // =====================================================================
    // Public API
    // =====================================================================

    public static boolean isAutoLockOnEnabled() {
        return autoLockOnEnabled;
    }

    // =====================================================================
    // Movement-locked detection (reuse Epic Fight capabilities)
    // =====================================================================

    private static boolean isInActionState(LocalPlayer player) {
        try {
            LocalPlayerPatch patch = EpicFightCapabilities.getLocalPlayerPatch(player);
            if (patch == null) return false;
            EntityState state = patch.getEntityState();
            if (state == null) return false;
            return state.movementLocked() || state.turningLocked();
        } catch (Exception e) {
            return false;
        }
    }

    // =====================================================================
    // Main tick handler — START: mouse flick; END: death, toggle, state
    // =====================================================================

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (MC.player == null || MC.level == null) return;
        if (MC.screen != null) return;

        if (event.phase == TickEvent.Phase.START) {
            // MouseHandler.turnPlayer() consumes accumulatedDX later this tick; Forge
            // ClientTick START runs at the beginning of Minecraft.tick().
            handleFlickTickStart();
            return;
        }

        handleToggleKeybind();

        if (settlingDelay > 0) settlingDelay--;

        EpicFightCameraAPI api = getAPI();
        boolean isLockedOn = api != null && api.isLockingOnTarget();
        LivingEntity currentTarget = api != null ? api.getFocusingEntity() : null;

        if (autoLockOnEnabled && api != null) {
            if (settlingDelay <= 0) {
                boolean targetDead = false;

                if (isLockedOn && currentTarget != null
                        && (!currentTarget.isAlive() || currentTarget.isRemoved())) {
                    targetDead = true;
                }

                if (wasLockedOn && !isLockedOn
                        && lastKnownTarget != null
                        && (!lastKnownTarget.isAlive() || lastKnownTarget.isRemoved())) {
                    targetDead = true;
                }

                if (targetDead) {
                    handleTargetLost(api);
                }
            }

            if (!isLockedOn || currentTarget == null || !currentTarget.isAlive()) {
                resetFlickState();
            }
        } else {
            resetFlickState();
        }

        wasLockedOn = isLockedOn;
        if (currentTarget != null) {
            lastKnownTarget = currentTarget;
        }
    }

    // =====================================================================
    // Toggle keybind
    // =====================================================================

    private static void handleToggleKeybind() {
        if (LockOnMovementFix.TOGGLE_AUTO_LOCKON == null) return;

        while (LockOnMovementFix.TOGGLE_AUTO_LOCKON.consumeClick()) {
            autoLockOnEnabled = !autoLockOnEnabled;

            if (MC.player != null) {
                Component status = autoLockOnEnabled
                    ? Component.literal("ON").withStyle(ChatFormatting.GREEN)
                    : Component.literal("OFF").withStyle(ChatFormatting.RED);
                MC.player.displayClientMessage(
                    Component.literal("Auto Lock-On: ").append(status), true
                );
            }
        }
    }

    // =====================================================================
    // Target death/removal -> auto-switch
    // =====================================================================

    private static void handleTargetLost(EpicFightCameraAPI api) {
        previousTarget = lastKnownTarget;

        LivingEntity best = findBestTarget(MC.player, lastKnownTarget, 0, null);
        if (best != null) {
            setFocusingEntityReflect(api, best);
            if (!api.isLockingOnTarget()) {
                api.setLockOn(true);
            }
            sendTargetingReflect(api, best);
            settlingDelay = SETTLING_TICKS;
        } else {
            if (api.isLockingOnTarget()) {
                api.setLockOn(false);
            }
            resetFlickState();
        }
    }

    // =====================================================================
    // MouseHandler reflection
    // =====================================================================

    private static void initMouseReflection() {
        if (mouseReflectionInitialized) return;
        mouseReflectionInitialized = true;

        Class<?> clazz = MouseHandler.class;
        String[] names = {"accumulatedDX", "cursorDeltaX", "f_91516_"};
        for (String name : names) {
            try {
                Field f = clazz.getDeclaredField(name);
                if (f.getType() != double.class && f.getType() != Double.TYPE) continue;
                f.setAccessible(true);
                mouseAccumDXField = f;
                LOGGER.info("Auto lock-on: MouseHandler horizontal delta field resolved as '{}'", name);
                return;
            } catch (NoSuchFieldException ignored) {}
        }
        LOGGER.error("Auto lock-on: could not resolve MouseHandler horizontal cursor delta field — flick switching disabled");
    }

    private static double readMouseAccumDx() {
        initMouseReflection();
        if (mouseAccumDXField == null) return 0;
        try {
            return mouseAccumDXField.getDouble(MC.mouseHandler);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Same order of magnitude as vanilla horizontal look from mouse for one tick
     * (MouseHandler.turnPlayer: dx * (sens*0.6+0.2), then cubed * 8).
     */
    private static double mouseDxToYawDegrees(double dx) {
        if (dx == 0) return 0;
        if (Math.abs(dx) > MAX_MOUSE_DX_PER_TICK) return 0;
        double sens = MC.options.sensitivity().get() * 0.6 + 0.2;
        double d = dx * sens;
        return d * d * d * 8.0;
    }

    // =====================================================================
    // Flick tick (START phase only)
    // =====================================================================

    private static void handleFlickTickStart() {
        if (!autoLockOnEnabled) return;

        EpicFightCameraAPI api = getAPI();
        if (api == null) return;

        LocalPlayer player = MC.player;
        boolean isLockedOn = api.isLockingOnTarget();
        LivingEntity currentTarget = api.getFocusingEntity();

        if (!isLockedOn || currentTarget == null || !currentTarget.isAlive()) return;
        if (settlingDelay > 0) return;
        if (isInActionState(player)) return;

        if (flickCooldown > 0) {
            flickCooldown--;
            return;
        }

        double dx = readMouseAccumDx();
        double degreesApprox = mouseDxToYawDegrees(dx);

        if (Math.abs(degreesApprox) >= MIN_TICK_DELTA_DEGREES) {
            flickAccum += degreesApprox;
        } else {
            flickAccum *= 0.3;
        }

        double threshold = getFlickSensitivity();
        if (Math.abs(flickAccum) > threshold) {
            int flickDir = flickAccum > 0 ? 1 : -1;

            LivingEntity best = findBestTarget(player, currentTarget, flickDir, currentTarget);
            if (best != null) {
                previousTarget = currentTarget;
                setFocusingEntityReflect(api, best);
                sendTargetingReflect(api, best);
                settlingDelay = SETTLING_TICKS;
            }

            flickAccum = 0;
            flickCooldown = FLICK_COOLDOWN_TICKS;
        }
    }

    private static void resetFlickState() {
        flickAccum = 0;
        flickCooldown = 0;
    }

    // =====================================================================
    // Target scoring engine
    // =====================================================================

    /**
     * Find the best lock-on candidate using cone alignment, distance, and
     * continuity scoring. For flick-triggered switches, candidates in the
     * wrong direction are excluded.
     *
     * @param player     the local player
     * @param exclude    entity to exclude from candidates (current/dead target)
     * @param flickDir   0 = no flick, 1 = right, -1 = left
     * @param reference  the current target for directional comparison (null if none)
     */
    private static LivingEntity findBestTarget(
        LocalPlayer player, LivingEntity exclude, int flickDir, LivingEntity reference
    ) {
        double maxRange = getLockOnRange();
        Vec3 cameraForward = new Vec3(MC.gameRenderer.getMainCamera().getLookVector());
        Vec3 playerEyePos = player.getEyePosition();

        LivingEntity bestCandidate = null;
        double bestScore = 0;

        for (Entity entity : MC.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (living == player) continue;
            if (living == exclude) continue;
            if (living instanceof ArmorStand) continue;
            if (!living.isAlive() || living.isRemoved() || living.isSpectator()) continue;
            if (!living.isPickable()) continue;
            if (living.isInvisibleTo(player)) continue;
            if (getFilterPlayers() && living instanceof Player) continue;

            double dist = player.distanceTo(living);
            if (dist > maxRange) continue;

            if (!player.hasLineOfSight(living)) continue;

            double score = scoreCandidate(
                player, living, cameraForward, playerEyePos, maxRange, flickDir, reference
            );

            if (score > bestScore) {
                bestScore = score;
                bestCandidate = living;
            }
        }

        return bestCandidate;
    }

    private static double scoreCandidate(
        LocalPlayer player, LivingEntity candidate, Vec3 cameraForward,
        Vec3 playerEyePos, double maxRange, int flickDir, LivingEntity reference
    ) {
        Vec3 toCandidate = candidate.getEyePosition().subtract(playerEyePos).normalize();

        double dot = Mth.clamp(cameraForward.dot(toCandidate), -1.0, 1.0);
        double angle = Math.toDegrees(Math.acos(dot));
        double coneScore = Math.max(0.0, 1.0 - (angle / MAX_CONE_ANGLE));

        double dist = player.distanceTo(candidate);
        double distScore = Math.max(0.0, 1.0 - (dist / maxRange));

        double contScore = 0.0;
        if (previousTarget != null && previousTarget.isAlive() && !previousTarget.isRemoved()) {
            Vec3 prevDir = previousTarget.getEyePosition().subtract(playerEyePos).normalize();
            double prevDot = Mth.clamp(prevDir.dot(toCandidate), -1.0, 1.0);
            double angleBetween = Math.toDegrees(Math.acos(prevDot));
            contScore = Math.max(0.0, 1.0 - (angleBetween / 90.0));
        }

        if (flickDir != 0 && reference != null) {
            float candYaw = getYawToEntity(player, candidate);
            float refYaw = getYawToEntity(player, reference);
            float relAngle = Mth.wrapDegrees(candYaw - refYaw);

            if (relAngle * flickDir <= 0) {
                return 0.0;
            }

            coneScore = Math.max(0.0, 1.0 - (Math.abs(relAngle) / 180.0));
        }

        return coneScore * CONE_WEIGHT + distScore * DIST_WEIGHT + contScore * CONT_WEIGHT;
    }

    private static float getYawToEntity(LocalPlayer player, Entity target) {
        double dx = target.getX() - player.getX();
        double dz = target.getZ() - player.getZ();
        return (float)(Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
    }

    // =====================================================================
    // Epic Fight integration via reflection
    // =====================================================================

    private static void initReflection() {
        if (reflectionInitialized) return;
        reflectionInitialized = true;

        try {
            focusingEntityField = EpicFightCameraAPI.class.getDeclaredField("focusingEntity");
            focusingEntityField.setAccessible(true);
        } catch (Exception e) {
            LOGGER.error("Auto lock-on: cannot access focusingEntity field — auto target switching disabled: {}", e.getMessage());
        }

        try {
            sendTargetingMethod = EpicFightCameraAPI.class.getDeclaredMethod("sendTargeting", LivingEntity.class);
            sendTargetingMethod.setAccessible(true);
        } catch (Exception e) {
            LOGGER.error("Auto lock-on: cannot access sendTargeting method — server sync disabled: {}", e.getMessage());
        }
    }

    private static void setFocusingEntityReflect(EpicFightCameraAPI api, LivingEntity target) {
        initReflection();
        if (focusingEntityField == null) return;
        try {
            focusingEntityField.set(api, target);
        } catch (Exception e) {
            LOGGER.warn("Failed to set focusing entity: {}", e.getMessage());
        }
    }

    private static void sendTargetingReflect(EpicFightCameraAPI api, LivingEntity target) {
        initReflection();
        if (sendTargetingMethod == null) return;
        try {
            sendTargetingMethod.invoke(api, target);
        } catch (Exception e) {
            LOGGER.warn("Failed to send targeting packet: {}", e.getMessage());
        }
    }
}
