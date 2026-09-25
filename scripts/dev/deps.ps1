# compile-check.ps1 与 orch-verify.ps1 共用的依赖路径解析。
# 依赖来自本地 Gradle 缓存（本机无法联网，也无法运行 Gradle 写 D:\Dev\.gradle）。

$script:DevCache = "D:\Dev\.gradle\caches\modules-2\files-2.1"

function Get-DshMinecraftJar {
    (Resolve-Path "common/build/moddev/artifacts/vanilla-26.3-1-merged.jar").Path
}

# Minecraft 自身运行所需的库（含 fastutil：必须用 MC 26.3 对应的 8.5.x，
# 8.3.1 缺少 Int2ObjectMap.computeIfAbsent(int, Int2ObjectFunction)，运行时会 NoSuchMethodError）。
function Get-DshMinecraftLibraryJars {
    $g = $script:DevCache
    $pairs = @(
        @("$g\com.google.code.gson\gson\2.14.0\efc0e34ede4e3204eaefb84a00e55e8c86634382\gson-2.14.0.jar", $null),
        @("$g\com.google.guava\guava\33.6.0-jre\c376b13067cc99a5774403530953f7b05a91e218\guava-33.6.0-jre.jar", $null),
        @("$g\org.spongepowered\mixin\0.8.5\9d1c0c3a304ae6697ecd477218fa61b850bf57fc\mixin-0.8.5.jar", $null),
        @("$g\io.github.llamalad7\mixinextras-common\0.5.4\626e00b72e3879a07e6653d8015cd3466ff5b75\mixinextras-common-0.5.4.jar", $null),
        @("$g\org.ow2.asm\asm\9.10.1\ada2141c0cc52ee8f5c48cd5fa4ce0e794f22236\asm-9.10.1.jar", $null),
        @("$g\org.joml\joml\1.10.9\438e036486bad66b189bff385dd07dea4f74a146\joml-1.10.9.jar", $null),
        @("$g\org.jspecify\jspecify\1.0.0\7425a601c1c7ec76645a78d22b8c6a627edee507\jspecify-1.0.0.jar", $null),
        @("$g\it.unimi.dsi\fastutil\8.5.18\a6cff377eecc19c2037bf31568a6d7106b50ba1f\fastutil-8.5.18.jar", $null),
        @("$g\com.mojang\brigadier\1.3.11\3373d1e7bf00c8b99bed1ea4efb8c47344e4a887\brigadier-1.3.11.jar", $null),
        @("$g\com.mojang\datafixerupper\10.0.21\b6b2ae770c02e0c1eb90f9985b151e9085a38d0b\datafixerupper-10.0.21.jar", $null),
        @("$g\com.mojang\authlib\10.0.77\add8754cda96cf0cd840441632875648836f8a71\authlib-10.0.77.jar", $null),
        @("$g\com.mojang\logging\1.7.12\351cea64a5233361327d8d54c44277041beed97f\logging-1.7.12.jar", $null),
        @("$g\com.mojang\jtracy\1.14.38\cc2ad81342001b4281c305a298d7f50332354058\jtracy-1.14.38.jar", $null),
        @("$g\org.apache.commons\commons-lang3\3.20.0\65897b3e5731220962e659e001904af3c3cbeba9\commons-lang3-3.20.0.jar", $null),
        @("$g\org.apache.logging.log4j\log4j-api\2.26.0\ad52af0ecf054a7e3f275a2e180ee06d9c490951\log4j-api-2.26.0.jar", $null),
        @("$g\org.slf4j\slf4j-api\2.0.17\d9e58ac9c7779ba3bf8142aff6c830617a7fe60f\slf4j-api-2.0.17.jar", $null),
        @("$g\org.lwjgl\lwjgl\3.4.3\7ab5265a73d8a959ed62332d546536ae6be2f0d5\lwjgl-3.4.3.jar", $null),
        @("$g\org.lwjgl\lwjgl-stb\3.4.3\cdce54785bd3758e44a81f345d6260774d921c78\lwjgl-stb-3.4.3.jar", $null),
        @("$g\org.lwjgl\lwjgl-sdl\3.4.3\c02146735d6ff70d070c73532b435e5c893a346b\lwjgl-sdl-3.4.3.jar", $null),
        @("$g\org.bytedeco\javacv\1.5.11\8bcc9af0d9995080960c061edcfd6da9be84b0b5\javacv-1.5.11.jar", $null),
        @("$g\org.bytedeco\javacpp\1.5.11\76a0197853ab5e2d3643804e62f9018053750ddf\javacpp-1.5.11.jar", $null),
        @("$g\org.bytedeco\ffmpeg\7.1-1.5.11\9704bf6e3ba57d8984fea5eaa0221cf049ec90d4\ffmpeg-7.1-1.5.11.jar", $null)
    )
    foreach ($p in $pairs) {
        if (Test-Path -LiteralPath $p[0]) { $p[0] } else { Write-Warning "Missing dependency: $($p[0])" }
    }
    Get-ChildItem "$g\io.netty" -Recurse -Filter "*.jar" -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch "sources|javadoc|natives|native-" } |
        Select-Object -ExpandProperty FullName
}

# 编译整个 common 源码树所需的 classpath。
# 除依赖外还要包含 BuildConfig 生成源码：它由 buildconfig 插件产出，不在 src/ 下，
# 但 Constants 依赖它。
function Get-DshCommonSourceRoots {
    $generated = "common/build/generated/sources/buildConfig/main"
    @("common/src/main/java") + $(if (Test-Path $generated) { @($generated) } else { @() })
}

function Get-DshCommonCompileClasspath {
    @(Get-DshMinecraftJar) + @(Get-DshMinecraftLibraryJars)
}

# 只编译子树（如 combat 包）时，其余 com.github.epsilon 类需要来自已编译产物；
# 全量编译 common 时不能用它，否则会掩盖源码错误。
function Get-DshSubsetCompileClasspath {
    $prebuilt = "common/build/classes/java/main"
    $roots = @()
    if (Test-Path $prebuilt) { $roots += (Resolve-Path $prebuilt).Path }
    $roots + @(Get-DshMinecraftJar) + @(Get-DshMinecraftLibraryJars)
}

# 运行 harness 所需的 classpath：本次编译产物必须排在旧 class 之前。
function Get-DshHarnessClasspath {
    param([Parameter(Mandatory)][string]$ClassesDir)
    @($ClassesDir) + @(Get-DshMinecraftJar) + @(Get-DshMinecraftLibraryJars)
}

function Invoke-DshJavac {
    param(
        [Parameter(Mandatory)][string]$ArgFile,
        [Parameter(Mandatory)][System.Collections.Generic.List[string]]$Lines
    )
    Set-Content -LiteralPath $ArgFile -Value $Lines -Encoding utf8NoBOM
    & javac "@$ArgFile"
    return $LASTEXITCODE
}
