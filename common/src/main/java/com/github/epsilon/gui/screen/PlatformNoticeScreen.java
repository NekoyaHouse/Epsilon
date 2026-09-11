package com.github.epsilon.gui.screen;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.settings.Setting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.utils.client.ClientPlatform;
import com.github.epsilon.utils.client.PlatformRequirement;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.github.epsilon.Constants.mc;

/**
 * 平台限定功能的说明界面。
 * <p>
 * 非支持平台上功能开关会被置灰，用户每次点击都会弹出该说明界面（同一功能不会重复堆叠），
 * 且不会修改用户已保存的配置值。
 */
public class PlatformNoticeScreen extends EpsilonDialogScreen {

    /**
     * 正在展示中的提示 key，用于避免同一功能重复堆叠窗口。
     */
    private static final Set<String> OPEN_KEYS = ConcurrentHashMap.newKeySet();

    private static final float ROW_HEIGHT = 17.0f;

    private final String noticeKey;
    private final String featureName;
    private final PlatformRequirement requirement;

    private PlatformNoticeScreen(Screen parent, String noticeKey, String featureName, PlatformRequirement requirement) {
        super(parent, Component.literal("Platform Only"));
        this.noticeKey = noticeKey;
        this.featureName = featureName;
        this.requirement = requirement;
    }

    /**
     * 在功能不可用的平台上弹出说明界面；同一 key 的窗口未关闭时不会重复打开。
     */
    public static void show(Screen parent, String key, String featureName, PlatformRequirement requirement) {
        if (requirement == null || requirement == PlatformRequirement.ANY || requirement.isSatisfied()) {
            return;
        }
        if (mc == null || !OPEN_KEYS.add(key)) {
            return;
        }
        mc.gui.setScreen(new PlatformNoticeScreen(parent, key, featureName, requirement));
    }

    /**
     * 设置行/枚举选项在非支持平台上被点击时调用。
     */
    public static void show(Setting<?> setting) {
        if (setting == null) {
            return;
        }
        Screen parent = mc != null ? mc.gui.screen() : null;
        show(parent, setting.getNoticeKey(), setting.getDisplayName(), setting.getPlatformRequirement());
    }

    /**
     * 枚举选项在非支持平台上被点击时调用。
     */
    public static void showOption(EnumSetting<?> setting, Enum<?> mode) {
        if (setting == null || mode == null) {
            return;
        }
        PlatformRequirement requirement = setting.getModeRequirementUnchecked(mode);
        String feature = setting.getDisplayName() + " · " + setting.getTranslatedValueUnchecked(mode);
        Screen parent = mc != null ? mc.gui.screen() : null;
        show(parent, setting.getNoticeKey() + "#" + mode.name(), feature, requirement);
    }

    @Override
    public void removed() {
        super.removed();
        OPEN_KEYS.remove(noticeKey);
    }

    @Override
    protected String titleText() {
        return EpsilonTranslations.PlatformOnly.TITLE.getTranslatedName();
    }

    @Override
    protected float cardWidth() {
        return 320.0f;
    }

    @Override
    protected float cardHeight() {
        return CARD_PADDING * 2.0f + 36.0f + ROW_HEIGHT * 4.0f + 24.0f;
    }

    @Override
    protected void buildBody(UiTree.Scope scope, float contentTop, int mouseX, int mouseY) {
        float y = contentTop;
        float labelScale = BODY_SCALE;

        scope.text(String.format(EpsilonTranslations.PlatformOnly.FEATURE.getTranslatedName(), featureName),
                CARD_PADDING, y, labelScale, MD3Theme.TEXT_PRIMARY);
        y += ROW_HEIGHT;
        scope.text(String.format(EpsilonTranslations.PlatformOnly.REQUIREMENT.getTranslatedName(), requirement.displayName()),
                CARD_PADDING, y, labelScale, MD3Theme.TEXT_SECONDARY);
        y += ROW_HEIGHT;
        scope.text(String.format(EpsilonTranslations.PlatformOnly.CURRENT.getTranslatedName(), ClientPlatform.displayName()),
                CARD_PADDING, y, labelScale, MD3Theme.TEXT_SECONDARY);
        y += ROW_HEIGHT + 6.0f;
        scope.text(EpsilonTranslations.PlatformOnly.HINT.getTranslatedName(),
                CARD_PADDING, y, labelScale, MD3Theme.TEXT_MUTED);
    }

    @Override
    protected List<DialogButton> buttons() {
        return List.of(new DialogButton(
                EpsilonTranslations.PlatformOnly.CONFIRM.getTranslatedName(), true, this::onClose));
    }

}
