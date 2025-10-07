package com.tejas.icedtea.culling;

import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.world.level.ChunkPos;
import java.util.concurrent.ConcurrentHashMap;

public class RenderCacheSystem {
    private static final long CACHE_VALIDITY_MS = 150;
    private static final int MAX_CACHE_SIZE = 4096;
    
    private final ConcurrentHashMap<ChunkPos, CachedRenderData> renderCache;
    private final ConcurrentHashMap<ChunkPos, Long> lastModificationTime;
    
    public RenderCacheSystem() {
        this.renderCache = new ConcurrentHashMap<>(1024);
        this.lastModificationTime = new ConcurrentHashMap<>(1024);
    }
    
    public boolean hasValidCache(ChunkPos pos) {
        CachedRenderData data = renderCache.get(pos);
        if (data == null) return false;
        
        Long lastMod = lastModificationTime.get(pos);
        if (lastMod != null && System.currentTimeMillis() - lastMod < 100) {
            return false;
        }
        
        return !data.isExpired();
    }
    
    public void cacheChunkRender(ChunkPos pos, boolean rendered, double distance) {
        if (renderCache.size() >= MAX_CACHE_SIZE) {
            evictOldEntries();
        }
        
        renderCache.put(pos, new CachedRenderData(rendered, distance));
    }
    
    public boolean shouldRenderCached(ChunkPos pos) {
        CachedRenderData data = renderCache.get(pos);
        return data != null && data.wasRendered;
    }
    
    public void invalidateChunk(ChunkPos pos) {
        renderCache.remove(pos);
        lastModificationTime.put(pos, System.currentTimeMillis());
    }
    
    public void invalidateArea(ChunkPos center, int radius) {
        long now = System.currentTimeMillis();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                ChunkPos pos = new ChunkPos(center.x + x, center.z + z);
                renderCache.remove(pos);
                lastModificationTime.put(pos, now);
            }
        }
    }
    
    public void clear() {
        renderCache.clear();
        lastModificationTime.clear();
    }
    
    private void evictOldEntries() {
        long now = System.currentTimeMillis();
        renderCache.entrySet().removeIf(entry -> 
            now - entry.getValue().timestamp > CACHE_VALIDITY_MS * 2
        );
        
        if (renderCache.size() > MAX_CACHE_SIZE * 0.75) {
            lastModificationTime.entrySet().removeIf(entry ->
                now - entry.getValue() > 5000
            );
        }
    }
    
    public int getCacheSize() {
        return renderCache.size();
    }
    
    public double getCacheHitRate() {
        return renderCache.size() > 0 ? 
            (double) renderCache.values().stream().filter(d -> !d.isExpired()).count() / renderCache.size() * 100.0 : 0.0;
    }
    
    private static class CachedRenderData {
        final boolean wasRendered;
        final double distance;
        final long timestamp;
        
        CachedRenderData(boolean wasRendered, double distance) {
            this.wasRendered = wasRendered;
            this.distance = distance;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_VALIDITY_MS;
        }
    }
}