package com.tejas.icedtea.culling;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class RaycastEngine {
    private static final int MAX_RAYCAST_DISTANCE = 256;
    private static final double STEP_SIZE = 0.75;

    public boolean isChunkOccluded(Vec3 origin, Vec3 target, Level level, float aggressiveness) {
        if (level == null) return false;
        
        double distance = origin.distanceTo(target);
        if (distance > MAX_RAYCAST_DISTANCE) return false;
        
        Vec3 direction = target.subtract(origin).normalize();

        int requiredOpaqueBlocks = (int) (2 + (aggressiveness * 4));
        int opaqueBlocksHit = 0;
        int totalSamples = 0;
        
        double stepSize = STEP_SIZE * (1.8 - (aggressiveness * 0.8));
        int maxSteps = (int) (distance / stepSize);
        
        BlockPos lastPos = null;
        int consecutiveAir = 0;
        int consecutiveOpaque = 0;
        
        for (int i = 1; i < maxSteps; i++) {
            Vec3 point = origin.add(direction.scale(i * stepSize));
            BlockPos pos = BlockPos.containing(point.x, point.y, point.z);
            
            if (pos.equals(lastPos)) continue;
            lastPos = pos;
            
            totalSamples++;
            
            if (totalSamples > 15 && opaqueBlocksHit == 0) {
                return false;
            }
            
            if (!level.isLoaded(pos)) {
                return false;
            }
            
            BlockState state = level.getBlockState(pos);
            
            if (!state.isAir() && state.isSolidRender(level, pos)) {
                opaqueBlocksHit++;
                consecutiveOpaque++;
                consecutiveAir = 0;
                
                if (consecutiveOpaque >= 3) {
                    return true;
                }
                
                if (opaqueBlocksHit >= requiredOpaqueBlocks) {
                    return true;
                }
            } else {
                consecutiveAir++;
                consecutiveOpaque = 0;
            }
            
            if (consecutiveAir > 8 && opaqueBlocksHit > 0 && opaqueBlocksHit < requiredOpaqueBlocks) {
                return false;
            }
        }
        
        return false;
    }
    
    public boolean hasLineOfSight(Vec3 from, Vec3 to, Level level) {
        if (level == null) return true;
        
        double distance = from.distanceTo(to);
        if (distance > 128) return true;
        
        Vec3 direction = to.subtract(from).normalize();
        double stepSize = 1.0;
        int maxSteps = (int) (distance / stepSize);
        
        BlockPos lastPos = null;
        int opaqueBlocks = 0;
        
        for (int i = 1; i < maxSteps; i++) {
            Vec3 point = from.add(direction.scale(i * stepSize));
            BlockPos pos = BlockPos.containing(point.x, point.y, point.z);
            
            if (pos.equals(lastPos)) continue;
            lastPos = pos;
            
            if (!level.isLoaded(pos)) {
                return true;
            }
            
            BlockState state = level.getBlockState(pos);
            
            if (!state.isAir() && state.isSolidRender(level, pos) && state.canOcclude()) {
                opaqueBlocks++;
                
                if (opaqueBlocks >= 2) {
                    return false;
                }
            }
        }
        
        return true;
    }

    public boolean hasVisibleOpening(Vec3 origin, Vec3 direction, Level level, double searchDistance) {
        if (level == null) return false;
        
        double stepSize = 2.0;
        int maxSteps = (int) (searchDistance / stepSize);
        
        BlockPos lastPos = null;
        boolean foundOpaque = false;
        
        for (int i = 1; i < maxSteps; i++) {
            Vec3 point = origin.add(direction.scale(i * stepSize));
            BlockPos pos = BlockPos.containing(point.x, point.y, point.z);
            
            if (pos.equals(lastPos)) continue;
            lastPos = pos;
            
            if (!level.isLoaded(pos)) {
                return true;
            }
            
            BlockState state = level.getBlockState(pos);
            
            if (state.isAir()) {
                if (foundOpaque) {
                    return true;
                }
            } else if (state.isSolidRender(level, pos)) {
                foundOpaque = true;
            }
        }
        
        return false;
    }
}