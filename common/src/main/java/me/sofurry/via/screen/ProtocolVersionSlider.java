package me.sofurry.via.screen;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ProtocolVersionSlider extends AbstractSliderButton {

    public static final int WIDTH = 125;
    public static final int HEIGHT = 20;

    private final List<ProtocolVersion> protocols;

    public ProtocolVersionSlider(final int x, final int y) {
        super(x, y, WIDTH, HEIGHT, Component.empty(), initialValue());
        this.protocols = createProtocols();
        updateMessage();
    }

    private static List<ProtocolVersion> createProtocols() {
        final List<ProtocolVersion> protocols = new ArrayList<>(ProtocolVersion.getReversedProtocols());
        Collections.reverse(protocols);
        return List.copyOf(protocols);
    }

    private static double initialValue() {
        final List<ProtocolVersion> protocols = createProtocols();
        if (protocols.size() <= 1) {
            return 0.0;
        }

        final int index = protocols.indexOf(ProtocolTranslator.getTargetVersion());
        return (double) Math.max(index, 0) / (protocols.size() - 1);
    }

    private int selectedIndex() {
        if (protocols.isEmpty()) {
            return -1;
        }
        return (int) Math.round(value * (protocols.size() - 1));
    }

    private ProtocolVersion selectedVersion() {
        final int index = selectedIndex();
        return index < 0 ? null : protocols.get(index);
    }

    @Override
    protected void updateMessage() {
        final ProtocolVersion selected = selectedVersion();
        setMessage(selected == null
                ? EpsilonTranslations.component(EpsilonTranslations.Via.BASE_SET_VERSION)
                : Component.literal(selected.getName()));
    }

    @Override
    protected void applyValue() {
        final ProtocolVersion selected = selectedVersion();
        if (selected == null) {
            return;
        }

        final int index = selectedIndex();
        value = protocols.size() <= 1 ? 0.0 : (double) index / (protocols.size() - 1);
        ProtocolTranslator.setTargetVersion(selected);
    }

}
