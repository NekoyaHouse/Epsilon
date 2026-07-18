# Lumin Graphics

[English](README.md) | **简体中文**

Lumin Graphics 是一个为现代 Minecraft 模组开发设计的轻量化、高性能渲染框架。

---

## 核心特性

* **SDF 圆角矩形**：通过片段着色器（Fragment Shader）计算平滑且抗锯齿的圆角。支持动态半径，无需改变顶点数据。
* **高性能 TTF 渲染**：先进的 TrueType 字体渲染技术，采用图集（Atlas）批处理机制，显著降低绘制调用（Draw Calls）。

---

## 💡警告

### 生命周期同步限制

在单帧渲染循环内，尽可能不在调用一次或多次 Renderer.draw() 之后再调用 Renderer.clear()
并再次对同一个实例调用 draw()。这会导致单帧内使用多个 Buffers 导致 In-Fight 优化力度减少

如果业务逻辑确实需要在单帧内进行多轮清理与绘制：

建议实例化一个新的 Renderer 来处理后续任务。

**💡注意**： 请避免创建过多的 Renderer 实例，否则会造成显存空间的过度占用，
或者你可以在创建 Renderer 实例时设置比默认值更小的 Buffer 大小。

---

## 快速上手

Lumin Graphics 的所有渲染操作均通过专门的 **Renderer（渲染器）** 完成。

### 可用渲染器

* `RectRenderer`：针对标准扁平矩形进行了优化。
* `RoundRectRenderer`：用于具有动态圆角半径且抗锯齿的矩形。
* `TtfTextRenderer`：用于高性能的 TrueType 字体渲染。
* `TextureRenderer`：用于批量绘制不同贴图

### 初始化与线程安全

渲染器 **必须** 在 **渲染线程（Render Thread）** 上进行初始化。我们建议使用 `Suppliers.memoize`（来自 Guava 或 Minecraft
库）来确保安全且延迟的初始化。

```java
// 推荐的初始化方式
private final Supplier<RectRenderer> rectRenderer = Suppliers.memoize(RectRenderer::new);

// 使用 .get() 获取渲染器实例
rectRenderer.get().addRect(10f,10f,100f,100f,Color.WHITE);

```

---

### 使用方式

#### 1. 基础绘制与重置

对于大多数即时模式（Immediate-mode）的 UI 任务，你需要在同一帧内添加形状并清理缓冲区：

```java
// 1. 向缓冲区添加形状
rectRenderer.get().addRect(10f,10f,200f,200f,Color.WHITE);

// 2. 绘制到屏幕并在下一帧前清理数据
rectRenderer.get().draw();
rectRenderer.get().clear();

// 你也可以直接使用 drawAndClear()
rectRenderer.get().drawAndClear();

```

#### 2. 缓冲区复用

如果你的 UI 内容并非每帧都在变化，你可以只添加一次顶点，并在后续帧中多次绘制，从而节省 CPU Overhead。

```java
// 在初始化阶段或首帧中：
rectRenderer.get().addRect(10f,10f,200f,200f,Color.CYAN);

// 在渲染循环中：
rectRenderer.get().draw(); // 内容会一直保存在 GPU 缓冲区中，直到调用 .clear()

```

---

### 💡 优化建议

在使用 **Lumin Graphics** 时请记住：多次调用 `.draw()` 而不调用 `.clear()` 是极其高效的。它仅会重新触发现有 GPU
数据的绘制指令，而无需重新上传顶点数据。

---

## 开源协议

* **Lumin Graphics** 采用 [GNU General Public License v3.0](LICENSE) 协议。

---

Copyright © 2026 NekoyaHouse.
