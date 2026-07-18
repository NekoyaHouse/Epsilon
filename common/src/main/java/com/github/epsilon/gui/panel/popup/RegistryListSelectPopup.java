package com.github.epsilon.gui.panel.popup;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.lib.render.UiContentBuffer;
import com.github.epsilon.gui.lib.render.UiRenderBatch;
import com.github.epsilon.gui.panel.utils.IMEFocusHelper;
import com.github.epsilon.gui.panel.utils.ScrollBarDragState;
import com.github.epsilon.gui.panel.utils.ScrollBarUtils;
import com.github.epsilon.gui.theme.EpsilonUiTheme;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.settings.Setting;
import com.github.epsilon.settings.impl.RegistryListSetting;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import com.github.epsilon.utils.world.BlockRegistryUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.github.epsilon.Constants.mc;

public class RegistryListSelectPopup<T> implements PanelPopupHost.Popup {

    private static final float PADDING = 8.0f;
    private static final float TITLE_HEIGHT = 18.0f;
    private static final float SEARCH_HEIGHT = 18.0f;
    private static final float HEADER_HEIGHT = 14.0f;
    private static final float ROW_HEIGHT = 18.0f;
    private static final float ROW_GAP = 2.0f;
    private static final float COLUMN_GAP = 6.0f;
    private static final float SCROLLBAR_GUTTER = ScrollBarUtils.TOTAL_WIDTH + 1.0f;
    private static final float SCROLL_STEP = 24.0f;
    private static final float SCROLL_DECAY = 0.86f;
    private static final float MIN_SCROLL_VELOCITY = 0.3f;
    private static final int MAX_QUERY_LENGTH = 64;
    private static final float ITEM_PREVIEW_SIZE = 14.0f;
    private static final float ITEM_PREVIEW_GAP = 4.0f;
    private static final float CATEGORY_TAB_HEIGHT = 16.0f;
    private static final float CATEGORY_TAB_GAP = 4.0f;

    private final UiRect bounds;
    private final Setting<List<T>> setting;
    private final Function<T, String> displayNameFn;
    private final Function<T, ItemStack> iconProvider;
    private final Consumer<T> addFn;
    private final Consumer<T> removeFn;
    private final List<T> allEntries;
    private final List<Category<T>> categories;
    private final UiContentBuffer availableBuffer = new UiContentBuffer(EpsilonUiTheme.INSTANCE);
    private final UiContentBuffer selectedBuffer = new UiContentBuffer(EpsilonUiTheme.INSTANCE);
    private final TextRenderer textRenderer = TextRenderer.create();
    private final Animation openAnimation = new Animation(Easing.EASE_OUT_CUBIC, 160L);
    private final ScrollBarDragState availableScrollBarDrag = new ScrollBarDragState();
    private final ScrollBarDragState selectedScrollBarDrag = new ScrollBarDragState();
    private final List<ItemPreview> itemPreviews = new ArrayList<>();

    private String query = "";
    private int selectedCategory = -1; // -1 = all
    private float availableScroll;
    private float selectedScroll;
    private float availableScrollVelocity;
    private float selectedScrollVelocity;
    private float maxAvailableScroll;
    private float maxSelectedScroll;
    private T hoveredAdd;
    private T hoveredRemove;
    private UiRect lastAvailableViewport;
    private UiRect lastSelectedViewport;
    private UiRect lastOverlayViewport;

    public RegistryListSelectPopup(UiRect bounds, Setting<List<T>> setting, Registry<T> registry,
                                   Function<T, String> displayNameFn, Consumer<T> addFn, Consumer<T> removeFn) {
        this(bounds, setting, registry, displayNameFn, null, List.of(), addFn, removeFn);
    }

    public RegistryListSelectPopup(UiRect bounds, Setting<List<T>> setting, Registry<T> registry,
                                   Function<T, String> displayNameFn, Function<T, ItemStack> iconProvider,
                                   Consumer<T> addFn, Consumer<T> removeFn) {
        this(bounds, setting, registry, displayNameFn, iconProvider, List.of(), addFn, removeFn);
    }

