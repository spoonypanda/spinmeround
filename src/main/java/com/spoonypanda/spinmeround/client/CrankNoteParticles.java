package com.spoonypanda.spinmeround.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;

/** Vanilla note appearance and animation with the original radial, upward launch. */
final class CrankNoteParticles {
    static void spawn(Minecraft minecraft, ClientLevel level, BlockPos origin) {
        var camera = minecraft.getCameraEntity();
        if (camera == null || camera.distanceToSqr(origin.getX() + .5, origin.getY() + .5, origin.getZ() + .5) > 32 * 32) return;
        ParticleStatus setting = minecraft.options.particles().get();
        if (setting == ParticleStatus.MINIMAL || (setting == ParticleStatus.DECREASED && level.random.nextInt(3) == 0)) return;

        double angle = level.random.nextDouble() * Math.PI * 2;
        double radius = Math.sqrt(level.random.nextDouble()) * .18;
        var particle = minecraft.particleEngine.createParticle(ParticleTypes.NOTE,
                origin.getX() + .5 + Math.cos(angle) * radius,
                origin.getY() + 1.15 + level.random.nextDouble() * .1,
                origin.getZ() + .5 + Math.sin(angle) * radius,
                level.random.nextDouble(), 0, 0);
        if (particle != null) {
            // NOTE interprets the first speed argument as its color; set motion afterward.
            double outwardSpeed = .12 + level.random.nextDouble() * .06;
            particle.setParticleSpeed(Math.cos(angle) * outwardSpeed,
                    .18 + level.random.nextDouble() * .06, Math.sin(angle) * outwardSpeed);
        }
    }

    private CrankNoteParticles() {}
}
