package com.tejas.icedtea.culling;

import net.minecraft.world.level.ChunkPos;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class ChunkVisibilityCache {
    private final ConcurrentHashMap<ChunkPos, OcclusionData> cache;
    private final ConcurrentLinkedDeque<ChunkPos> accessOrder;
    private final int maxSize;
    
    public ChunkVisibilityCache(int maxSize) {
        this.maxSize = maxSize;
        this.cache = new ConcurrentHashMap<>(maxSize);
        this.accessOrder = new ConcurrentLinkedDeque<>();
    }
    
    public OcclusionData get(ChunkPos chunkPos) {
        OcclusionData data = cache.get(chunkPos);
        if (data != null) {
            accessOrder.remove(chunkPos);
            accessOrder.addLast(chunkPos);
        }
        return data;
    }
    
    public void put(ChunkPos chunkPos, OcclusionData data) {
        if (cache.containsKey(chunkPos)) {
            accessOrder.remove(chunkPos);
        } else if (cache.size() >= maxSize) {
            ChunkPos oldest = accessOrder.pollFirst();
            if (oldest != null) {
                cache.remove(oldest);
            }
        }
        
        cache.put(chunkPos, data);
        accessOrder.addLast(chunkPos);
    }
    
    public void clear() {
        cache.clear();
        accessOrder.clear();
    }
    
    public int size() {
        return cache.size();
    }

    public void cleanExpired() {
        long currentTime = System.currentTimeMillis();
        cache.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().isExpired();
            if (expired) {
                accessOrder.remove(entry.getKey());
            }
            return expired;
        });
    }
}