    public RegistryListSelectPopup(UiRect bounds, Setting<List<T>> setting, Registry<T> registry,
                                   Function<T, String> displayNameFn, Function<T, ItemStack> iconProvider,
                                   List<Category<T>> categories,
                                   Consumer<T> addFn, Consumer<T> removeFn) {
        this(bounds, setting, collectRegistryEntries(registry, setting), displayNameFn, iconProvider, categories, addFn, removeFn);
    }

    public RegistryListSelectPopup(UiRect bounds, Setting<List<T>> setting, Collection<T> entries,
                                   Function<T, String> displayNameFn, Function<T, ItemStack> iconProvider,
                                   List<Category<T>> categories,
                                   Consumer<T> addFn, Consumer<T> removeFn) {
        this.bounds = bounds;
        this.setting = setting;
        this.displayNameFn = displayNameFn;
        this.iconProvider = iconProvider;
        this.categories = categories;
        this.addFn = addFn;
        this.removeFn = removeFn;
        this.openAnimation.setStartValue(0.0f);
        this.allEntries = new ArrayList<>(entries);
    }

    private static <T> List<T> collectRegistryEntries(Registry<T> registry, Setting<List<T>> setting) {
        List<T> entries = new ArrayList<>();
        Predicate<T> filter = filterFor(setting);
        for (T entry : registry) {
            if (filter == null || filter.test(entry)) {
                entries.add(entry);
            }
        }
        entries.sort(Comparator.comparing(e -> {
            Identifier key = registry.getKey(e);
            return key != null ? key.toString() : "";
        }));
        return entries;
    }

