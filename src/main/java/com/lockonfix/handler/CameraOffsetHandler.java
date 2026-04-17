package com.lockonfix.handler;

import com.lockonfix.FixConfig;
import com.lockonfix.LockOnMovementFix;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import yesman.epicfight.api.client.camera.EpicFightCameraAPI;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Over-the-shoulder camera system:
 * 1) Camera offset (ViewportEvent.ComputeCameraAngles) — lateral + vertical shift
 * 2) Crosshair / hitResult correction (TickEvent.ClientTickEvent) — when not locked on
 * 3) Player visibility (RenderLivingEvent.Pre) — hide when camera is too close
 *
 * All hooks fire AFTER Epic Fight's camera mixin has completed, avoiding the
 * conflict that Shoulder Surfing Reloaded has with Epic Fight's lock-on camera.
 */
@Mod.EventBusSubscriber(modid = LockOnMovementFix.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class CameraOffsetHandler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Minecraft MC = Minecraft.getInstance();

    // Smoothed offset state (lerps toward target for shoulder swap transitions)
    private static float currentOffsetX = 0;
    private static float currentOffsetY = 0;
    private static boolean initialized = false;

    // Shoulder swap state (runtime toggle, not persisted to config)
    private static boolean shoulderSwapped = false;

    // Whether the offset was actually applied this frame (used by crosshair + visibility)
    private static boolean offsetActiveThisFrame = false;

    // Reflection: Camera.position field (found by type, mapping-agnostic)
    private static Field cameraPositionField = null;
    // Reflection: Camera.move(double, double, double) method (fallback)
    private static Method cameraMoveMethod = null;
    private static boolean reflectionAttempted = false;

    // EF API cache
    private static EpicFightCameraAPI cachedAPI = null;

    // =====================================================================
    // Safe config reads
    // =====================================================================

    private static double getOffsetX() {
        try { return FixConfig.CAMERA_OFFSET_X.get(); }
        catch (Exception e) { return -0.75; }
    }

    private static double getOffsetY() {
        try { return FixConfig.CAMERA_OFFSET_Y.get(); }
        catch (Exception e) { return 0.15; }
    }

    private static double getSmoothing() {
        try { return FixConfig.CAMERA_OFFSET_SMOOTHING.get(); }
        catch (Exception e) { return 0.5; }
    }

    private static boolean getHidePlayerWhenClose() {
        try { return FixConfig.HIDE_PLAYER_WHEN_CLOSE.get(); }
        catch (Exception e) { return true; }
    }

    private static double getHidePlayerDistance() {
        try { return FixConfig.HIDE_PLAYER_DISTANCE.get(); }
        catch (Exception e) { return 0.8; }
    }

    // =====================================================================
    // EF API helper
    // =====================================================================

    private static EpicFightCameraAPI getAPI() {
        if (cachedAPI == null) {
            try { cachedAPI = EpicFightCameraAPI.getInstance(); }
            catch (Exception e) { return null; }
        }
        return cachedAPI;
    }

    private static boolean isLockedOn() {
        EpicFightCameraAPI api = getAPI();
        return api != null && api.isLockingOnTarget();
    }

    // =====================================================================
    // Reflection: find Camera internals by type/signature (mapping-agnostic)
    // =====================================================================

    private static void initReflection() {
        if (reflectionAttempted) return;
        reflectionAttempted = true;

        // Strategy 1: Find Camera's Vec3 position field by type.
        // Camera has exactly one Vec3 field (its position). This works
        // regardless of mapping environment (dev/Parchment vs production/SRG).
        for (Field f : Camera.class.getDeclaredFields()) {
            if (f.getType() == Vec3.class) {
                f.setAccessible(true);
                cameraPositionField = f;
                LOGGER.info("CameraOffsetHandler: Found Camera position field '{}' by type", f.getName());
                return;
            }
        }

        // Strategy 2: Find Camera.move(double, double, double) by signature.
        // Camera has exactly one method taking (double, double, double).
        for (Method m : Camera.class.getDeclaredMethods()) {
            Class<?>[] params = m.getParameterTypes();
            if (params.length == 3
                    && params[0] == double.class
                    && params[1] == double.class
                    && params[2] == double.class) {
                m.setAccessible(true);
                cameraMoveMethod = m;
                LOGGER.info("CameraOffsetHandler: Found Camera move method '{}' by signature", m.getName());
                return;
            }
        }

        LOGGER.error("CameraOffsetHandler: Could not find Camera position field or move method — offset disabled");
    }

    /**
     * Set the camera's world position. Uses field access (preferred) or
     * falls back to Camera.move() if field wasn't found.
     */
    private static void setCameraPosition(Camera camera, Vec3 pos) {
        initReflection();
        try {
            if (cameraPositionField != null) {
                cameraPositionField.set(camera, pos);
                return;
            }
            if (cameraMoveMethod != null) {
                Vec3 current = camera.getPosition();
                Vec3 delta = pos.subtract(current);
                // move() takes (forward, up, left) in camera-relative space,
                // but we have a world-space delta. We'd need to decompose.
                // Since field access should always work, this is a fallback.
                // For the fallback, just set position using the move deltas
                // projected onto camera axes.
                Vec3 fwd = new Vec3(camera.getLookVector());
                Vec3 up = new Vec3(camera.getUpVector());
                Vec3 left = new Vec3(camera.getLeftVector());
                double dFwd = delta.dot(fwd);
                double dUp = delta.dot(up);
                double dLeft = delta.dot(left);
                cameraMoveMethod.invoke(camera, dFwd, dUp, dLeft);
            }
        } catch (Exception e) {
            LOGGER.warn("CameraOffsetHandler: Failed to set camera position: {}", e.getMessage());
        }
    }

    // =====================================================================
    // Part 1: Shoulder swap keybind + crosshair correction (client tick)
    // =====================================================================

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (MC.player == null || MC.screen != null) return;

        // --- Shoulder swap keybind ---
        if (LockOnMovementFix.SWAP_SHOULDER != null) {
            while (LockOnMovementFix.SWAP_SHOULDER.consumeClick()) {
                shoulderSwapped = !shoulderSwapped;
            }
        }


    }

    // =====================================================================
    // Part 2: Camera offset — applied every frame after camera setup
    // =====================================================================

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        offsetActiveThisFrame = false;

        if (MC.options.getCameraType() != CameraType.THIRD_PERSON_BACK) return;

        LocalPlayer player = MC.player;
        if (player == null) return;

        Camera camera = event.getCamera();

        float targetX = (float) getOffsetX();
        if (shoulderSwapped) targetX = -targetX;
        float targetY = (float) getOffsetY();

        if (!initialized) {
            currentOffsetX = targetX;
            currentOffsetY = targetY;
            initialized = true;
        }

        float smoothing = (float) getSmoothing();
        currentOffsetX += (targetX - currentOffsetX) * smoothing;
        currentOffsetY += (targetY - currentOffsetY) * smoothing;

        if (Math.abs(currentOffsetX) < 0.001f && Math.abs(currentOffsetY) < 0.001f) return;

        Vec3 left = new Vec3(camera.getLeftVector());
        Vec3 up = new Vec3(camera.getUpVector());
        Vec3 currentPos = camera.getPosition();

        Vec3 desired = currentPos
            .add(left.scale(currentOffsetX))
            .add(up.scale(currentOffsetY));

        Vec3 finalPos = clipOffsetToWall(currentPos, desired, player);

        setCameraPosition(camera, finalPos);
        offsetActiveThisFrame = true;
    }

    // =====================================================================
    // Part 3: Player visibility — hide when camera is very close
    // =====================================================================

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        if (MC.player == null) return;
        if (event.getEntity() != MC.player) return;
        if (MC.options.getCameraType() != CameraType.THIRD_PERSON_BACK) return;
        if (!getHidePlayerWhenClose()) return;

        double dist = MC.gameRenderer.getMainCamera().getPosition()
            .distanceTo(MC.player.getEyePosition(event.getPartialTick()));

        if (dist < getHidePlayerDistance() || MC.gameRenderer.getMainCamera().getPosition().distanceTo(MC.player.position()) < getHidePlayerDistance()) {
            event.setCanceled(true);
        }
    }

    // =====================================================================
    // Wall collision raytrace
    // =====================================================================

    private static Vec3 clipOffsetToWall(Vec3 basePos, Vec3 desiredPos, LocalPlayer player) {
        if (player.level() == null) return desiredPos;
        
        Vec3 eyePos = player.getEyePosition(1.0f);
        Vec3 dirFromEye = desiredPos.subtract(eyePos).normalize();
        double fullDist = desiredPos.distanceTo(eyePos);
        double safeDist = fullDist;

        // Perform 8 raytraces from corners of the near plane to ensure no clipping
        for(int i = 0; i < 8; ++i) {
            float f = (float)((i & 1) * 2 - 1);
            float f1 = (float)((i >> 1 & 1) * 2 - 1);
            float f2 = (float)((i >> 2 & 1) * 2 - 1);
            f *= 0.1F;
            f1 *= 0.1F;
            f2 *= 0.1F;
            
            Vec3 startBox = eyePos.add(f, f1, f2);
            Vec3 endBox = desiredPos.add(f, f1, f2);
            
            BlockHitResult hit = player.level().clip(new ClipContext(
                startBox, endBox,
                ClipContext.Block.VISUAL,
                ClipContext.Fluid.NONE,
                player
            ));
            
            if (hit.getType() != HitResult.Type.MISS) {
                double hitDist = hit.getLocation().distanceTo(startBox);
                if (hitDist < safeDist) {
                    safeDist = hitDist;
                }
            }
        }
        
        if (safeDist < fullDist) {
            return eyePos.add(dirFromEye.scale(safeDist));
        }
        
        return desiredPos;
    }
}
