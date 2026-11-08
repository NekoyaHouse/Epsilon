# Lumin Graphics

**English** | [简体中文](README_zh.md)

Lumin Graphics is a lightweight, high-performance rendering framework designed for modern Minecraft modding.

The package is implemented in Kotlin (`common/src/main/kotlin/com/github/epsilon/graphics`) and exposes Kotlin and Java
entrypoints. The ordered 2D canvas API below replaces the old layer-handle API.

---

## Core Features

* **SDF Rounded Rectangles**: Smooth, anti-aliased corners calculated via fragment shaders. Supports dynamic radii
  without modifying vertex data.
* **MSDF TTF rendering**: STB_truetype supplies glyph metrics, outlines and effect coverage. The msdfgen core
  colors linear/quadratic/cubic edges and generates RGB distance fields in a linear RGBA8 atlas; shaders reconstruct
  coverage with the RGB median and an eight-texel distance range. A separate R8 atlas serves blur/glitch effects.
  Glyph generation stays on the CPU worker and atlas uploads stay on the render thread. Missing-glyph boxes use the
  same MSDF path. Fabric and NeoForge bundle LWJGL msdfgen 3.4.3 with Windows/Linux/macOS x64 and ARM64 natives;
  font loading continues to use STB rather than the msdfgen FreeType extension.
  All text uses the single-sample `TTF_FONT` pipeline (`ttf_font.fsh`), which reconstructs pixel coverage from
  screen-space distance with a one-pixel edge transition. There is no font AA setting or supersampling branch.

---

## 💡 WARNING

### Standalone renderer buffer reuse

Within a single frame's render loop, avoid calling `Renderer.clear()`
and then `draw()` again on the same instance after one or more
`Renderer.draw()` calls have already been executed. Doing so will lead to multiple buffer allocations within a single
frame, reducing the effectiveness of **In-Flight** optimizations.

Use the consuming 2D canvas for repeated submissions in one frame. Its fence-backed allocations support multiple flushes
safely. Standalone renderers keep their existing ring-buffer behavior.

---

## Retained UI and ordered 2D rendering

`UiScene` owns the composition tree and a shared 2D backend. Composition nodes keep their own display lists and child
references; unchanged lists reuse local GPU geometry. Transform, clip and tint reads have independent state
subscriptions. The executor caches compatible draw ranges and uses indexed indirect multi-draw when supported, with
ordinary draw calls as a fallback.

```text
Composition → UiRenderNode → local display lists → GeometryArena
                    ↓                               ↓
             Transform / Clip / Paint → ordered draw plan → executor
Immediate canvas → transient geometry ────────────────────────┘
```

### Ordering and submission

Within each flush, explicit layers sort ascending; submissions within a layer preserve declaration order, with child
trees expanded at their insertion point. Viewports follow the same rule and submit through the host batch. Primitive
types and textures never override this order. Rectangles, rounded rectangles, outlines and chevron triangles share the
shape pipeline; text, textures and effects use their own pipelines. Only adjacent ranges with compatible pipeline,
geometry page, resources, uniforms and scissor can batch. Ordinary AA/non-AA text allows up to eight atlas/sampler pairs
in one run; a per-range instance attribute selects the atlas without changing glyph order. A ninth pair starts another
run. Blur/glitch text and arbitrary textures retain their existing resource boundaries. No spatial reordering or global
overlap graph is used.

Dropdown module rows explicitly preserve their previous alpha composition inside each panel's reserved layer interval:
button fills/outlines, translucent row surfaces and Hold underlines, then names/key labels/visibility icons. The
previous scheduler implicitly drew round rectangles and outlines before rectangles; the row surface therefore dimmed the
buttons. Declaration order alone changes that appearance. These local layers retain the dimming and light labels while
keeping later panels above earlier panels. Non-overlapping header arrows precede header text, and row icons share the
text stage. Compatible ranges remain adjacent for indirect batching; the executor reuses unchanged buffer, scissor and
resource bindings within a pass.

The Dropdown main-panel icon grid keeps independent item caches, but submits its seven button surfaces before its seven
icons using relative layers inside the panel interval. The top divider stays before the button surfaces; settings and
hover labels retain their later stage. With shared geometry pages and atlas, the grid uses two compatible runs instead
of fourteen alternating shape/text runs. Adjacent chevrons also compile into the same geometry batch as fills and
outlines. A planner regression verifies the grid run count and each surface-before-icon relationship; this does not
establish a captured GPU frame-time improvement.