    @SuppressWarnings("unchecked")
    private static <T> Predicate<T> filterFor(Object setting) {
        if (setting instanceof RegistryListSetting<?> registryListSetting) {
            return (Predicate<T>) registryListSetting.getFilter();
        }
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static PanelPopupHost.Popup create(UiRect bounds, RegistryListSetting<?> setting) {
        return switch (setting.getRegistryType()) {
            case BLOCK -> blockPopup(bounds, (RegistryListSetting) setting);
            case ITEM -> itemPopup(bounds, (RegistryListSetting) setting);
            case ENTITY_TYPE -> entityTypePopup(bounds, (RegistryListSetting) setting);
            case SOUND_EVENT -> soundEventPopup(bounds, (RegistryListSetting) setting);
            case ENCHANTMENT -> enchantmentPopup(bounds, (RegistryListSetting) setting);
        };
    }

    private static RegistryListSelectPopup<Block> blockPopup(UiRect bounds, RegistryListSetting<Block> setting) {
        return new RegistryListSelectPopup<>(bounds, setting, BuiltInRegistries.BLOCK,
                BlockRegistryUtils::displayName, RegistryListSelectPopup::blockPreviewStack,
                setting::add, setting::remove);
    }

    private static RegistryListSelectPopup<Item> itemPopup(UiRect bounds, RegistryListSetting<Item> setting) {
        return new RegistryListSelectPopup<>(bounds, setting, BuiltInRegistries.ITEM,
                RegistryListSelectPopup::itemDisplayName, RegistryListSelectPopup::itemPreviewStack,
                setting::add, setting::remove);
    }

    private static RegistryListSelectPopup<EntityType<?>> entityTypePopup(UiRect bounds,
                                                                          RegistryListSetting<EntityType<?>> setting) {
        return new RegistryListSelectPopup<>(bounds, setting, BuiltInRegistries.ENTITY_TYPE,
                entityType -> entityType.getDescription().getString(), RegistryListSelectPopup::entityTypePreviewStack,
                setting::add, setting::remove);
    }

    private static RegistryListSelectPopup<SoundEvent> soundEventPopup(UiRect bounds,
                                                                       RegistryListSetting<SoundEvent> setting) {
        return new RegistryListSelectPopup<>(bounds, setting, BuiltInRegistries.SOUND_EVENT,
                sound -> {
                    Identifier key = BuiltInRegistries.SOUND_EVENT.getKey(sound);
                    return key != null ? key.getPath() : "";
                },
                setting::add, setting::remove);
    }

    private static RegistryListSelectPopup<String> enchantmentPopup(UiRect bounds,
                                                                    RegistryListSetting<String> setting) {
        return new RegistryListSelectPopup<>(bounds, setting, collectEnchantments(),
                RegistryListSelectPopup::enchantmentDisplayName, null, List.of(), setting::add, setting::remove);
    }

    private static List<String> collectEnchantments() {
        List<String> ids = new ArrayList<>();
        if (mc.level != null) {
            mc.level.registryAccess().lookup(Registries.ENCHANTMENT).ifPresent(registry ->
                    registry.listElementIds()
                            .map(ResourceKey::identifier)
                            .map(Identifier::toString)
                            .forEach(ids::add)
            );
        }
        ids.sort(String.CASE_INSENSITIVE_ORDER);
        return ids;
    }

    private static String enchantmentDisplayName(String id) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null || mc.level == null) {
            return formatPath(id);
        }
        return mc.level.registryAccess().lookup(Registries.ENCHANTMENT)
                .flatMap(registry -> registry.get(ResourceKey.create(Registries.ENCHANTMENT, identifier)))
                .map(holder -> holder.value().description().getString())
                .orElseGet(() -> formatPath(identifier.getPath()));
    }

    private static String itemDisplayName(Item item) {
        if (item == null) {
            return "";
        }
        if (item.builtInRegistryHolder().areComponentsBound()) {
            return item.getDefaultInstance().getHoverName().getString();
        }
        Identifier key = BuiltInRegistries.ITEM.getKey(item);
        return key != null ? formatPath(key.getPath()) : "";
    }

    private static ItemStack blockPreviewStack(Block block) {
        if (block == null) {
            return ItemStack.EMPTY;
        }
        return itemPreviewStack(block.asItem());
    }

    private static ItemStack itemPreviewStack(Item item) {
        if (item == null || item == Items.AIR || !item.builtInRegistryHolder().areComponentsBound()) {
            return ItemStack.EMPTY;
        }
        return item.getDefaultInstance();
    }

    private static ItemStack entityTypePreviewStack(EntityType<?> entityType) {
        Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        if (key == null) {
            return ItemStack.EMPTY;
        }
        Identifier eggId = Identifier.tryParse(key.getNamespace() + ":" + key.getPath() + "_spawn_egg");
        if (eggId != null) {
            Item egg = BuiltInRegistries.ITEM.getOptional(eggId).orElse(null);
            ItemStack eggStack = itemPreviewStack(egg);
            if (!eggStack.isEmpty()) {
                return eggStack;
            }
        }
        return itemPreviewStack(BuiltInRegistries.ITEM.getOptional(key).orElse(null));
    }

    private static String formatPath(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String path = value.contains(":") ? value.substring(value.indexOf(':') + 1) : value;
        String[] parts = path.split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }

    @Override
    public UiRect getBounds() {
        return bounds;
    }

    @Override
    public void extractGui(GuiGraphicsExtractor guiGraphics, UiRenderBatch renderBatch, int mouseX, int mouseY, float partialTick) {
        availableBuffer.clear();
        selectedBuffer.clear();
        itemPreviews.clear();
        List<T> available = filteredAvailable();
        List<T> selected = filteredSelected();
        float availableContentHeight = available.size() * (ROW_HEIGHT + ROW_GAP);
        float selectedContentHeight = selected.size() * (ROW_HEIGHT + ROW_GAP);

        UiTree tree = UiTree.build(scope -> {
            float progress = scope.animate(openAnimation, 1.0f);
            float popupY = bounds.y() - (1.0f - progress) * 6.0f;
            UiRect animatedBounds = new UiRect(bounds.x(), popupY, bounds.width(), bounds.height());
            UiRect searchBounds = getSearchBounds(popupY);
            UiRect animatedViewport = getViewport(popupY);
            scope.pushAbsolute(animatedBounds, popup -> {
                popup.popupCard(animatedBounds.atOrigin(), MD3Theme.CARD_RADIUS, MD3Theme.POPUP_SHADOW_BLUR,
                        MD3Theme.withAlpha(MD3Theme.SHADOW, (int) (MD3Theme.POPUP_SHADOW_ALPHA * progress)),
                        MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER_LOW, 255));

                float titleY = centeredTextY(6.0f, TITLE_HEIGHT, 0.68f);
                float summaryScale = 0.52f;
                String summary = setting.getValue().size() + EpsilonTranslations.Gui.LIST_SELECTED.getTranslatedName();
                popup.text(setting.getDisplayName(), PADDING, titleY, 0.68f, MD3Theme.TEXT_PRIMARY);
                popup.text(summary, animatedBounds.width() - PADDING - textRenderer.getWidth(summary, summaryScale),
                        centeredTextY(6.0f, TITLE_HEIGHT, summaryScale), summaryScale, MD3Theme.TEXT_MUTED);
                popup.input(searchBounds.relativeTo(animatedBounds), true, 1.0f, 8.0f,
                        query.isEmpty() ? EpsilonTranslations.Gui.LIST_SEARCH.getTranslatedName() : query, 0.54f,
                        query.isEmpty() ? MD3Theme.TEXT_MUTED : MD3Theme.TEXT_PRIMARY,
                        query.length(), MD3Theme.PRIMARY, null, 0.0f, null);
                IMEFocusHelper.updateCursorPos(searchBounds.x() + 8.0f, searchBounds.y() + 4.0f);

                // Category tabs
                float columnViewportWidth = (animatedViewport.width() - COLUMN_GAP) / 2.0f;
                float leftX = animatedViewport.x();
                float rightX = leftX + columnViewportWidth + COLUMN_GAP;
                float viewportY = animatedViewport.y();
                if (!categories.isEmpty()) {
                    float catY = searchBounds.bottom() + 4.0f;
                    float catTabX = leftX;
                    for (int ci = -1; ci < categories.size(); ci++) {
                        String catName = ci < 0 ? EpsilonTranslations.Gui.LIST_ALL.getTranslatedName() : categories.get(ci).name();
                        float catTextW = textRenderer.getWidth(catName, 0.44f) + 10.0f;
                        boolean catSelected = selectedCategory == ci;
                        UiRect catBounds = new UiRect(catTabX, catY, catTextW, CATEGORY_TAB_HEIGHT);
                        popup.roundRect(catBounds.x() - animatedBounds.x(), catBounds.y() - animatedBounds.y(),
                                catBounds.width(), catBounds.height(), CATEGORY_TAB_HEIGHT / 2.0f,
                                catSelected ? MD3Theme.PRIMARY : MD3Theme.SURFACE_CONTAINER_HIGH);
                        popup.text(catName,
                                catBounds.x() - animatedBounds.x() + 5.0f,
                                catBounds.y() - animatedBounds.y() + (catBounds.height() - textRenderer.getHeight(0.44f)) * 0.5f,
                                0.44f, catSelected ? MD3Theme.ON_PRIMARY : MD3Theme.TEXT_SECONDARY);
                        catTabX += catTextW + CATEGORY_TAB_GAP;
                    }
                    viewportY = catY + CATEGORY_TAB_HEIGHT + HEADER_HEIGHT + 4.0f;
                }
                final float effectiveViewportY = viewportY;

                float headerY = effectiveViewportY - HEADER_HEIGHT;
                float headerTextY = centeredTextY(headerY, HEADER_HEIGHT, 0.50f);
                popup.text(EpsilonTranslations.Gui.LIST_AVAILABLE.getTranslatedName(), leftX - animatedBounds.x() + 4.0f, headerTextY - animatedBounds.y(), 0.50f, MD3Theme.TEXT_SECONDARY);
                popup.text(EpsilonTranslations.Gui.LIST_SELECTED_HEADER.getTranslatedName(), rightX - animatedBounds.x() + 4.0f, headerTextY - animatedBounds.y(), 0.50f, MD3Theme.TEXT_SECONDARY);

                hoveredAdd = null;
                hoveredRemove = null;
                float viewportHeight = animatedViewport.bottom() - effectiveViewportY;
                UiRect availableViewport = new UiRect(leftX, effectiveViewportY, columnViewportWidth, viewportHeight);
                UiRect selectedViewport = new UiRect(rightX, effectiveViewportY, columnViewportWidth, viewportHeight);
                lastAvailableViewport = availableViewport;
                lastSelectedViewport = selectedViewport;
                lastOverlayViewport = new UiRect(availableViewport.x(), availableViewport.y(),
                        selectedViewport.right() - availableViewport.x(), availableViewport.height());

                maxAvailableScroll = Math.max(0.0f, availableContentHeight - availableViewport.height());
                maxSelectedScroll = Math.max(0.0f, selectedContentHeight - selectedViewport.height());
                availableScroll = Mth.clamp(availableScroll, 0.0f, maxAvailableScroll);
                selectedScroll = Mth.clamp(selectedScroll, 0.0f, maxSelectedScroll);
                updateAvailableSmoothScroll(partialTick);
                updateSelectedSmoothScroll(partialTick);

                UiRect localAvailableViewport = availableViewport.relativeTo(animatedBounds);
                popup.viewport(availableBuffer, localAvailableViewport, availableScroll,
                        maxAvailableScroll, availableContentHeight, mouseX, mouseY, content -> {
                            buildColumn(content, available, availableViewport.x(), availableViewport.y() - availableScroll,
                                    availableViewport.width() - (maxAvailableScroll > 0.0f ? SCROLLBAR_GUTTER : 0.0f),
                                    mouseX, mouseY, true, availableViewport);
                        });
                UiRect localSelectedViewport = selectedViewport.relativeTo(animatedBounds);
                popup.viewport(selectedBuffer, localSelectedViewport, selectedScroll,
                        maxSelectedScroll, selectedContentHeight, mouseX, mouseY, content -> {
                            buildColumn(content, selected, selectedViewport.x(), selectedViewport.y() - selectedScroll,
                                    selectedViewport.width() - (maxSelectedScroll > 0.0f ? SCROLLBAR_GUTTER : 0.0f),
                                    mouseX, mouseY, false, selectedViewport);
                        });
            });
        });
        renderBatch.render(tree);
    }

    @Override
    public void flush(UiRenderBatch renderBatch) {
        availableBuffer.flushAndClear();
        selectedBuffer.flushAndClear();
    }

    @Override
    public void extractOverlay(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (iconProvider == null || itemPreviews.isEmpty()) return;
        guiGraphics.nextStratum();
        if (lastOverlayViewport != null) {
            guiGraphics.enableScissor(
                    toMinecraftGuiXInt(lastOverlayViewport.x()),
                    toMinecraftGuiYInt(lastOverlayViewport.y()),
                    toMinecraftGuiXInt(lastOverlayViewport.right()),
                    toMinecraftGuiYInt(lastOverlayViewport.bottom())
            );
        }
        for (ItemPreview preview : itemPreviews) {
            drawItemPreview(guiGraphics, preview);
        }
        if (lastOverlayViewport != null) {
            guiGraphics.disableScissor();
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (event.button() != 0 || !bounds.contains(event.x(), event.y())) return false;
        // Check category tab clicks
        if (!categories.isEmpty()) {
            UiRect searchBounds = getSearchBounds(bounds.y());
            float catY = searchBounds.bottom() + 4.0f;
            float catTabX = bounds.x() + PADDING;
            for (int ci = -1; ci < categories.size(); ci++) {
                String catName = ci < 0 ? EpsilonTranslations.Gui.LIST_ALL.getTranslatedName() : categories.get(ci).name();
                float catTextW = textRenderer.getWidth(catName, 0.44f) + 10.0f;
                if (event.x() >= catTabX && event.x() <= catTabX + catTextW
                        && event.y() >= catY && event.y() <= catY + CATEGORY_TAB_HEIGHT) {
                    selectedCategory = ci;
                    resetScroll();
                    return true;
                }
                catTabX += catTextW + CATEGORY_TAB_GAP;
            }
        }
        UiRect availableViewport = lastAvailableViewport != null ? lastAvailableViewport : getAvailableViewport();
        UiRect selectedViewport = lastSelectedViewport != null ? lastSelectedViewport : getSelectedViewport();
        if (availableScrollBarDrag.mouseClicked(event.x(), event.y(), availableViewport, availableScroll, maxAvailableScroll)) {
            applyDraggedAvailableScroll(event.y(), availableViewport);
            return true;
        }
        if (selectedScrollBarDrag.mouseClicked(event.x(), event.y(), selectedViewport, selectedScroll, maxSelectedScroll)) {
            applyDraggedSelectedScroll(event.y(), selectedViewport);
            return true;
        }
        if (hoveredAdd != null) {
            addFn.accept(hoveredAdd);
            return true;
        }
        if (hoveredRemove != null) {
            removeFn.accept(hoveredRemove);
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return availableScrollBarDrag.mouseReleased() | selectedScrollBarDrag.mouseReleased();
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double mouseX, double mouseY) {
        boolean handled = false;
        if (availableScrollBarDrag.isDragging()) {
            applyDraggedAvailableScroll(event.y(), lastAvailableViewport != null ? lastAvailableViewport : getAvailableViewport());
            handled = true;
        }
        if (selectedScrollBarDrag.isDragging()) {
            applyDraggedSelectedScroll(event.y(), lastSelectedViewport != null ? lastSelectedViewport : getSelectedViewport());
            handled = true;
        }
        return handled;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return switch (event.key()) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (!query.isEmpty()) {
                    query = query.substring(0, query.length() - 1);
                    resetScroll();
                }
                yield true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                query = "";
                resetScroll();
                yield true;
            }
            default -> false;
        };
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (query.length() >= MAX_QUERY_LENGTH) return false;
        query += event.codepointAsString();
        resetScroll();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        UiRect availableViewport = lastAvailableViewport != null ? lastAvailableViewport : getAvailableViewport();
        if (availableViewport.contains(mouseX, mouseY) && maxAvailableScroll > 0.0f) {
            availableScrollVelocity -= (float) scrollY * SCROLL_STEP;
            return true;
        }
        UiRect selectedViewport = lastSelectedViewport != null ? lastSelectedViewport : getSelectedViewport();
        if (selectedViewport.contains(mouseX, mouseY) && maxSelectedScroll > 0.0f) {
            selectedScrollVelocity -= (float) scrollY * SCROLL_STEP;
            return true;
        }
        return false;
    }

    private List<T> filteredAvailable() {
        String needle = query.toLowerCase(Locale.ROOT).trim();
        List<T> result = new ArrayList<>();
        for (T entry : allEntries) {
            if (setting.getValue().contains(entry)) continue;
            if (selectedCategory >= 0 && selectedCategory < categories.size()) {
                if (!categories.get(selectedCategory).predicate().test(entry)) continue;
            }
            String text = displayNameFn.apply(entry).toLowerCase(Locale.ROOT);
            if (needle.isEmpty() || text.contains(needle)) result.add(entry);
        }
        return result;
    }

    private List<T> filteredSelected() {
        String needle = query.toLowerCase(Locale.ROOT).trim();
        List<T> result = new ArrayList<>();
        for (T entry : setting.getValue()) {
            String text = displayNameFn.apply(entry).toLowerCase(Locale.ROOT);
            if (needle.isEmpty() || text.contains(needle)) result.add(entry);
        }
        return result;
    }

    private void buildColumn(UiTree.Scope scope, List<T> entries, float columnX, float startY,
                             float columnWidth, int mouseX, int mouseY, boolean addColumn, UiRect viewport) {
        UiRect origin = scope.bound();
        final boolean hasIcons = iconProvider != null;
        for (int i = 0; i < entries.size(); i++) {
            T entry = entries.get(i);
            float rowY = startY + i * (ROW_HEIGHT + ROW_GAP);
            if (rowY + ROW_HEIGHT < viewport.y() || rowY > viewport.bottom()) continue;

            UiRect rowBounds = new UiRect(columnX, rowY, columnWidth, ROW_HEIGHT);
            boolean hovered = rowBounds.contains(mouseX, mouseY) && viewport.contains(mouseX, mouseY);
            if (hovered) {
                if (addColumn) hoveredAdd = entry;
                else hoveredRemove = entry;
            }

            float bgHover = addColumn ? (hovered ? 1.0f : 0.0f) : (hovered ? 0.45f : 0.0f);
            String rawName = displayNameFn.apply(entry);
            float actionX = rowBounds.right() - 12.0f;
            float textMaxWidth = rowBounds.width() - 22.0f;
            if (hasIcons) {
                ItemStack previewStack = iconProvider.apply(entry);
                if (previewStack != null && !previewStack.isEmpty()) {
                    float previewX = actionX - ITEM_PREVIEW_GAP - ITEM_PREVIEW_SIZE;
                    float previewY = rowBounds.y() + (rowBounds.height() - ITEM_PREVIEW_SIZE) * 0.5f;
                    itemPreviews.add(new ItemPreview(previewStack, previewX, previewY, ITEM_PREVIEW_SIZE));
                    textMaxWidth = previewX - rowBounds.x() - 12.0f;
                }
            }
            final String display = trim(rawName, 0.50f, textMaxWidth);
            final float textY = centeredTextY(0.0f, rowBounds.height(), 0.50f);

            UiRect localRowBounds = rowBounds.relativeTo(origin);
            scope.pushRelative(localRowBounds, row -> {
                if (addColumn) {
                    row.roundRect(0.0f, 0.0f, rowBounds.width(), rowBounds.height(), MD3Theme.CONTROL_RADIUS,
                            MD3Theme.lerp(MD3Theme.SURFACE_CONTAINER, MD3Theme.SURFACE_CONTAINER_HIGH, bgHover));
                    row.text(display, 6.0f, textY, 0.50f, hovered ? MD3Theme.TEXT_PRIMARY : MD3Theme.TEXT_SECONDARY);
                    row.text("+", rowBounds.width() - 12.0f, textY, 0.54f, hovered ? MD3Theme.TEXT_PRIMARY : MD3Theme.TEXT_SECONDARY);
                } else {
                    row.roundRect(0.0f, 0.0f, rowBounds.width(), rowBounds.height(), MD3Theme.CONTROL_RADIUS,
                            MD3Theme.lerp(MD3Theme.SECONDARY_CONTAINER, MD3Theme.PRIMARY_CONTAINER, bgHover));
                    row.text(display, 6.0f, textY, 0.50f, MD3Theme.ON_SECONDARY_CONTAINER);
                    row.text("-", rowBounds.width() - 12.0f, textY, 0.54f, MD3Theme.ON_SECONDARY_CONTAINER);
                }
            });
        }
    }

    private UiRect getSearchBounds(float popupY) {
        return new UiRect(bounds.x() + PADDING, popupY + TITLE_HEIGHT + 10.0f, bounds.width() - PADDING * 2.0f, SEARCH_HEIGHT);
    }

    private UiRect getViewport() {
        return getViewport(bounds.y());
    }

    private UiRect getViewport(float popupY) {
        float y = popupY + TITLE_HEIGHT + SEARCH_HEIGHT + HEADER_HEIGHT + 18.0f;
        return new UiRect(bounds.x() + PADDING, y, bounds.width() - PADDING * 2.0f, bounds.bottom() - y - PADDING);
    }

    private UiRect getAvailableViewport() {
        return getColumnViewports(getViewport(bounds.y()))[0];
    }

    private UiRect getSelectedViewport() {
        return getColumnViewports(getViewport(bounds.y()))[1];
    }

    private UiRect[] getColumnViewports(UiRect viewport) {
        float columnViewportWidth = (viewport.width() - COLUMN_GAP) / 2.0f;
        UiRect availableViewport = new UiRect(viewport.x(), viewport.y(), columnViewportWidth, viewport.height());
        UiRect selectedViewport = new UiRect(availableViewport.right() + COLUMN_GAP,
                viewport.y(), columnViewportWidth, viewport.height());
        return new UiRect[]{availableViewport, selectedViewport};
    }

    private void updateAvailableSmoothScroll(float partialTick) {
        if (maxAvailableScroll <= 0.0f) {
            resetAvailableScroll();
            return;
        }
        if (Math.abs(availableScrollVelocity) <= 0.01f || partialTick <= 0.0f) return;
        float nextScroll = Mth.clamp(availableScroll + availableScrollVelocity * partialTick, 0.0f, maxAvailableScroll);
        if (Float.compare(nextScroll, availableScroll) == 0) {
            availableScrollVelocity = 0.0f;
            return;
        }
        availableScroll = nextScroll;
        availableScrollVelocity *= SCROLL_DECAY;
        if (Math.abs(availableScrollVelocity) < MIN_SCROLL_VELOCITY) availableScrollVelocity = 0.0f;
    }

    private void updateSelectedSmoothScroll(float partialTick) {
        if (maxSelectedScroll <= 0.0f) {
            resetSelectedScroll();
            return;
        }
        if (Math.abs(selectedScrollVelocity) <= 0.01f || partialTick <= 0.0f) return;
        float nextScroll = Mth.clamp(selectedScroll + selectedScrollVelocity * partialTick, 0.0f, maxSelectedScroll);
        if (Float.compare(nextScroll, selectedScroll) == 0) {
            selectedScrollVelocity = 0.0f;
            return;
        }
        selectedScroll = nextScroll;
        selectedScrollVelocity *= SCROLL_DECAY;
        if (Math.abs(selectedScrollVelocity) < MIN_SCROLL_VELOCITY) selectedScrollVelocity = 0.0f;
    }

    private void resetScroll() {
        resetAvailableScroll();
        resetSelectedScroll();
    }

    private void resetAvailableScroll() {
        availableScroll = 0.0f;
        availableScrollVelocity = 0.0f;
    }

    private void resetSelectedScroll() {
        selectedScroll = 0.0f;
        selectedScrollVelocity = 0.0f;
    }

    private void applyDraggedAvailableScroll(double mouseY, UiRect viewport) {
        float newScroll = availableScrollBarDrag.mouseDragged(mouseY, viewport, maxAvailableScroll);
        if (newScroll >= 0.0f) {
            availableScroll = Mth.clamp(newScroll, 0.0f, maxAvailableScroll);
            availableScrollVelocity = 0.0f;
        }
    }

    private void applyDraggedSelectedScroll(double mouseY, UiRect viewport) {
        float newScroll = selectedScrollBarDrag.mouseDragged(mouseY, viewport, maxSelectedScroll);
        if (newScroll >= 0.0f) {
            selectedScroll = Mth.clamp(newScroll, 0.0f, maxSelectedScroll);
            selectedScrollVelocity = 0.0f;
        }
    }

    private String trim(String value, float scale, float maxWidth) {
        if (value == null || value.isEmpty()) return "";
        int maxChars = Math.max(3, (int) (maxWidth / (5.0f * scale)));
        return value.length() <= maxChars ? value : value.substring(0, Math.max(0, maxChars - 3)) + "...";
    }

    private float centeredTextY(float boxY, float boxHeight, float scale) {
        return boxY + (boxHeight - textRenderer.getHeight(scale)) * 0.5f;
    }

    private void drawItemPreview(GuiGraphicsExtractor guiGraphics, ItemPreview preview) {
        if (preview.stack().isEmpty()) return;
        float scale = preview.size() / 16.0f;
        float guiScale = (float) (scale * LuminRenderSystem.getGuiScale() / mc.getWindow().getGuiScale());
        float guiX = toMinecraftGuiX(preview.x());
        float guiY = toMinecraftGuiY(preview.y());
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(guiX + guiScale, guiY + guiScale);
        guiGraphics.pose().scale(guiScale, guiScale);
        guiGraphics.item(preview.stack(), 0, 0);
        guiGraphics.pose().popMatrix();
    }

    private float toMinecraftGuiX(float epsilonX) {
        return (float) LuminRenderSystem.toMinecraftGuiX(epsilonX);
    }

    private float toMinecraftGuiY(float epsilonY) {
        return (float) LuminRenderSystem.toMinecraftGuiY(epsilonY);
    }

    private int toMinecraftGuiXInt(float epsilonX) {
        return (int) Math.round(toMinecraftGuiX(epsilonX));
    }

    private int toMinecraftGuiYInt(float epsilonY) {
        return (int) Math.round(toMinecraftGuiY(epsilonY));
    }

    @Override
    public void close() {
        availableBuffer.close();
        selectedBuffer.close();
        textRenderer.close();
        itemPreviews.clear();
    }

    private record ItemPreview(ItemStack stack, float x, float y, float size) {
    }

    public record Category<T>(String name, Predicate<T> predicate) {
    }
}
