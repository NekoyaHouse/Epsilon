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
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class Chams extends Module {

    public static final Chams INSTANCE = new Chams();

    private Chams() {
        super("Chams", Category.RENDER);
    }

    private final BoolSetting noDepth = boolSetting("No Depth", true);
    private final BoolSetting players = boolSetting("Players", true, noDepth::getValue);
    private final BoolSetting chests = boolSetting("Chests", true, noDepth::getValue);

    private static final ThreadLocal<Boolean> RENDERING_THIRD_PERSON_HAND_ITEM = ThreadLocal.withInitial(() -> false);

    public static DepthStencilState depthState(boolean writeDepth) {
        return new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, writeDepth, -1.0f, -1100000.0f);
    }

    private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation("pipeline/epsilon_entity_chams")
            .withShaderDefine("ALPHA_CUTOUT", 0.1f)
            .withShaderDefine("PER_FACE_LIGHTING")
            .withSampler("Sampler1")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .withDepthStencilState(depthState(true))
            .build();

    private static final RenderPipeline ITEM_TRANSLUCENT_PIPELINE = RenderPipeline.builder(RenderPipelines.ITEM_SNIPPET)
            .withLocation("pipeline/epsilon_item_chams_translucent")
            .withShaderDefine("ALPHA_CUTOUT", 0.1f)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(depthState(true))
            .build();

    private static final Function<Identifier, RenderType> TYPE = Util.memoize(
            texture -> RenderType.create("epsilon_entity_chams", RenderSetup.builder(PIPELINE)
                    .withTexture("Sampler0", texture)
                    .useLightmap()
                    .useOverlay()
                    .affectsCrumbling()
                    .sortOnUpload()
                    .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                    .createRenderSetup()));

    private static final Function<Identifier, RenderType> ITEM_TRANSLUCENT_TYPE = Util.memoize(
            texture -> RenderType.create("epsilon_item_chams_translucent", RenderSetup.builder(ITEM_TRANSLUCENT_PIPELINE)
                    .withTexture("Sampler0", texture)
                    .useLightmap()
                    .affectsCrumbling()
                    .sortOnUpload()
                    .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                    .createRenderSetup()));

    public RenderType getRenderType(Identifier texture) {
        return TYPE.apply(texture);
    }

    public RenderType getItemRenderType(Identifier texture) {
        return ITEM_TRANSLUCENT_TYPE.apply(texture);
    }

    public boolean shouldRenderPlayer(Player player) {
        return isEnabled() && noDepth.getValue() && players.getValue() && player != mc.player;
    }

    public boolean shouldRenderChests() {
        return isEnabled() && noDepth.getValue() && chests.getValue();
    }

    public void beginThirdPersonHandItemRender() {
        RENDERING_THIRD_PERSON_HAND_ITEM.set(true);
    }

    public void endThirdPersonHandItemRender() {
        RENDERING_THIRD_PERSON_HAND_ITEM.set(false);
    }

    public boolean shouldApplyThirdPersonHandItemRenderType() {
        return RENDERING_THIRD_PERSON_HAND_ITEM.get();
    }

    public List<BakedQuad> withChamsRenderType(List<BakedQuad> quads, Chams chamsModule) {
        List<BakedQuad> result = new ArrayList<>(quads.size());
        for (BakedQuad quad : quads) {
            BakedQuad.MaterialInfo material = quad.materialInfo();
            BakedQuad.MaterialInfo chamsMaterial = new BakedQuad.MaterialInfo(material.sprite(), material.layer(), chamsModule.getItemRenderType(material.sprite().atlasLocation()), material.tintIndex(), material.shade(), material.lightEmission());
            result.add(new BakedQuad(quad.position0(), quad.position1(), quad.position2(), quad.position3(), quad.packedUV0(), quad.packedUV1(), quad.packedUV2(), quad.packedUV3(), quad.direction(), chamsMaterial));
        }
        return result;
    }

}
