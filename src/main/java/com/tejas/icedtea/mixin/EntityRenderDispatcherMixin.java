package com.tejas.icedtea.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tejas.icedtea.IcedTeaMod;
import com.tejas.icedtea.client.IcedTeaHudOverlay;
import com.tejas.icedtea.culling.RaycastEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.entity.player.Player;
import java.util.Map;
import java.util.WeakHashMap;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
    
    private static final AtomicInteger totalEntities = new AtomicInteger(0);
    private static final AtomicInteger renderedEntities = new AtomicInteger(0);
    private static final AtomicInteger frameCounter = new AtomicInteger(0);
    
    private static final RaycastEngine raycastEngine = new RaycastEngine();
    
    private static int frameRaycastBudget = 0;
    private static final int MAX_RAYCASTS_PER_FRAME = 20;
    private static final ConcurrentHashMap<Integer, Long> lastRaycastTime = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, Boolean> raycastResultCache = new ConcurrentHashMap<>();
    private static final Map<Entity, Long> recentlyHitEntities = new WeakHashMap<>();
    private static final long RECENT_HIT_DURATION = 10000;
    private static final long RAYCAST_CACHE_TIME = 500;
    private static long lastCleanupTime = 0;

    private static void onEntityHit(Entity entity) {
    recentlyHitEntities.put(entity, System.currentTimeMillis());
}
    
    @Inject(
        method = "render",
        at = @At("HEAD"),
        cancellable = true
    )
    private <E extends Entity> void onRenderEntity(
        E entity,
        double x, double y, double z,
        float yaw,
        float partialTicks,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int packedLight,
        CallbackInfo ci
    ) {
        if (!IcedTeaMod.isModEnabled()) {
            renderedEntities.incrementAndGet();
            return;
        }

        if (entity instanceof Player) {
            renderedEntities.incrementAndGet();
            return;
        }

        Long lastHit = recentlyHitEntities.get(entity);
        if (lastHit != null && System.currentTimeMillis() - lastHit < RECENT_HIT_DURATION) {
    renderedEntities.incrementAndGet();
    return;
}
        
        totalEntities.incrementAndGet();
        
        if (!IcedTeaMod.getConfig().isEntityCullingEnabled()) {
            renderedEntities.incrementAndGet();
            return;
        }
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.cameraEntity == null) {
            renderedEntities.incrementAndGet();
            return;
        }
        
        Vec3 cameraPos = mc.cameraEntity.getEyePosition(partialTicks);
        Vec3 entityPos = entity.position();
        double distance = cameraPos.distanceTo(entityPos);
        
        double cullDistance = IcedTeaMod.getConfig().getEntityCullingDistance();
if (distance > cullDistance) {
    if (IcedTeaMod.isDebugMode()) {
        IcedTeaMod.LOGGER.info("[IcedTea][ENTITY] {}: Beyond culling distance ({:.1f} > {:.1f}), culled", entity.getType().toString(), distance, cullDistance);
    }
    ci.cancel();
    checkAndUpdateStats();
    return;
}

if (IcedTeaMod.getConfig().isEntityLODEnabled() && distance > cullDistance * 0.5) {
    if (entity.getBoundingBox().getSize() < 0.5 && distance > cullDistance * 0.7) {
        if (IcedTeaMod.isDebugMode()) {
            IcedTeaMod.LOGGER.info("[IcedTea][ENTITY][LOD] {}: LOD culled (size {:.2f}, dist {:.1f})", entity.getType().toString(), entity.getBoundingBox().getSize(), distance);
        }
        ci.cancel();
        checkAndUpdateStats();
        return;
    }
}



if (isEntityInsideOpaqueBlock(entity)) {
    if (IcedTeaMod.isDebugMode()) {
        IcedTeaMod.LOGGER.info("[IcedTea][ENTITY] {}: Inside opaque block, culled", entity.getType().toString());
    }
    ci.cancel();
    checkAndUpdateStats();
    return;
}

BlockPos entityBlockPos = entity.blockPosition();
if (!mc.level.isLoaded(entityBlockPos)) {
    if (IcedTeaMod.isDebugMode()) {
        IcedTeaMod.LOGGER.info("[IcedTea][ENTITY] {}: Chunk not loaded, culled", entity.getType().toString());
    }
    ci.cancel();
    checkAndUpdateStats();
    return;
}

