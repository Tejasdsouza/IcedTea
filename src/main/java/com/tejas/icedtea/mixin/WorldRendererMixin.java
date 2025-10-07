package com.tejas.icedtea.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tejas.icedtea.IcedTeaMod;
import com.tejas.icedtea.client.IcedTeaHudOverlay;
import com.tejas.icedtea.culling.OcclusionCullingSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.level.ChunkPos;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class WorldRendererMixin {
    
    @Shadow
    private Minecraft minecraft;
    
    @Shadow
    private Frustum cullingFrustum;
    
    private FrustumIntersection cachedFrustumIntersection;
    private long lastFrustumUpdate = 0;

    @Inject(
        method = "renderLevel",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;setupRender(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;ZZ)V",
            shift = At.Shift.AFTER
        )
    )
    private void onBeforeRenderLevel(
        PoseStack poseStack,
        float partialTick,
        long finishNanoTime,
        boolean renderBlockOutline,
        Camera camera,
        net.minecraft.client.renderer.GameRenderer gameRenderer,
        net.minecraft.client.renderer.LightTexture lightTexture,
        Matrix4f projectionMatrix,
        CallbackInfo ci
    ) {
        if (!IcedTeaMod.getConfig().isOcclusionCullingEnabled()) {
            return;
        }
        
        try {
            OcclusionCullingSystem cullingSystem = IcedTeaMod.getCullingSystem();
            if (cullingSystem == null) {
                return;
            }
            
            int renderDistance = minecraft.options.renderDistance().get();
            
            FrustumIntersection frustumIntersection = createFrustumIntersection(
                poseStack,
                projectionMatrix
            );
            
            long startTime = System.nanoTime();
            cullingSystem.cullChunks(camera, frustumIntersection, renderDistance);
            IcedTeaHudOverlay.recordFrameTime(System.nanoTime() - startTime);
            
        } catch (Exception e) {
            IcedTeaMod.LOGGER.error("Error during occlusion culling", e);
        }
    }

    private FrustumIntersection createFrustumIntersection(PoseStack poseStack, Matrix4f projectionMatrix) {
        long currentTime = System.currentTimeMillis();
        
        if (cachedFrustumIntersection != null && (currentTime - lastFrustumUpdate) < 50) {
            return cachedFrustumIntersection;
        }
        
        Matrix4f modelViewMatrix = new Matrix4f(poseStack.last().pose());
        Matrix4f viewProjection = new Matrix4f(projectionMatrix);
        viewProjection.mul(modelViewMatrix);
        
        cachedFrustumIntersection = new FrustumIntersection(viewProjection);
        lastFrustumUpdate = currentTime;
        
        return cachedFrustumIntersection;
    }

    @Inject(
        method = "allChanged",
        at = @At("TAIL")
    )
    private void onWorldChanged(CallbackInfo ci) {
        if (IcedTeaMod.getCullingSystem() != null) {
            IcedTeaMod.getCullingSystem().clearCache();
        }
        cachedFrustumIntersection = null;
    }
}