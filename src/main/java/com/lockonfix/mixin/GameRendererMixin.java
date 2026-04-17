package com.lockonfix.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import yesman.epicfight.api.client.camera.EpicFightCameraAPI;

import java.util.function.Predicate;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    private boolean isLockedOn() {
        try {
            EpicFightCameraAPI api = EpicFightCameraAPI.getInstance();
            return api != null && api.isLockingOnTarget();
        } catch (Exception e) {
            return false;
        }
    }

    @Redirect(
        method = "pick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/projectile/ProjectileUtil;getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;"
        )
    )
    private EntityHitResult redirectGetEntityHitResult(Entity shooter, Vec3 startPos, Vec3 endPos, AABB boundingBox, Predicate<Entity> filter, double interactionRangeSq) {
        Minecraft mc = Minecraft.getInstance();
        
        if (mc.player != null && shooter == mc.player && mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK && !isLockedOn()) {
            // Over-the-shoulder perspective: Trace from camera instead of player eyes
            Camera camera = mc.gameRenderer.getMainCamera();
            Vec3 cameraPos = camera.getPosition();
            Vec3 lookVec = new Vec3(camera.getLookVector());
            double interactionRange = Math.sqrt(interactionRangeSq);
            
            // The interaction range must be increased to account for the camera being further back than the player
            float partialTick = mc.getFrameTime();
            double distance = interactionRange + cameraPos.distanceTo(shooter.getEyePosition(partialTick));
            Vec3 cameraEndPos = cameraPos.add(lookVec.scale(distance));
            
            // The interactionRangeSq here still needs to be the expanded square
            double newInteractionRangeSq = distance * distance;
            
            // Inflate search box from camera position to ensure entities are found correctly
            AABB searchBox = new AABB(cameraPos, cameraEndPos).inflate(1.0);
            
            return ProjectileUtil.getEntityHitResult(shooter, cameraPos, cameraEndPos, searchBox, filter, newInteractionRangeSq);
        }
        
        // Vanilla fallback
        return ProjectileUtil.getEntityHitResult(shooter, startPos, endPos, boundingBox, filter, interactionRangeSq);
    }
}
