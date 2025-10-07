package com.tejas.icedtea.culling;

public class OcclusionData {
    private final boolean visible;
    private final long timestamp;
    private static final long EXPIRATION_TIME = 1000;
    
    public OcclusionData(boolean visible) {
        this.visible = visible;
        this.timestamp = System.currentTimeMillis();
    }
    
    public boolean isExpired() {
        return System.currentTimeMillis() - timestamp > EXPIRATION_TIME;
    }

    public boolean isVisible() {
        return visible;
    }

    public long getTimestamp() {
        return timestamp;
    }
}