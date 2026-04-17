package com.lockonfix.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yesman.epicfight.api.client.camera.EpicFightCameraAPI;

@Mixin(Entity.class)
public abstract class EntityMixin {

    private boolean isLockedOn() {
        try {
            EpicFightCameraAPI api = EpicFightCameraAPI.getInstance();
            return api != null && api.isLockingOnTarget();
        } catch (Exception e) {
            return false;
        }
    }

    @Inject(method = "pick", at = @At("HEAD"), cancellable = true)
    public void onPick(double interactionRange, float partialTick, boolean stopOnFluid, CallbackInfoReturnable<HitResult> cir) {
        Entity self = (Entity) (Object) this;
        
        if (self.level().isClientSide) {
            Minecraft mc = Minecraft.getInstance();
            if (self == mc.player && mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK && !isLockedOn()) {
                Camera camera = mc.gameRenderer.getMainCamera();
                Vec3 cameraPos = camera.getPosition();
                Vec3 lookVec = new Vec3(camera.getLookVector());
                
                // Increase the interaction range by the distance between the camera and the player's eyes
                // This ensures the raytrace reaches the block the player intends to interact with
                double distance = interactionRange + cameraPos.distanceTo(self.getEyePosition(partialTick));
                
                Vec3 end = cameraPos.add(lookVec.scale(distance));
                ClipContext.Fluid fluidCtx = stopOnFluid ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE;
                
                BlockHitResult blockHit = self.level().clip(new ClipContext(
                    cameraPos, end,
                    ClipContext.Block.OUTLINE,
                    fluidCtx,
                    self
                ));
                
                cir.setReturnValue(blockHit);
            }
        }
    }
}
