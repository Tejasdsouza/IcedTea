package com.tejas.icedtea.optimization;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.ChunkPos;
import java.util.concurrent.ConcurrentHashMap;

public class BiomeAwareOptimizer {
    private final ConcurrentHashMap<ChunkPos, BiomeType> biomeTypeCache;
    private long lastCacheCleanup = 0;
    
    public BiomeAwareOptimizer() {
        this.biomeTypeCache = new ConcurrentHashMap<>();
    }
    
    public OptimizationProfile getOptimizationProfile(ChunkPos chunkPos, Level level) {
        BiomeType type = getBiomeType(chunkPos, level);
        
        return switch (type) {
            case OCEAN -> new OptimizationProfile(
                0.95f,
                200.0,
                true,
                0.3f
            );
            case PLAINS, DESERT -> new OptimizationProfile(
                0.90f,
                180.0,
                true,
                0.4f
            );
            case SPARSE_FOREST -> new OptimizationProfile(
                0.75f,
                140.0,
                true,
                0.6f
            );
            case DENSE_FOREST, JUNGLE -> new OptimizationProfile(
                0.50f,
                100.0,
                false,
                1.0f
            );
            default -> new OptimizationProfile(
                0.70f,
                120.0,
                true,
                0.7f
            );
        };
    }
    
    private BiomeType getBiomeType(ChunkPos chunkPos, Level level) {
        BiomeType cached = biomeTypeCache.get(chunkPos);
        if (cached != null) {
            return cached;
        }
        
        if (level == null) return BiomeType.UNKNOWN;
        
        BlockPos centerPos = new BlockPos(
            chunkPos.getMinBlockX() + 8,
            64,
            chunkPos.getMinBlockZ() + 8
        );
        
        if (!level.isLoaded(centerPos)) {
            return BiomeType.UNKNOWN;
        }
        
        Holder<Biome> biomeHolder = level.getBiome(centerPos);
        BiomeType type = classifyBiome(biomeHolder);
        
        biomeTypeCache.put(chunkPos, type);
        
        cleanupCacheIfNeeded();
        
        return type;
    }
    
    private BiomeType classifyBiome(Holder<Biome> biomeHolder) {
        String biomeName = biomeHolder.toString().toLowerCase();
        
        if (biomeName.contains("ocean") || biomeName.contains("deep")) {
            return BiomeType.OCEAN;
        }
        if (biomeName.contains("plains") || biomeName.contains("savanna")) {
            return BiomeType.PLAINS;
        }
        if (biomeName.contains("desert")) {
            return BiomeType.DESERT;
        }
        if (biomeName.contains("jungle")) {
            return BiomeType.JUNGLE;
        }
        if (biomeName.contains("forest")) {
            if (biomeName.contains("dark") || biomeName.contains("old_growth")) {
                return BiomeType.DENSE_FOREST;
            }
            return BiomeType.SPARSE_FOREST;
        }
        
        return BiomeType.UNKNOWN;
    }
    
    private void cleanupCacheIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastCacheCleanup > 30000) {
            if (biomeTypeCache.size() > 2000) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    ChunkPos playerPos = new ChunkPos(mc.player.blockPosition());
                    biomeTypeCache.entrySet().removeIf(entry ->
                        entry.getKey().getChessboardDistance(playerPos) > 32
                    );
                }
            }
            lastCacheCleanup = now;
        }
    }
    
    public void clear() {
        biomeTypeCache.clear();
    }
    
    public enum BiomeType {
        OCEAN,
        PLAINS,
        DESERT,
        SPARSE_FOREST,
        DENSE_FOREST,
        JUNGLE,
        UNKNOWN
    }
    
    public static class OptimizationProfile {
        public final float aggressiveness;
        public final double cullingDistance;
        public final boolean enableLOD;
        public final float detailMultiplier;
        
        public OptimizationProfile(float aggressiveness, double cullingDistance, 
                                boolean enableLOD, float detailMultiplier) {
            this.aggressiveness = aggressiveness;
            this.cullingDistance = cullingDistance;
            this.enableLOD = enableLOD;
            this.detailMultiplier = detailMultiplier;
        }
    }
}