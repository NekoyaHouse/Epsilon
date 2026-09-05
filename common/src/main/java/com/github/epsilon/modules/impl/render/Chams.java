package com.github.epsilon.modules.impl.render;

import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;

import java.util.function.Function;

public class Chams extends Module {

    public static final Chams INSTANCE = new Chams();

    private Chams() {
        super("Chams", Category.RENDER);
    }

    private final BoolSetting noDepth = boolSetting("No Depth", true);
    private final BoolSetting players = boolSetting("Players", true, noDepth::getValue);
    private final BoolSetting chests = boolSetting("Chests", true, noDepth::getValue);

    private final ThreadLocal<Boolean> submittingPlayer = ThreadLocal.withInitial(() -> false);

    private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation("pipeline/epsilon_entity_chams")
            .withShaderDefine("ALPHA_CUTOUT", 0.1f)
            .withShaderDefine("PER_FACE_LIGHTING")
            .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
            .withCull(false)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
            .build();

    private static final Function<Identifier, RenderType> TYPE = Util.memoize(
            texture -> RenderType.create("epsilon_entity_chams", RenderSetup.builder(PIPELINE)
                    .withTexture("Sampler0", texture)
                    .useLightmap()
                    .useOverlay()
                    .affectsCrumbling()
                    .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                    .createRenderSetup()));

    public RenderType getRenderType(Identifier texture) {
        return TYPE.apply(texture);
    }

    public boolean isChamsRenderType(RenderType renderType) {
        return renderType.pipeline() == PIPELINE;
    }

    public boolean noDepth() {
        return isEnabled() && noDepth.getValue();
    }

    public boolean shouldRenderPlayer(Player player) {
        return noDepth() && players.getValue() && player != mc.player;
    }

    public boolean isSubmittingPlayer() {
        return submittingPlayer.get();
    }

    public void setSubmittingPlayer(boolean submittingPlayer) {
        this.submittingPlayer.set(submittingPlayer);
    }

    public boolean shouldRenderChests() {
        return noDepth() && chests.getValue();
    }

}
