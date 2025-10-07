package com.tejas.icedtea.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tejas.icedtea.IcedTeaMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.ConcurrentHashMap;

@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherMixin {
    
    private static final ConcurrentHashMap<BlockPos, Long> lastRenderTime = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<BlockPos, Integer> renderSkipCounter = new ConcurrentHashMap<>();
    private static final long RENDER_CACHE_TIME = 100;
    
    @Inject(
        method = "render",
        at = @At("HEAD"),
        cancellable = true
    )
    private <E extends BlockEntity> void onRenderBlockEntity(
        E blockEntity,
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        CallbackInfo ci
    ) {
        if (!IcedTeaMod.getConfig().isBlockEntityCullingEnabled()) {
            return;
        }
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.cameraEntity == null) {
            return;
        }
        
        BlockPos pos = blockEntity.getBlockPos();
        Vec3 cameraPos = mc.cameraEntity.getEyePosition(partialTick);
        
        double distance = Math.sqrt(pos.distToCenterSqr(cameraPos));
if (distance > 64.0) {
    if (IcedTeaMod.isDebugMode()) {
        IcedTeaMod.LOGGER.info("[IcedTea][BLOCKENTITY] {}: Beyond culling distance, culled", blockEntity.getType().toString());
    }
    ci.cancel();
    return;
}

if (!mc.level.isLoaded(pos)) {
    if (IcedTeaMod.isDebugMode()) {
        IcedTeaMod.LOGGER.info("[IcedTea][BLOCKENTITY] {}: Chunk not loaded, culled", blockEntity.getType().toString());
    }
    ci.cancel();
    return;
}

if (IcedTeaMod.getConfig().isBlockEntityCachingEnabled()) {
    if (isStaticBlockEntity(blockEntity) && distance > 32.0) {
        Integer skipCount = renderSkipCounter.compute(pos, (k, v) -> {
            if (v == null) return 0;
            return (v + 1) % 4;
        });

        if (skipCount != 0) {
            if (IcedTeaMod.isDebugMode()) {
                IcedTeaMod.LOGGER.info("[IcedTea][BLOCKENTITY][CACHE] {}: Skipped render (cache)", blockEntity.getType().toString());
            }
            ci.cancel();
            return;
        }
    }
}

if (IcedTeaMod.getConfig().isBlockEntityCachingEnabled()) {
    if (isStaticBlockEntity(blockEntity)) {
        Long lastRender = lastRenderTime.get(pos);
        long currentTime = System.currentTimeMillis();

        if (lastRender != null && (currentTime - lastRender) < RENDER_CACHE_TIME) {
            if (distance > 32.0) {
                if (IcedTeaMod.isDebugMode()) {
                    IcedTeaMod.LOGGER.info("[IcedTea][BLOCKENTITY][CACHE] {}: Render throttled (cache)", blockEntity.getType().toString());
                }
                ci.cancel();
                return;
            }
        }

        lastRenderTime.put(pos, currentTime);
    }
}
    }
    
    private boolean isStaticBlockEntity(BlockEntity blockEntity) {
        String className = blockEntity.getClass().getSimpleName();
        return className.contains("Sign") ||
               className.contains("Chest") ||
               className.contains("Barrel") ||
               className.contains("Banner") ||
                className.contains("Lectern") ||
                className.contains("Campfire") ||
                className.contains("Furnace") ||
                className.contains("Smoker") ||
                className.contains("BlastFurnace") ||
                className.contains("ShulkerBox") ||
                className.contains("FlowerPot") ||
                className.contains("Bed") ||
                className.contains("Anvil") ||
                className.contains("EnderChest");

    }
    
    @Inject(
        method = "render",
        at = @At("RETURN")
    )
    private void cleanupCache(CallbackInfo ci) {
        if (lastRenderTime.size() > 1000) {
            long currentTime = System.currentTimeMillis();
            lastRenderTime.entrySet().removeIf(entry -> 
                currentTime - entry.getValue() > 5000
            );
        }
        
        if (renderSkipCounter.size() > 1000) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.cameraEntity != null) {
                Vec3 cameraPos = mc.cameraEntity.getEyePosition(1.0f);
                renderSkipCounter.entrySet().removeIf(entry -> {
                    double dist = Math.sqrt(entry.getKey().distToCenterSqr(cameraPos));
                    return dist > 96.0;
                });
            }
        }
    }
}