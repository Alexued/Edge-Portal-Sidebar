package com.codex.edgeshelf.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.VelocityTracker
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import android.widget.OverScroller
import com.codex.edgeshelf.R
import com.codex.edgeshelf.data.LaunchableApp
import com.codex.edgeshelf.data.ShelfSettings
import com.codex.edgeshelf.data.ShelfSide
import com.codex.edgeshelf.recording.RecordingUiState
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

class EdgeRailView(
    context: Context,
    private val onLaunch: (LaunchableApp) -> Unit,
    private val onAddApp: () -> Unit = {},
    private val onToggleRecording: () -> Unit = {},
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
            expandSettleMs = RailMotion.EXPAND_DURATION_MS,
            collapseSettleMs = RailMotion.COLLAPSE_DURATION_MS,
        ),
    )
    private val expandInterpolator = PathInterpolator(
        RailMotion.EXPAND_INTERPOLATOR.x1,
        RailMotion.EXPAND_INTERPOLATOR.y1,
        RailMotion.EXPAND_INTERPOLATOR.x2,
        RailMotion.EXPAND_INTERPOLATOR.y2,
    )
    private val collapseInterpolator = PathInterpolator(
        RailMotion.COLLAPSE_INTERPOLATOR.x1,
        RailMotion.COLLAPSE_INTERPOLATOR.y1,
        RailMotion.COLLAPSE_INTERPOLATOR.x2,
        RailMotion.COLLAPSE_INTERPOLATOR.y2,
    )
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
    private val sectionDividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 72, 78, 96)
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val sectionTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 72, 78, 96)
        textAlign = Paint.Align.CENTER
        textSize = dp(9f)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val recordingIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.2f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val recordingFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val viewConfiguration = ViewConfiguration.get(context)
    private val touchSlop = viewConfiguration.scaledTouchSlop.toFloat()
    private val minimumFlingVelocity = viewConfiguration.scaledMinimumFlingVelocity.toFloat()
    private val maximumFlingVelocity = viewConfiguration.scaledMaximumFlingVelocity.toFloat()
    private val listScroller = OverScroller(context)

    private var settings = ShelfSettings()
    private var rows: List<RailRow> = listOf(LoadingRow)
    private var recordingUiState = RecordingUiState.IDLE
    private var systemHidden = false
    private var panelProgress = 0f
    private var settleAnimator: ValueAnimator? = null
    private var settleTargetExpanded: Boolean? = null
    private var contentAnimator: ValueAnimator? = null
    private var contentMotionElapsedMs = 0L
    private var contentExitProgress = 0f
    private var lockedExpandedHeight: Float? = null
    private var scrollOffset = 0f
    private var verticalFraction = settings.verticalFraction
    private var downTouchX = 0f
    private var downTouchY = 0f
    private var lastTouchY = 0f
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var velocityTracker: VelocityTracker? = null
    private var suppressTouchUntilGestureEnd = false
    private var downRecordingControl = false
    private var downRowIndex = -1
    private var downRowIdentity: String? = null
    private var scrollingApps = false
    private var gestureMoved = false
    private var launchFeedbackIndex = -1
    private var launchFeedbackUntilMs = 0L
    private var pendingLaunch: Runnable? = null
    private var cachedVisibleRowCapacity = 1
    private var cachedMaximumScrollOffset = 0f

    init {
        refreshScrollMetrics()
        setWillNotDraw(false)
        isClickable = true
        isLongClickable = true
    }

    fun updateSettings(newSettings: ShelfSettings) {
        val modeChanged = settings.mode != newSettings.mode
        settings = newSettings
        verticalFraction = newSettings.verticalFraction
        if (modeChanged) {
            cancelListInteractionForContentChange()
            rows = buildRailRows(mode = newSettings.mode, contentLoaded = false)
            scrollOffset = 0f
        }
        refreshScrollMetrics()
        updateAccessibilityDescription()
        clampScrollOffset()
        if (!isGeometryHeightLocked()) publishWindowGeometry()
        invalidateGeometry()
    }

    fun updateDisplayRows(newRows: List<RailRow>) {
        cancelListInteractionForContentChange()
        rows = newRows.toList().ifEmpty { listOf(EmptyRow) }
        refreshScrollMetrics()
        updateAccessibilityDescription()
        clampScrollOffset()
        if (!isGeometryHeightLocked()) publishWindowGeometry()
        invalidateGeometry()
    }

    fun updateRecordingState(newState: RecordingUiState) {
        if (recordingUiState == newState) return
        val previousState = recordingUiState
        recordingUiState = newState
        when (newState) {
            RecordingUiState.RECORDING ->
                announceForAccessibility(resources.getString(R.string.recording_started))
            RecordingUiState.IDLE -> if (previousState != RecordingUiState.IDLE) {
                announceForAccessibility(resources.getString(R.string.recording_stopped))
            }
            RecordingUiState.ERROR ->
                announceForAccessibility(resources.getString(R.string.recording_failed))
            RecordingUiState.STARTING,
            RecordingUiState.STOPPING,
            -> Unit
        }
        postInvalidateOnAnimation()
    }

    /**
     * Compatibility entry point for callers that still provide one flattened app list.
     * New callers should build rows with [buildRailRows] so the all-apps section is retained.
     */
    fun updateDisplayApps(
        apps: List<LaunchableApp>,
        contentLoaded: Boolean = true,
    ) {
        updateDisplayRows(legacyRailRows(settings.mode, apps, contentLoaded))
    }

    fun collapse(
        immediate: Boolean = false,
        preservePendingLaunch: Boolean = false,
    ) {
        if (!preservePendingLaunch) cancelPendingLaunch()
        cancelListInteraction()
        if (immediate) {
            scrollOffset = 0f
            settleAnimator?.cancel()
            settleAnimator = null
            settleTargetExpanded = null
            contentAnimator?.cancel()
            contentAnimator = null
            panelProgress = 0f
            contentMotionElapsedMs = 0L
            contentExitProgress = 0f
            launchFeedbackIndex = -1
            lockedExpandedHeight = null
            gestureMachine.markCollapsed()
            publishWindowGeometry()
            invalidateGeometry()
        } else {
            animatePanel(
                expanded = false,
                durationMs = RailMotion.COLLAPSE_DURATION_MS,
            )
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
        settleTargetExpanded = null
        contentAnimator?.cancel()
        cancelListInteraction()
        pendingLaunch?.let(::removeCallbacks)
        pendingLaunch = null
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

    override fun computeScroll() {
        super.computeScroll()
        if (!listScroller.computeScrollOffset()) return

        val maximumOffset = maxScrollOffset()
        val nextOffset = clampRailScrollOffset(listScroller.currY.toFloat(), maximumOffset)
        if (nextOffset != listScroller.currY.toFloat()) listScroller.forceFinished(true)
        if (nextOffset != scrollOffset) {
            scrollOffset = nextOffset
            postInvalidateOnAnimation()
        }
        if (!listScroller.isFinished) postInvalidateOnAnimation()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (systemHidden) return false
        if (suppressTouchUntilGestureEnd) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> suppressTouchUntilGestureEnd = false
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL,
                -> {
                    suppressTouchUntilGestureEnd = false
                    return true
                }
                else -> return true
            }
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_OUTSIDE -> {
                if (panelProgress > COLLAPSED_EPSILON) collapse() else cancelListInteraction()
                downRowIndex = -1
                downRowIdentity = null
                downRecordingControl = false
                return true
            }

            MotionEvent.ACTION_DOWN -> {
                val interruptedFling = stopListFling()
                recycleVelocityTracker()
                velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                activePointerId = event.getPointerId(0)
                downTouchX = event.rawX
                downTouchY = event.rawY
                lastTouchY = downTouchY
                scrollingApps = false
                gestureMoved = interruptedFling
                val expanded = isExpanded()
                downRecordingControl = expanded && recordingHeaderContains(event.x, event.y)
                downRowIndex = if (expanded && !downRecordingControl) {
                    rowIndexAt(event.x, event.y)
                } else {
                    -1
                }
                downRowIdentity = rows.getOrNull(downRowIndex)?.interactionIdentity()
                gestureMachine.onDown(event.rawX, event.rawY, settings.side)
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex < 0) {
                    val wasScrolling = scrollingApps
                    cancelListTouch()
                    if (!wasScrolling) applyGestureEffect(gestureMachine.onCancel())
                    return true
                }
                val touchX = rawPointerX(event, pointerIndex)
                val touchY = rawPointerY(event, pointerIndex)
                val deltaX = touchX - downTouchX
                val deltaY = touchY - downTouchY
                if (abs(deltaX) > touchSlop || abs(deltaY) > touchSlop) gestureMoved = true

                if (gestureMachine.state == RailGestureState.Expanded &&
                    !scrollingApps && shouldStartRailScroll(
                        deltaX = deltaX,
                        deltaY = deltaY,
                        touchSlop = touchSlop,
                        maximumOffset = maxScrollOffset(),
                    )
                ) {
                    scrollingApps = true
                    lastTouchY = downTouchY + if (deltaY > 0f) touchSlop else -touchSlop
                }
                if (scrollingApps) {
                    scrollOffset = railOffsetAfterDrag(
                        currentOffset = scrollOffset,
                        fingerDeltaY = touchY - lastTouchY,
                        maximumOffset = maxScrollOffset(),
                    )
                    lastTouchY = touchY
                    postInvalidateOnAnimation()
                    return true
                }

                applyGestureEffect(gestureMachine.onMove(touchX, touchY))
                lastTouchY = touchY
                return true
            }

            MotionEvent.ACTION_UP -> {
                velocityTracker?.addMovement(event)
                val wasDragging = gestureMachine.state == RailGestureState.Dragging
                val wasExpanded = isExpanded()
                val upRecordingControl = wasExpanded && recordingHeaderContains(event.x, event.y)
                val upIndex = if (wasExpanded && !upRecordingControl) {
                    rowIndexAt(event.x, event.y)
                } else {
                    -1
                }
                val effect = gestureMachine.onUp(event.rawX, event.rawY)

                if (wasDragging) persistDraggedPosition()
                if (scrollingApps) {
                    startListFling()
                    scrollingApps = false
                    recycleVelocityTracker()
                    downRowIndex = -1
                    downRowIdentity = null
                    downRecordingControl = false
                    return true
                }

                applyGestureEffect(effect)
                val upRow = rows.getOrNull(upIndex)
                val sameRow = downRowIndex >= 0 &&
                    downRowIndex == upIndex &&
                    downRowIdentity == upRow?.interactionIdentity()
                if (wasExpanded && !gestureMoved && downRecordingControl && upRecordingControl) {
                    performClick()
                    onToggleRecording()
                } else if (wasExpanded && !gestureMoved && sameRow) {
                    when (val row = upRow) {
                        is AppRow -> {
                            performClick()
                            launchWithFeedback(upIndex, row.app)
                        }

                        AddRow -> {
                            performClick()
                            collapse(immediate = true)
                            onAddApp()
                        }

                        EmptyRow -> {
                            performClick()
                            collapse(immediate = true)
                            onOpenRecentSettings()
                        }

                        is SectionRow,
                        LoadingRow,
                        null,
                        -> Unit
                    }
                } else if (wasExpanded && !gestureMoved && !downRecordingControl && downRowIndex < 0) {
                    collapse()
                }
                downRowIndex = -1
                downRowIdentity = null
                downRecordingControl = false
                recycleVelocityTracker()
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                velocityTracker?.addMovement(event)
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                velocityTracker?.addMovement(event)
                if (event.getPointerId(event.actionIndex) == activePointerId) {
                    val replacementIndex = if (event.actionIndex == 0) 1 else 0
                    if (replacementIndex >= event.pointerCount) {
                        val wasScrolling = scrollingApps
                        cancelListTouch()
                        if (!wasScrolling) applyGestureEffect(gestureMachine.onCancel())
                        return true
                    }
                    activePointerId = event.getPointerId(replacementIndex)
                    downTouchX = rawPointerX(event, replacementIndex)
                    downTouchY = rawPointerY(event, replacementIndex)
                    lastTouchY = downTouchY
                    velocityTracker?.clear()
                    velocityTracker?.addMovement(event)
                    gestureMachine.onDown(downTouchX, downTouchY, settings.side)
                    gestureMoved = true
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                val wasDragging = gestureMachine.state == RailGestureState.Dragging
                if (wasDragging) persistDraggedPosition()
                val wasScrolling = scrollingApps
                scrollingApps = false
                recycleVelocityTracker()
                downRowIndex = -1
                downRowIdentity = null
                downRecordingControl = false
                if (!wasScrolling) applyGestureEffect(gestureMachine.onCancel())
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun launchWithFeedback(index: Int, app: LaunchableApp) {
        pendingLaunch?.let(::removeCallbacks)
        if (!ValueAnimator.areAnimatorsEnabled()) {
            collapse(immediate = true)
            onLaunch(app)
            return
        }

        launchFeedbackIndex = index
        launchFeedbackUntilMs = SystemClock.uptimeMillis() + LAUNCH_FEEDBACK_DURATION_MS
        val launch = Runnable {
            pendingLaunch = null
            if (!systemHidden && isAttachedToWindow) onLaunch(app)
        }
        pendingLaunch = launch
        postDelayed(launch, LAUNCH_DISPATCH_DELAY_MS)
        // Start the rail exit immediately; the short delay only preserves tactile feedback
        // before the launch request reaches the system window manager.
        collapse(preservePendingLaunch = true)
        postInvalidateOnAnimation()
    }

    private fun cancelPendingLaunch() {
        pendingLaunch?.let(::removeCallbacks)
        pendingLaunch = null
    }

    private fun startListFling() {
        val pointerId = activePointerId
        val tracker = velocityTracker ?: return
        if (pointerId == MotionEvent.INVALID_POINTER_ID) return

        tracker.computeCurrentVelocity(1_000, maximumFlingVelocity)
        val contentVelocity = railContentFlingVelocity(
            fingerVelocityY = tracker.getYVelocity(pointerId),
            minimumVelocity = minimumFlingVelocity,
            maximumVelocity = maximumFlingVelocity,
        )
        val maximumOffset = maxScrollOffset()
        if (!canRailFling(scrollOffset, maximumOffset, contentVelocity)) return

        listScroller.fling(
            0,
            scrollOffset.roundToInt(),
            0,
            contentVelocity.roundToInt(),
            0,
            0,
            0,
            maximumOffset.roundToInt(),
        )
        postInvalidateOnAnimation()
    }

    private fun stopListFling(): Boolean {
        if (listScroller.isFinished) return false
        if (listScroller.computeScrollOffset()) {
            scrollOffset = clampRailScrollOffset(listScroller.currY.toFloat(), maxScrollOffset())
        }
        listScroller.forceFinished(true)
        postInvalidateOnAnimation()
        return true
    }

    private fun cancelListTouch() {
        scrollingApps = false
        recycleVelocityTracker()
        downRowIndex = -1
        downRowIdentity = null
        downRecordingControl = false
    }

    private fun cancelListInteraction() {
        stopListFling()
        cancelListTouch()
    }

    private fun cancelListInteractionForContentChange() {
        stopListFling()
        if (activePointerId == MotionEvent.INVALID_POINTER_ID) {
            cancelListTouch()
            return
        }

        if (scrollingApps || gestureMachine.state == RailGestureState.Expanded) {
            cancelListTouch()
            gestureMoved = true
            suppressTouchUntilGestureEnd = true
        } else {
            // A refresh may finish while the collapsed handle is still being pulled.
            // Keep that gesture alive, but discard samples captured against the old rows.
            velocityTracker?.clear()
        }
    }

    private fun recycleVelocityTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
        activePointerId = MotionEvent.INVALID_POINTER_ID
    }

    private fun rawPointerX(event: MotionEvent, pointerIndex: Int): Float =
        event.rawX + event.getX(pointerIndex) - event.getX(0)

    private fun rawPointerY(event: MotionEvent, pointerIndex: Int): Float =
        event.rawY + event.getY(pointerIndex) - event.getY(0)

    private fun applyGestureEffect(effect: GestureEffect) {
        when (effect) {
            is GestureEffect.Peek -> {
                if (panelProgress <= COLLAPSED_EPSILON) {
                    lockExpandedHeight(force = true)
                    if (effect.requestRefresh) onRefreshRequested()
                }
                panelProgress = (
                    effect.inwardDistance / dp(RailMotion.PANEL_TRAVEL_DP)
                ).coerceIn(0f, 1f)
                contentMotionElapsedMs = 0L
                contentExitProgress = 0f
                publishWindowGeometry(
                    progressOverride = panelProgress,
                    fractionOverride = verticalFraction,
                )
                postInvalidateOnAnimation()
            }

            GestureEffect.BeginExpanded -> {
                lockExpandedHeight()
                panelProgress = 1f
                contentMotionElapsedMs = 0L
                contentExitProgress = 0f
                gestureMachine.markExpanded()
                startContentAnimator()
                invalidateGeometry()
            }

            GestureEffect.BeginDragging -> {
                settleAnimator?.cancel()
                settleAnimator = null
                settleTargetExpanded = null
                contentAnimator?.cancel()
                contentAnimator = null
                panelProgress = 0f
                contentMotionElapsedMs = 0L
                contentExitProgress = 0f
                lockedExpandedHeight = null
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
                postInvalidateOnAnimation()
            }

            is GestureEffect.Settle -> animatePanel(effect.expanded, effect.durationMs)
            GestureEffect.Collapse -> collapse()
            GestureEffect.BeginEditing -> Unit
            GestureEffect.NoOp -> Unit
        }
    }

    private fun animatePanel(expanded: Boolean, durationMs: Long) {
        if (settleAnimator?.isRunning == true && settleTargetExpanded == expanded) return
        settleAnimator?.cancel()
        settleTargetExpanded = null
        if (expanded) {
            lockExpandedHeight(force = panelProgress <= COLLAPSED_EPSILON)
        } else {
            contentAnimator?.cancel()
            contentAnimator = null
        }
        val start = panelProgress
        val target = if (expanded) 1f else 0f
        val reducedMotion = !ValueAnimator.areAnimatorsEnabled()
        if (reducedMotion) {
            panelProgress = target
            contentMotionElapsedMs = if (expanded) {
                contentTimelineDurationMs()
            } else {
                0L
            }
            contentExitProgress = if (expanded) 0f else 1f
            if (expanded) {
                gestureMachine.markExpanded()
                lockedExpandedHeight = null
            } else {
                gestureMachine.markCollapsed()
                lockedExpandedHeight = null
                scrollOffset = 0f
            }
            publishWindowGeometry(progressOverride = target, fractionOverride = verticalFraction)
            invalidateGeometry()
            return
        }
        if (abs(start - target) < COLLAPSED_EPSILON) {
            panelProgress = target
            contentMotionElapsedMs = 0L
            contentExitProgress = if (expanded) 0f else 1f
            if (expanded) {
                gestureMachine.markExpanded()
            } else {
                gestureMachine.markCollapsed()
                scrollOffset = 0f
            }
            lockedExpandedHeight = null
            publishWindowGeometry(progressOverride = target, fractionOverride = verticalFraction)
            invalidateGeometry()
            if (expanded) startContentAnimator()
            return
        }

        val distance = abs(target - start)
        val duration = (durationMs * distance)
            .roundToInt()
            .toLong()
            .coerceAtLeast(MIN_SETTLE_MS)
            .coerceAtMost(durationMs)
        val interpolator = if (expanded) expandInterpolator else collapseInterpolator
        settleTargetExpanded = expanded
        settleAnimator = ValueAnimator.ofFloat(panelProgress, target).apply {
            this.duration = duration
            this.interpolator = interpolator
            addUpdateListener { animator ->
                panelProgress = animator.animatedValue as Float
                if (expanded) {
                    // Content waits until the panel is stable; a shared animator starts at
                    // the end of this panel transition and drives all row entrances.
                    contentMotionElapsedMs = 0L
                    contentExitProgress = 0f
                } else {
                    contentExitProgress = animator.animatedFraction.coerceIn(0f, 1f)
                }
                publishWindowGeometry(
                    progressOverride = panelProgress,
                    fractionOverride = verticalFraction,
                )
                postInvalidateOnAnimation()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var canceled = false

                override fun onAnimationCancel(animation: android.animation.Animator) {
                    canceled = true
                    if (settleAnimator === animation) {
                        settleAnimator = null
                        settleTargetExpanded = null
                    }
                }

                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (canceled || settleAnimator !== animation) return
                    panelProgress = target
                    contentMotionElapsedMs = if (expanded) {
                        0L
                    } else {
                        0L
                    }
                    contentExitProgress = if (expanded) 0f else 1f
                    if (expanded) {
                        gestureMachine.markExpanded()
                        // Apply any list refresh that arrived during the transition now that
                        // the panel has a stable target height.
                        lockedExpandedHeight = null
                    } else {
                        gestureMachine.markCollapsed()
                        lockedExpandedHeight = null
                        scrollOffset = 0f
                    }
                    publishWindowGeometry(progressOverride = target, fractionOverride = verticalFraction)
                    settleAnimator = null
                    settleTargetExpanded = null
                    invalidateGeometry()
                    if (expanded) startContentAnimator()
                }
            })
            start()
        }
    }

    private fun startContentAnimator() {
        contentAnimator?.cancel()
        val duration = contentTimelineDurationMs()
        contentMotionElapsedMs = 0L
        if (!ValueAnimator.areAnimatorsEnabled() || duration <= 0L) {
            contentMotionElapsedMs = duration
            invalidateGeometry()
            return
        }
        contentAnimator = ValueAnimator.ofInt(0, duration.toInt()).apply {
            this.duration = duration
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                contentMotionElapsedMs = (animator.animatedValue as Int).toLong()
                postInvalidateOnAnimation()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var canceled = false

                override fun onAnimationCancel(animation: android.animation.Animator) {
                    canceled = true
                    if (contentAnimator === animation) contentAnimator = null
                }

                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (!canceled && contentAnimator === animation) {
                        contentMotionElapsedMs = duration
                        contentAnimator = null
                        invalidateGeometry()
                    }
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
        val progress = panelProgress.coerceIn(0f, 1f)
        backgroundPaint.color = Color.argb(
            lerpInt(92, 228, progress),
            lerpInt(232, 246, progress),
            lerpInt(235, 247, progress),
            lerpInt(244, 251, progress),
        )
        val radius = dp(lerpFloat(COLLAPSED_RADIUS_DP, PANEL_RADIUS_DP, progress))
        canvas.drawRoundRect(panel, radius, radius, backgroundPaint)
        outlinePaint.color = Color.argb(lerpInt(62, 86, progress), 255, 255, 255)
        canvas.drawRoundRect(panel, radius, radius, outlinePaint)

        if (panelProgress <= COLLAPSED_EPSILON) return
        val recordingRect = geometry.recordingRect
        val rowsRect = geometry.rowsRect
        val panelContentScale = RailMotion.panelContentScale(
            panelProgress = panelProgress,
            collapsedContentFraction = COLLAPSED_CONTENT_SCALE,
        )
        val panelContentAlpha = RailMotion.panelContentAlpha(panelProgress)
        canvas.save()
        canvas.clipRect(geometry.panelRect)
        if (!recordingRect.isEmpty) {
            drawMotionItem(
                canvas = canvas,
                motionIndex = 0,
                feedbackIndex = null,
                centerX = recordingRect.centerX(),
                centerY = recordingRect.centerY(),
                panelContentScale = panelContentScale,
                panelContentAlpha = panelContentAlpha,
            ) { itemAlpha ->
                drawRecordingControl(
                    canvas = canvas,
                    centerX = recordingRect.centerX(),
                    centerY = recordingRect.centerY(),
                    alpha = itemAlpha,
                )
            }
        }
        canvas.clipRect(rowsRect)
        val visibleRange = visibleRailRowRange(
            scrollOffset = scrollOffset,
            viewportHeight = rowsRect.height(),
            itemHeight = itemHeight(),
            rowCount = rows.size,
        )
        for (index in visibleRange) {
            val row = rows.getOrNull(index) ?: continue
            val rowTop = rowsRect.top + index * itemHeight() - scrollOffset
            val centerX = rowsRect.centerX()
            val centerY = rowTop + itemHeight() / 2f
            drawMotionItem(
                canvas = canvas,
                motionIndex = index + 1,
                feedbackIndex = index,
                centerX = centerX,
                centerY = centerY,
                panelContentScale = panelContentScale,
                panelContentAlpha = panelContentAlpha,
            ) { itemAlpha ->
                when (row) {
                    is AppRow -> drawAppIcon(
                        canvas = canvas,
                        icon = row.app.icon,
                        label = row.app.label,
                        centerX = centerX,
                        centerY = centerY,
                        alpha = itemAlpha,
                    )

                    is SectionRow -> drawSectionRow(
                        canvas = canvas,
                        title = row.title,
                        left = rowsRect.left,
                        right = rowsRect.right,
                        centerX = centerX,
                        centerY = centerY,
                        alpha = itemAlpha,
                    )

                    AddRow -> drawAddButton(canvas, centerX, centerY, itemAlpha)
                    EmptyRow -> drawRecentEmptyButton(canvas, centerX, centerY, itemAlpha)
                    LoadingRow -> drawLoadingIndicator(canvas, centerX, centerY, itemAlpha)
                }
            }
        }
        if (maxScrollOffset() > 0f) {
            val indicatorAlpha = (255f * (1f - contentExitProgress)).roundToInt()
            drawScrollIndicator(canvas, geometry, indicatorAlpha)
        }
        canvas.restore()
    }

    private fun drawSectionRow(
        canvas: Canvas,
        title: String,
        left: Float,
        right: Float,
        centerX: Float,
        centerY: Float,
        alpha: Int,
    ) {
        val previousDividerAlpha = sectionDividerPaint.alpha
        val previousTextAlpha = sectionTextPaint.alpha
        sectionDividerPaint.alpha = multipliedAlpha(previousDividerAlpha, alpha)
        sectionTextPaint.alpha = multipliedAlpha(previousTextAlpha, alpha)
        val lineY = centerY - dp(15f)
        val inset = dp(6f)
        canvas.drawLine(left + inset, lineY, right - inset, lineY, sectionDividerPaint)
        val baseline = centerY + dp(8f) -
            (sectionTextPaint.ascent() + sectionTextPaint.descent()) / 2f
        canvas.drawText(title, centerX, baseline, sectionTextPaint)
        sectionDividerPaint.alpha = previousDividerAlpha
        sectionTextPaint.alpha = previousTextAlpha
    }

    private fun drawMotionItem(
        canvas: Canvas,
        motionIndex: Int,
        feedbackIndex: Int?,
        centerX: Float,
        centerY: Float,
        panelContentScale: Float,
        panelContentAlpha: Float,
        draw: (Int) -> Unit,
    ) {
        val baseFrame = iconFrameForElapsed(motionIndex)
        val exitProgress = contentExitProgress.coerceIn(0f, 1f)
        val launchScale = feedbackIndex?.let(::launchFeedbackScale) ?: 1f
        val frame = baseFrame.copy(
            alpha = baseFrame.alpha * panelContentAlpha,
            scale = baseFrame.scale * panelContentScale * launchScale,
            edgeOffsetDp = RailMotion.contentEdgeOffsetDp(
                entranceOffsetDp = baseFrame.edgeOffsetDp,
                exitProgress = exitProgress,
            ) * panelContentScale,
        )
        val alpha = (frame.alpha * 255f).roundToInt().coerceIn(0, 255)
        if (alpha == 0) return

        val edgeSign = if (settings.side == ShelfSide.RIGHT) 1f else -1f
        canvas.save()
        canvas.translate(edgeSign * dp(frame.edgeOffsetDp), 0f)
        canvas.scale(frame.scale, frame.scale, centerX, centerY)
        draw(alpha)
        canvas.restore()
    }

    private fun iconFrameForElapsed(index: Int): IconMotionFrame {
        if (!ValueAnimator.areAnimatorsEnabled()) {
            return RailMotion.iconFrame(
                index = index,
                elapsedMillis = contentMotionElapsedMs,
                reducedMotion = true,
            )
        }
        val localElapsed = contentMotionElapsedMs - RailMotion.iconStartDelayMillis(index)
        val localProgress = if (localElapsed <= 0L) {
            0f
        } else {
            (localElapsed.toFloat() / RailMotion.ICON_DURATION_MS).coerceIn(0f, 1f)
        }
        val progress = expandInterpolator.getInterpolation(localProgress)
        return IconMotionFrame(
            progress = progress,
            alpha = progress,
            scale = RailMotion.ICON_START_SCALE +
                (RailMotion.ICON_END_SCALE - RailMotion.ICON_START_SCALE) * progress,
            edgeOffsetDp = RailMotion.ICON_START_EDGE_OFFSET_DP * (1f - progress),
        )
    }

    private fun launchFeedbackScale(index: Int): Float {
        if (index != launchFeedbackIndex) return 1f
        val remaining = launchFeedbackUntilMs - SystemClock.uptimeMillis()
        if (remaining <= 0L) {
            launchFeedbackIndex = -1
            return 1f
        }
        postInvalidateOnAnimation()
        val elapsed = 1f - remaining.toFloat() / LAUNCH_FEEDBACK_DURATION_MS
        return 1f + 0.06f * sin((elapsed * Math.PI).toFloat()).coerceAtLeast(0f)
    }

    private fun drawRecordingControl(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        alpha: Int,
    ) {
        val active = recordingUiState == RecordingUiState.RECORDING ||
            recordingUiState == RecordingUiState.STOPPING
        val muted = recordingUiState == RecordingUiState.STARTING ||
            recordingUiState == RecordingUiState.STOPPING
        val error = recordingUiState == RecordingUiState.ERROR
        val ink = if (active || error) Color.rgb(190, 54, 66) else Color.rgb(63, 69, 86)
        val iconAlpha = multipliedAlpha(alpha, if (muted) 135 else 235)

        if (active) {
            recordingFillPaint.color = Color.rgb(190, 54, 66)
            recordingFillPaint.alpha = multipliedAlpha(alpha, 28)
            canvas.drawCircle(centerX, centerY, dp(17f), recordingFillPaint)
            recordingIconPaint.style = Paint.Style.STROKE
            recordingIconPaint.color = ink
            recordingIconPaint.alpha = iconAlpha
            recordingIconPaint.strokeWidth = dp(2f)
            canvas.drawCircle(centerX, centerY, dp(13f), recordingIconPaint)
            recordingIconPaint.style = Paint.Style.FILL
            canvas.drawRoundRect(
                RectF(
                    centerX - dp(5f),
                    centerY - dp(5f),
                    centerX + dp(5f),
                    centerY + dp(5f),
                ),
                dp(2f),
                dp(2f),
                recordingIconPaint,
            )
            recordingFillPaint.color = ink
            recordingFillPaint.alpha = iconAlpha
            canvas.drawCircle(centerX + dp(13f), centerY - dp(13f), dp(3f), recordingFillPaint)
            return
        }

        recordingIconPaint.style = Paint.Style.STROKE
        recordingIconPaint.color = ink
        recordingIconPaint.alpha = iconAlpha
        recordingIconPaint.strokeWidth = dp(2.2f)
        val capsule = RectF(
            centerX - dp(5.5f),
            centerY - dp(11f),
            centerX + dp(5.5f),
            centerY + dp(4f),
        )
        canvas.drawRoundRect(capsule, dp(5.5f), dp(5.5f), recordingIconPaint)
        val cradle = RectF(
            centerX - dp(10f),
            centerY - dp(3f),
            centerX + dp(10f),
            centerY + dp(11f),
        )
        canvas.drawArc(cradle, 0f, 180f, false, recordingIconPaint)
        canvas.drawLine(
            centerX,
            centerY + dp(11f),
            centerX,
            centerY + dp(15f),
            recordingIconPaint,
        )
        canvas.drawLine(
            centerX - dp(5f),
            centerY + dp(15f),
            centerX + dp(5f),
            centerY + dp(15f),
            recordingIconPaint,
        )
        if (error) {
            canvas.drawLine(
                centerX - dp(10f),
                centerY - dp(12f),
                centerX + dp(10f),
                centerY + dp(12f),
                recordingIconPaint,
            )
        }
    }

    private fun drawAppIcon(
        canvas: Canvas,
        icon: Drawable?,
        label: String,
        centerX: Float,
        centerY: Float,
        alpha: Int,
    ) {
        val iconSize = dp(ICON_SIZE_DP).toInt()
        if (icon != null) {
            val left = (centerX - iconSize / 2f).toInt()
            val top = (centerY - iconSize / 2f).toInt()
            icon.setBounds(left, top, left + iconSize, top + iconSize)
            val previousAlpha = icon.alpha
            icon.alpha = multipliedAlpha(previousAlpha, alpha)
            runCatching { icon.draw(canvas) }
                .onFailure {
                    val previousPlaceholderAlpha = placeholderPaint.alpha
                    placeholderPaint.alpha = multipliedAlpha(previousPlaceholderAlpha, alpha)
                    drawPlaceholder(canvas, label, centerX, centerY)
                    placeholderPaint.alpha = previousPlaceholderAlpha
                }
            icon.alpha = previousAlpha
        } else {
            val previousAlpha = placeholderPaint.alpha
            placeholderPaint.alpha = multipliedAlpha(previousAlpha, alpha)
            drawPlaceholder(canvas, label, centerX, centerY)
            placeholderPaint.alpha = previousAlpha
        }
    }

    private fun drawAddButton(canvas: Canvas, centerX: Float, centerY: Float, alpha: Int) {
        val previousAlpha = addPaint.alpha
        addPaint.alpha = multipliedAlpha(previousAlpha, alpha)
        val half = dp(8f)
        canvas.drawLine(centerX - half, centerY, centerX + half, centerY, addPaint)
        canvas.drawLine(centerX, centerY - half, centerX, centerY + half, addPaint)
        addPaint.alpha = previousAlpha
    }

    private fun drawRecentEmptyButton(canvas: Canvas, centerX: Float, centerY: Float, alpha: Int) {
        val previousBackgroundAlpha = emptyStateBackgroundPaint.alpha
        val previousLineAlpha = emptyStatePaint.alpha
        val previousBadgeAlpha = emptyStateBadgePaint.alpha
        val previousTextAlpha = emptyStateBadgeTextPaint.alpha
        emptyStateBackgroundPaint.alpha = multipliedAlpha(previousBackgroundAlpha, alpha)
        emptyStatePaint.alpha = multipliedAlpha(previousLineAlpha, alpha)
        emptyStateBadgePaint.alpha = multipliedAlpha(previousBadgeAlpha, alpha)
        emptyStateBadgeTextPaint.alpha = multipliedAlpha(previousTextAlpha, alpha)
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
        emptyStateBackgroundPaint.alpha = previousBackgroundAlpha
        emptyStatePaint.alpha = previousLineAlpha
        emptyStateBadgePaint.alpha = previousBadgeAlpha
        emptyStateBadgeTextPaint.alpha = previousTextAlpha
    }

    private fun updateAccessibilityDescription() {
        contentDescription = if (rows.any { it is EmptyRow }) {
            resources.getString(R.string.recent_empty_action_description)
        } else {
            null
        }
    }

    private fun drawLoadingIndicator(canvas: Canvas, centerX: Float, centerY: Float, alpha: Int) {
        val previousAlpha = loadingPaint.alpha
        loadingPaint.alpha = multipliedAlpha(previousAlpha, alpha)
        val dotRadius = dp(2f)
        val spacing = dp(7f)
        canvas.drawCircle(centerX - spacing, centerY, dotRadius, loadingPaint)
        canvas.drawCircle(centerX, centerY, dotRadius, loadingPaint)
        canvas.drawCircle(centerX + spacing, centerY, dotRadius, loadingPaint)
        loadingPaint.alpha = previousAlpha
    }

    private fun drawPlaceholder(canvas: Canvas, label: String, centerX: Float, centerY: Float) {
        val glyph = label.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        val baseline = centerY - (placeholderPaint.ascent() + placeholderPaint.descent()) / 2f
        canvas.drawText(glyph, centerX, baseline, placeholderPaint)
    }

    private fun drawScrollIndicator(canvas: Canvas, geometry: Geometry, alpha: Int) {
        val rowsRect = geometry.rowsRect
        val trackHeight = rowsRect.height()
        if (trackHeight <= 0f) return
        val totalHeight = rows.size * itemHeight()
        val thumbHeight = max(dp(18f), trackHeight * trackHeight / totalHeight)
        val travel = trackHeight - thumbHeight
        val maximumOffset = maxScrollOffset()
        if (maximumOffset <= 0f) return
        val thumbTop = rowsRect.top + travel * (scrollOffset / maximumOffset)
        val x = if (settings.side == ShelfSide.RIGHT) geometry.panelRect.left + dp(5f)
        else geometry.panelRect.right - dp(5f)
        handlePaint.color = Color.argb(92, 90, 98, 120)
        val previousAlpha = handlePaint.alpha
        handlePaint.alpha = multipliedAlpha(previousAlpha, alpha)
        handlePaint.strokeWidth = dp(2f)
        handlePaint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(x, thumbTop, x, thumbTop + thumbHeight, handlePaint)
        handlePaint.alpha = previousAlpha
    }

    private fun multipliedAlpha(baseAlpha: Int, animationAlpha: Int): Int =
        (baseAlpha.coerceIn(0, 255) * animationAlpha.coerceIn(0, 255) + 127) / 255

    private fun lerpFloat(start: Float, end: Float, progress: Float): Float =
        start + (end - start) * progress.coerceIn(0f, 1f)

    private fun lerpInt(start: Int, end: Int, progress: Float): Int =
        lerpFloat(start.toFloat(), end.toFloat(), progress).roundToInt()

    private fun rowIndexAt(x: Float, y: Float): Int {
        val geometry = geometry()
        if (!geometry.panelRect.contains(x, y) || !geometry.rowsRect.contains(x, y)) return -1
        return railRowIndexAt(
            localY = y - geometry.rowsRect.top,
            viewportHeight = geometry.rowsRect.height(),
            scrollOffset = scrollOffset,
            itemHeight = itemHeight(),
            rowCount = rows.size,
        )
    }

    private fun recordingHeaderContains(x: Float, y: Float): Boolean {
        val geometry = geometry()
        val header = geometry.recordingRect
        return geometry.panelRect.contains(x, y) && railHeaderContains(
            x = x,
            y = y,
            left = header.left,
            top = header.top,
            right = header.right,
            bottom = header.bottom,
        )
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
        val headerHeight = min(itemHeight(), (content.bottom - content.top).coerceAtLeast(0f))
        val rowsTop = (content.top + headerHeight).coerceAtMost(content.bottom)
        val recording = RectF(content.left, content.top, content.right, rowsTop)
        val rows = RectF(content.left, rowsTop, content.right, content.bottom)
        return Geometry(0f, height.toFloat(), panel, recording, rows)
    }

    private fun publishWindowGeometry(
        progressOverride: Float = panelProgress,
        fractionOverride: Float = verticalFraction,
    ) {
        val collapsedWidth = dp(COLLAPSED_TOUCH_WIDTH_DP)
        val width = collapsedWidth + (dp(EXPANDED_WIDTH_DP) - collapsedWidth) * progressOverride
        val railHeight = collapsedHeight() +
            (geometryExpandedHeight() - collapsedHeight()) * progressOverride
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

    private fun lockExpandedHeight(force: Boolean = false) {
        if (force || lockedExpandedHeight == null) {
            lockedExpandedHeight = expandedHeight()
        }
    }

    private fun geometryExpandedHeight(): Float = lockedExpandedHeight ?: expandedHeight()

    private fun isGeometryHeightLocked(): Boolean =
        lockedExpandedHeight != null && panelProgress > COLLAPSED_EPSILON

    private fun contentTimelineDurationMs(): Long {
        return RailMotion.CONTENT_TIMELINE_DURATION_MS
    }

    private fun expandedHeight(): Float {
        val visibleCount = rows.size.coerceAtMost(visibleRowCapacity())
        return max(
            collapsedHeight(),
            itemHeight() + visibleCount * itemHeight() + dp(CONTENT_PADDING_DP * 2f),
        )
    }

    private fun collapsedHeight(): Float = dp(COLLAPSED_HEIGHT_DP)

    private fun itemHeight(): Float = dp(ITEM_HEIGHT_DP)

    private fun maxScrollOffset(): Float = cachedMaximumScrollOffset

    private fun clampScrollOffset() {
        scrollOffset = clampRailScrollOffset(scrollOffset, maxScrollOffset())
    }

    private fun visibleRowCapacity(): Int = cachedVisibleRowCapacity

    private fun refreshScrollMetrics() {
        cachedVisibleRowCapacity = calculateVisibleRowCapacity()
        cachedMaximumScrollOffset = maxRailScrollOffset(
            rowCount = rows.size,
            visibleRowCapacity = cachedVisibleRowCapacity,
            itemHeight = itemHeight(),
        )
    }

    private fun calculateVisibleRowCapacity(): Int {
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
            reservedHeight = itemHeight().roundToInt(),
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
        val recordingRect: RectF,
        val rowsRect: RectF,
    )

    private companion object {
        const val MAX_VISIBLE_ROWS_PHONE = 6
        const val MAX_VISIBLE_ROWS_LARGE_SCREEN = 10
        const val EXPAND_THRESHOLD_DP = 24f
        const val LONG_PRESS_MS = 450L
        const val MIN_SETTLE_MS = 140L
        const val LAUNCH_FEEDBACK_DURATION_MS = 120L
        const val LAUNCH_DISPATCH_DELAY_MS = 40L
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
        const val COLLAPSED_CONTENT_SCALE =
            COLLAPSED_VISIBLE_WIDTH_DP / (EXPANDED_WIDTH_DP - CONTENT_PADDING_DP * 2f)
        const val COLLAPSED_RADIUS_DP = 4f
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
