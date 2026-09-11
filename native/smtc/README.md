# Epsilon SMTC bridge

该目录包含 Windows x64 的 C++/WinRT SMTC 读取核心、JNI 桥接和独立探针。桥接返回当前媒体会话的标题、艺术家、专辑、来源应用、播放状态与变更后的封面字节，不计算或上报精确播放进度。

## Build

```powershell
$env:JAVA_HOME = "C:/Program Files/Java/jdk-25.0.4"
cmake -S native/smtc -B native/smtc/build -A x64 -DJAVA_HOME="$env:JAVA_HOME"
cmake --build native/smtc/build --config Release --parallel
```

生成文件：

- `common/src/main/resources/natives/windows-x86_64/epsilon_smtc.dll`
- `native/smtc/build/Release/epsilon_smtc_probe.exe`

运行 `epsilon_smtc_probe.exe` 可以直接检查当前 Windows SMTC 会话及封面字节数。
