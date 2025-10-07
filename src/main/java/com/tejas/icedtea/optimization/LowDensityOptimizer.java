package com.tejas.icedtea.optimization;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import java.util.concurrent.ConcurrentHashMap;

public class LowDensityOptimizer {
    private final ConcurrentHashMap<ChunkPos, EntityDensityData> densityMap;
    private long lastAnalysis = 0;
    private static final long ANALYSIS_INTERVAL = 1000;
    
    public LowDensityOptimizer() {
        this.densityMap = new ConcurrentHashMap<>();
    }
    
    public void analyzeArea(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (now - lastAnalysis < ANALYSIS_INTERVAL) {
            return;
        }
        
        if (mc.level == null || mc.player == null) {
            return;
        }
        
        densityMap.clear();
        
        Vec3 playerPos = mc.player.position();
        ChunkPos playerChunk = new ChunkPos(mc.player.blockPosition());
        
        for (Entity entity : mc.level.entitiesForRendering()) {
            ChunkPos entityChunk = new ChunkPos(entity.blockPosition());
            
            densityMap.computeIfAbsent(entityChunk, k -> new EntityDensityData())
                     .incrementCount();
        }
        
        lastAnalysis = now;
    }
    
    public boolean isLowDensityArea(ChunkPos pos) {
        EntityDensityData data = densityMap.get(pos);
        return data == null || data.entityCount < 3;
    }
    
    public int getEntityCount(ChunkPos pos) {
        EntityDensityData data = densityMap.get(pos);
        return data != null ? data.entityCount : 0;
    }
    
    public OptimizationSettings getSettings(ChunkPos pos) {
        int count = getEntityCount(pos);
        
        if (count == 0) {
            return new OptimizationSettings(
                true,
                0.95f,
                4,
                true
            );
        } else if (count < 3) {
            return new OptimizationSettings(
                true,
                0.85f,
                3,
                true
            );
        } else if (count < 10) {
            return new OptimizationSettings(
                false,
                0.70f,
                2,
                false
            );
        } else {
            return new OptimizationSettings(
                false,
                0.50f,
                1,
                false
            );
        }
    }
    
    public void clear() {
        densityMap.clear();
    }
    
    private static class EntityDensityData {
        int entityCount = 0;
        
        void incrementCount() {
            entityCount++;
        }
    }
    
    public static class OptimizationSettings {
        public final boolean skipDetailedChecks;
        public final float cullingAggressiveness;
        public final int updateFrequency;
        public final boolean enableDistanceSkipping;
        
        public OptimizationSettings(boolean skipDetailedChecks,
                                   float cullingAggressiveness,
                                   int updateFrequency,
                                   boolean enableDistanceSkipping) {
            this.skipDetailedChecks = skipDetailedChecks;
            this.cullingAggressiveness = cullingAggressiveness;
            this.updateFrequency = updateFrequency;
            this.enableDistanceSkipping = enableDistanceSkipping;
        }
    }
}