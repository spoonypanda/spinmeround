package com.spoonypanda.spinmeround.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.ParticleNote;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Keeps vanilla note colors and animation, with an outward launch from the grinder. */
public final class CrankNoteParticle extends ParticleNote {
    private CrankNoteParticle(World world, BlockPos grinder, double outwardAngleRadians, double spawnRadius) {
        super(world, grinder.getX() + 0.5 + Math.cos(outwardAngleRadians) * spawnRadius,
                grinder.getY() + 1.15 + world.rand.nextDouble() * 0.1,
                grinder.getZ() + 0.5 + Math.sin(outwardAngleRadians) * spawnRadius,
                world.rand.nextDouble(), 0, 0);
        double outwardSpeed = 0.12 + world.rand.nextDouble() * 0.06;
        motionX = Math.cos(outwardAngleRadians) * outwardSpeed;
        motionY = 0.18 + world.rand.nextDouble() * 0.06;
        motionZ = Math.sin(outwardAngleRadians) * outwardSpeed;
    }

    public static void spawn(Minecraft minecraft, World world, BlockPos grinder) {
        Entity camera = minecraft.getRenderViewEntity();
        if (camera == null || camera.getDistanceSqToCenter(grinder) > 32 * 32) {
            return;
        }
        int particleSetting = minecraft.gameSettings.particleSetting;
        // Match vanilla's minimal/decreased particle settings when adding a custom effect.
        if (particleSetting >= 2 || (particleSetting == 1 && world.rand.nextInt(3) == 0)) {
            return;
        }
        double outwardAngleRadians = world.rand.nextDouble() * Math.PI * 2;
        double spawnRadius = Math.sqrt(world.rand.nextDouble()) * 0.18;
        minecraft.effectRenderer.addEffect(new CrankNoteParticle(world, grinder, outwardAngleRadians, spawnRadius));
    }
}
