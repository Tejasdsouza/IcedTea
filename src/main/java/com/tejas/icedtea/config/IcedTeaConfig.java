package com.tejas.icedtea.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tejas.icedtea.IcedTeaMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;


public class IcedTeaConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final File CONFIG_FILE = new File(
        FabricLoader.getInstance().getConfigDir().toFile(),
        "icedtea.json"
    );
    
    private List<String> importantParticles = Arrays.asList(
    "redstone", "portal", "note", "flame", "lava", "water"
);

    public List<String> getImportantParticles() { return importantParticles; }
    public void setImportantParticles(List<String> particles) { this.importantParticles = particles; }
    private boolean occlusionCullingEnabled = true;
    private boolean undergroundCullingEnabled = true;
    private float occlusionAggressiveness = 0.8f;
    private int occlusionCacheSize = 2048;
    
    private boolean entityCullingEnabled = true;
    private double entityCullingDistance = 128.0;
    private boolean entityLODEnabled = true;
    
    private boolean particleCullingEnabled = true;
    private double particleCullingDistance = 64.0;
    private int maxParticles = 4000;
    
    private boolean blockEntityCullingEnabled = true;
    private boolean blockEntityCachingEnabled = true;
    
    private int threadCount = Math.max(2, Runtime.getRuntime().availableProcessors() - 2);
    
    private boolean performanceOverlayEnabled = false;
    private boolean showDetailedStats = false;
    
    private boolean enhancedFrustumCulling = true;
    private boolean portalDetection = true;

    private long cacheExpirationTimeMs = 1000;
    private int maxRaycastDistance = 256;

    private int maxCacheSize = 4096;
    private long cacheValidityMs = 150;

    public static IcedTeaConfig load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                IcedTeaConfig config = GSON.fromJson(reader, IcedTeaConfig.class);
                IcedTeaMod.LOGGER.info("Configuration loaded from {}", CONFIG_FILE.getName());
                return config;
            } catch (IOException e) {
                IcedTeaMod.LOGGER.error("Failed to load configuration, using defaults", e);
            }
        }
        
        IcedTeaConfig config = new IcedTeaConfig();
        config.save();
        return config;
    }
    
    public void save() {
        try {
            CONFIG_FILE.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(this, writer);
                IcedTeaMod.LOGGER.info("Configuration saved to {}", CONFIG_FILE.getName());
            }
        } catch (IOException e) {
            IcedTeaMod.LOGGER.error("Failed to save configuration", e);
        }
    }
    
    public boolean isOcclusionCullingEnabled() { return occlusionCullingEnabled; }
    public boolean isUndergroundCullingEnabled() { return undergroundCullingEnabled; }
    public float getOcclusionAggressiveness() { return occlusionAggressiveness; }
    public int getOcclusionCacheSize() { return occlusionCacheSize; }
    
    public boolean isEntityCullingEnabled() { return entityCullingEnabled; }
    public double getEntityCullingDistance() { return entityCullingDistance; }
    public boolean isEntityLODEnabled() { return entityLODEnabled; }
    
    public boolean isParticleCullingEnabled() { return particleCullingEnabled; }
    public double getParticleCullingDistance() { return particleCullingDistance; }
    public int getMaxParticles() { return maxParticles; }
    
    public boolean isBlockEntityCullingEnabled() { return blockEntityCullingEnabled; }
    public boolean isBlockEntityCachingEnabled() { return blockEntityCachingEnabled; }
    
    public int getThreadCount() { return threadCount; }
    
    public boolean isPerformanceOverlayEnabled() { return performanceOverlayEnabled; }
    public boolean isShowDetailedStats() { return showDetailedStats; }
    
    public boolean isEnhancedFrustumCulling() { return enhancedFrustumCulling; }
    public boolean isPortalDetection() { return portalDetection; }
    
    public void setOcclusionCullingEnabled(boolean enabled) { this.occlusionCullingEnabled = enabled; }
    public void setEntityCullingEnabled(boolean enabled) { this.entityCullingEnabled = enabled; }
    public void setParticleCullingEnabled(boolean enabled) { this.particleCullingEnabled = enabled; }
    public void setPerformanceOverlayEnabled(boolean enabled) { this.performanceOverlayEnabled = enabled; }

    public long getCacheExpirationTimeMs() { return cacheExpirationTimeMs; }
    public void setCacheExpirationTimeMs(long ms) { this.cacheExpirationTimeMs = ms; }

    public int getMaxRaycastDistance() { return maxRaycastDistance; }
    public void setMaxRaycastDistance(int dist) { this.maxRaycastDistance = dist; }

    public int getMaxCacheSize() { return maxCacheSize; }
    public void setMaxCacheSize(int size) { this.maxCacheSize = size; }

    public long getCacheValidityMs() { return cacheValidityMs; }
    public void setCacheValidityMs(long ms) { this.cacheValidityMs = ms; }
    public void setUndergroundCullingEnabled(boolean value) { this.undergroundCullingEnabled = value; }
    public void setOcclusionAggressiveness(float value) { this.occlusionAggressiveness = value; }
    public void setOcclusionCacheSize(int value) { this.occlusionCacheSize = value; }
    public void setEntityCullingDistance(double value) { this.entityCullingDistance = value; }
    public void setEntityLODEnabled(boolean value) { this.entityLODEnabled = value; }
    public void setParticleCullingDistance(double value) { this.particleCullingDistance = value; }
    public void setMaxParticles(int value) { this.maxParticles = value; }
    public void setBlockEntityCullingEnabled(boolean value) { this.blockEntityCullingEnabled = value; }
    public void setBlockEntityCachingEnabled(boolean value) { this.blockEntityCachingEnabled = value; }
    public void setThreadCount(int value) { this.threadCount = value; }
    public void setShowDetailedStats(boolean value) { this.showDetailedStats = value; }
    public void setEnhancedFrustumCulling(boolean value) { this.enhancedFrustumCulling = value; }
    public void setPortalDetection(boolean value) { this.portalDetection = value; }
    
}