package com.codex.edgeshelf.overlay

import com.codex.edgeshelf.data.LaunchableApp
import com.codex.edgeshelf.data.ShelfMode

sealed interface RailRow

sealed interface RailHeaderItem

data object RecordingToolItem : RailHeaderItem

data object ScreenshotToolItem : RailHeaderItem

data class PinnedAppItem(
    val app: LaunchableApp,
) : RailHeaderItem

data class AppRow(
    val app: LaunchableApp,
) : RailRow

data class SectionRow(
    val title: String,
) : RailRow

data object AddRow : RailRow

data object LoadingRow : RailRow

data object EmptyRow : RailRow

const val ALL_APPS_SECTION_TITLE = "全部应用"

internal fun RailRow.interactionIdentity(): String = when (this) {
    is AppRow -> "app:${app.key.stableId}"
    is SectionRow -> "section:$title"
    AddRow -> "add"
    LoadingRow -> "loading"
    EmptyRow -> "empty"
}

internal fun RailHeaderItem.interactionIdentity(): String = when (this) {
    RecordingToolItem -> "tool:recording"
    ScreenshotToolItem -> "tool:screenshot"
    is PinnedAppItem -> "pinned:${app.key.stableId}"
}

fun buildRailHeaderItems(
    recordingEnabled: Boolean,
    pinnedApps: Iterable<LaunchableApp>,
): List<RailHeaderItem> = buildList {
    if (recordingEnabled) add(RecordingToolItem)
    add(ScreenshotToolItem)
    pinnedApps
        .distinctBy(LaunchableApp::key)
        .take(3)
        .forEach { app -> add(PinnedAppItem(app)) }
}

fun buildRailRows(
    mode: ShelfMode,
    recentApps: Iterable<LaunchableApp> = emptyList(),
    allApps: Iterable<LaunchableApp> = emptyList(),
    fixedApps: Iterable<LaunchableApp> = emptyList(),
    contentLoaded: Boolean = true,
    allAppsSectionTitle: String = ALL_APPS_SECTION_TITLE,
): List<RailRow> = when (mode) {
    ShelfMode.FIXED -> buildList {
        fixedApps.forEach { app -> add(AppRow(app)) }
        add(AddRow)
    }

    ShelfMode.RECENT -> if (!contentLoaded) {
        listOf(LoadingRow)
    } else {
        buildList {
            recentApps.forEach { app -> add(AppRow(app)) }
            val remainingApps = allApps.toList()
            if (remainingApps.isNotEmpty()) {
                add(SectionRow(allAppsSectionTitle))
                remainingApps.forEach { app -> add(AppRow(app)) }
            }
            if (isEmpty()) add(EmptyRow)
        }
    }
}

internal fun legacyRailRows(
    mode: ShelfMode,
    apps: Iterable<LaunchableApp>,
    contentLoaded: Boolean,
): List<RailRow> = when (mode) {
    ShelfMode.FIXED -> buildRailRows(
        mode = mode,
        fixedApps = apps,
        contentLoaded = contentLoaded,
    )

    ShelfMode.RECENT -> if (contentLoaded) {
        apps.map(::AppRow).ifEmpty { listOf(EmptyRow) }
    } else {
        listOf(LoadingRow)
    }
}
