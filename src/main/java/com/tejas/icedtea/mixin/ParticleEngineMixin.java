package com.tejas.icedtea.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tejas.icedtea.IcedTeaMod;
import com.tejas.icedtea.client.IcedTeaHudOverlay;
import com.tejas.icedtea.mixin.ParticleAccessor;
import com.tejas.icedtea.util.ParticleWithDistance;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {

    @Shadow
    private Map<ParticleRenderType, Queue<Particle>> particles;

    private static int totalParticles = 0;
    private static int renderedParticles = 0;
    private static int frameCounter = 0;

    private final Map<ParticleRenderType, List<Particle>> batchedParticles = new HashMap<>();

    @Inject(
        method = "tick",
        at = @At("HEAD")
    )
    private void onTickParticles(CallbackInfo ci) {
        if (!IcedTeaMod.isModEnabled() || !IcedTeaMod.getConfig().isParticleCullingEnabled()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.cameraEntity == null || mc.level == null) {
            return;
        }

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.getPosition();
        double cullDistance = IcedTeaMod.getConfig().getParticleCullingDistance();
        double cullDistanceSq = cullDistance * cullDistance;

        totalParticles = 0;
        renderedParticles = 0;

        for (Queue<Particle> particleQueue : particles.values()) {
            particleQueue.removeIf(particle -> {
                totalParticles++;

                if (particle == null) {
                    return true;
                }

                ParticleAccessor accessor = (ParticleAccessor) particle;
                double dx = accessor.getXo() - cameraPos.x;
                double dy = accessor.getYo() - cameraPos.y;
                double dz = accessor.getZo() - cameraPos.z;
                double distSq = dx * dx + dy * dy + dz * dz;

if (distSq > cullDistanceSq) {
    if (IcedTeaMod.isDebugMode()) {
        IcedTeaMod.LOGGER.info("[IcedTea][PARTICLE] {}: Beyond culling distance, culled", particle.getClass().getSimpleName());
    }
    return true;
}
                renderedParticles++;
                return false;
            });
        }

        int maxParticles = IcedTeaMod.getConfig().getMaxParticles();
        if (totalParticles > maxParticles) {
            cullExcessParticles(maxParticles);
        }

        if (++frameCounter >= 20) {
            IcedTeaHudOverlay.updateParticleStats(
                totalParticles,
                renderedParticles
            );
            frameCounter = 0;
        }
    }

    @Inject(
        method = "render",
        at = @At("HEAD")
    )
    private void onRenderParticlesPre(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, LightTexture lightTexture, Camera camera, float partialTicks, CallbackInfo ci) {
        if (!IcedTeaMod.isModEnabled() || !IcedTeaMod.getConfig().isParticleCullingEnabled()) {
            return;
        }

        batchedParticles.clear();

        for (Map.Entry<ParticleRenderType, Queue<Particle>> entry : particles.entrySet()) {
            ParticleRenderType renderType = entry.getKey();
            Queue<Particle> particleQueue = entry.getValue();

            if (particleQueue.isEmpty()) continue;

            List<Particle> batch = batchedParticles.computeIfAbsent(
                renderType,
                k -> new ArrayList<>()
            );

            batch.addAll(particleQueue);
        }
    }

    private void cullExcessParticles(int maxParticles) {
    Minecraft mc = Minecraft.getInstance();
    Vec3 cameraPos = mc.cameraEntity != null ?
        mc.cameraEntity.getEyePosition(1.0f) :
        Vec3.ZERO;

    List<ParticleWithDistance> allParticles = new ArrayList<>();

    for (Queue<Particle> particleQueue : particles.values()) {
        for (Particle p : particleQueue) {
            if (p != null) {
                ParticleAccessor accessor = (ParticleAccessor) p;
                double distSq = cameraPos.distanceToSqr(accessor.getXo(), accessor.getYo(), accessor.getZo());
                allParticles.add(new ParticleWithDistance(p, distSq));
            }
        }
    }

    allParticles.sort((a, b) -> {
        boolean aImportant = isImportantParticle(a.particle);
        boolean bImportant = isImportantParticle(b.particle);
        if (aImportant && !bImportant) return 1;
        if (!aImportant && bImportant) return -1;
        return Double.compare(b.distanceSq, a.distanceSq);
    });

    int toRemove = totalParticles - maxParticles;
    int removed = 0;

    for (int i = 0; i < toRemove && i < allParticles.size(); i++) {
        Particle toRemoveParticle = allParticles.get(i).particle;
        for (Queue<Particle> particleQueue : particles.values()) {
            if (particleQueue.remove(toRemoveParticle)) {
                removed++;
                if (IcedTeaMod.isDebugMode()) {
                    IcedTeaMod.LOGGER.info("[IcedTea][PARTICLE] {}: Excess particle culled (distanceSq: {:.1f})", toRemoveParticle.getClass().getSimpleName(), allParticles.get(i).distanceSq);
                }
                break;
            }
        }
    }
}

private boolean isImportantParticle(Particle p) {
    String name = p.getClass().getSimpleName().toLowerCase();
    List<String> important = IcedTeaMod.getConfig().getImportantParticles();
    for (String type : important) {
        if (name.contains(type)) return true;
    }
    return false;
}
}