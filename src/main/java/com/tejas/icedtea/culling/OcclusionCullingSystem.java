package com.tejas.icedtea.culling;

import com.tejas.icedtea.IcedTeaMod;
import com.tejas.icedtea.client.IcedTeaHudOverlay;
import com.tejas.icedtea.config.IcedTeaConfig;
import com.tejas.icedtea.optimization.BiomeAwareOptimizer;
import com.tejas.icedtea.optimization.LowDensityOptimizer;
import com.tejas.icedtea.util.ThreadPoolManager;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.joml.FrustumIntersection;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class OcclusionCullingSystem {
    private final IcedTeaConfig config;
    private final ChunkVisibilityCache visibilityCache;
    private final RaycastEngine raycastEngine;
    private final ConcurrentHashMap<ChunkPos, Boolean> visibleChunks;
    private final RenderCacheSystem renderCache;
    private final BiomeAwareOptimizer biomeOptimizer;
    private final LowDensityOptimizer densityOptimizer;
    
    private final AtomicInteger totalChunks = new AtomicInteger(0);
    private final AtomicInteger culledChunks = new AtomicInteger(0);
    
    private final ConcurrentHashMap<ChunkPos, Integer> chunkHeightMap;
    
    private long lastCullDuration = 5_000_000;
    private static final long TARGET_CULL_TIME_NS = 8_000_000;
    
    public OcclusionCullingSystem(IcedTeaConfig config) {
        this.config = config;
        this.visibilityCache = new ChunkVisibilityCache(config.getOcclusionCacheSize());
        this.raycastEngine = new RaycastEngine();
        this.visibleChunks = new ConcurrentHashMap<>();
        this.chunkHeightMap = new ConcurrentHashMap<>();
        this.renderCache = new RenderCacheSystem();
        this.biomeOptimizer = new BiomeAwareOptimizer();
        this.densityOptimizer = new LowDensityOptimizer();
    }
    
    public void cullChunks(Camera camera, FrustumIntersection frustum, int renderDistance) {
        if (!config.isOcclusionCullingEnabled() || !IcedTeaMod.isModEnabled()) {
            return;
        }
        
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) return;
        
        densityOptimizer.analyzeArea(mc);
        
        Vec3 cameraPos = camera.getPosition();
        ChunkPos cameraChunkPos = new ChunkPos(
            BlockPos.containing(cameraPos.x, cameraPos.y, cameraPos.z)
        );
        
        visibleChunks.clear();
        totalChunks.set(0);
        culledChunks.set(0);
        
        if (totalChunks.get() % 100 == 0) {
            visibilityCache.cleanExpired();
        }
        
        List<ChunkPos> chunksToTest = new ArrayList<>();
        for (int x = -renderDistance; x <= renderDistance; x++) {
            for (int z = -renderDistance; z <= renderDistance; z++) {
                ChunkPos chunkPos = new ChunkPos(
                    cameraChunkPos.x + x,
                    cameraChunkPos.z + z
                );
                
                if (chunkPos.getChessboardDistance(cameraChunkPos) <= renderDistance) {
                    chunksToTest.add(chunkPos);
                    totalChunks.incrementAndGet();
                }
            }
        }
        
        ExecutorService executor = ThreadPoolManager.getExecutor();
        int threadCount = config.getThreadCount();
        int chunkCount = chunksToTest.size();
        int chunksPerThread = Math.max(1, chunkCount / threadCount);
        
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        long timeoutMs = Math.max(3, Math.min(16, lastCullDuration / 1_000_000));
        
        long startTime = System.nanoTime();
        
        for (int i = 0; i < threadCount; i++) {
            int startIdx = i * chunksPerThread;
            int endIdx = (i == threadCount - 1) ? chunkCount : (i + 1) * chunksPerThread;
            
            if (startIdx >= chunkCount) {
                latch.countDown();
                continue;
            }
            
            List<ChunkPos> batch = chunksToTest.subList(startIdx, Math.min(endIdx, chunkCount));
            
            executor.submit(() -> {
                try {
                    for (ChunkPos chunkPos : batch) {
                        boolean visible = testChunkVisibility(
                            chunkPos, cameraPos, level, frustum, camera
                        );
                        visibleChunks.put(chunkPos, visible);
                        if (!visible) {
                            culledChunks.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    IcedTeaMod.LOGGER.error("Error in chunk culling thread", e);
                } finally {
                    latch.countDown();
                }
            });
        }
        
        try {
            latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        lastCullDuration = System.nanoTime() - startTime;
        
        IcedTeaHudOverlay.updateChunkStats(
            totalChunks.get(), 
            totalChunks.get() - culledChunks.get()
        );
    }
    
private boolean testChunkVisibility(ChunkPos chunkPos, Vec3 cameraPos, 
                                    Level level, FrustumIntersection frustum, Camera camera) {

    ChunkPos cameraChunkPos = new ChunkPos(
        BlockPos.containing(cameraPos.x, cameraPos.y, cameraPos.z)
    );
    if (chunkPos.equals(cameraChunkPos)) {
        if (IcedTeaMod.isDebugMode()) {
            IcedTeaMod.LOGGER.info("[IcedTea][CULL] {}: Camera chunk, always visible", chunkPos);
        }
        cacheResult(chunkPos, true, 0);
        return true;
    }

    if (renderCache.hasValidCache(chunkPos)) {
        if (IcedTeaMod.isDebugMode()) {
            IcedTeaMod.LOGGER.info("[IcedTea][CACHE] {}: Using render cache: {}", chunkPos, renderCache.shouldRenderCached(chunkPos));
        }
        return renderCache.shouldRenderCached(chunkPos);
    }

    OcclusionData cachedData = visibilityCache.get(chunkPos);
    if (cachedData != null && !cachedData.isExpired()) {
        if (IcedTeaMod.isDebugMode()) {
            IcedTeaMod.LOGGER.info("[IcedTea][CACHE] {}: Using visibility cache: {}", chunkPos, cachedData.isVisible());
        }
        return cachedData.isVisible();
    }

    Vec3 chunkCenter = getChunkCenter(chunkPos);
    double distance = cameraPos.distanceTo(chunkCenter);

    BiomeAwareOptimizer.OptimizationProfile profile = 
        biomeOptimizer.getOptimizationProfile(chunkPos, level);

    LowDensityOptimizer.OptimizationSettings densitySettings =
        densityOptimizer.getSettings(chunkPos);

    if (config.isEnhancedFrustumCulling()) {
        double chunkRadius = 128.5;

        if (!frustum.testSphere(
            (float) chunkCenter.x, 
            (float) chunkCenter.y, 
            (float) chunkCenter.z, 
            (float) chunkRadius)) {
            if (IcedTeaMod.isDebugMode()) {
                IcedTeaMod.LOGGER.info("[IcedTea][FRUSTUM] {}: Outside frustum, culled", chunkPos);
            }
            cacheResult(chunkPos, false, distance);
            return false;
        }
    }

    if (config.isUndergroundCullingEnabled()) {
        if (isUndergroundChunkCulled(chunkPos, cameraPos, level)) {
            if (IcedTeaMod.isDebugMode()) {
                IcedTeaMod.LOGGER.info("[IcedTea][UNDERGROUND] {}: Underground culling, culled", chunkPos);
            }
            cacheResult(chunkPos, false, distance);
            return false;
        }
    }

    if (distance > profile.cullingDistance) {
        if (IcedTeaMod.isDebugMode()) {
            IcedTeaMod.LOGGER.info("[IcedTea][DISTANCE] {}: Beyond biome culling distance ({:.1f} > {:.1f}), culled", chunkPos, distance, profile.cullingDistance);
        }
        cacheResult(chunkPos, false, distance);
        return false;
    }

    if (!densitySettings.skipDetailedChecks && shouldPerformRaycastTest(chunkPos, cameraPos)) {
        float aggressiveness = Math.max(
            config.getOcclusionAggressiveness(),
            profile.aggressiveness
        );

        boolean occluded = raycastEngine.isChunkOccluded(
            cameraPos, 
            chunkCenter, 
            level,
            aggressiveness
        );

        if (IcedTeaMod.isDebugMode()) {
            IcedTeaMod.LOGGER.info("[IcedTea][RAYCAST] {}: Raycast test (aggr {:.2f}) result: {}", chunkPos, aggressiveness, occluded ? "CULLED" : "VISIBLE");
        }

        if (occluded) {
            cacheResult(chunkPos, false, distance);
            return false;
        }
    } else if (IcedTeaMod.isDebugMode()) {
        IcedTeaMod.LOGGER.info("[IcedTea][DENSITY/LOD] {}: Skipped detailed checks (density/LOD)", chunkPos);
    }

    if (IcedTeaMod.isDebugMode()) {
        IcedTeaMod.LOGGER.info("[IcedTea][VISIBLE] {}: Passed all checks, visible", chunkPos);
    }
    cacheResult(chunkPos, true, distance);
    return true;
    }

    private void cacheResult(ChunkPos pos, boolean visible, double distance) {
        visibilityCache.put(pos, new OcclusionData(visible));
        renderCache.cacheChunkRender(pos, visible, distance);
    }

    private boolean isUndergroundChunkCulled(ChunkPos chunkPos, Vec3 cameraPos, Level level) {
        if (cameraPos.y < 63) return false;
        
        Integer cachedHeight = chunkHeightMap.get(chunkPos);
        int avgChunkY;
        
        if (cachedHeight != null) {
            avgChunkY = cachedHeight;
        } else {
            avgChunkY = calculateChunkAverageHeight(chunkPos, level);
            chunkHeightMap.put(chunkPos, avgChunkY);
        }
        
        if (cameraPos.y - avgChunkY < 32) return false;
        
        if (hasVerticalOpening(chunkPos, cameraPos, level)) {
            return false;
        }
        
        LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);
        if (chunk == null) return false;
        
        int samples = 0;
        int solidAbove = 0;
        
        for (int x = 0; x < 16; x += 4) {
            for (int z = 0; z < 16; z += 4) {
                samples++;
                BlockPos pos = new BlockPos(
                    chunkPos.getMinBlockX() + x,
                    (int) cameraPos.y - 1,
                    chunkPos.getMinBlockZ() + z
                );
                
                if (!level.isLoaded(pos)) continue;
                
                BlockState state = level.getBlockState(pos);
                if (!state.isAir() && state.isSolidRender(level, pos)) {
                    solidAbove++;
                }
            }
        }
        
        return (double) solidAbove / samples > 0.6;
    }
    
    private boolean hasVerticalOpening(ChunkPos chunkPos, Vec3 cameraPos, Level level) {
        for (int x = 4; x < 16; x += 6) {
            for (int z = 4; z < 16; z += 6) {
                BlockPos topPos = new BlockPos(
                    chunkPos.getMinBlockX() + x,
                    (int) cameraPos.y,
                    chunkPos.getMinBlockZ() + z
                );
                
                int airBlocks = 0;
                for (int y = 0; y < 20; y++) {
                    BlockPos checkPos = topPos.below(y);
                    if (!level.isLoaded(checkPos)) break;
                    
                    if (level.getBlockState(checkPos).isAir()) {
                        airBlocks++;
                        if (airBlocks >= 4) {
                            return true;
                        }
                    } else {
                        airBlocks = 0;
                    }
                }
            }
        }
        return false;
    }

    private int calculateChunkAverageHeight(ChunkPos chunkPos, Level level) {
        LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);
        if (chunk == null) return 64;
        
        int totalHeight = 0;
        int samples = 0;
        
        for (int x = 0; x < 16; x += 8) {
            for (int z = 0; z < 16; z += 8) {
                int height = chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z);
                totalHeight += height;
                samples++;
            }
        }
        
        return samples > 0 ? totalHeight / samples : 64;
    }

    private boolean shouldPerformRaycastTest(ChunkPos chunkPos, Vec3 cameraPos) {
        Vec3 chunkCenter = getChunkCenter(chunkPos);
        double distance = cameraPos.distanceTo(chunkCenter);
        return distance > 64 && distance < 192;
    }

    private Vec3 getChunkCenter(ChunkPos chunkPos) {
        return new Vec3(
            chunkPos.getMinBlockX() + 8,
            64,
            chunkPos.getMinBlockZ() + 8
        );
    }

    public boolean isChunkVisible(ChunkPos chunkPos) {
        return visibleChunks.getOrDefault(chunkPos, true);
    }
    
    public boolean shouldRenderChunk(ChunkPos chunkPos) {
        return isChunkVisible(chunkPos);
    }
    
    public double getCullingEfficiency() {
        int total = totalChunks.get();
        if (total == 0) return 0.0;
        return (culledChunks.get() * 100.0) / total;
    }

    public void updateConfig(IcedTeaConfig newConfig) {
        visibilityCache.clear();
        chunkHeightMap.clear();
        renderCache.clear();
        biomeOptimizer.clear();
        densityOptimizer.clear();
    }
    
    public void clearCache() {
        visibilityCache.clear();
        visibleChunks.clear();
        chunkHeightMap.clear();
        renderCache.clear();
        biomeOptimizer.clear();
        densityOptimizer.clear();
    }
    
    public RenderCacheSystem getRenderCache() {
        return renderCache;
    }
}