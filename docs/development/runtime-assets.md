# 运行时资源下载

主菜单视频、玲纱立绘和 FFmpeg 原生库体积较大，不再随 mod jar 分发。它们在首次使用对应功能时
由界面确认后下载到配置目录，缺少资源时功能静默降级，不会影响客户端启动。

## 缓存布局

全部产物保存在 `ConfigManager` 的配置根目录下：

```text
~/.epsilon/assets/
├── video/columbina.mp4          # 主菜单 Columbina 背景视频
├── reisa/reisa_XX.png           # 玲纱立绘（00-18、99 共 20 张）
├── ffmpeg/natives/*.dll         # JavaCPP 需要的 FFmpeg 原生库
└── .tmp/*.part                  # 下载中的临时文件
```

`AssetManager` 提供三种资源的就绪判定：`isVideoReady()`、`isReisaReady()`、`isFfmpegReady()`。
下载先在 `.tmp` 写入 `.part` 文件，校验通过后再原子移动到目标位置，因此半包不会被判定为可用。

## 下载源

| 设置 | 默认值 | 说明 |
|---|---|---|
| `Resource Base URL` | `https://github.com/NekoyaHouse/Epsilon-Resources/releases/download/assets-v1/` | 资源基础地址，客户端按 `${base}columbina.mp4`、`${base}reisa.zip` 拼接；资源托管在 [Epsilon-Resources](https://github.com/NekoyaHouse/Epsilon-Resources) |
| `FFmpeg Download URL` | 阿里云 Maven 镜像的 `ffmpeg-6.1.1-1.5.10-windows-x86_64.jar` | JavaCPP 原生库压缩包，解压出 DLL 后删除原始 jar |

设置位于 `Client Setting` 的 `Resources` 分组，另提供「下载资源」「清除资源缓存」「打开资源目录」
三个按钮。清除缓存会删除 `~/.epsilon/assets` 并注销已注册的玲纱纹理。

## 下载与校验

`Http.download` 负责网络层：跟随重定向、15 秒连接超时、复用游戏内 HTTP 代理，并按 64 KiB
分块回调进度。`AssetManager` 在此基础上串行下载单个资源，失败自动重试三次。

校验规则：

- 视频：文件头 64 字节内必须出现 `ftyp` box。
- 玲纱：zip 内必须包含 `reisa_00` … `reisa_18`、`reisa_99` 共 20 张 PNG，且每张通过 PNG 魔数校验。
- FFmpeg：jar 内必须包含 JavaCPP 需要的 `av*`/`jni*`/`sw*` DLL 集合。

## FFmpeg 加载顺序

FFmpeg 原生库解压到 `~/.epsilon/assets/ffmpeg/natives` 后，`AssetManager.ensureFfmpegLoaded()`
会设置系统属性 `org.bytedeco.javacpp.platform.linkpath`。JavaCPP 的 `Loader.findLibrary` 在
classpath 资源之后回退搜索该目录，因此**必须在任何 `org.bytedeco.ffmpeg` 类初始化之前调用**，
否则 JavaCPP 会缓存不带该路径的平台属性并抛出 `UnsatisfiedLinkError`。

## Platform Only

视频背景与 SMTC 音乐岛仅支持 Windows x86_64。设置模型通过 `Setting.platformOnly(...)` 和
`EnumSetting.restrictMode(...)` 声明平台要求：

- Panel 的 `BoolSettingRow` / `EnumSettingRow` / `EnumSelectPopup`，以及 Dropdown 的
  `BoolWidget` / `EnumWidget` 会为不满足要求的设置渲染 `Platform Only` 徽标并禁用交互。
- 用户点击被禁用的开关或受限枚举项时，`PlatformNoticeScreen` 会弹出说明界面；
  同一功能的窗口未关闭时不会重复堆叠，关闭后可再次点击打开。
- 运行时会静默回退（视频走经典着色器背景、音乐岛不显示），且不会改写用户已保存的配置值。
