package com.github.epsilon.modules.impl.render.maseffects;

import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.*;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.SettingGroup;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.StringListSetting;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import java.util.List;

public class MasEffects extends Module {

    public static final MasEffects INSTANCE = new MasEffects();

    private static final Identifier GLIDE_ICON = ResourceLocationUtils.getIdentifier("textures/maseffects/hud/elytra_icon.png");

    private final SettingGroup generalGroup = settingGroup("General");
    private final SettingGroup maceGroup = settingGroup("Mace");
    private final SettingGroup hitboxGroup = settingGroup("Hitboxes");

    private final BoolSetting windParticles = boolSetting("Wind Particles", true).group(generalGroup);
    private final BoolSetting customTotemEffect = boolSetting("Custom Totem Effect", true).group(generalGroup);
    private final DoubleSetting totemEffectOpacity = doubleSetting("Totem Effect Opacity", 1.0, 0.0, 1.0, 0.05, customTotemEffect::getValue).group(generalGroup);
    private final BoolSetting playerDeathEffect = boolSetting("Player Death Effect", true).group(generalGroup);
    private final BoolSetting pearlTrailParticles = boolSetting("Pearl Trail Particles", true).group(generalGroup);
    private final DoubleSetting pearlTrailOpacity = doubleSetting("Pearl Trail Opacity", 0.5, 0.0, 1.0, 0.05, pearlTrailParticles::getValue).group(generalGroup);
    private final BoolSetting glideIcon = boolSetting("Glide Icon", true).group(generalGroup);

    private final BoolSetting maceShockwave = boolSetting("Mace Shockwave", true).group(maceGroup);
    private final DoubleSetting maceShockwaveSize = doubleSetting("Mace Shockwave Size", 1.0, 0.1, 3.0, 0.05, maceShockwave::getValue).group(maceGroup);
    private final DoubleSetting maceShockwaveOpacity = doubleSetting("Mace Shockwave Opacity", 1.0, 0.0, 1.0, 0.05).group(maceGroup);
    private final BoolSetting maceSpark = boolSetting("Mace Spark", true).group(maceGroup);
    private final BoolSetting maceFlash = boolSetting("Mace Flash", true).group(maceGroup);
    private final BoolSetting shieldEffect = boolSetting("Shield Effect", true).group(maceGroup);
    private final BoolSetting armorParticles = boolSetting("Armor Particles", true).group(maceGroup);
    private final BoolSetting legacyMaceShockwave = boolSetting("Legacy Mace Shockwave", false, maceShockwave::getValue).group(maceGroup);

    private final BoolSetting customHitbox = boolSetting("Custom Hitbox", true).group(hitboxGroup);
    private final BoolSetting pearlHitboxColors = boolSetting("Pearl Hitbox Colors", true, customHitbox::getValue).group(hitboxGroup);
    private final StringListSetting pearlAllies = stringListSetting("Pearl Allies", List.of(), () -> customHitbox.getValue() && pearlHitboxColors.getValue()).group(hitboxGroup);
    private final ColorSetting selfPearlColor = colorSetting("Self Pearl Color", new Color(255, 255, 0, 179), true, () -> customHitbox.getValue() && pearlHitboxColors.getValue()).group(hitboxGroup);
    private final ColorSetting allyPearlColor = colorSetting("Ally Pearl Color", new Color(0, 0, 255, 179), true, () -> customHitbox.getValue() && pearlHitboxColors.getValue()).group(hitboxGroup);
    private final ColorSetting otherPearlColor = colorSetting("Other Pearl Color", new Color(255, 0, 0, 179), true, () -> customHitbox.getValue() && pearlHitboxColors.getValue()).group(hitboxGroup);
    private final DoubleSetting mobHitboxOpacity = doubleSetting("Mob Hitbox Opacity", 0.3, 0.0, 1.0, 0.05, customHitbox::getValue).group(hitboxGroup);
    private final DoubleSetting playerHitboxOpacity = doubleSetting("Player Hitbox Opacity", 1.0, 0.0, 1.0, 0.05, customHitbox::getValue).group(hitboxGroup);
    private final DoubleSetting hitboxFadeDistance = doubleSetting("Hitbox Fade Distance", 15.0, 0.0, 64.0, 0.5, customHitbox::getValue).group(hitboxGroup);
    private final DoubleSetting projectileFadeDistance = doubleSetting("Projectile Fade Distance", 5.0, 0.0, 64.0, 0.5, customHitbox::getValue).group(hitboxGroup);

