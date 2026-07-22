# Edge Shelf 滚动列表与应用双开实施计划

## 目标

实现已批准规格：最近区之后追加“全部应用”，侧栏固定为手机 6 行/平板 10 行并支持内部滚动；使用公开 `LauncherApps` 枚举和启动主用户及 XSpace Profile 应用实例；固定项和本地历史按实例持久化。

## 实施原则

- 先建立可单元测试的纯模型、编码和合并函数，再接 Android API。
- 运行时使用 `UserHandle`，持久化只使用稳定 `userSerial`。
- 实例身份始终为 `packageName + userSerial + componentName`，不再在任何边界按包名去重。
- 保留现有 Canvas 侧栏和自由窗几何，不引入新的 UI/动画库。
- `LauncherApps` 是跨 Profile 主路径，HyperOS 私有参数仅是隔离的最后后备。

## 任务 1：实例键与持久化编码

涉及文件：

- 新增 `app/src/main/java/com/codex/edgeshelf/data/AppInstance.kt`
- 修改 `ShelfModels.kt`、`ShelfStore.kt`
- 修改/新增 `ShelfStoreTest.kt`、`AppInstanceTest.kt`

步骤：

1. 新增 `AppInstanceKey(packageName, userSerial, componentName)` 和稳定字符串编码/解析函数。
2. 将 `RecentEntry` 改为保存实例键，将 `ShelfSettings.favorites` 改为 `List<AppInstanceKey>`。
3. 新编码使用版本化行格式；解码同时接受旧 favorites 包名和旧 `timestamp + packageName` 历史。
4. 旧包名只迁移到当前用户实例，不猜测 XSpace；组件变化时允许按 `packageName + userSerial` 重新绑定。
5. 本地最近记录容量从 10 提升到 40，按完整实例键保留最新时间。
6. Profile 暂时消失时只过滤运行时结果，不主动删除持久化实例键。

验证：新旧混合编码、坏行、同包不同 serial、组件重绑定、40 条上限、迁移幂等全部有 JVM 测试。

## 任务 2：Profile-aware 应用目录

涉及文件：

- 重构 `AppCatalogRepository.kt`
- 更新 `AppCatalogRepositoryTest.kt`

步骤：

1. 用 `LauncherApps.getProfiles()` 和 `getActivityList(null, profile)` 替代当前用户 `PackageManager.queryIntentActivities()`。
2. 用 `UserManager.getSerialNumberForUser()` 建立 serial/UserHandle 映射；未知 serial 跳过。
3. 每个 `(packageName, userSerial)` 选择确定性 Launcher 组件，构造显式 MAIN Intent。
4. 使用 `LauncherActivityInfo.getBadgedIcon(densityDpi)`；失败时允许空图标并由 UI 占位。
5. 排除 Edge Shelf 自身，按本地化标签和实例键稳定排序。
6. 目录返回当前用户 serial 和完整实例列表，供 UsageStats 映射与 Store 对账。

验证：fake source 覆盖主用户/XSpace 同包双实例、多入口选择、徽标失败、Profile 消失和稳定排序；平板确认 user 0 serial 0、user 999 serial 10 均可枚举。

## 任务 3：最近区、全部应用区与固定解析

涉及文件：

- 重构 `ShelfContentResolver.kt`
- 更新 `ShelfContentResolverTest.kt`
- 调整 `UsageRepository.kt` 纯归一化测试

步骤：

1. 系统 UsageStats 包名只映射当前用户的确定性实例。
2. 本地实例历史补充 XSpace 和系统结果缺失项，最近区最多 40 个实例。
3. 全部应用区从完整目录移除最近区的相同实例，再按标签排序。
4. 同包不同 userSerial 不互相去重。
5. 固定模式按实例键保存顺序解析，忽略最近/全部分区。
6. 最近为空时直接从全部应用分区开始，不再生成旧“最近记录”占位。

验证：最近顺序、全部区排重、主/分身并存、无 UsageStats、空最近、固定保序和 Profile 缺失。

## 任务 4：固定应用选择器实例化

涉及文件：

- 修改 `EdgeShelfViewModel.kt`、`AppPickerScreen.kt`
- 更新 `EdgeShelfViewModelTest.kt`

步骤：

1. `selectedPackages`/`originalFavorites` 改为实例键集合/列表。
2. LazyColumn key、toggle、save 和排序合并全部使用 `AppInstanceKey`。
3. 原应用和第二应用以系统 badged icon 分别显示、分别选择；不增加自定义可见角标。
4. 搜索继续匹配标签和包名；无障碍描述补充 Profile 实例含义。
5. 打开选择器后先加载 Profile 目录并对账旧数据，再显示选择状态。

