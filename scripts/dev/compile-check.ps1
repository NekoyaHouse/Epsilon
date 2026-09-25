# 在无法运行 Gradle 的环境下用 javac 校验源码树编译。
#
# 背景：DSH 沙箱禁止写入 D:\Dev\.gradle（Gradle 发行版锁文件）且无外网，
# 因此无法运行 ./gradlew。这只是本地替代验证，不等于 gradlew buildRelease。
#
# 用法：
#   pwsh -File scripts/dev/compile-check.ps1                 # 整个 common 源码树
#   pwsh -File scripts/dev/compile-check.ps1 -Target combat  # 仅 combat 包（更快）
param(
    [ValidateSet("combat", "orchestration", "common")]
    [string]$Target = "common",
    [switch]$Quiet
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $root
. "$PSScriptRoot/deps.ps1"

$sourceRoots = switch ($Target) {
    "combat" { @("common/src/main/java/com/github/epsilon/modules/impl/combat") }
    "orchestration" { @("common/src/main/java/com/github/epsilon/modules/orchestration") }
    "common" { Get-DshCommonSourceRoots }
    default { throw "Unknown target: $Target" }
}

$out = "build/compile-check/$Target"
if (Test-Path $out) { Remove-Item -Recurse -Force $out }
New-Item -ItemType Directory -Force -Path $out | Out-Null

$files = $sourceRoots | ForEach-Object { Get-ChildItem $_ -Recurse -Filter *.java } | ForEach-Object { $_.FullName }
Write-Host "Compiling $($files.Count) files from $($sourceRoots -join ', ')"

# 源码数量会超出 Windows 命令行长度上限，改用 javac 参数文件。
# 子集目标依赖 common/build/classes 中的其余类；全量目标必须完全从源码编译。
$classpath = if ($Target -eq "common") { Get-DshCommonCompileClasspath } else { Get-DshSubsetCompileClasspath }
$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("-encoding"); $lines.Add("UTF-8"); $lines.Add("-nowarn")
$lines.Add("-d"); $lines.Add((Resolve-Path -LiteralPath $out).Path)
$lines.Add("-cp"); $lines.Add(($classpath -join ";"))
foreach ($f in $files) { $lines.Add($f) }

$exit = Invoke-DshJavac -ArgFile "build/compile-check/$Target.args" -Lines $lines
if ($exit -ne 0) {
    Write-Host "COMPILE FAILED ($exit)"
    exit $exit
}
Write-Host "COMPILE OK"
