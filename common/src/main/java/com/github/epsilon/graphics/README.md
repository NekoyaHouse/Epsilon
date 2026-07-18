# Lumin Graphics

**English** | [简体中文](README_zh.md)

Lumin Graphics is a lightweight, high-performance rendering framework designed for modern Minecraft modding.

### IMPORTANT: The English version may occasionally be out of sync.

---

## Core Features

* **SDF Rounded Rectangles**: Smooth, anti-aliased corners calculated via fragment shaders. Supports dynamic radii
  without modifying vertex data.
* **High-Performance TTF**: Advanced TrueType Font rendering utilizing an atlas-based batching mechanism to
  significantly reduce Draw Calls.

---

## 💡 WARNING

### Lifecycle Synchronization Constraints

Within a single frame's render loop, avoid calling `Renderer.clear()`
and then `draw()` again on the same instance after one or more
`Renderer.draw()` calls have already been executed. Doing so will lead
to multiple buffer allocations within a single frame, reducing the
effectiveness of **In-Flight** optimizations.

If your logic requirements necessitate multiple cycles of clearing and
drawing within a single frame:

**It is recommended to instantiate a new `Renderer` to handle subsequent tasks.**

**💡 NOTE**: Please avoid creating excessive `Renderer` instances, as this may lead to over-allocation of VRAM.
Alternatively, you can specify a smaller buffer size than the default value when instantiating a `Renderer`.

---

## Quick Start

All rendering operations in Lumin Graphics are performed through specialized **Renderers**.

### Available Renderers

* `RectRenderer`: Optimized for standard flat rectangles.
* `RoundRectRenderer`: Designed for anti-aliased rectangles with dynamic corner radii.
* `TtfTextRenderer`: For high-performance TrueType font rendering.
* `TextureRenderer`: For batch-drawing various textures.

### Initialization & Thread Safety

Renderers **must** be initialized on the **Render Thread**. We recommend using `Suppliers.memoize` (from Guava or
Minecraft libraries) to ensure safe and lazy initialization.

```java
// Recommended initialization method
private final Supplier<RectRenderer> rectRenderer = Suppliers.memoize(RectRenderer::new);

// Use .get() to access the renderer instance
rectRenderer.get().addRect(10f,10f,100f,100f,Color.WHITE);

```

---

### Usage Patterns

#### 1. Basic Draw & Reset

For most immediate-mode UI tasks, you need to add shapes and clear the buffer within the same frame:

```java
// 1. Add shapes to the buffer
rectRenderer.get().addRect(10f,10f,200f,200f,Color.WHITE);

// 2. Draw to the screen and clear data before the next frame
rectRenderer.get().draw();
rectRenderer.get().clear();

// Alternatively, you can use the shortcut:
rectRenderer.get().drawAndClear();

```

#### 2. Buffer Reusability

If your UI content does not change every frame, you can add vertices once and draw them multiple times in subsequent
frames, thereby saving CPU overhead.

```java
// During the initialization phase or the first frame:
rectRenderer.get().addRect(10f,10f,200f,200f,Color.CYAN);

// In the rendering loop:
rectRenderer.get().draw(); // Content remains in the GPU buffer until .clear() is called.

```

---

### 💡 Optimization Tips

When using **Lumin Graphics**, keep in mind: calling `.draw()` multiple times without calling `.clear()` is extremely
efficient. It simply re-triggers the draw command for existing GPU data without the need to re-upload vertex data.

---

## License

* **Lumin Graphics** is licensed under the [GNU General Public License v3.0](LICENSE).

---

Copyright © 2026 NekoyaHouse.
