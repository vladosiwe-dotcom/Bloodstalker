package com.vladosiwe.gorewidow.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/** Particle implementations for the Gorewidow's silk and blood effects. */
public final class ModParticleProviders {
    private ModParticleProviders() {}

    /** Drifting silk fibre: slow fall, gentle sway, fades out. */
    public static final class WebFiber extends TextureSheetParticle {
        private final float spin;

        WebFiber(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
            super(level, x, y, z, dx, dy, dz);
            this.pickSprite(sprites);
            this.xd = dx + (this.random.nextDouble() - 0.5) * 0.03;
            this.yd = dy + 0.01;
            this.zd = dz + (this.random.nextDouble() - 0.5) * 0.03;
            this.gravity = 0.015F;
            this.friction = 0.92F;
            this.lifetime = 25 + this.random.nextInt(25);
            this.quadSize = 0.08F + this.random.nextFloat() * 0.1F;
            this.spin = (this.random.nextFloat() - 0.5F) * 0.2F;
            float shade = 0.88F + this.random.nextFloat() * 0.12F;
            this.setColor(shade, shade, shade * 0.95F);
        }

        @Override
        public void tick() {
            super.tick();
            this.oRoll = this.roll;
            this.roll += this.spin;
            this.alpha = Mth.clamp(1.0F - (float) this.age / this.lifetime, 0, 1) * 0.9F;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    /** Blood droplet: falls with gravity and splats (shrinks) on landing. */
    public static final class BloodDrop extends TextureSheetParticle {
        BloodDrop(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
            super(level, x, y, z, dx, dy, dz);
            this.pickSprite(sprites);
            this.gravity = 0.9F;
            this.friction = 0.96F;
            this.lifetime = 20 + this.random.nextInt(20);
            this.quadSize = 0.05F + this.random.nextFloat() * 0.06F;
            float dark = 0.6F + this.random.nextFloat() * 0.4F;
            this.setColor(dark, 0.05F * dark, 0.07F * dark);
        }

        @Override
        public void tick() {
            super.tick();
            if (this.onGround) {
                this.xd *= 0.5;
                this.zd *= 0.5;
                this.quadSize *= 0.92F;
            }
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
        }
    }

    /** Blood drawn to the fangs: travels exactly its spawn velocity per tick, glowing faintly. */
    public static final class BloodStream extends TextureSheetParticle {
        BloodStream(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
            super(level, x, y, z, 0, 0, 0);
            this.pickSprite(sprites);
            this.xd = dx;
            this.yd = dy;
            this.zd = dz;
            this.gravity = 0;
            this.friction = 1.0F;
            this.hasPhysics = false;
            this.lifetime = 10;
            this.quadSize = 0.07F + this.random.nextFloat() * 0.05F;
            this.setColor(0.85F, 0.05F, 0.08F);
        }

        @Override
        public void tick() {
            super.tick();
            this.quadSize *= 0.97F;
        }

        @Override
        protected int getLightColor(float partialTick) {
            return 0xF000F0;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    /** Short bright bite flash that grows and fades. */
    public static final class Impact extends TextureSheetParticle {
        private final SpriteSet sprites;

        Impact(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
            super(level, x, y, z, 0, 0, 0);
            this.sprites = sprites;
            this.lifetime = 6;
            this.quadSize = 0.6F;
            this.hasPhysics = false;
            this.setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            this.setSpriteFromAge(this.sprites);
            this.quadSize *= 1.12F;
            this.alpha = 1.0F - (float) this.age / this.lifetime;
        }

        @Override
        protected int getLightColor(float partialTick) {
            return 0xF000F0;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    public record WebFiberProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Nullable
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            return new WebFiber(level, x, y, z, dx, dy, dz, this.sprites);
        }
    }

    public record BloodDropProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Nullable
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            return new BloodDrop(level, x, y, z, dx, dy, dz, this.sprites);
        }
    }

    public record BloodStreamProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Nullable
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            return new BloodStream(level, x, y, z, dx, dy, dz, this.sprites);
        }
    }

    public record ImpactProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Nullable
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            return new Impact(level, x, y, z, this.sprites);
        }
    }
}
