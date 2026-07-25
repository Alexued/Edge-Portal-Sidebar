# Contributing

感谢你改进界枢侧边栏。提交代码即表示你同意按本仓库的 Apache License 2.0 提供该贡献。

## 开发环境

- JDK 17
- Android SDK 34
- 可选：一台 Android 10+ 设备和可用的 `adb`

克隆仓库后，用项目自带的 Gradle Wrapper 构建，不要把本机 SDK、Gradle 缓存路径或设备序列号写入仓库。

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Windows PowerShell 使用 `./gradlew.bat`。

## 提交改动

1. 先建立聚焦单一问题的分支。
2. 遵循现有 Kotlin、Compose 和资源命名风格，避免与目标无关的大范围重构。
3. 行为变更应补充单元测试；涉及手势、几何、滚动物理或自由窗策略时，优先测试纯 Kotlin 逻辑。
4. 用户可见文案同时更新默认英文资源与 `values-zh-rCN` 简体中文资源。
5. 不要重命名 `com.codex.edgeshelf`、DataStore 键或 `Recordings/EdgeShelf`、`Pictures/EdgeShelf` 等兼容标识，除非同时提交经过验证的数据迁移。
6. 在 Pull Request 中说明目标、实现、测试方式、设备与 Android/HyperOS 版本；界面改动请附前后对比截图。

## 报告问题

普通 Bug 可提交 Issue，并附最小复现步骤、预期行为、实际行为、设备型号、Android/HyperOS 版本和相关日志。请先移除日志中的账号、文件路径、通知内容及其他私人数据。

安全漏洞不要提交公开 Issue，请按 [SECURITY.md](SECURITY.md) 私下报告。
