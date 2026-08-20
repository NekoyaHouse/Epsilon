package me.sofurry.via.screen;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

public final class ProtocolVersionSlider extends AbstractSliderButton {

    public static final int WIDTH = 125;
    public static final int HEIGHT = 20;

    private final List<ProtocolVersion> protocols;

    public ProtocolVersionSlider(final int x, final int y) {
        super(x, y, WIDTH, HEIGHT, Component.empty(), initialValue());
        this.protocols = ProtocolVersion.getProtocols();
        updateMessage();
    }

    private static double initialValue() {
        final List<ProtocolVersion> protocols = ProtocolVersion.getProtocols();
        final int index = protocols.indexOf(ProtocolTranslator.getTargetVersion());
        return (double) Math.max(index, 0) / (protocols.size() - 1);
    }

    private int selectedIndex() {
        if (protocols.isEmpty()) {
            return -1;
        }
        if (value >= 1.0) {
            return protocols.size() - 1;
        }
        return Math.min((int) Math.floor(value * protocols.size()), protocols.size() - 1);
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
        // Keep mouse dragging continuous. Committing here would reload version-dependent
        // client state for every protocol crossed and make the slider stutter.
    }

    @Override
    public void onRelease(final MouseButtonEvent event) {
        super.onRelease(event);

        final int index = selectedIndex();
        if (index < 0) {
            return;
        }

        setValue(valueForIndex(index));
        applySelectedVersion();
    }

    @Override
    public boolean keyPressed(final KeyEvent event) {
        if (event.isSelection()) {
            return super.keyPressed(event);
        }
        if (!canChangeValue || protocols.isEmpty()) {
            return false;
        }

        final int direction;
        if (event.isLeft()) {
            direction = -1;
        } else if (event.isRight()) {
            direction = 1;
        } else {
            return false;
        }

        final int index = Mth.clamp(selectedIndex() + direction, 0, protocols.size() - 1);
        setValue(valueForIndex(index));
        applySelectedVersion();
        return true;
    }

    private double valueForIndex(final int index) {
        return protocols.size() <= 1 ? 0.0 : (double) index / (protocols.size() - 1);
    }

    private void applySelectedVersion() {
        final ProtocolVersion selected = selectedVersion();
        if (selected != null) {
            ProtocolTranslator.setTargetVersion(selected);
        }
    }

}
