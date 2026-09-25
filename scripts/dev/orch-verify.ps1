# 编排校验：编译整个 common 源码树，再运行调度契约与节点图不变量 harness。
#
# 背景：DSH 沙箱禁止写入 D:\Dev\.gradle（Gradle 发行版锁文件）且无外网，
# 因此无法运行 ./gradlew。这只是本地替代验证，正式提交前仍需在有网络的机器上跑 Gradle。
#
# 覆盖：
#   1. common 全量源码编译（含 combat 之外的包，避免只编译子集漏掉签名变化）；
#   2. smoke      —— 调度计划排序契约（阶段、priority、跨模块依赖、重复/缺失诊断）；
#   3. invariants —— 所有 combat 模块的节点图不变量（唯一键、依赖存在、无环、
#                    阶段与 priority 稳定、事件类型与处理器一致）。
#
# 关键点：harness 的 classpath 必须把本次编译产物放在旧 class 之前，
# 否则会读到 common/build/classes 里的过期声明，得出假通过。
param(
    [ValidateSet("smoke", "invariants", "all")]
    [string]$Task = "all",
    [switch]$SkipCompile
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $root
. "$PSScriptRoot/deps.ps1"

$classes = "build/compile-check/common"
if (-not $SkipCompile) {
    & "$PSScriptRoot/compile-check.ps1" -Target common
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
if (-not (Test-Path $classes)) {
    Write-Host "Missing $classes; run without -SkipCompile first."
    exit 1
}

$cp = (Get-DshHarnessClasspath -ClassesDir (Resolve-Path -LiteralPath $classes).Path) -join ";"
$out = "build/compile-check/harness"
New-Item -ItemType Directory -Force -Path $out | Out-Null

$tasks = if ($Task -eq "all") { @("smoke", "invariants") } else { @($Task) }

foreach ($t in $tasks) {
    $main = if ($t -eq "smoke") { "OrchSmoke" } else { "OrchestrationInvariants" }
    $sources = if ($t -eq "smoke") {
        @("scripts/dev/harness/OrchSmoke.java", "scripts/dev/harness/NodeDefinitionProbe.java")
    } else {
        @("scripts/dev/harness/OrchestrationInvariants.java")
    }

    Write-Host "==> $t ($main)"
    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add("-encoding"); $lines.Add("UTF-8"); $lines.Add("-nowarn")
    $lines.Add("-d"); $lines.Add((Resolve-Path -LiteralPath $out).Path)
    $lines.Add("-cp"); $lines.Add($cp)
    foreach ($s in $sources) { $lines.Add((Resolve-Path -LiteralPath $s).Path) }

    $exit = Invoke-DshJavac -ArgFile "build/compile-check/harness-$t.args" -Lines $lines
    if ($exit -ne 0) { Write-Host "HARNESS COMPILE FAILED"; exit 1 }

    $output = & java -cp "$((Resolve-Path -LiteralPath $out).Path);$cp" $main 2>&1
    $exit = $LASTEXITCODE
    $output | Where-Object { $_ -notmatch "^SLF4J" } | ForEach-Object { Write-Host $_ }
    if ($exit -ne 0) { Write-Host "HARNESS FAILED: $t"; exit $exit }
}

Write-Host "ALL HARNESS TASKS PASSED"