Dropdown and Panel GUI Setting Group headers submit card fills, outlines, count chips and chevrons before title/count
text. Each sibling traversal defers labels across consecutive collapsed headers, whose rectangles do not overlap, so
shared geometry/resources allow one shape run and one text run for the sequence. Labels are drained before expanded
cards and ordinary setting controls; nested traversals drain their own labels before leaving the current clip. This
preserves parent/child alpha order and animation clipping without global layer changes. Regression coverage uses actual
Dropdown section nodes to verify run count, positions, control boundaries and nested animation clips; GPU draw counts
still require a capture.

The draw-plan regression fixture with forty collapsed rows and two atlases produces five runs including the
shadow/header, or six with the final scrollbar. This is a planner test, not a GPU capture guarantee: geometry page
changes when consolidation cannot run, expanded settings, different clips/uniforms, additional effects or more than
eight atlas/sampler pairs can increase the count. Each indexed run uses one API draw when the device supports multi-draw
indirect and nonzero first-instance; otherwise it uses the ordinary per-range fallback.

On that indirect path, after renderers prepare and before packets capture buffer bindings, the arena consolidates active
same-stride geometry into one GPU page. Instance properties and indirect commands use separate pages and never move with
geometry. Only addresses change; draw order, resources and scissor boundaries remain intact. Adjacent source ranges
share copy commands, and retained renderers resolve their updated slices on submission. A regression reproducing the
captured A/B/A/B/A vertex-page pattern reduces five shape runs containing fifty ranges to one; this validates allocation
and planning, not a new capture or frame-time result.

The old `layer()` / `LayerHandle` entrypoints are replaced by `canvas(layer)` / `Canvas`; `UiRenderBatch.layerHandle()`
becomes `canvas()`.

Packet recording caches each source pipeline's immutable uniform names and reuses a binding snapshot while its pipeline,
buffers, resources and scissor stay unchanged. Vertex slice offsets and instance-property indices remain per-range
values. Resource changes create new snapshots, preserving earlier packets. Input collection looks up hover probes by
composition identity; refreshing a composition visits only its own probes, including when deferred transforms move its
bounds.

```kotlin
val canvas = scheduler.canvas(0)
canvas.addRect(10f, 10f, 100f, 24f, Color.WHITE)
scheduler.flush() // Consumes this submission.
scheduler.flush() // Draws nothing.
canvas.addText("Next", 10f, 40f, 1f, Color.WHITE)
scheduler.flush() // New geometry remains valid alongside earlier GPU work.
```

Immediate mode requires no stable key. `clear()` discards unsubmitted records, while `resetCache()` releases retained
caches. To replay consumed work, explicitly submit it again. A flush is an ordering barrier: flush before vanilla
rendering, target changes, blur passes or `LuminImmediateRenderer` / 3D rendering. These independent renderers retain
their existing APIs. Standalone `RectRenderer.draw()` and other renderer draws still replay their buffers; their
behavior differs from the consuming scheduler.

### Deferred properties and geometry

Import the DSL from `com.github.epsilon.gui.lib` and transforms from `graphics.schedulers.render2d`:

```kotlin
scene.LuminUIScene(bounds, key = "list") {
    Clip({ UiRect(0f, 0f, size.width, size.height) }) {
        Transform({ Render2DTransform.translation(0f, -scroll.value) }) {
            Paint({ foreground.value }) {
                CacheDraw { Text(label.value, 4f, 4f, 0.7f, Color.WHITE) }
            }
        }
    }
}
```

Reads in `Transform`, `Clip` and `Paint` invalidate only their property phase. `CacheDraw` observes geometry
dependencies independently and allows primitives and drawing containers, excluding composition, input and viewport
declarations. Reads directly in the composition retain normal recomposition semantics. `Paint` multiplies each
primitive's RGBA; it is not offscreen group opacity. Input uses inverse transforms, and frame callbacks and animation
clocks continue when geometry is reused.

Use stable item keys with `LuminUIItem`. Legacy absolute coordinates are normalized when geometry is compiled, never
used as cache identity. `retainPosition = true` on `LuminUIScene` lets a same-size scene move through properties only;
enable it only when closures obey local-coordinate contracts. The default remains `false` for existing
position-dependent callbacks. Use deferred `Transform` for scrolling; the legacy `Viewport(scroll = ...)` argument still
shifts coordinates during composition and can trigger rebuilding. Dropdown category rows use the deferred transform
path. Other legacy layout and animated widgets recompose when their geometry inputs change.

Clipping uses hardware scissor. Rotated or skewed clips use their transformed axis-aligned bounding boxes, not an exact
rotated mask. Offscreen ranges are culled, but the first compilation still prepares their geometry; this is not list
virtualization.

### Resources, lifetime and measurement

Each executor submission batches geometry, property and indirect-command uploads. The batch polls retirement fences once
before preparation, instead of polling again for every small upload. Allocation under budget pressure can make one
additional nonblocking reclamation attempt before reporting failure. Batch nesting and exceptions restore the normal
reclamation policy. Earlier draws remain protected by the same fences.

