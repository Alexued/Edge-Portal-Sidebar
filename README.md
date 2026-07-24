# Edge Shelf · 界枢

独立的 Android 10+ 边缘侧栏应用。默认贴合右侧屏幕边缘，向内拖动展开应用图标；长按轨道可上下调整位置，点击图标会先收起侧栏并通过透明前台代理尝试以自由窗口启动，设备不支持时由系统降级为普通启动。侧栏默认显示最近使用的应用，也可在设置中切换为用户选择的固定应用。

## 1.5.0

- 新增 Android 11+ 全局一键截图；截图前会收回并隐藏轨道，图片保存到 `Pictures/EdgeShelf`。
- 设置页可查看截图缩略图、全屏预览元数据，并逐张确认删除。
- 一键录音可单独关闭；录音中使用非线性交叉过渡和低振幅呼吸环反馈。
- 可 Pin 0–3 个应用实例到工具下方；Pin 区不随应用列表滚动，并保留原应用/应用多开身份。

## 构建

```powershell
$gradle = 'C:\Users\wjy\.gradle\wrapper\dists\gradle-8.13-all\54h0s9kvb6g2sinako7ub77ku\gradle-8.13\gradle-8.13\bin\gradle.bat'
& $gradle --offline :app:testDebugUnitTest :app:assembleDebug
```

APK 输出在 `app/build/outputs/apk/debug/app-debug.apk`。工程使用本机缓存的 Gradle 8.13、AGP 8.2.0、Kotlin 1.9.20 和 Compose 1.5.x 依赖。

## 安装与启用

```powershell
adb -s test-device install -r app/build/outputs/apk/debug/app-debug.apk
adb -s test-device shell am start -n com.codex.edgeshelf/.MainActivity
```

在设置页授予“显示在其他应用上层”，打开总开关即可。通知权限、Usage Access 和电池优化豁免是可选引导；Usage Access 不可用时收藏和侧栏仍可工作。

## 交互

- 收起状态只保留边缘触摸热区，不覆盖其他应用内容。
- 向内拖动约 24dp 展开；向外滑动收起。
- 长按轨道约 450ms 后上下拖动，只改变垂直位置。
- 最近应用模式每次展开时刷新：顶部按最近使用顺序显示，下方以“全部应用”分区补齐其余可启动应用；未授予使用情况访问权限时使用侧栏启动历史作为回退。
- 手机与平板分别保持 6 行和 10 行同屏视口，应用数量超过首屏后可在侧栏内部继续向下滚动，面板高度不会随应用总数增长。
- 固定应用模式按用户保存的顺序显示全部有效应用；在设置页点击“管理”或在侧栏点击“+”即可增删，超出可见行时同样可上下滚动。
- 主用户应用与设备厂商应用双开/cloned-app profile 实例按独立条目显示、保存和启动；界枢只使用 Android 系统提供的 Profile 图标徽标区分它们。
- 最近应用模式不显示“+”；没有最近记录时直接从“全部应用”分区开始显示。
- 点击侧栏外部会自动收起，同时不拦截底层应用的点击。
- 设置页显示当前构建版本；应用品牌统一使用「界枢」图标和名称。
- 点击应用后，Edge Shelf 会携带 Android 自由窗模式、设备厂商自由窗标志和启动边界；代理页不可见且不保留在最近任务。
- 平板上对可调整大小且未锁定横屏的应用优先请求约 `9:16` 的竖向窄自由窗；明确横屏或不可调整大小的应用使用 `8:5` 宽自由窗。手机继续使用 `5:8`/`8:5` 响应式边界。
- 长按移动侧栏时只显示实心灰色胶囊，不会泄露面板内的应用图标。
- 应用启动失败不会阻塞侧栏，系统自由窗口不可用时走普通启动。

## 已知限制

- Android vendor/vendor Android system 可能在“隐藏非系统悬浮窗”的设置页、锁屏或部分全屏场景暂时隐藏侧栏。
- vendor Android system 首次读取应用目录时可能询问是否允许界枢“获取已安装的应用信息”；允许后才能完整显示“全部应用”和应用双开实例，界枢不会读取应用内容。
- Android vendor/vendor Android system 可能在首次打开某个目标应用时询问“是否允许界枢打开此应用”；选择“始终允许”后，后续可直接进入小窗。确认后的第一次跳转可能被系统以全屏打开，再次从侧栏启动即可应用自由窗参数；选择“本次允许”时系统可能在下次启动再次询问。
- Android 的系统使用情况记录只提供当前用户历史；双开应用的最近顺序由界枢自己的本地启动记录补充。Profile 暂时不可访问时对应条目会隐藏，但不会删除已保存的固定选择。
- 自由窗口能力仍由系统、目标应用是否支持调整尺寸及厂商策略决定；不支持的小窗应用会由系统以全屏打开。
- 已在 Android vendor `M2007J1SC` / Android 13 真机验证 Cloudflare 1.1.1.1 与 1Chat 均进入 `mode=freeform`，无需修改全局设置。
- 已针对设备厂商平板 7S Pro 12.5 的 `3200 × 2136` 横屏与 `2136 × 3200` 竖屏工作区增加几何适配和单元测试；在 vendor Android system 3 真机上验证 Notein/QQ 进入约 `9:16` 窄自由窗，Bilibili HD 等不可调整大小应用进入宽自由窗。
- 平板规格参考设备厂商官方发布信息与公开规格页：12.5 英寸、`3200 × 2136`、`3:2`、144Hz、vendor Android system 2；实现不依赖固定分辨率，旋转和窗口变化后会用最新 `WindowMetrics` 重新定位侧栏。
- 不使用无障碍、root、Shizuku 或网络服务；所有配置和最近启动记录仅保存在设备本地。

## 平板适配参考

- [设备厂商官方发布信息](https://weibo.com/2202387347/PylNXERYo)：设备厂商平板 7S Pro、12.5 英寸、玄戒 O1、10610mAh、120W。
- [Android WindowMetrics](https://developer.android.com/reference/android/view/WindowMetrics)：用于读取当前可用窗口边界和 Insets。
- [Android 多窗口适配](https://developer.android.com/develop/ui/views/layout/support-multi-window-mode)：用于约束旋转、分屏和自由窗中的响应式行为。
