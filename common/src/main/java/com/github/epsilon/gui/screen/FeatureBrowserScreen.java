package com.github.epsilon.gui.screen;

import com.github.epsilon.features.FeatureText;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Supplier;

public final class FeatureBrowserScreen extends Screen {
    public record Row(String label, List<String> details, Runnable action) {}
    private final Screen parent;
    private final Supplier<List<Row>> provider;
    private final String notice;
    private EditBox search;
    private String query = "";
    private int page;
    private final List<Button> rows = new ArrayList<>();
    private List<String> detail = List.of();
    private int detailPage;
    public FeatureBrowserScreen(Screen parent, String title, String notice, Supplier<List<Row>> provider) {
        super(Component.literal(title)); this.parent = parent; this.notice = notice; this.provider = provider;
    }
    @Override protected void init() {
        rows.clear();
        search = new EditBox(font, 12, 28, Math.max(80, width - 24), 20, FeatureText.component("search"));
        search.setMaxLength(256); search.setValue(query);
        search.setResponder(value -> { query = value; page = 0; refresh(); });
        addRenderableWidget(search);
        int available = Math.max(1, Math.min(7, (height - 115) / 22));
        for (int i = 0; i < available; i++) {
            final int index = i;
            rows.add(addRenderableWidget(Button.builder(Component.empty(), b -> select(index)).bounds(12, 54 + i * 22, Math.max(80, width - 24), 20).build()));
        }
        addRenderableWidget(Button.builder(Component.literal("<"), b -> { if (detail.isEmpty()) page = Math.max(0, page - 1); else detailPage = Math.max(0, detailPage - 1); refresh(); }).bounds(12, height - 28, 36, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> { if (detail.isEmpty()) page++; else detailPage++; refresh(); }).bounds(52, height - 28, 36, 20).build());
        addRenderableWidget(Button.builder(FeatureText.component("back"), b -> { if (detail.isEmpty()) onClose(); else { detail = List.of(); detailPage = 0; refresh(); } }).bounds(width - 92, height - 28, 80, 20).build());
        refresh();
    }
    private List<Row> filtered() {
        String q = query.toLowerCase(Locale.ROOT);
        return provider.get().stream().filter(r -> (r.label() + " " + String.join(" ", r.details())).toLowerCase(Locale.ROOT).contains(q)).toList();
    }
    private void select(int index) {
        List<Row> list = filtered(); int selected = page * rows.size() + index;
        if (selected >= list.size()) return;
        Row row = list.get(selected);
        if (row.action() != null) row.action().run();
        else { detail = row.details(); detailPage = 0; refresh(); }
    }
    private void refresh() {
        List<Row> list = filtered();
        page = Math.min(page, Math.max(0, (list.size() - 1) / Math.max(1, rows.size())));
        for (int i = 0; i < rows.size(); i++) {
            Button button = rows.get(i); int index = page * rows.size() + i;
            button.visible = detail.isEmpty(); button.active = index < list.size();
            button.setMessage(Component.literal(index < list.size() ? font.plainSubstrByWidth(list.get(index).label(), Math.max(60, width - 50)) : "—"));
        }
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
        g.fill(0, 0, width, height, 0xEC101114);
        g.text(font, getTitle(), 12, 10, 0xFFFFFFFF, false);
        if (!detail.isEmpty()) {
            List<net.minecraft.util.FormattedCharSequence> wrapped = new ArrayList<>();
            for (String line : detail) wrapped.addAll(font.split(Component.literal(line), Math.max(60, width - 24)));
            int lines = Math.max(1, (height - 103) / 12);
            detailPage = Math.min(detailPage, Math.max(0, (wrapped.size() - 1) / lines));
            for (int i = 0; i < lines && detailPage * lines + i < wrapped.size(); i++)
                g.text(font, wrapped.get(detailPage * lines + i), 12, 54 + i * 12, 0xFFE1E3E7, false);
        }
        g.text(font, font.plainSubstrByWidth(notice, Math.max(60, width - 24)), 12, height - 43, 0xFFFFC877, false);
        super.extractRenderState(g, x, y, delta);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