Create, update and close scenes and GPU resources on the render thread. `beginFrame()` / `endFrame()` bracket a scene
frame; `abortFrame()` drops pending work after a failure. Submission captures immutable geometry references and property
values. New allocations replace old handles; ranges become reusable only after a GPU fence completes. Closing a scene
releases its subscriptions, cached lists, query pools and geometry pages through the existing manager lifecycle. Buffer
destruction delegates to the backend; it never blocks on an unsubmitted fence. A retained Screen instance can reopen its
scene with `beginFrame()`, which creates a fresh backend. Font loader identity, glyph/atlas revisions, scale and
resource reload generations invalidate dependent caches. Failed compilation retains the previous cache and logs and
propagates the error so the host can abort the frame and retry.

The arena has a 64 MiB resident budget per scheduler. Cold lists have a 32 MiB / 2,048-entry soft budget and are removed
when no longer submitted; active or in-flight allocations cannot be evicted prematurely. Exhausting the hard budget
reports an allocation failure instead of blocking the render thread on a current-submit fence. Adjacent free ranges
coalesce. Consolidation targets roughly twice the active geometry size (at least 64 KiB, rounded to stride), capped by
available resident budget. Old allocations coexist with the new page until their fence completes; insufficient migration
headroom leaves the existing pages usable and retries later. Partially copied targets are quarantined until a fence
completes, while original handles remain valid for retry. Geometry changes trigger this work; unchanged geometry
performs no repeated scan or copy.

`stats` exposes cumulative recordings, geometry builds, draw-plan builds, cache hits, uploaded bytes, actual API draw
calls/ranges, CPU composition/recording/geometry/planning/upload/submission times and live/resident/peak bytes.
`geometryCompactions` counts completed page consolidations, `geometryCopiedBytes` counts encoded GPU-copy bytes
(including completed commands in a failed attempt), and `geometryCopyNanos` measures CPU copy-command preparation and
encoding. Neither copy nor upload time measures GPU transfer completion. `unusedResidentBytes` includes free space and
page padding; it is not solely external fragmentation. `measureGpu = true` enables asynchronous timestamps around the
draw pass; `gpuNanos` is the latest completed draw sample, excludes preceding geometry copies, and does not wait for the
current frame.

Warm static lists avoid recording, geometry generation and static vertex uploads. The executor still traverses retained
references and compares current draw packets to validate its reusable plan; this first version does not promise
constant-time traversal or partial splicing of a changed plan. Profile warmed scenes using counter deltas, total CPU/GPU
frame time, uploads and actual draws together. No speedup factor or visual acceptance is implied by compilation alone.

---

## Quick Start

All rendering operations in Lumin Graphics are performed through specialized **Renderers**.

### Available Renderers

* `RectRenderer`: Optimized for standard flat rectangles.
* `RoundRectRenderer`: Designed for anti-aliased rectangles with dynamic corner radii.
* `TtfTextRenderer`: For high-performance TrueType font rendering.
* `TextureRenderer`: For batch-drawing various textures.

### Initialization & Thread Safety

Renderers **must** be initialized on the **Render Thread**. Use `lazy` (or Guava's `Suppliers.memoize`) to ensure safe
and lazy initialization.

```kotlin
// Recommended initialization method
private val rectRenderer: RectRenderer by lazy { RectRenderer.create() }

// Access the renderer instance
rectRenderer.addRect(10f, 10f, 100f, 100f, Color.WHITE)
```

The same code from Java:

```java
private final Supplier<RectRenderer> rectRenderer = Suppliers.memoize(RectRenderer::create);

rectRenderer.

get().

addRect(10f,10f,100f,100f,Color.WHITE);
```

---

### Usage Patterns

#### 1. Basic Draw & Reset

For most immediate-mode UI tasks, you need to add shapes and clear the buffer within the same frame:

```kotlin
// 1. Add shapes to the buffer
rectRenderer.addRect(10f, 10f, 200f, 200f, Color.WHITE)

// 2. Draw to the screen and clear data before the next frame
rectRenderer.draw()
rectRenderer.clear()

// Alternatively, you can use the shortcut:
rectRenderer.drawAndClear()
```

#### 2. Buffer Reusability

If your UI content does not change every frame, you can add vertices once and draw them multiple times in subsequent
frames, thereby saving CPU overhead.

```kotlin
// During the initialization phase or the first frame:
rectRenderer.addRect(10f, 10f, 200f, 200f, Color.CYAN)

// In the rendering loop:
rectRenderer.draw() // Content remains in the GPU buffer until clear() is called.
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
