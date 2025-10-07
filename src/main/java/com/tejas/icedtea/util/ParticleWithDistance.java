package com.tejas.icedtea.util;

import net.minecraft.client.particle.Particle;

public class ParticleWithDistance {
    public final Particle particle;
    public final double distanceSq;

    public ParticleWithDistance(Particle particle, double distanceSq) {
        this.particle = particle;
        this.distanceSq = distanceSq;
    }
}