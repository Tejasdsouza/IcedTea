package com.tejas.icedtea.mixin;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Particle.class)
public interface ParticleAccessor {
    @Accessor("xo")
    double getXo();

    @Accessor("yo")
    double getYo();

    @Accessor("zo")
    double getZo();
}