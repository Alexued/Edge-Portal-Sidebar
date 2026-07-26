# 侧边栏“回到界枢”按钮实现计划

1. 在 `RailRows.kt` 增加 `MainAppToolItem`，给它稳定交互身份，并把它放在截图之后、Pin 之前。
2. 扩展 `RailRowsTest`，覆盖工具顺序、录音关闭和交互身份。
3. 在 `EdgeRailView` 增加 `onOpenMainApp` 回调、点击分发和门户箭头绘制，复用现有启动反馈与收回时序。
4. 在 `EdgeShelfService` 增加显式主界面启动方法并连接回调。
5. 在 `MainActivity` 增加专用 action，确保返回主页并关闭可能残留的选择器。
6. 增加中英文无障碍文案，版本升级到 1.7.4 (18)。
7. 运行单元测试、Lint、Debug 构建和 `git diff --check`。
8. 安装到当前 ADB 真机，验证桌面与其他应用中的按钮、侧边栏收回、任务复用和手势回归。
9. 提交实现并推送 `main`。
