# Edge Shelf 滚动列表与应用双开设计

## 状态

- 日期：2026-07-22
- 状态：用户已逐段确认设计
- 范围：最近/全部应用列表、侧栏内部滚动、Android Profile 应用实例、固定选择、自由窗启动与历史迁移

## 目标

1. 最近模式不再被手机 6 个或平板 10 个数据上限截断。6/10 只代表同屏可见行数，超出内容可以在侧栏内部向下滚动。
2. 最近应用之后显示“全部应用”分区，列出尚未出现在最近区的所有可启动应用。
3. 正确区分主用户应用与小米 XSpace/应用双开实例。原应用和第二应用可以分别出现在最近列表、全部应用列表和固定列表中，并分别启动。
4. 保留现有窄侧栏、固定视口、滚动指示条、外部点击收回和自由窗比例行为。

## 已确认的产品规则

- 最近区按最近使用顺序排列；系统 UsageStats 只提供主用户顺序，第二应用使用 Edge Shelf 自身记录的实例历史补充。
- 最近区与全部应用区之间显示细分隔线和“全部应用”标题。
- 全部应用区按应用名称排序，并排除已经在最近区出现的同一实例。
- 原应用和第二应用是两个独立条目，可以在固定模式中分别勾选和排序。
- 图标只使用 `LauncherActivityInfo.getBadgedIcon()` 返回的系统 Profile 徽标，不叠加自定义“2/双开”角标。
- 手机和平板仍分别保持 6/10 行同屏视口；列表总量不再使用该值截断。
- 收起后滚动位置归零；滚动手势超过触摸阈值后不触发应用点击。

## 实例身份模型

### AppInstanceKey

应用实例的逻辑主键为：

```text
packageName + userSerial + componentName
```

`userSerial` 使用 `UserManager.getSerialNumberForUser(UserHandle)` 持久化；不把 `userId=999` 等瞬时整数写入 DataStore。恢复时通过 `getUserForSerialNumber()` 获取当前可访问的 `UserHandle`。`componentName` 用于同包名存在多个 Launcher Activity 时保持确定性。

`LaunchableApp` 扩展为包含：实例键、短标签、Profile 用户句柄、主启动组件、系统徽标图标和必要的启动 Intent。`UserHandle` 只作为运行时对象，不进入持久化编码。

### Profile 目录

使用公开 Android `LauncherApps` API：

1. `getProfiles()` 获取当前用户可访问的 Profile。
2. 对每个 Profile 调用 `getActivityList(null, profile)` 枚举 `MAIN/LAUNCHER` Activity。
3. 每个 Profile 内按包名归并，按确定性规则选择一个主启动 Activity；组件名仍保留在实例键中，避免同包入口混淆。
4. 使用 `getBadgedIcon()` 获取系统提供的主用户/分身徽标图标。

当前用户实例视为原应用；其他可访问 Profile 的同包实例视为第二应用。API 35 以上可以读取 Clone Profile 类型，但最低支持 API 29，因此不能依赖该常量才能工作。不可访问或已删除的 Profile 不进入目录。

## 列表数据流

### 最近区

服务刷新时建立实例目录，然后合并两类历史：

1. 主用户的 `UsageStatsManager` 包名结果映射到主用户实例。
2. Edge Shelf 本地历史按 `AppInstanceKey` 排序，补充第二应用和系统结果缺失的主用户实例。

合并后按实例键去重，保留系统顺序优先，再按本地最近时间补齐。最近区不设 6/10 条上限；系统先查询 80 个候选，过滤后最多保留 40 个最近实例，本地历史也保留 40 个实例，以便滚动并控制资源占用。

当最近区为空时不再显示旧的“最近记录”占位按钮，列表直接从“全部应用”分区开始；使用情况访问入口仍保留在设置页。

### 全部应用区

将完整可启动目录按本地化标签、实例键排序，移除已经出现在最近区的实例后追加。该区不设数量上限，仅受系统实际可访问的 Launcher Activity 数量限制。

### 视图行模型

侧栏不再只接收 `List<LaunchableApp>`，改为接收明确的行模型：

```text
AppRow(instance)
SectionRow("全部应用")
AddRow                  // 仅固定模式
```

`EdgeRailView` 继续使用现有 Canvas 裁剪、`scrollOffset` 和滚动指示条。行索引、点击命中和最大滚动距离都基于行模型；分区标题不可点击。最近模式不显示 `+`，固定模式保留末尾 `+` 并可滚动到达。

