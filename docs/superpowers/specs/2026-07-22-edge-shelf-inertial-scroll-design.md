# Edge Shelf Inertial Scrolling Design

## Status

- Date: 2026-07-22
- Status: Approved through the user's standing instruction to use the recommended option
- Scope: Vertical scrolling inside the expanded edge rail

## Problem

The current list follows the finger one-to-one during `ACTION_MOVE`, then stops immediately
on `ACTION_UP`. This zero-velocity release feels like excessive damping even though the drag
itself is direct.

## Decision

Use Android's platform `VelocityTracker` and `OverScroller` with default friction.

- Dragging remains one-to-one with the active pointer.
- A release above the platform minimum velocity starts a bounded spline fling.
- A slow release stops at the current offset.
- The list never overscrolls, bounces, or snaps to rows.
- Touching during a fling captures the current position and stops it without jumping.
- The same behavior applies on phones and tablets; platform density and velocity thresholds
  provide device-appropriate tuning.

Alternatives considered:

1. Hand-written exponential decay: flexible, but duplicates platform physics and is harder to
   keep consistent across refresh rates.
2. Custom spring/decay animation: expressive, but bounce and row settling conflict with the
   quiet utility character of the rail.
3. Platform `OverScroller`: selected for native spline deceleration, stable bounds, and proven
   lifecycle behavior without another dependency.

## Architecture

`RailScrollPhysics.kt` owns pure calculations for gesture qualification, drag offset, velocity
direction and clamping, and boundary eligibility. These functions stay Android-free so JVM
tests can cover the contract.

`EdgeRailView` owns the Android runtime objects:

- `VelocityTracker` records the active gesture.
- `OverScroller` advances inertial offset from `computeScroll()` callbacks.
- `activePointerId` keeps multi-touch transitions continuous.
- Existing `scrollOffset` remains the single rendering and hit-testing source of truth.

No `requestLayout()` or `WindowManager` geometry update is issued during list scrolling. Only
`postInvalidateOnAnimation()` schedules the next frame.

## Interaction Flow

1. `ACTION_DOWN` captures the active pointer and stops any running fling at its current frame.
2. A vertical movement beyond touch slop starts list scrolling only when content exceeds the
   viewport.
3. `ACTION_MOVE` applies raw finger delta with no damping multiplier.
4. `ACTION_UP` converts finger velocity to content velocity by reversing its Y direction and
   starts a bounded fling when it exceeds the platform threshold.
5. `computeScroll()` publishes each platform spline position until the fling finishes.
6. `ACTION_CANCEL`, collapse, row replacement, mode replacement, system hide, and detach stop
   the fling and release velocity tracking.

## Accessibility And Failure Behavior

Inertial scrolling is essential navigation feedback rather than decorative motion, so it does
not depend on `ValueAnimator.areAnimatorsEnabled()`. Invalid pointers or non-finite values cancel
the gesture safely. A reduced or empty list clamps to zero and cannot start a fling.

## Verification

JVM tests cover gesture qualification, delta direction, velocity threshold and cap, non-finite
inputs, and top/bottom boundary eligibility.

ADB-only device checks on Xiaomi Pad 7S Pro cover:

- fast upward and downward flings continuing after touch release;
- slow releases stopping without drift;
- top and bottom bounds without bounce;
- touching during a fling without a jump or accidental launch;
- collapse and reopen resetting to the top;
- repeated flings with `gfxinfo framestats` and screen-recorded frame inspection.