ChunkPos entityChunkPos = new ChunkPos(entityBlockPos);
if (IcedTeaMod.getCullingSystem() != null && 
    !IcedTeaMod.getCullingSystem().shouldRenderChunk(entityChunkPos)) {
    if (IcedTeaMod.isDebugMode()) {
        IcedTeaMod.LOGGER.info("[IcedTea][ENTITY] {}: Parent chunk not visible, culled", entity.getType().toString());
    }
    ci.cancel();
    checkAndUpdateStats();
    return;
}

if (distance > 48.0 && distance < cullDistance) {
    if (shouldPerformRaycast(entity, distance)) {
        boolean los = raycastEngine.hasLineOfSight(cameraPos, entityPos, mc.level);
        if (IcedTeaMod.isDebugMode()) {
            IcedTeaMod.LOGGER.info("[IcedTea][ENTITY][RAYCAST] {}: Raycast LOS: {}", entity.getType().toString(), los);
        }
        if (!los) {
            raycastResultCache.put(entity.getId(), false);
            ci.cancel();
            checkAndUpdateStats();
            return;
        }
        raycastResultCache.put(entity.getId(), true);
    } else {
        Boolean cachedResult = raycastResultCache.get(entity.getId());
        if (cachedResult != null && !cachedResult) {
            if (IcedTeaMod.isDebugMode()) {
                IcedTeaMod.LOGGER.info("[IcedTea][ENTITY][RAYCAST] {}: Cached raycast result: CULLED", entity.getType().toString());
            }
            ci.cancel();
            checkAndUpdateStats();
            return;
        }
    }
}
        
        renderedEntities.incrementAndGet();
    }
    
    private boolean shouldPerformRaycast(Entity entity, double distance) {
        if (distance < 48.0) {
            return false;
        }
        
        if (frameRaycastBudget >= MAX_RAYCASTS_PER_FRAME) {
            return false;
        }
        
        int entityId = entity.getId();
        Long lastTest = lastRaycastTime.get(entityId);
        long now = System.currentTimeMillis();
        
        if (lastTest != null && (now - lastTest) < RAYCAST_CACHE_TIME) {
            return false;
        }
        
        if ((entityId % 10) < 7) {
            return false;
        }
        
        frameRaycastBudget++;
        lastRaycastTime.put(entityId, now);
        return true;
    }

    private boolean isEntityInsideOpaqueBlock(Entity entity) {
        Level level = entity.level();
        if (level == null) return false;
        
        BlockPos pos = entity.blockPosition();
        if (!level.isLoaded(pos)) return false;
        
        BlockState state = level.getBlockState(pos);
        
        if (!state.isAir() && state.isSolidRender(level, pos) && state.canOcclude()) {
            BlockPos headPos = new BlockPos(
                (int) entity.getX(),
                (int) (entity.getY() + entity.getEyeHeight()),
                (int) entity.getZ()
            );
            
            if (level.isLoaded(headPos)) {
                BlockState headState = level.getBlockState(headPos);
                return !headState.isAir() && headState.isSolidRender(level, headPos);
            }
            
            return true;
        }
        
        return false;
    }
    
    private static void checkAndUpdateStats() {
        if (frameCounter.incrementAndGet() >= 60) {
            updateStats();
        }
    }
    
    @Inject(
        method = "render",
        at = @At("RETURN")
    )
    private void onPostRender(CallbackInfo ci) {
        if (!IcedTeaMod.isModEnabled()) {
            return;
        }
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getGameTime() % 20 == 0) {
            frameRaycastBudget = 0;
            
            long now = System.currentTimeMillis();
            if (now - lastCleanupTime > 1000) {
                cleanupCaches(now);
                lastCleanupTime = now;
            }
        }
        
        checkAndUpdateStats();
    }
    
    private void cleanupCaches(long currentTime) {
        if (lastRaycastTime.size() > 500) {
            lastRaycastTime.entrySet().removeIf(entry -> 
                currentTime - entry.getValue() > 2000
            );
        }
        
        if (raycastResultCache.size() > 500) {
            raycastResultCache.clear();
        }
    }

    private static void updateStats() {
        int total = totalEntities.get();
        int rendered = renderedEntities.get();
        
        if (total > 0) {
            IcedTeaHudOverlay.updateEntityStats(total, rendered);
            totalEntities.set(0);
            renderedEntities.set(0);
            frameCounter.set(0);
        }
    }
}