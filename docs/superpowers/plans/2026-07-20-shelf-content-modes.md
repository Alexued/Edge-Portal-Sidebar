# Edge Shelf 侧栏内容模式实施计划

> 历史说明：本文对应 1.0 阶段实现。应用数量上限、最近模式空状态和包名级持久化已由 [2026-07-22 滚动列表与应用双开实施计划](./2026-07-22-edge-shelf-scroll-and-clone-plan.md) 取代。

**目标：** 在不破坏现有固定列表和自由窗启动链的前提下，实现默认最近应用与可切换固定应用两种模式。

## 约束

- `RECENT` 是缺少模式字段时的统一默认值，旧固定列表只保留、不自动显示。
- 系统最近使用结果不持久化；本地历史记录所有从 Edge Shelf 成功发起的应用。
- 最近模式手机最多 6 个、平板最多 10 个，每次展开刷新并采用 3 秒节流。
- 最近模式不显示 `+`；固定模式显示 `+` 和管理入口。
- 权限缺失、查询失败或结果为空时降级到本地历史。
- 空最近列表显示设置入口，不自动切换模式。

## 任务 1：扩展持久化模型

涉及文件：

- `app/src/main/java/com/codex/edgeshelf/data/ShelfModels.kt`
- `app/src/main/java/com/codex/edgeshelf/data/ShelfStore.kt`
- `app/src/test/java/com/codex/edgeshelf/data/ShelfStoreTest.kt`

步骤：

1. 新增 `ShelfMode.RECENT/FIXED` 和 `ShelfSettings.mode`。
2. 新增 DataStore 模式键和 `setMode()`。
3. 缺失、未知或损坏值统一恢复为 `RECENT`。
4. 取消固定列表对本地最近历史的排除。
5. 测试默认、保存、损坏迁移、历史去重与固定应用仍可进入历史。

## 任务 2：实现内容选择器

涉及文件：

- `app/src/main/java/com/codex/edgeshelf/data/ShelfContentResolver.kt`
- `app/src/test/java/com/codex/edgeshelf/data/ShelfContentResolverTest.kt`

步骤：

1. 用纯函数解析最终包名列表。
2. 固定模式保持用户顺序。
3. 最近模式按系统结果优先、本地历史补足并去重。
4. 根据手机/平板容量限量，并过滤不存在于可启动目录的包。
5. 添加模式、权限降级、重复、卸载和 6/10 容量测试。

## 任务 3：重构侧栏展示接口

涉及文件：

- `app/src/main/java/com/codex/edgeshelf/overlay/EdgeRailView.kt`
- `app/src/main/java/com/codex/edgeshelf/overlay/RailGeometry.kt`
- 对应 overlay 测试

步骤：

1. 移除 View 内部应用目录查询职责。
2. 新增服务传入展示应用与模式的接口。
3. 最近模式隐藏 `+`，固定模式保留 `+`。
4. 新增最近空状态图标和打开设置回调。
5. 在收起状态首次向内拉动时回调刷新，确保一次展开只触发一次。
6. 保留现有展开、滚动、拖动胶囊和自动收起行为。

## 任务 4：接入服务刷新协调

涉及文件：

- `app/src/main/java/com/codex/edgeshelf/service/EdgeShelfService.kt`
- `app/src/main/java/com/codex/edgeshelf/data/UsageRepository.kt`
- 相关服务/纯函数测试

步骤：

1. 服务持有应用目录与使用统计仓库。
2. 在 IO 线程加载目录、查询系统最近并解析最终列表。
3. 首次挂载、模式切换、展开请求、强制刷新和成功启动后刷新。
4. 普通展开采用 3 秒节流，`ACTION_REFRESH` 绕过节流。
5. 查询中保留旧列表，通过序号或 Job 防止旧结果覆盖新模式。
6. 最近空状态点击后打开设置主页。

## 任务 5：增加设置模式界面

涉及文件：

- `app/src/main/java/com/codex/edgeshelf/ui/EdgeShelfViewModel.kt`
- `app/src/main/java/com/codex/edgeshelf/ui/EdgeShelfScreen.kt`
- `app/src/main/java/com/codex/edgeshelf/ui/AppPickerScreen.kt`
- `app/src/main/java/com/codex/edgeshelf/MainActivity.kt`
- 中英文字符串与 UI 测试

步骤：

1. 新增模式切换事件并持久化。
2. 在状态卡和权限区之间加入双按钮内容模式卡。
3. 最近模式缺权限时显示本地历史降级说明和授权按钮。
4. 固定管理入口只在固定模式显示。
5. 将清理项改名为“侧栏启动历史”。
6. 固定选择器更新标题和说明，不包含模式切换。

## 任务 6：验证与交付

1. 运行定向单测，再运行全部单测、lint 和 debug 构建。
2. 安装到测试设备，验证默认最近、权限降级、模式切换、固定 `+` 与最近空状态。
3. 验证最近应用仍以 `mode=freeform` 启动。
4. 更新 README，提交代码并推送 `Alexued/EdgeShelf` 私有仓库。
