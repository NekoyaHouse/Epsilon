package me.sofurry.via.features.world.footstep_particle;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Quaternionf;

public class FootStepParticle1_12_2 extends SingleQuadParticle {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("viafabricplus", "footstep");
    private static final Identifier SPRITE_ID = Identifier.fromNamespaceAndPath("viafabricplus", "footprint");
    public static final SimpleParticleType TYPE = new SimpleParticleType(true);
    public static int RAW_ID;

    private FootStepParticle1_12_2(ClientLevel clientWorld, double x, double y, double z, TextureAtlasSprite sprite) {
        super(clientWorld, x, y, z, sprite);

        this.quadSize = 0.125F;
        this.setLifetime(200);
    }

    public static void init() {
        if (BuiltInRegistries.PARTICLE_TYPE.getValue(ID) == null) {
            Registry.register(BuiltInRegistries.PARTICLE_TYPE, ID, TYPE);
        }
        RAW_ID = BuiltInRegistries.PARTICLE_TYPE.getId(TYPE);
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    @Override
    protected void extractRotatedQuad(final QuadParticleRenderState submittable, final Camera camera, final Quaternionf rotation, final float tickProgress) {
        final float strength = ((float) this.age + tickProgress) / (float) this.lifetime;
        this.alpha = 2.0F - (strength * strength) * 2.0F;
        if (this.alpha > 1.0F) {
            this.alpha = 0.2F;
        } else {
            this.alpha *= 0.2F;
        }

        super.extractRotatedQuad(submittable, camera, new Quaternionf().rotateX(-Mth.HALF_PI), tickProgress);
    }

    public static class Factory implements ParticleProvider<SimpleParticleType> {

        @Override
        public Particle createParticle(SimpleParticleType parameters, ClientLevel world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, RandomSource random) {
            if (ProtocolTranslator.getTargetVersion().newerThan(ProtocolVersion.v1_12_2)) {
                throw new UnsupportedOperationException("FootStepParticle is not supported on versions newer than 1.12.2");
            }

            final AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_PARTICLES);
            final TextureAtlasSprite sprite = ((TextureAtlas) texture).getSprite(SPRITE_ID);
            return new FootStepParticle1_12_2(world, x, y, z, sprite);
        }
    }

}