验证：同包双实例可独立选中、保存顺序稳定、取消/重试/迁移不丢选择。

## 任务 5：侧栏统一行模型与滚动

涉及文件：

- 新增 `overlay/RailRows.kt`
- 重构 `EdgeRailView.kt`、`RailGeometry.kt`
- 更新 `RailGeometryTest.kt`、`RailMotionTest.kt`

步骤：

1. 定义 `AppRow`、`SectionRow("全部应用")`、`AddRow`、`LoadingRow` 等明确行类型。
2. 最近模式扁平化为 recent app rows + section row + remaining app rows；固定模式为 fixed rows + add row。
3. 所有行使用现有 54dp 槽位，Section 内绘制细分隔线和紧凑标题，保持统一命中与几何。
4. `rowCount`、最大滚动距离、点击索引和滚动条改为基于行模型。
5. 绘制只遍历裁剪范围内的首尾行；Section 不可点击，App 回调完整实例，Add 打开选择器。
6. 刷新变短时 clamp offset；收起或模式切换后回到顶部；视口仍限制为手机 6 行、平板 10 行。
7. `performClick()` 不再根据全局 tail 状态产生副作用，避免 Section/滚动后误触。

验证：10 行以上可滚动到底、Section 计入几何但不可点击、最后 App/Add 可点击、刷新缩短不越界、展开高度不随总行数增长。

## 任务 6：按实例启动与自由窗降级

涉及文件：

- 新增 `launch/AppInstanceLauncher.kt` 和隔离的 `XSpaceLaunchFallback.kt`
- 重构 `LaunchCoordinator.kt`、`LaunchProxyActivity.kt`
- 修改 `EdgeShelfService.kt`
- 更新 `LaunchCoordinatorTest.kt` 和启动策略测试

步骤：

1. View 的启动回调传 `AppInstanceKey` 或 `LaunchableApp`，服务不再按包名重新查询第一项。
2. 当前用户实例继续使用现有 LaunchProxy/Freeform options 路径。
3. 非当前 Profile 使用 `LauncherApps.startMainActivity(component, user, sourceBounds, FreeformLaunchOptions.create(bounds))`。
4. Profile 自由窗失败时先尝试同 Profile 普通启动；Xiaomi/HyperOS 上再使用隔离的 XSpace extras 后备。
5. XSpace 后备仅在 manufacturer/目标 Profile 条件成立时启用，普通 Android 路径不引用 MIUI 私有类型。
6. 启动成功后记录完整实例键；取消异常继续向上传播，快速连点由最新请求覆盖。
7. 自由窗能力和方向判断继续使用目标组件的 ActivityInfo；无法跨 Profile读取时使用保守窄/宽策略并允许系统修正。

验证：主用户、XSpace、freeform 失败、普通启动失败、取消竞态和历史记录实例身份。

## 任务 7：服务集成、版本和文档

涉及文件：

- `EdgeShelfService.kt`
- `app/build.gradle.kts`
- `README.md`、相关 strings 和旧内容模式规格说明

步骤：

1. 系统最近候选查询提高到 80，最终最近实例上限 40；删除 6/10 数据截断方法和常量。
2. 服务一次刷新中加载目录、对账 Store、构建最近/全部内容并传给 View。
3. 固定模式继续避免不必要的 UsageStats 查询，同时支持 Profile 目录变化刷新。
4. 版本提升为 `1.3.0` / versionCode `6`。
5. 文档将“最多 6/10 个”改为“同屏 6/10 行，超出可滚动”，记录 Profile 限制和降级行为。

## 任务 8：完整验证与推送

自动验证：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
git diff --check
```

Xiaomi Pad 7S Pro：

1. 覆盖安装 Debug APK，确认设置与旧收藏迁移。
2. 最近区后出现“全部应用”，滚动超过首屏 10 行并点击末尾应用。
3. 在固定选择器中分别勾选主用户“粉笔”和 XSpace“粉笔”，确认系统徽标不同。
4. 分别启动两个实例，通过 `dumpsys activity activities` 确认任务为 `u0` 与 `u999`。
5. 检查两个实例尽可能进入既定自由窗；失败时验证普通跨 Profile 降级。
6. 测试外部点击收起、旋转、滚动回顶、关闭动画和 Profile 暂时不可用。
7. 提交实现并推送私有仓库 `origin/main`。

## 完成标准

- 主应用和第二应用在目录、固定项、最近历史、列表 key 与启动路径中始终保持独立身份。
- 平板列表可查看并启动超过 10 个条目，悬浮窗高度不增长。
- 不新增广泛跨用户权限，现有侧栏动效与窄自由窗回归测试通过。
- 工作区干净、APK 已安装验证、远端 main 包含设计、计划和实现提交。
