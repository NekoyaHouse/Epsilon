package me.sofurry.via.features.entity.legacy_boat_model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.AbstractBoatRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.BoatRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Renderer for boats in 1.8 and lower.
 */
public final class BoatRenderer1_8 extends AbstractBoatRenderer {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("viafabricplus", "textures/boat1_8.png");
    private final BoatModel1_8 model;

    public BoatRenderer1_8(EntityRendererProvider.Context ctx) {
        super(ctx, TEXTURE);
        shadowRadius = 0.5F;
        model = new BoatModel1_8(ctx.bakeLayer(BoatModel1_8.MODEL_LAYER));
    }

    @Override
    protected EntityModel<BoatRenderState> model() {
        return this.model;
    }

    @Override
    public void submit(final BoatRenderState state, final PoseStack matrices, final SubmitNodeCollector submitNodeCollector, final CameraRenderState camera) {
        matrices.pushPose();
        matrices.translate(0, 0.25, 0);
        matrices.mulPose(Axis.YP.rotationDegrees(180 - state.yRot));

        if (state.hurtTime > 0) {
            matrices.mulPose(Axis.XP.rotationDegrees(Mth.sin(state.hurtTime) * state.hurtTime * state.damageTime / 10 * state.hurtDir));
        }

        matrices.scale(-1, -1, 1);
        model.setupAnim(state);
        submitNodeCollector.submitModel(model(), state, matrices, this.texture, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor, null);

        matrices.popPose();
    }

}
