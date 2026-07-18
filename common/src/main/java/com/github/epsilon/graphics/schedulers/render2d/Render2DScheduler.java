package com.github.epsilon.graphics.schedulers.render2d;

import com.github.epsilon.graphics.LuminRenderPipelines;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.renderers.*;
import com.github.epsilon.graphics.text.ttf.TtfFontLoader;
import com.github.epsilon.modules.impl.ClientSetting;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * 2D GUI 渲染调度器。
 * <p>
 * GUI 只提交声明式绘制命令；调度器负责 layer、scissor、空间桶、批次规划和 renderer 复用。
 */
public final class Render2DScheduler implements AutoCloseable {

    public static final int DEFAULT_QUADTREE_THRESHOLD = 192;
    private static final int DEFAULT_QUADTREE_MAX_DEPTH = 5;
    private static final Comparator<Render2DCommand> STABLE_SEQUENCE_ORDER = Comparator.comparingLong(Render2DCommand::sequence);
    private static final List<Render2DCommandKind> KIND_FLUSH_ORDER = List.of(
            Render2DCommandKind.SHADOW,
            Render2DCommandKind.ROUND_RECT,
            Render2DCommandKind.ROUND_RECT_OUTLINE,
            Render2DCommandKind.RECT,
            Render2DCommandKind.TRIANGLE,
            Render2DCommandKind.TEXTURE,
            Render2DCommandKind.TEXT
    );

    private final CommandStore commandStore;
    private final Int2ObjectMap<LayerHandle> layerHandles = new Int2ObjectOpenHashMap<>();
    private final RendererPool rendererPool = new RendererPool();
    private final TextRenderer measureTextRenderer = TextRenderer.create(256 * 1024L);
    private long sequence;

    public Render2DScheduler() {
        this(DEFAULT_QUADTREE_THRESHOLD);
    }

    public Render2DScheduler(int quadtreeThreshold) {
        this.commandStore = new CommandStore(Math.max(1, quadtreeThreshold));
    }

    public LayerHandle layer(int layer) {
        return layerHandles.computeIfAbsent(layer, ignored -> new LayerHandle(this, layer));
    }

    public TextRenderer textMetrics() {
        return measureTextRenderer;
    }

    public boolean isEmpty() {
        return commandStore.isEmpty();
    }

    public void clear() {
        commandStore.clear();
        for (LayerHandle handle : layerHandles.values()) {
            handle.clearScissor();
        }
        sequence = 0L;
    }

    public void clearLayer(int layer) {
        commandStore.clearLayer(layer);
        LayerHandle handle = layerHandles.get(layer);
        if (handle != null) {
            handle.clearScissor();
        }
    }

    public void flush() {
        if (commandStore.isEmpty()) {
            return;
        }

        flushBatchGroups(FrameBatchPlanner.plan(commandStore.planBatches()));
        rendererPool.clear();
    }

    public void flushLayer(int layer) {
        LayerBucket bucket = commandStore.layer(layer);
        if (bucket != null) {
            flushLayer(bucket);
            rendererPool.clear();
        }
    }

    public void flushAndClear() {
        flush();
        clear();
    }

    private void add(Render2DCommand command) {
        commandStore.add(command);
    }

    private long nextSequence() {
        return sequence++;
    }

    private void flushLayer(LayerBucket bucket) {
        if (bucket.count() == 0) {
            return;
        }
        flushBatchGroups(FrameBatchPlanner.plan(bucket.planBatches()));
    }

    private void flushBatchGroups(List<BatchGroup> groups) {
        if (groups.isEmpty()) {
            return;
        }
        List<PreparedBatch> prepared = new ArrayList<>(groups.size());
        for (BatchGroup group : groups) {
            PreparedBatch batch = prepareBatchGroup(group);
            if (batch != null) {
                prepared.add(batch);
            }
        }
        flushPreparedBatches(prepared);
    }

    private PreparedBatch prepareBatchGroup(BatchGroup group) {
        if (group.commands().isEmpty() || group.scissor() != null && !group.scissor().visible()) {
            return null;
        }
        RendererBundle renderers = rendererPool.acquire(group.kind(), group.scissor());
        for (Render2DCommand command : group.commands()) {
            emit(command, renderers);
        }
        return new PreparedBatch(pipelineFor(group.kind()), renderers);
    }

