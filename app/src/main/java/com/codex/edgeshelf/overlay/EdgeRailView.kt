package com.codex.edgeshelf.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.PathInterpolator
import com.codex.edgeshelf.R
import com.codex.edgeshelf.data.LaunchableApp
import com.codex.edgeshelf.data.ShelfSettings
import com.codex.edgeshelf.data.ShelfSide
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class EdgeRailView(
    context: Context,
    private val onLaunch: (String) -> Unit,
    private val onAddApp: () -> Unit = {},
    private val onOpenRecentSettings: () -> Unit = {},
    private val onRefreshRequested: () -> Unit = {},
    private val onVerticalFractionChanged: (Float) -> Unit = {},
    private val onWindowGeometryChanged: (RailWindowGeometry) -> Unit = {},
) : View(context) {
    private val density = resources.displayMetrics.density
    private val gestureMachine = GestureStateMachine(
        thresholds = GestureThresholds(
            expandDp = dp(EXPAND_THRESHOLD_DP),
            longPressMs = LONG_PRESS_MS,
            settleMs = SETTLE_MS,
        ),
    )
    private val settleInterpolator = PathInterpolator(0.2f, 0.8f, 0.2f, 1f)
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val addPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(225, 63, 69, 86)
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        strokeCap = Paint.Cap.ROUND
    }
    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 72, 78, 96)
        textAlign = Paint.Align.CENTER
        textSize = dp(18f)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val emptyStatePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 72, 78, 96)
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val emptyStateBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(115, 255, 255, 255)
        style = Paint.Style.FILL
    }
    private val emptyStateBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, 72, 78, 96)
        style = Paint.Style.FILL
    }
    private val emptyStateBadgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = dp(9f)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val loadingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 72, 78, 96)
        style = Paint.Style.FILL
    }
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    private var settings = ShelfSettings()
    private var visibleApps: List<LaunchableApp> = emptyList()
    private var contentLoaded = false
    private var systemHidden = false
    private var panelProgress = 0f
    private var settleAnimator: ValueAnimator? = null
    private var scrollOffset = 0f
    private var verticalFraction = settings.verticalFraction
    private var downRawX = 0f
    private var downRawY = 0f
    private var lastTouchY = 0f
    private var downRowIndex = -1
    private var scrollingApps = false
    private var gestureMoved = false

    init {
        setWillNotDraw(false)
        isClickable = true
        isLongClickable = true
    }

    fun updateSettings(newSettings: ShelfSettings) {
        val modeChanged = settings.mode != newSettings.mode
        settings = newSettings
        verticalFraction = newSettings.verticalFraction
        if (modeChanged) {
            visibleApps = emptyList()
            contentLoaded = false
            scrollOffset = 0f
        }
        updateAccessibilityDescription()
        scrollOffset = scrollOffset.coerceIn(0f, maxScrollOffset())
        publishWindowGeometry()
        invalidateGeometry()
    }

    fun updateDisplayApps(
        apps: List<LaunchableApp>,
        contentLoaded: Boolean = true,
    ) {
        visibleApps = apps.toList()
        this.contentLoaded = contentLoaded
        updateAccessibilityDescription()
        scrollOffset = scrollOffset.coerceIn(0f, maxScrollOffset())
        publishWindowGeometry()
        invalidateGeometry()
    }

    fun collapse(immediate: Boolean = false) {
        scrollingApps = false
        scrollOffset = 0f
        if (immediate) {
            settleAnimator?.cancel()
            settleAnimator = null
            panelProgress = 0f
            gestureMachine.markCollapsed()
            publishWindowGeometry()
            invalidateGeometry()
        } else {
            animatePanel(expanded = false, durationMs = SETTLE_MS)
        }
    }

    fun setSystemHidden(hidden: Boolean) {
        if (systemHidden == hidden) return
        systemHidden = hidden
        if (hidden) collapse(immediate = true)
        visibility = if (hidden) INVISIBLE else VISIBLE
        invalidateGeometry()
    }

    override fun onDetachedFromWindow() {
        settleAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (systemHidden || width == 0 || height == 0) return

        val geometry = geometry()
        if (gestureMachine.state == RailGestureState.Dragging) {
            drawDraggingHandle(canvas, geometry)
        } else if (panelProgress <= COLLAPSED_EPSILON) {
            drawCollapsedHandle(canvas, geometry)
        } else {
            drawPanel(canvas, geometry)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (systemHidden) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_OUTSIDE -> {
                if (panelProgress > COLLAPSED_EPSILON) collapse()
                scrollingApps = false
                downRowIndex = -1
                return true
            }

            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                lastTouchY = event.y
                scrollingApps = false
                gestureMoved = false
                downRowIndex = if (isExpanded()) rowIndexAt(event.x, event.y) else -1
                gestureMachine.onDown(event.rawX, event.rawY, settings.side)
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.rawX - downRawX
                val deltaY = event.rawY - downRawY
                if (abs(deltaX) > touchSlop || abs(deltaY) > touchSlop) gestureMoved = true

                if (isExpanded() && !scrollingApps && abs(deltaY) > touchSlop && abs(deltaY) > abs(deltaX)) {
                    scrollingApps = maxScrollOffset() > 0f
                }
                if (scrollingApps) {
                    val delta = event.y - lastTouchY
                    scrollOffset = (scrollOffset - delta).coerceIn(0f, maxScrollOffset())
                    lastTouchY = event.y
                    invalidate()
                    return true
                }

                applyGestureEffect(gestureMachine.onMove(event.rawX, event.rawY))
                lastTouchY = event.y
                return true
            }

            MotionEvent.ACTION_UP -> {
                val wasDragging = gestureMachine.state == RailGestureState.Dragging
                val wasExpanded = isExpanded()
                val upIndex = if (wasExpanded) rowIndexAt(event.x, event.y) else -1
                val effect = gestureMachine.onUp(event.rawX, event.rawY)

                if (wasDragging) persistDraggedPosition()
                if (scrollingApps) {
                    scrollingApps = false
                    downRowIndex = -1
                    return true
                }

                applyGestureEffect(effect)
                if (wasExpanded && !gestureMoved && downRowIndex >= 0 && downRowIndex == upIndex) {
                    if (upIndex == visibleApps.size) {
                        when (tailRow()) {
                            RailTailRow.ADD -> {
                                performClick()
                                collapse(immediate = true)
                                onAddApp()
                            }

                            RailTailRow.RECENT_EMPTY -> {
                                performClick()
                            }

                            RailTailRow.LOADING,
                            RailTailRow.NONE,
                            -> Unit
                        }
                    } else {
                        visibleApps.getOrNull(upIndex)?.packageName?.let { packageName ->
                            performClick()
                            collapse()
                            onLaunch(packageName)
                        }
                    }
                } else if (wasExpanded && !gestureMoved && downRowIndex < 0) {
                    collapse()
                }
                downRowIndex = -1
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                val wasDragging = gestureMachine.state == RailGestureState.Dragging
                if (wasDragging) persistDraggedPosition()
                scrollingApps = false
                downRowIndex = -1
                applyGestureEffect(gestureMachine.onCancel())
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        if (tailRow() == RailTailRow.RECENT_EMPTY) {
            collapse(immediate = true)
            onOpenRecentSettings()
        }
        return true
    }

    private fun applyGestureEffect(effect: GestureEffect) {
        when (effect) {
            is GestureEffect.Peek -> {
                if (effect.requestRefresh) onRefreshRequested()
                panelProgress = (effect.inwardDistance / dp(EXPAND_THRESHOLD_DP)).coerceIn(0f, 1f)
                publishWindowGeometry(
                    progressOverride = panelProgress,
                    fractionOverride = verticalFraction,
                )
                invalidate()
            }

            GestureEffect.BeginExpanded -> {
                panelProgress = 1f
                gestureMachine.markExpanded()
                invalidateGeometry()
            }

            GestureEffect.BeginDragging -> {
                settleAnimator?.cancel()
                settleAnimator = null
                panelProgress = 0f
                performLongClick()
                publishWindowGeometry(progressOverride = 0f, fractionOverride = verticalFraction)
                invalidateGeometry()
            }

            is GestureEffect.MoveVertical -> {
                val (topInset, bottomInset) = systemBarInsets()
                val displayHeight = displayHeightPx().toFloat()
                val travel = (
                    displayHeight - topInset - bottomInset - collapsedHeight()
                ).coerceAtLeast(1f)
                verticalFraction = (verticalFraction + effect.delta / travel).coerceIn(0f, 1f)
                publishWindowGeometry(progressOverride = panelProgress, fractionOverride = verticalFraction)
                invalidate()
            }

            is GestureEffect.Settle -> animatePanel(effect.expanded, effect.durationMs)
            GestureEffect.Collapse -> collapse()
            GestureEffect.BeginEditing -> Unit
            GestureEffect.NoOp -> Unit
        }
    }

    private fun animatePanel(expanded: Boolean, durationMs: Long) {
        settleAnimator?.cancel()
        val target = if (expanded) 1f else 0f
        if (abs(panelProgress - target) < COLLAPSED_EPSILON) {
            panelProgress = target
            if (expanded) gestureMachine.markExpanded() else gestureMachine.markCollapsed()
            publishWindowGeometry(progressOverride = target, fractionOverride = verticalFraction)
            invalidateGeometry()
            return
        }
        settleAnimator = ValueAnimator.ofFloat(panelProgress, target).apply {
            duration = durationMs
            interpolator = settleInterpolator
            addUpdateListener { animator ->
                panelProgress = animator.animatedValue as Float
                publishWindowGeometry(
                    progressOverride = panelProgress,
                    fractionOverride = verticalFraction,
                )
                invalidateGeometry()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var canceled = false

                override fun onAnimationCancel(animation: android.animation.Animator) {
                    canceled = true
                    if (settleAnimator === animation) settleAnimator = null
                }

                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (canceled || settleAnimator !== animation) return
                    panelProgress = target
                    if (expanded) gestureMachine.markExpanded() else gestureMachine.markCollapsed()
                    publishWindowGeometry(progressOverride = target, fractionOverride = verticalFraction)
                    settleAnimator = null
                    invalidateGeometry()
                }
            })
            start()
        }
    }

    private fun persistDraggedPosition() {
        settings = settings.copy(verticalFraction = verticalFraction)
        onVerticalFractionChanged(verticalFraction)
        publishWindowGeometry(progressOverride = panelProgress, fractionOverride = verticalFraction)
    }

    private fun drawCollapsedHandle(canvas: Canvas, geometry: Geometry) {
        val visibleWidth = dp(COLLAPSED_VISIBLE_WIDTH_DP)
        val handle = if (settings.side == ShelfSide.RIGHT) {
            RectF(width - visibleWidth, geometry.top, width.toFloat(), geometry.bottom)
        } else {
            RectF(0f, geometry.top, visibleWidth, geometry.bottom)
        }
        handlePaint.color = Color.argb(92, 232, 235, 244)
        canvas.drawRoundRect(handle, dp(4f), dp(4f), handlePaint)
        outlinePaint.color = Color.argb(62, 255, 255, 255)
        canvas.drawRoundRect(handle, dp(4f), dp(4f), outlinePaint)
    }

    private fun drawDraggingHandle(canvas: Canvas, geometry: Geometry) {
        val handleWidth = dp(DRAGGING_HANDLE_WIDTH_DP)
        val handleHeight = min(dp(DRAGGING_HANDLE_HEIGHT_DP), geometry.bottom - geometry.top)
        val verticalInset = (geometry.bottom - geometry.top - handleHeight) / 2f
        val edgeInset = dp(DRAGGING_HANDLE_EDGE_INSET_DP)
        val handle = if (settings.side == ShelfSide.RIGHT) {
            RectF(
                width - edgeInset - handleWidth,
                geometry.top + verticalInset,
                width - edgeInset,
                geometry.bottom - verticalInset,
            )
        } else {
            RectF(
                edgeInset,
                geometry.top + verticalInset,
                edgeInset + handleWidth,
                geometry.bottom - verticalInset,
            )
        }
        handlePaint.color = Color.argb(235, 145, 147, 151)
        val radius = handleWidth / 2f
        canvas.drawRoundRect(handle, radius, radius, handlePaint)
    }

    private fun drawPanel(canvas: Canvas, geometry: Geometry) {
        val panel = geometry.panelRect
        val alpha = (118 + 110 * panelProgress).toInt().coerceIn(0, 228)
        backgroundPaint.color = Color.argb(alpha, 246, 247, 251)
        canvas.drawRoundRect(panel, dp(PANEL_RADIUS_DP), dp(PANEL_RADIUS_DP), backgroundPaint)
        outlinePaint.color = Color.argb((86 * panelProgress).toInt(), 255, 255, 255)
        canvas.drawRoundRect(panel, dp(PANEL_RADIUS_DP), dp(PANEL_RADIUS_DP), outlinePaint)

        val contentRect = geometry.contentRect
        val contentAlpha = railContentAlpha(panelProgress)
        if (contentAlpha == 0) return
        canvas.saveLayerAlpha(contentRect, contentAlpha)
        canvas.clipRect(contentRect)
        visibleApps.forEachIndexed { index, app ->
            val rowTop = contentRect.top + index * itemHeight() - scrollOffset
            val rowBottom = rowTop + itemHeight()
            if (rowBottom >= contentRect.top && rowTop <= contentRect.bottom) {
                drawAppIcon(canvas, app.icon, app.label, contentRect.centerX(), rowTop + itemHeight() / 2f)
            }
        }
        val tailRowTop = contentRect.top + visibleApps.size * itemHeight() - scrollOffset
        if (tailRowTop + itemHeight() >= contentRect.top && tailRowTop <= contentRect.bottom) {
            val centerX = contentRect.centerX()
            val centerY = tailRowTop + itemHeight() / 2f
            when (tailRow()) {
                RailTailRow.ADD -> drawAddButton(canvas, centerX, centerY)
                RailTailRow.RECENT_EMPTY -> drawRecentEmptyButton(canvas, centerX, centerY)
                RailTailRow.LOADING -> drawLoadingIndicator(canvas, centerX, centerY)
                RailTailRow.NONE -> Unit
            }
        }
        if (maxScrollOffset() > 0f) drawScrollIndicator(canvas, geometry)
        canvas.restore()
    }

    private fun drawAppIcon(canvas: Canvas, icon: Drawable?, label: String, centerX: Float, centerY: Float) {
        val iconSize = dp(ICON_SIZE_DP).toInt()
        if (icon != null) {
            val left = (centerX - iconSize / 2f).toInt()
            val top = (centerY - iconSize / 2f).toInt()
            icon.setBounds(left, top, left + iconSize, top + iconSize)
            runCatching { icon.draw(canvas) }
                .onFailure { drawPlaceholder(canvas, label, centerX, centerY) }
        } else {
            drawPlaceholder(canvas, label, centerX, centerY)
        }
    }

    private fun drawAddButton(canvas: Canvas, centerX: Float, centerY: Float) {
        val half = dp(8f)
        canvas.drawLine(centerX - half, centerY, centerX + half, centerY, addPaint)
        canvas.drawLine(centerX, centerY - half, centerX, centerY + half, addPaint)
    }

    private fun drawRecentEmptyButton(canvas: Canvas, centerX: Float, centerY: Float) {
        canvas.drawCircle(centerX, centerY, dp(19f), emptyStateBackgroundPaint)
        val clockCenterX = centerX - dp(2f)
        val clockCenterY = centerY - dp(2f)
        val radius = dp(9f)
        val clockBounds = RectF(
            clockCenterX - radius,
            clockCenterY - radius,
            clockCenterX + radius,
            clockCenterY + radius,
        )
        canvas.drawArc(clockBounds, -65f, 300f, false, emptyStatePaint)
        val arrowTipX = clockCenterX - radius * 0.72f
        val arrowTipY = clockCenterY - radius * 0.7f
        canvas.drawLine(arrowTipX, arrowTipY, arrowTipX - dp(1f), arrowTipY + dp(5f), emptyStatePaint)
        canvas.drawLine(arrowTipX, arrowTipY, arrowTipX + dp(4f), arrowTipY + dp(2f), emptyStatePaint)
        canvas.drawLine(clockCenterX, clockCenterY, clockCenterX, clockCenterY - dp(4f), emptyStatePaint)
        canvas.drawLine(clockCenterX, clockCenterY, clockCenterX + dp(3.5f), clockCenterY + dp(2f), emptyStatePaint)

        val badgeCenterX = centerX + dp(11f)
        val badgeCenterY = centerY + dp(11f)
        canvas.drawCircle(badgeCenterX, badgeCenterY, dp(6.5f), emptyStateBadgePaint)
        val badgeBaseline = badgeCenterY -
            (emptyStateBadgeTextPaint.ascent() + emptyStateBadgeTextPaint.descent()) / 2f
        canvas.drawText("i", badgeCenterX, badgeBaseline, emptyStateBadgeTextPaint)
    }

    private fun updateAccessibilityDescription() {
        contentDescription = if (tailRow() == RailTailRow.RECENT_EMPTY) {
            resources.getString(R.string.recent_empty_action_description)
        } else {
            null
        }
    }

    private fun drawLoadingIndicator(canvas: Canvas, centerX: Float, centerY: Float) {
        val dotRadius = dp(2f)
        val spacing = dp(7f)
        canvas.drawCircle(centerX - spacing, centerY, dotRadius, loadingPaint)
        canvas.drawCircle(centerX, centerY, dotRadius, loadingPaint)
        canvas.drawCircle(centerX + spacing, centerY, dotRadius, loadingPaint)
    }

    private fun drawPlaceholder(canvas: Canvas, label: String, centerX: Float, centerY: Float) {
        val glyph = label.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        val baseline = centerY - (placeholderPaint.ascent() + placeholderPaint.descent()) / 2f
        canvas.drawText(glyph, centerX, baseline, placeholderPaint)
    }

    private fun drawScrollIndicator(canvas: Canvas, geometry: Geometry) {
        val trackHeight = geometry.contentRect.height()
        val totalHeight = rowCount() * itemHeight()
        val thumbHeight = max(dp(18f), trackHeight * trackHeight / totalHeight)
        val travel = trackHeight - thumbHeight
        val thumbTop = geometry.contentRect.top + travel * (scrollOffset / maxScrollOffset())
        val x = if (settings.side == ShelfSide.RIGHT) geometry.panelRect.left + dp(5f)
        else geometry.panelRect.right - dp(5f)
        handlePaint.color = Color.argb(92, 90, 98, 120)
        handlePaint.strokeWidth = dp(2f)
        handlePaint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(x, thumbTop, x, thumbTop + thumbHeight, handlePaint)
    }

    private fun rowIndexAt(x: Float, y: Float): Int {
        val geometry = geometry()
        if (!geometry.panelRect.contains(x, y) || !geometry.contentRect.contains(x, y)) return -1
        val index = floor((y - geometry.contentRect.top + scrollOffset) / itemHeight()).toInt()
        return index.takeIf { it in 0 until rowCount() } ?: -1
    }

    private fun geometry(): Geometry {
        val currentWidth = dp(COLLAPSED_VISIBLE_WIDTH_DP) +
            (dp(EXPANDED_WIDTH_DP) - dp(COLLAPSED_VISIBLE_WIDTH_DP)) * panelProgress
        val panel = if (settings.side == ShelfSide.RIGHT) {
            RectF(width - currentWidth, 0f, width.toFloat(), height.toFloat())
        } else {
            RectF(0f, 0f, currentWidth, height.toFloat())
        }
        val contentPadding = dp(CONTENT_PADDING_DP) * panelProgress
        val content = RectF(
            panel.left + contentPadding,
            panel.top + contentPadding,
            panel.right - contentPadding,
            panel.bottom - contentPadding,
        )
        return Geometry(0f, height.toFloat(), panel, content)
    }

    private fun publishWindowGeometry(
        progressOverride: Float = panelProgress,
        fractionOverride: Float = verticalFraction,
    ) {
        val collapsedWidth = dp(COLLAPSED_TOUCH_WIDTH_DP)
        val width = collapsedWidth + (dp(EXPANDED_WIDTH_DP) - collapsedWidth) * progressOverride
        val railHeight = collapsedHeight() + (expandedHeight() - collapsedHeight()) * progressOverride
        val screenHeight = displayHeightPx()
        val (topInset, bottomInset) = systemBarInsets()
        val y = verticalTop(
            verticalFraction = fractionOverride,
            screenHeight = screenHeight,
            railHeight = railHeight.toInt(),
            topInset = topInset,
            bottomInset = bottomInset,
        )
        onWindowGeometryChanged(
            RailWindowGeometry(
                widthPx = width.toInt().coerceAtLeast(1),
                heightPx = railHeight.toInt().coerceAtLeast(1),
                yPx = y,
                side = settings.side,
            ),
        )
    }

    private fun expandedHeight(): Float {
        val visibleCount = rowCount().coerceAtMost(visibleRowCapacity())
        return max(
            collapsedHeight(),
            visibleCount * itemHeight() + dp(CONTENT_PADDING_DP * 2f),
        )
    }

    private fun collapsedHeight(): Float = dp(COLLAPSED_HEIGHT_DP)

    private fun itemHeight(): Float = dp(ITEM_HEIGHT_DP)

    private fun rowCount(): Int = visibleApps.size + if (tailRow() == RailTailRow.NONE) 0 else 1

    private fun tailRow(): RailTailRow = railTailRow(
        mode = settings.mode,
        appCount = visibleApps.size,
        contentLoaded = contentLoaded,
    )

    private fun maxScrollOffset(): Float =
        max(0f, rowCount() * itemHeight() - visibleRowCapacity() * itemHeight())

    private fun visibleRowCapacity(): Int {
        val (topInset, bottomInset) = systemBarInsets()
        val preferredMaximum = if (resources.configuration.smallestScreenWidthDp >= 600) {
            MAX_VISIBLE_ROWS_LARGE_SCREEN
        } else {
            MAX_VISIBLE_ROWS_PHONE
        }
        return visibleRailRowCapacity(
            availableHeight = displayHeightPx() - topInset - bottomInset,
            itemHeight = itemHeight().toInt(),
            verticalPadding = dp(CONTENT_PADDING_DP).toInt(),
            preferredMaximum = preferredMaximum,
        )
    }

    private fun isExpanded(): Boolean =
        gestureMachine.state == RailGestureState.Expanded || panelProgress >= 1f - COLLAPSED_EPSILON

    private fun invalidateGeometry() {
        invalidate()
        if (isAttachedToWindow && viewTreeObserver.isAlive) requestLayout()
    }

    private fun systemBarInsets(): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val insets = context.getSystemService(WindowManager::class.java)
                ?.currentWindowMetrics
                ?.windowInsets
                ?.getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
                )
            if (insets != null) return insets.top to insets.bottom
        }
        return systemDimension("status_bar_height") to systemDimension("navigation_bar_height")
    }

    private fun displayHeightPx(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.getSystemService(WindowManager::class.java)
            ?.currentWindowMetrics
            ?.bounds
            ?.height()
            ?: resources.displayMetrics.heightPixels
    } else {
        resources.displayMetrics.heightPixels
    }

    private fun systemDimension(name: String): Int {
        val identifier = resources.getIdentifier(name, "dimen", "android")
        return if (identifier == 0) 0 else resources.getDimensionPixelSize(identifier)
    }

    private fun dp(value: Float): Float = value * density

    private data class Geometry(
        val top: Float,
        val bottom: Float,
        val panelRect: RectF,
        val contentRect: RectF,
    )

    private companion object {
        const val MAX_VISIBLE_ROWS_PHONE = 6
        const val MAX_VISIBLE_ROWS_LARGE_SCREEN = 10
        const val EXPAND_THRESHOLD_DP = 24f
        const val LONG_PRESS_MS = 450L
        const val SETTLE_MS = 210L
        const val COLLAPSED_VISIBLE_WIDTH_DP = 5f
        const val COLLAPSED_TOUCH_WIDTH_DP = 28f
        const val COLLAPSED_HEIGHT_DP = 116f
        const val DRAGGING_HANDLE_WIDTH_DP = 20f
        const val DRAGGING_HANDLE_HEIGHT_DP = 64f
        const val DRAGGING_HANDLE_EDGE_INSET_DP = 2f
        const val EXPANDED_WIDTH_DP = 68f
        const val ITEM_HEIGHT_DP = 54f
        const val ICON_SIZE_DP = 40f
        const val CONTENT_PADDING_DP = 6f
        const val PANEL_RADIUS_DP = 18f
        const val COLLAPSED_EPSILON = 0.001f
    }
}

data class RailWindowGeometry(
    val widthPx: Int,
    val heightPx: Int,
    val yPx: Int,
    val side: ShelfSide,
)
