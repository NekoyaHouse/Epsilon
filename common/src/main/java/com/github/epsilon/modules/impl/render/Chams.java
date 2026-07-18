package com.github.epsilon.modules.impl.render;

import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class Chams extends Module {

    public static final Chams INSTANCE = new Chams();

    private Chams() {
        super("Chams", Category.RENDER);
    }

    private static final ThreadLocal<Boolean> RENDERING_THIRD_PERSON_HAND_ITEM = ThreadLocal.withInitial(() -> false);

    public final BoolSetting noDepth = boolSetting("No Depth", true);

    private static final RenderPipeline ENTITY_CHAMS_PIPELINE = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation("pipeline/epsilon_entity_chams")
            .withShaderDefine("ALPHA_CUTOUT", 0.1f)
            .withShaderDefine("PER_FACE_LIGHTING")
            .withSampler("Sampler1")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true, -1.0f, -1100000.0f))
            .build();

    private static final RenderPipeline ITEM_CHAMS_CUTOUT_PIPELINE = RenderPipeline.builder(RenderPipelines.ITEM_SNIPPET)
            .withLocation("pipeline/epsilon_item_chams_cutout")
            .withShaderDefine("ALPHA_CUTOUT", 0.1f)
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true, -1.0f, -1100000.0f))
            .build();

    private static final RenderPipeline ITEM_CHAMS_TRANSLUCENT_PIPELINE = RenderPipeline.builder(RenderPipelines.ITEM_SNIPPET)
            .withLocation("pipeline/epsilon_item_chams_translucent")
            .withShaderDefine("ALPHA_CUTOUT", 0.1f)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true, -1.0f, -1100000.0f))
            .build();

    private static final Function<Identifier, RenderType> ENTITY_CHAMS_TYPE = Util.memoize(
            texture -> RenderType.create("sakura_entity_chams", RenderSetup.builder(ENTITY_CHAMS_PIPELINE)
                    .withTexture("Sampler0", texture)
                    .useLightmap()
                    .useOverlay()
                    .affectsCrumbling()
                    .sortOnUpload()
                    .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                    .createRenderSetup()));

    private static final Function<Identifier, RenderType> ITEM_CHAMS_CUTOUT_TYPE = Util.memoize(
            texture -> RenderType.create("sakura_item_chams_cutout", RenderSetup.builder(ITEM_CHAMS_CUTOUT_PIPELINE)
                    .withTexture("Sampler0", texture)
                    .useLightmap()
                    .affectsCrumbling()
                    .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                    .createRenderSetup()));

    private static final Function<Identifier, RenderType> ITEM_CHAMS_TRANSLUCENT_TYPE = Util.memoize(
            texture -> RenderType.create("sakura_item_chams_translucent", RenderSetup.builder(ITEM_CHAMS_TRANSLUCENT_PIPELINE)
                    .withTexture("Sampler0", texture)
                    .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                    .useLightmap()
                    .affectsCrumbling()
                    .sortOnUpload()
                    .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                    .createRenderSetup()));

    public RenderType getRenderType(Identifier texture) {
        return ENTITY_CHAMS_TYPE.apply(texture);
    }

    public RenderType getItemRenderType(Identifier texture, boolean translucent) {
        return translucent ? ITEM_CHAMS_TRANSLUCENT_TYPE.apply(texture) : ITEM_CHAMS_CUTOUT_TYPE.apply(texture);
    }

    public void beginThirdPersonHandItemRender() {
        RENDERING_THIRD_PERSON_HAND_ITEM.set(true);
    }

    public void endThirdPersonHandItemRender() {
        RENDERING_THIRD_PERSON_HAND_ITEM.set(false);
    }

    public boolean shouldApplyThirdPersonHandItemRenderType() {
        return isEnabled() && noDepth.getValue() && RENDERING_THIRD_PERSON_HAND_ITEM.get();
    }

    public List<BakedQuad> withChamsRenderType(List<BakedQuad> quads, Chams chamsModule) {
        List<BakedQuad> result = new ArrayList<>(quads.size());
        for (BakedQuad quad : quads) {
            BakedQuad.MaterialInfo material = quad.materialInfo();
            BakedQuad.MaterialInfo chamsMaterial = new BakedQuad.MaterialInfo(material.sprite(), material.layer(), chamsModule.getItemRenderType(material.sprite().atlasLocation(), material.itemRenderType().hasBlending()), material.tintIndex(), material.shade(), material.lightEmission());
            result.add(new BakedQuad(quad.position0(), quad.position1(), quad.position2(), quad.position3(), quad.packedUV0(), quad.packedUV1(), quad.packedUV2(), quad.packedUV3(), quad.direction(), chamsMaterial));
        }
        return result;
    }

}