    private void flushPreparedBatches(List<PreparedBatch> batches) {
        if (batches.isEmpty()) {
            return;
        }

        LuminRenderSystem.applyOrthoProjection();
        List<PreparedBatch> drawable = new ArrayList<>(batches.size());
        for (PreparedBatch batch : batches) {
            if (batch.renderers().prepareSharedDraw()) {
                drawable.add(batch);
            }
        }
        if (drawable.isEmpty()) {
            return;
        }

        int index = 0;
        while (index < drawable.size()) {
            RenderPipeline pipeline = drawable.get(index).pipeline();
            int end = index + 1;
            while (end < drawable.size() && drawable.get(end).pipeline() == pipeline) {
                end++;
            }
            flushPipelineRun(pipeline, drawable, index, end);
            index = end;
        }
    }

    private void flushPipelineRun(RenderPipeline pipeline, List<PreparedBatch> batches, int start, int end) {
        GpuTextureView colorView = LuminRenderSystem.resolveColorView();
        if (colorView == null) {
            return;
        }

        GpuTextureView depthView = pipeline == LuminRenderPipelines.TEXTURE ? null : LuminRenderSystem.resolveDepthView();
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Lumin 2D Pipeline Run",
                colorView, OptionalInt.empty(),
                depthView, OptionalDouble.empty())
        ) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            for (int i = start; i < end; i++) {
                batches.get(i).renderers().draw(pass);
            }
        }
    }

    private static RenderPipeline pipelineFor(Render2DCommandKind kind) {
        return switch (kind) {
            case SHADOW -> LuminRenderPipelines.SHADOW;
            case ROUND_RECT -> LuminRenderPipelines.ROUND_RECT;
            case ROUND_RECT_OUTLINE -> LuminRenderPipelines.ROUND_RECT_OUTLINE;
            case RECT -> LuminRenderPipelines.RECTANGLE;
            case TRIANGLE -> LuminRenderPipelines.TRIANGLE;
            case TEXTURE -> LuminRenderPipelines.TEXTURE;
            case TEXT -> ClientSetting.INSTANCE.fontAntiAliasing.getValue()
                    ? LuminRenderPipelines.TTF_FONT_AA
                    : LuminRenderPipelines.TTF_FONT_NO_AA;
        };
    }

    private static void emit(Render2DCommand command, RendererBundle renderers) {
        switch (command) {
            case Render2DCommand.Shadow shadow -> renderers.shadowRenderer().addShadow(
                    shadow.bounds().x(), shadow.bounds().y(), shadow.bounds().width(), shadow.bounds().height(),
                    shadow.radiusTopLeft(), shadow.radiusTopRight(), shadow.radiusBottomRight(), shadow.radiusBottomLeft(),
                    shadow.blurRadius(), shadow.color()
            );
            case Render2DCommand.RoundRect rect -> renderers.roundRectRenderer().addRoundRectGradient(
                    rect.bounds().x(), rect.bounds().y(), rect.bounds().width(), rect.bounds().height(),
                    rect.radiusTopLeft(), rect.radiusTopRight(), rect.radiusBottomRight(), rect.radiusBottomLeft(),
                    rect.topLeft(), rect.bottomLeft(), rect.bottomRight(), rect.topRight()
            );
            case Render2DCommand.RoundRectOutline outline -> renderers.roundRectOutlineRenderer().addOutline(
                    outline.bounds().x(), outline.bounds().y(), outline.bounds().width(), outline.bounds().height(),
                    outline.radiusTopLeft(), outline.radiusTopRight(), outline.radiusBottomRight(), outline.radiusBottomLeft(),
                    outline.outlineWidth(), outline.color()
            );
            case Render2DCommand.Rect rect -> renderers.rectRenderer().addRectGradient(
                    rect.bounds().x(), rect.bounds().y(), rect.bounds().width(), rect.bounds().height(),
                    rect.topLeft(), rect.bottomLeft(), rect.bottomRight(), rect.topRight()
            );
            case Render2DCommand.Triangle triangle -> renderers.triangleRenderer().addChevronTriangle(
                    triangle.centerX(), triangle.centerY(), triangle.size(), triangle.progress(), triangle.color()
            );
            case Render2DCommand.Texture texture -> {
                if (texture.texture() instanceof Render2DTexture.IdentifierRef ref) {
                    if (texture.rotationDegrees() == 0.0f) {
                        renderers.textureRenderer().addRoundedTexture(ref.identifier(),
                                texture.bounds().x(), texture.bounds().y(), texture.bounds().width(), texture.bounds().height(),
                                texture.radiusTopLeft(), texture.radiusTopRight(), texture.radiusBottomRight(), texture.radiusBottomLeft(),
                                texture.u0(), texture.v0(), texture.u1(), texture.v1(), texture.color(), ref.linearFilter());
                    } else {
                        renderers.textureRenderer().addRotatedTexture(ref.identifier(),
                                texture.bounds().x(), texture.bounds().y(), texture.bounds().width(), texture.bounds().height(),
                                texture.u0(), texture.v0(), texture.u1(), texture.v1(), texture.color(),
                                texture.originX(), texture.originY(), texture.rotationDegrees(), ref.linearFilter());
                    }
                } else if (texture.texture() instanceof Render2DTexture.LuminRef ref) {
                    if (texture.rotationDegrees() == 0.0f) {
                        renderers.textureRenderer().addRoundedTexture(ref.texture(),
                                texture.bounds().x(), texture.bounds().y(), texture.bounds().width(), texture.bounds().height(),
                                texture.radiusTopLeft(), texture.radiusTopRight(), texture.radiusBottomRight(), texture.radiusBottomLeft(),
                                texture.u0(), texture.v0(), texture.u1(), texture.v1(), texture.color());
                    } else {
                        renderers.textureRenderer().addRotatedTexture(ref.texture(),
                                texture.bounds().x(), texture.bounds().y(), texture.bounds().width(), texture.bounds().height(),
                                texture.u0(), texture.v0(), texture.u1(), texture.v1(), texture.color(),
                                texture.originX(), texture.originY(), texture.rotationDegrees());
                    }
                }
            }
            case Render2DCommand.Text text -> {
                if (text.fontLoader() != null) {
                    if (text.rotationDegrees() == 0.0f) {
                        renderers.textRenderer().addText(text.text(), text.x(), text.y(), text.scale(), text.color(), text.fontLoader());
                    } else {
                        renderers.textRenderer().addRotatedText(text.text(), text.x(), text.y(), text.scale(), text.color(), text.fontLoader(), text.originX(), text.originY(), text.rotationDegrees());
                    }
                } else {
                    if (text.rotationDegrees() == 0.0f) {
                        renderers.textRenderer().addText(text.text(), text.x(), text.y(), text.scale(), text.color());
                    } else {
                        renderers.textRenderer().addRotatedText(text.text(), text.x(), text.y(), text.scale(), text.color(), text.originX(), text.originY(), text.rotationDegrees());
                    }
                }
            }
        }
    }

    @Override
    public void close() {
        clear();
        rendererPool.close();
        measureTextRenderer.close();
        layerHandles.clear();
    }

    public static final class LayerHandle {
        private final Render2DScheduler scheduler;
        private final int layer;
        private Render2DScissor scissor;

        private LayerHandle(Render2DScheduler scheduler, int layer) {
            this.scheduler = scheduler;
            this.layer = layer;
        }

        public int layer() {
            return layer;
        }

        public Render2DScissor scissor() {
            return scissor;
        }

        public void setScissor(int x, int y, int width, int height) {
            // 裁剪状态跟随 layer handle 被采样进命令，后续命令会带着当时的 scissor 进入批次规划。
            this.scissor = new Render2DScissor(x, y, width, height);
        }

        public void clearScissor() {
            this.scissor = null;
        }

        public void addShadow(float x, float y, float width, float height, float radius, float blurRadius, Color color) {
            addShadow(x, y, width, height, radius, radius, radius, radius, blurRadius, color);
        }

        public void addShadow(float x, float y, float width, float height, float topLeft, float topRight,
                              float bottomRight, float bottomLeft, float blurRadius, Color color) {
            scheduler.add(new Render2DCommand.Shadow(layer, scheduler.nextSequence(),
                    Render2DBounds.of(x, y, width, height), scissor,
                    topLeft, topRight, bottomRight, bottomLeft, blurRadius, color));
        }

        public void addRoundRect(float x, float y, float width, float height, float radius, Color color) {
            addRoundRect(x, y, width, height, radius, radius, radius, radius, color);
        }

        public void addRoundRect(float x, float y, float width, float height, float topLeft, float topRight,
                                 float bottomRight, float bottomLeft, Color color) {
            addRoundRectGradient(x, y, width, height, topLeft, topRight, bottomRight, bottomLeft, color, color, color, color);
        }

        public void addRoundRectGradient(float x, float y, float width, float height, float topLeftRadius, float topRightRadius,
                                         float bottomRightRadius, float bottomLeftRadius, Color topLeft, Color bottomLeft,
                                         Color bottomRight, Color topRight) {
            scheduler.add(new Render2DCommand.RoundRect(layer, scheduler.nextSequence(),
                    Render2DBounds.of(x, y, width, height), scissor,
                    topLeftRadius, topRightRadius, bottomRightRadius, bottomLeftRadius,
                    topLeft, bottomLeft, bottomRight, topRight));
        }

        public void addOutline(float x, float y, float width, float height, float radius, float outlineWidth, Color color) {
            addOutline(x, y, width, height, radius, radius, radius, radius, outlineWidth, color);
        }

        public void addOutline(float x, float y, float width, float height, float topLeft, float topRight,
                               float bottomRight, float bottomLeft, float outlineWidth, Color color) {
            scheduler.add(new Render2DCommand.RoundRectOutline(layer, scheduler.nextSequence(),
                    Render2DBounds.of(x, y, width, height), scissor,
                    topLeft, topRight, bottomRight, bottomLeft, outlineWidth, color));
        }

        public void addRect(float x, float y, float width, float height, Color color) {
            addRectGradient(x, y, width, height, color, color, color, color);
        }

        public void addRectGradient(float x, float y, float width, float height,
                                    Color topLeft, Color bottomLeft, Color bottomRight, Color topRight) {
            scheduler.add(new Render2DCommand.Rect(layer, scheduler.nextSequence(),
                    Render2DBounds.of(x, y, width, height), scissor,
                    topLeft, bottomLeft, bottomRight, topRight));
        }

        public void addRectOutline(float x, float y, float width, float height, float outline, Color color) {
            float sideHeight = height - outline * 2.0f;
            addRect(x, y, width, outline, color);
            addRect(x, y + height - outline, width, outline, color);
            addRect(x, y + outline, outline, sideHeight, color);
            addRect(x + width - outline, y + outline, outline, sideHeight, color);
        }

        public void addChevronTriangle(float centerX, float centerY, float size, float progress, Color color) {
            scheduler.add(new Render2DCommand.Triangle(layer, scheduler.nextSequence(),
                    Render2DBounds.of(centerX - size, centerY - size, size * 2.0f, size * 2.0f), scissor,
                    centerX, centerY, size, progress, color));
        }

        public void addTexture(Render2DTexture texture, float x, float y, float width, float height,
                               float u0, float v0, float u1, float v1, Color color) {
            addRoundedTexture(texture, x, y, width, height, 0.0f, u0, v0, u1, v1, color);
        }

        public void addRoundedTexture(Render2DTexture texture, float x, float y, float width, float height,
                                      float radius, float u0, float v0, float u1, float v1, Color color) {
            addRoundedTexture(texture, x, y, width, height, radius, radius, radius, radius, u0, v0, u1, v1, color);
        }

        public void addRoundedTexture(Render2DTexture texture, float x, float y, float width, float height,
                                      float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft,
                                      float u0, float v0, float u1, float v1, Color color) {
            // Texture command 只保存轻量资源引用；真正的纹理缓存和 GPU 提交由 TextureRenderer 在 flush 时处理。
            scheduler.add(new Render2DCommand.Texture(layer, scheduler.nextSequence(),
                    Render2DBounds.of(x, y, width, height), scissor, texture,
                    radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft,
                    u0, v0, u1, v1, color, x, y, 0.0f));
        }

        public void addRotatedTexture(Render2DTexture texture, float x, float y, float width, float height,
                                      float u0, float v0, float u1, float v1, Color color,
                                      float originX, float originY, float rotationDegrees) {
            scheduler.add(new Render2DCommand.Texture(layer, scheduler.nextSequence(),
                    Render2DBounds.of(x, y, width, height), scissor, texture,
                    0.0f, 0.0f, 0.0f, 0.0f,
                    u0, v0, u1, v1, color, originX, originY, rotationDegrees));
        }

        public void addText(String text, float x, float y, float scale, Color color) {
            addText(text, x, y, scale, color, null);
        }

        public void addText(String text, float x, float y, float scale, Color color, TtfFontLoader fontLoader) {
            addText(text, x, y, scale, color, fontLoader, x, y, 0.0f);
        }

        public void addRotatedText(String text, float x, float y, float scale, Color color, float originX, float originY, float rotationDegrees) {
            addText(text, x, y, scale, color, null, originX, originY, rotationDegrees);
        }

        public void addRotatedText(String text, float x, float y, float scale, Color color, TtfFontLoader fontLoader, float originX, float originY, float rotationDegrees) {
            addText(text, x, y, scale, color, fontLoader, originX, originY, rotationDegrees);
        }

        private void addText(String text, float x, float y, float scale, Color color, TtfFontLoader fontLoader, float originX, float originY, float rotationDegrees) {
            TextRenderer metrics = scheduler.textMetrics();
            float width = fontLoader != null ? metrics.getWidth(text, scale, fontLoader) : metrics.getWidth(text, scale);
            float height = fontLoader != null ? metrics.getHeight(scale, fontLoader) : metrics.getHeight(scale);
            Render2DBounds bounds = rotationDegrees == 0.0f
                    ? Render2DBounds.of(x, y, width, height)
                    : rotatedBounds(x, y, width, height, originX, originY, rotationDegrees);
            scheduler.add(new Render2DCommand.Text(layer, scheduler.nextSequence(),
                    bounds, scissor,
                    text, x, y, scale, color, fontLoader, originX, originY, rotationDegrees));
        }

        private static Render2DBounds rotatedBounds(float x, float y, float width, float height, float originX, float originY, float rotationDegrees) {
            float radians = (float) Math.toRadians(rotationDegrees);
            float cos = (float) Math.cos(radians);
            float sin = (float) Math.sin(radians);
            float x1 = rotateX(x, y, originX, originY, cos, sin);
            float y1 = rotateY(x, y, originX, originY, cos, sin);
            float x2 = rotateX(x, y + height, originX, originY, cos, sin);
            float y2 = rotateY(x, y + height, originX, originY, cos, sin);
            float x3 = rotateX(x + width, y + height, originX, originY, cos, sin);
            float y3 = rotateY(x + width, y + height, originX, originY, cos, sin);
            float x4 = rotateX(x + width, y, originX, originY, cos, sin);
            float y4 = rotateY(x + width, y, originX, originY, cos, sin);
            float minX = Math.min(Math.min(x1, x2), Math.min(x3, x4));
            float minY = Math.min(Math.min(y1, y2), Math.min(y3, y4));
            float maxX = Math.max(Math.max(x1, x2), Math.max(x3, x4));
            float maxY = Math.max(Math.max(y1, y2), Math.max(y3, y4));
            return Render2DBounds.of(minX, minY, maxX - minX, maxY - minY);
        }

        private static float rotateX(float x, float y, float originX, float originY, float cos, float sin) {
            float dx = x - originX;
            float dy = y - originY;
            return originX + dx * cos - dy * sin;
        }

        private static float rotateY(float x, float y, float originX, float originY, float cos, float sin) {
            float dx = x - originX;
            float dy = y - originY;
            return originY + dx * sin + dy * cos;
        }
    }

    private static final class CommandStore {
        private final int quadtreeThreshold;
        private final Int2ObjectMap<LayerBucket> layers = new Int2ObjectOpenHashMap<>();

        private CommandStore(int quadtreeThreshold) {
            this.quadtreeThreshold = quadtreeThreshold;
        }

        private void add(Render2DCommand command) {
            // 先按整数 layer 隔离命令；每个 layer 内部再决定使用线性数组还是四叉树。
            layers.computeIfAbsent(command.layer(), ignored -> new LayerBucket(quadtreeThreshold)).add(command);
        }

        private boolean isEmpty() {
            return layers.isEmpty();
        }

        private Set<Integer> layers() {
            return layers.keySet();
        }

        private LayerBucket layer(int layer) {
            return layers.get(layer);
        }

        private List<BatchGroup> planBatches() {
            List<Integer> orderedLayers = new ArrayList<>(layers.keySet());
            Collections.sort(orderedLayers);

            List<BatchGroup> result = new ArrayList<>();
            for (int layer : orderedLayers) {
                LayerBucket bucket = layers.get(layer);
                if (bucket == null || bucket.count() == 0) {
                    continue;
                }
                for (BatchGroup group : bucket.planBatches()) {
                    if (group.visible()) {
                        result.add(group);
                    }
                }
            }
            return result;
        }

        private void clear() {
            layers.clear();
        }

        private void clearLayer(int layer) {
            layers.remove(layer);
        }
    }

    private static final class LayerBucket {
        private final int quadtreeThreshold;
        private final List<Render2DCommand> flatCommands = new ArrayList<>();
        private QuadTreeBucket quadTree;
        private Render2DBounds bounds;

        private LayerBucket(int quadtreeThreshold) {
            this.quadtreeThreshold = quadtreeThreshold;
        }

        private void add(Render2DCommand command) {
            if (bounds == null) {
                bounds = command.bounds();
            } else {
                bounds = bounds.include(command.bounds());
            }
            if (quadTree == null && flatCommands.size() + 1 < quadtreeThreshold) {
                // 小批量 GUI 使用紧凑数组，避免四叉树节点分配带来的固定成本。
                flatCommands.add(command);
                return;
            }
            if (quadTree == null) {
                promoteToQuadTree();
            }
            quadTree.add(command);
        }

        private int count() {
            return quadTree != null ? quadTree.count() : flatCommands.size();
        }

        private List<BatchGroup> planBatches() {
            List<Render2DCommand> ordered = new ArrayList<>(count());
            if (quadTree == null) {
                ordered.addAll(flatCommands);
            } else {
                quadTree.collect(ordered);
            }
            ordered.sort(STABLE_SEQUENCE_ORDER);
            return BatchPlanner.plan(ordered);
        }

        private void promoteToQuadTree() {
            // 命令数量超过阈值后才升级为空间桶，为后续可见性裁剪和脏区重建预留结构。
            Render2DBounds rootBounds = bounds == null ? Render2DBounds.of(0.0f, 0.0f, 1.0f, 1.0f) : padded(bounds);
            quadTree = new QuadTreeBucket(rootBounds, 0, DEFAULT_QUADTREE_MAX_DEPTH);
            for (Render2DCommand command : flatCommands) {
                quadTree.add(command);
            }
            flatCommands.clear();
        }

        private static Render2DBounds padded(Render2DBounds bounds) {
            float size = Math.max(Math.max(bounds.width(), bounds.height()), 1.0f);
            return Render2DBounds.of(bounds.x() - 1.0f, bounds.y() - 1.0f, size + 2.0f, size + 2.0f);
        }
    }

    private static final class QuadTreeBucket {
        private static final int NODE_CAPACITY = 24;

        private final Render2DBounds bounds;
        private final int depth;
        private final int maxDepth;
        private final List<Render2DCommand> commands = new ArrayList<>();
        private QuadTreeBucket[] children;
        private int count;

        private QuadTreeBucket(Render2DBounds bounds, int depth, int maxDepth) {
            this.bounds = bounds;
            this.depth = depth;
            this.maxDepth = maxDepth;
        }

        private void add(Render2DCommand command) {
            count++;
            if (children != null && insertIntoChild(command)) {
                return;
            }
            commands.add(command);
            if (commands.size() > NODE_CAPACITY && depth < maxDepth) {
                split();
            }
        }

        private int count() {
            return count;
        }

        private void collect(List<Render2DCommand> out) {
            out.addAll(commands);
            if (children == null) {
                return;
            }
            for (QuadTreeBucket child : children) {
                child.collect(out);
            }
        }

        private boolean insertIntoChild(Render2DCommand command) {
            for (QuadTreeBucket child : children) {
                if (child.bounds.contains(command.bounds())) {
                    child.add(command);
                    return true;
                }
            }
            return false;
        }

        private void split() {
            if (children != null) {
                return;
            }
            // 只把完全落入子象限的命令下沉，跨象限的大图元留在父节点以避免重复提交。
            float halfW = bounds.width() * 0.5f;
            float halfH = bounds.height() * 0.5f;
            children = new QuadTreeBucket[]{
                    new QuadTreeBucket(Render2DBounds.of(bounds.x(), bounds.y(), halfW, halfH), depth + 1, maxDepth),
                    new QuadTreeBucket(Render2DBounds.of(bounds.x() + halfW, bounds.y(), halfW, halfH), depth + 1, maxDepth),
                    new QuadTreeBucket(Render2DBounds.of(bounds.x(), bounds.y() + halfH, halfW, halfH), depth + 1, maxDepth),
                    new QuadTreeBucket(Render2DBounds.of(bounds.x() + halfW, bounds.y() + halfH, halfW, halfH), depth + 1, maxDepth)
            };
            Iterator<Render2DCommand> iterator = commands.iterator();
            while (iterator.hasNext()) {
                Render2DCommand command = iterator.next();
                if (insertIntoChild(command)) {
                    iterator.remove();
                }
            }
        }
    }

    private static final class BatchPlanner {
        private BatchPlanner() {
        }

        private static List<BatchGroup> plan(List<Render2DCommand> commands) {
            Map<BatchKey, BatchGroup> groups = new LinkedHashMap<>();
            for (Render2DCommand command : commands) {
                BatchKey key = new BatchKey(command.kind(), command.scissor());
                groups.computeIfAbsent(key, ignored -> new BatchGroup(command.kind(), command.scissor())).add(command);
            }

            List<BatchGroup> result = new ArrayList<>(groups.values());
            // 同 layer 内按 renderer 类型合批；需要精确保序的图元应通过更细的 layer 表达遮挡关系。
            result.sort(Comparator
                    .comparingInt((BatchGroup group) -> KIND_FLUSH_ORDER.indexOf(group.kind()))
                    .thenComparingLong(BatchGroup::firstSequence));
            for (BatchGroup group : result) {
                group.commands().sort(STABLE_SEQUENCE_ORDER);
            }
            return result;
        }
    }

    private static final class FrameBatchPlanner {
        private FrameBatchPlanner() {
        }

        private static List<BatchGroup> plan(List<BatchGroup> groups) {
            int count = groups.size();
            if (count <= 1) {
                return groups;
            }

            List<List<Integer>> outgoing = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                outgoing.add(new ArrayList<>());
            }
            int[] incomingCounts = new int[count];

            for (int i = 0; i < count; i++) {
                BatchGroup before = groups.get(i);
                for (int j = i + 1; j < count; j++) {
                    BatchGroup after = groups.get(j);
                    if (mustKeepOrder(before, after)) {
                        outgoing.get(i).add(j);
                        incomingCounts[j]++;
                    }
                }
            }

            boolean[] scheduled = new boolean[count];
            List<BatchGroup> result = new ArrayList<>(count);
            Render2DCommandKind preferredKind = null;
            int scheduledCount = 0;

            while (scheduledCount < count) {
                int next = selectReady(groups, scheduled, incomingCounts, preferredKind);
                if (next < 0) {
                    next = selectFirstUnscheduled(scheduled);
                }

                BatchGroup merged = groups.get(next);
                scheduledCount += schedule(next, outgoing, incomingCounts, scheduled);

                BatchKey mergeKey = merged.key();
                int sameKey;
                while ((sameKey = selectReady(groups, scheduled, incomingCounts, mergeKey)) >= 0) {
                    merged.append(groups.get(sameKey));
                    scheduledCount += schedule(sameKey, outgoing, incomingCounts, scheduled);
                }

                result.add(merged);
                preferredKind = merged.kind();
            }

            return result;
        }

        private static boolean mustKeepOrder(BatchGroup before, BatchGroup after) {
            return before.bounds().intersects(after.bounds());
        }

        private static int selectReady(List<BatchGroup> groups, boolean[] scheduled, int[] incomingCounts,
                                       Render2DCommandKind preferredKind) {
            int fallback = -1;
            for (int i = 0; i < groups.size(); i++) {
                if (scheduled[i] || incomingCounts[i] > 0) {
                    continue;
                }
                if (fallback < 0) {
                    fallback = i;
                }
                if (preferredKind != null && groups.get(i).kind() == preferredKind) {
                    return i;
                }
            }
            return fallback;
        }

        private static int selectReady(List<BatchGroup> groups, boolean[] scheduled, int[] incomingCounts,
                                       BatchKey key) {
            for (int i = 0; i < groups.size(); i++) {
                if (!scheduled[i] && incomingCounts[i] == 0 && groups.get(i).key().equals(key)) {
                    return i;
                }
            }
            return -1;
        }

        private static int selectFirstUnscheduled(boolean[] scheduled) {
            for (int i = 0; i < scheduled.length; i++) {
                if (!scheduled[i]) {
                    return i;
                }
            }
            return -1;
        }

        private static int schedule(int index, List<List<Integer>> outgoing, int[] incomingCounts, boolean[] scheduled) {
            if (index < 0 || scheduled[index]) {
                return 0;
            }
            scheduled[index] = true;
            for (int target : outgoing.get(index)) {
                incomingCounts[target]--;
            }
            return 1;
        }
    }

    private record BatchKey(Render2DCommandKind kind, Render2DScissor scissor) {
    }

    private static final class BatchGroup {
        private final Render2DCommandKind kind;
        private final Render2DScissor scissor;
        private final List<Render2DCommand> commands = new ArrayList<>();
        private Render2DBounds bounds;

        private BatchGroup(Render2DCommandKind kind, Render2DScissor scissor) {
            this.kind = kind;
            this.scissor = scissor;
        }

        private Render2DCommandKind kind() {
            return kind;
        }

        private Render2DScissor scissor() {
            return scissor;
        }

        private List<Render2DCommand> commands() {
            return commands;
        }

        private Render2DBounds bounds() {
            return bounds;
        }

        private BatchKey key() {
            return new BatchKey(kind, scissor);
        }

        private void add(Render2DCommand command) {
            commands.add(command);
            Render2DBounds orderingBounds = command.orderingBounds();
            bounds = bounds == null ? orderingBounds : bounds.include(orderingBounds);
        }

        private void append(BatchGroup other) {
            if (other.commands.isEmpty()) {
                return;
            }
            commands.addAll(other.commands);
            bounds = bounds == null ? other.bounds : bounds.include(other.bounds);
        }

        private boolean visible() {
            return !commands.isEmpty() && (scissor == null || scissor.visible());
        }

        private long firstSequence() {
            return commands.isEmpty() ? Long.MAX_VALUE : commands.getFirst().sequence();
        }
    }

    private static final class RendererPool implements AutoCloseable {
        private final Map<Render2DCommandKind, Deque<RendererBundle>> available = new Object2ObjectOpenHashMap<>();
        private final List<RendererBundle> used = new ArrayList<>();

        private RendererBundle acquire(Render2DCommandKind kind, Render2DScissor scissor) {
            Deque<RendererBundle> bundles = available.computeIfAbsent(kind, ignored -> new ArrayDeque<>());
            RendererBundle bundle = bundles.pollFirst();
            if (bundle == null) {
                // renderer 延迟创建并按 kind 回收到池中，scissor 每次取出时重新应用，避免按历史裁剪矩形增长。
                bundle = RendererBundle.create(kind);
            }
            bundle.applyScissor(scissor);
            used.add(bundle);
            return bundle;
        }

        private void clear() {
            for (RendererBundle bundle : used) {
                bundle.clear();
                available.computeIfAbsent(bundle.kind(), ignored -> new ArrayDeque<>()).addLast(bundle);
            }
            used.clear();
        }

        @Override
        public void close() {
            clear();
            for (Deque<RendererBundle> bundles : available.values()) {
                for (RendererBundle bundle : bundles) {
                    bundle.close();
                }
            }
            available.clear();
        }
    }

    private record PreparedBatch(RenderPipeline pipeline, RendererBundle renderers) {
    }

    private static final class RendererBundle implements AutoCloseable {
        private final Render2DCommandKind kind;
        private final IRenderer renderer;
        private Render2DScissor scissor;

        private RendererBundle(Render2DCommandKind kind, IRenderer renderer) {
            this.kind = kind;
            this.renderer = renderer;
        }

        private static RendererBundle create(Render2DCommandKind kind) {
            return switch (kind) {
                case SHADOW -> new RendererBundle(kind, ShadowRenderer.create());
                case ROUND_RECT -> new RendererBundle(kind, RoundRectRenderer.create());
                case ROUND_RECT_OUTLINE -> new RendererBundle(kind, RoundRectOutlineRenderer.create());
                case RECT -> new RendererBundle(kind, RectRenderer.create());
                case TRIANGLE -> new RendererBundle(kind, TriangleRenderer.create());
                case TEXTURE -> new RendererBundle(kind, TextureRenderer.create());
                case TEXT -> new RendererBundle(kind, TextRenderer.create());
            };
        }

        private Render2DCommandKind kind() {
            return kind;
        }

        private Render2DScissor scissor() {
            return scissor;
        }

        private ShadowRenderer shadowRenderer() {
            return (ShadowRenderer) renderer;
        }

        private RoundRectRenderer roundRectRenderer() {
            return (RoundRectRenderer) renderer;
        }

        private RoundRectOutlineRenderer roundRectOutlineRenderer() {
            return (RoundRectOutlineRenderer) renderer;
        }

        private RectRenderer rectRenderer() {
            return (RectRenderer) renderer;
        }

        private TriangleRenderer triangleRenderer() {
            return (TriangleRenderer) renderer;
        }

        private TextureRenderer textureRenderer() {
            return (TextureRenderer) renderer;
        }

        private TextRenderer textRenderer() {
            return (TextRenderer) renderer;
        }

        private void applyScissor(Render2DScissor scissor) {
            this.scissor = scissor;
            clearScissor();
            if (scissor == null) {
                return;
            }
            switch (renderer) {
                case ShadowRenderer shadow ->
                        shadow.setScissor(scissor.x(), scissor.y(), scissor.width(), scissor.height());
                case RoundRectRenderer roundRect ->
                        roundRect.setScissor(scissor.x(), scissor.y(), scissor.width(), scissor.height());
                case RoundRectOutlineRenderer outline ->
                        outline.setScissor(scissor.x(), scissor.y(), scissor.width(), scissor.height());
                case RectRenderer rect -> rect.setScissor(scissor.x(), scissor.y(), scissor.width(), scissor.height());
                case TriangleRenderer triangle ->
                        triangle.setScissor(scissor.x(), scissor.y(), scissor.width(), scissor.height());
                case TextureRenderer texture ->
                        texture.setScissor(scissor.x(), scissor.y(), scissor.width(), scissor.height());
                case TextRenderer text -> text.setScissor(scissor.x(), scissor.y(), scissor.width(), scissor.height());
                default -> {
                }
            }
        }

        private void clearScissor() {
            switch (renderer) {
                case ShadowRenderer shadow -> shadow.clearScissor();
                case RoundRectRenderer roundRect -> roundRect.clearScissor();
                case RoundRectOutlineRenderer outline -> outline.clearScissor();
                case RectRenderer rect -> rect.clearScissor();
                case TriangleRenderer triangle -> triangle.clearScissor();
                case TextureRenderer texture -> texture.clearScissor();
                case TextRenderer text -> text.clearScissor();
                default -> {
                }
            }
        }

        private void draw() {
            renderer.draw();
        }

        private boolean prepareSharedDraw() {
            return renderer.prepareSharedDraw();
        }

        private void draw(RenderPass pass) {
            renderer.draw(pass);
        }

        private void clear() {
            clearScissor();
            renderer.clear();
        }

        @Override
        public void close() {
            renderer.close();
        }
    }

}