视口高度只由可见行容量决定，列表行数增加不会扩大悬浮窗。刷新列表变短时立即将 `scrollOffset` 收敛到新最大值；开始新的展开时从顶部开始。

## 固定选择与持久化

固定选择器复用同一 Profile-aware 目录，LazyColumn 的 key 使用 `AppInstanceKey`，因此主用户和第二应用可以分别选择。DataStore 中的 favorites 从包名列表迁移为实例键列表；旧包名记录按当前用户的主启动实例转换。应用升级导致 Launcher 组件变化时，先按 `packageName + userSerial` 重新绑定到新的确定性主入口，再更新运行时实例键。

本地最近记录扩展为实例键和时间戳。旧的 `timestamp + packageName` 编码按当前用户解析，不会自动猜测分身。重复实例保留最新时间。Profile 消失时只过滤运行时显示，不主动删除持久化键；Profile 恢复后可重新解析。

## 启动策略

引入按实例启动的协调接口：

1. 主用户应用沿用现有自由窗策略和窄/宽比例判断。
2. HyperOS 3 在同包存在 XSpace 分身时会忽略 `LauncherApps` 的明确 Profile 并插入二选一界面，因此小米设备优先使用隔离的 XSpace 适配器选择 owner 或 clone。
3. XSpace 适配器只对小米 `user 999` 生效，附加已验证的 `xspace_cached_uid` 与 `xspace_userid_selected=true`，再通过已经进入前台的透明代理 Activity 发送自由窗请求。
4. Xiaomi 私有路径不可用时，第二应用依次降级到 `LauncherApps` 跨 Profile 自由窗和同 Profile 普通启动；其他厂商直接从公开路径开始。
5. 所有路径同步失败时记录日志并保留普通侧栏状态；只有启动请求被系统 API 接受后才写入该实例历史。异步代理和 `LauncherApps.startMainActivity()` 都没有目标首帧回执，因此无法在不增加跨用户权限的前提下进一步确认首帧已经显示。

真机补充：HyperOS 可能首次询问是否允许界枢启动目标应用。确认页会以空 ActivityOptions 二次启动目标，因此第一次获批跳转仍可能全屏；用户选择“始终允许”后，后续请求绕过确认页并保留 owner/clone 选择参数与自由窗边界。没有分身的 owner 应用、普通工作资料和其他厂商不进入 XSpace 兼容分支。

不新增 `INTERACT_ACROSS_USERS_FULL` 等广泛跨用户权限，不使用 `getLaunchIntentForPackage()` 启动分身，也不依赖 UsageStats 跨用户查询。

## 错误与降级

- `LauncherApps` 枚举失败：保留上一次有效目录，若无缓存则显示空状态。
- Profile 在刷新期间被删除：过滤运行时条目，固定显示列表自动收敛但不删除持久化选择，点击不崩溃。
- 徽标加载失败：沿用现有首字母占位绘制。
- 跨 Profile 启动抛出 `SecurityException`/`ActivityNotFoundException`：按启动策略继续降级。
- UsageStats 不可用：仍显示本地实例历史，随后追加全部可启动应用。

## 测试与验收

### 单元测试

- Profile 实例目录映射、实例键稳定性和主/分身去重。
- 最近区与全部区合并、重复实例排除和固定顺序。
- 旧包名历史迁移、实例历史归一化和 Profile 消失过滤。
- 行模型滚动距离、分区标题不可点击、固定模式 `+` 可到达。
- 启动策略在 LauncherApps 成功、权限拒绝和普通降级路径中的行为。

### Xiaomi Pad 7S Pro 验收

- 当前设备的 user 0 与 XSpace user 999 中的同一应用显示为两个条目，图标保留系统分身徽标。
- 向下拖动可看到超过首屏 10 行的应用，滚动条和末尾点击正确。
- 固定模式可以只选主应用、只选第二应用或同时选择两者。
- 分别点击两个实例，任务用户分别为 user 0/user 999，并尽可能进入既定窄自由窗。
- 旋转、收起再展开、权限缺失和分身被删除时不出现空白窗口或崩溃。

## 非目标

- 不复制 Android 最近任务界面或任务快照。
- 不请求 root、Shizuku、无障碍或广泛跨用户权限。
- 不把所有系统 UsageStats 持久化到 Edge Shelf。
- 不改变现有侧栏触发区、品牌动效和自由窗比例决策。