    private final MasEffectsParticleRenderer particleRenderer = new MasEffectsParticleRenderer(this);

    private MasEffects() {
        super("Mas Effects", Category.RENDER);
    }

    @Override
    protected void onDisable() {
        particleRenderer.clear();
    }

    @EventHandler
    private void onTick(PlayerTickEvent.Pre event) {
        particleRenderer.tick();

        if (playerDeathEffect.getValue()) {
            for (AbstractClientPlayer player : mc.level.players()) {
                if (player.deathTime == 1) {
                    particleRenderer.spawnDeath(player.position());
                }
            }
        }

        if (pearlTrailParticles.getValue()) {
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity instanceof ThrownEnderpearl pearl) {
                    particleRenderer.spawnPearlTrail(pearl);
                }
            }
        }
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (event.getPlayer() != mc.player || !event.getPlayer().getMainHandItem().is(Items.MACE)) return;

        Entity victim = event.getEntity();
        boolean shielding = victim instanceof Player player
                && event.getPlayer().fallDistance >= 1.5F
                && isShielding(event.getPlayer(), player);

        if (shielding && shieldEffect.getValue()) {
            particleRenderer.spawnShieldWave((Player) victim);
        } else if (victim instanceof LivingEntity livingEntity
                && armorParticles.getValue()
                && event.getPlayer().fallDistance > 1.5F
                && event.getPlayer().getMainHandItem().getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)
                .keySet().stream().anyMatch(holder -> holder.is(Enchantments.BREACH))) {
            particleRenderer.spawnArmorParticles(livingEntity);
        }
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (
                !(event.getPacket() instanceof ClientboundEntityEventPacket packet)
                        || packet.getEventId() != EntityEvent.PROTECTED_FROM_DEATH || !customTotemEffect.getValue()
        ) {
            return;
        }

        mc.execute(() -> {
            if (!isEnabled() || !customTotemEffect.getValue() || mc.level == null) return;
            Entity entity = packet.getEntity(mc.level);
            if (entity != null) {
                particleRenderer.spawnTotem(entity);
            }
        });
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        particleRenderer.render(event.getPoseStack());
    }

    @EventHandler
    private void onRender2D(Render2DEvent.HUD event) {
        if (mc.player == null || !glideIcon.getValue() || !mc.player.isFallFlying()) return;

        event.getGuiGraphics().blit(
                RenderPipelines.GUI_TEXTURED,
                GLIDE_ICON,
                event.getGuiGraphics().guiWidth() / 2 - 5,
                event.getGuiGraphics().guiHeight() / 2 + 7,
                0.0F,
                0.0F,
                9,
                9,
                9,
                9
        );
    }

    @EventHandler
    private void onLevelUpdate(LevelUpdateEvent event) {
        particleRenderer.clear();
    }

    public void onLevelEvent(int type, Vec3 position) {
        if (isEnabled() && type == LevelEvent.PARTICLES_SMASH_ATTACK) {
            particleRenderer.spawnSlam(position, 5.0);
        }
    }

    public void onWindSeed(double x, double y, double z) {
        if (isEnabled() && windParticles.getValue()) {
            particleRenderer.spawnWindWave(new Vec3(x, y, z), 0.3F, 1.0F, 5.0F);
        }
    }

    public boolean renderCustomHitbox(Entity entity, float partialTicks) {
        if (!isEnabled() || !customHitbox.getValue() || mc.player == null) return false;

        if (entity instanceof Player) {
            float fade = distanceFade(entity, hitboxFadeDistance.getValue().floatValue());
            int color = ARGB.colorFromFloat((1.0F - fade) * playerHitboxOpacity.getValue().floatValue(), 1.0F, 1.0F, 1.0F);
            drawHitbox(entity, partialTicks, color);
            return true;
        } else if (entity instanceof ThrownEnderpearl pearl) {
            float fade = distanceFade(entity, projectileFadeDistance.getValue().floatValue());
            Color selected = pearlHitboxColors.getValue() ? pearlColor(pearl) : new Color(255, 255, 0);
            int alpha = Math.round(fade * selected.getAlpha());
            drawHitbox(entity, partialTicks, ARGB.color(alpha, selected.getRed(), selected.getGreen(), selected.getBlue()));
            return true;
        } else if (entity instanceof WindCharge) {
            float fade = distanceFade(entity, projectileFadeDistance.getValue().floatValue());
            drawHitbox(entity, partialTicks, ARGB.colorFromFloat(fade, 0.9F, 0.9F, 1.0F));
            return true;
        } else if (entity instanceof LivingEntity) {
            float fade = distanceFade(entity, hitboxFadeDistance.getValue().floatValue());
            int color = ARGB.colorFromFloat((1.0F - fade) * mobHitboxOpacity.getValue().floatValue(), 1.0F, 1.0F, 1.0F);
            drawHitbox(entity, partialTicks, color);
            return true;
        }

        return false;
    }

    public boolean shouldHideVanillaTotemParticles() {
        return isEnabled() && customTotemEffect.getValue();
    }

    public float getMaceShockwaveOpacity() {
        return maceShockwaveOpacity.getValue().floatValue();
    }

    public float getTotemEffectOpacity() {
        return totemEffectOpacity.getValue().floatValue();
    }

    public float getPearlTrailOpacity() {
        return pearlTrailOpacity.getValue().floatValue();
    }

    public boolean isMaceShockwaveEnabled() {
        return maceShockwave.getValue();
    }

    public float getMaceShockwaveSize() {
        return maceShockwaveSize.getValue().floatValue();
    }

    public boolean isLegacyMaceShockwave() {
        return legacyMaceShockwave.getValue();
    }

    public boolean isMaceSparkEnabled() {
        return maceSpark.getValue();
    }

    public boolean isMaceFlashEnabled() {
        return maceFlash.getValue();
    }

    private boolean isShielding(Player attacker, Player victim) {
        if (!victim.isBlocking()) return false;

        Vec3 directionToAttacker = attacker.position().subtract(victim.position());
        directionToAttacker = new Vec3(directionToAttacker.x, 0.0, directionToAttacker.z).normalize();
        Vec3 shieldDirection = victim.calculateViewVector(0.0F, victim.getYHeadRot());
        return directionToAttacker.dot(shieldDirection) > 0.0;
    }

    private float distanceFade(Entity entity, float startDistance) {
        return Mth.clamp((mc.player.distanceTo(entity) - startDistance) / 20.0F, 0.0F, 1.0F);
    }

    private Color pearlColor(ThrownEnderpearl pearl) {
        Entity owner = pearl.getOwner();
        if (owner != null && owner.getUUID().equals(mc.player.getUUID())) {
            return selfPearlColor.getValue();
        }
        if (owner != null && pearlAllies.getValue().contains(owner.getScoreboardName())) {
            return allyPearlColor.getValue();
        }
        return otherPearlColor.getValue();
    }

    private void drawHitbox(Entity entity, float partialTicks, int color) {
        Vec3 offset = entity.getPosition(partialTicks).subtract(entity.position());
        Gizmos.cuboid(entity.getBoundingBox().move(offset), GizmoStyle.stroke(color));
    }

}
