package com.luna.music.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Velocity
import com.metrocompose.MetroRisingPageState
import com.metrocompose.metroRiseOverscroll
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Makes pushing a rising page down — the player, back into its strip — easy to start.
 *
 * The page's own detector (`metroRiseDrag`) decides the axis once, at the touch slop, by whichever
 * component of the travel is larger, and a tie goes sideways to the track pager. A thumb does not
 * move straight down: it swings in an arc from the joint, and the first few millimetres of a
 * downward push are as often 45 degrees off as not. Those pushes changed track, or sprang back,
 * instead of lowering the player — which is what "too hard to swipe down" was.
 *
 * This claims a drag for the page earlier and more generously, before that detector decides:
 *
 *  - **downward only** — upward is the page still rising, or the queue coming out over it, and
 *    stays the page detector's to decide;
 *  - after [SlopShare] of the touch slop rather than all of it;
 *  - anywhere within about 63 degrees of straight down, rather than within 45.
 *
 * A flick is enough. Letting go with any real downward speed puts the page away however little it has
 * travelled, which only works because the speed is the finger's and not the page's — see the note
 * in the detector.
 *
 * It must sit *inside* `metroRiseDrag` in the modifier chain: pointer events reach inner modifiers
 * first, so a drag claimed here is already consumed when the outer detector looks, and it stands
 * aside. Children still come first of all — the seek bar and the lyrics list keep their drags.
 *
 * The claimed drag is handed to the page through `metroRiseOverscroll`, the same public route by
 * which a list inside a page passes on the drag it cannot use: the page follows the finger, and on
 * letting go it settles from the hand's speed exactly as it does for its own gesture.
 */
fun Modifier.pushDownToCollapse(
    state: MetroRisingPageState,
    enabled: Boolean = true
): Modifier = if (!enabled) this else composed {
    val dispatcher = remember { NestedScrollDispatcher() }
    val connection = remember { object : NestedScrollConnection {} }
    val scope = rememberCoroutineScope()

    metroRiseOverscroll(state)
        .nestedScroll(connection, dispatcher)
        .pointerInput(state) {
            awaitEachGesture {
                val first = awaitFirstDown(requireUnconsumed = false)
                // Speed is measured from where the finger has *travelled*, not from where it is on the
                // page. The page moves down under the finger as it is dragged, so the finger's
                // position on the page hardly changes during a drag — and a speed worked out from
                // that came out near zero, which is why a downward flick did not close the player and
                // only a long, slow drag did. The sum of the deltas is the finger's own movement.
                val tracker = VelocityTracker()
                var fingerY = 0f
                tracker.addPosition(first.uptimeMillis, Offset(0f, fingerY))
                var travelX = 0f
                var travelY = 0f
                var claimed = false
                val slop = viewConfiguration.touchSlop * SlopShare

                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == first.id } ?: break
                    // Something under the finger took it — the seek bar, the lyrics list.
                    if (!claimed && change.isConsumed) return@awaitEachGesture
                    if (!change.pressed) break
                    val delta = change.positionChange()
                    fingerY += delta.y
                    tracker.addPosition(change.uptimeMillis, Offset(0f, fingerY))

                    if (!claimed) {
                        travelX += delta.x
                        travelY += delta.y
                        if (abs(travelX) < slop && abs(travelY) < slop) continue
                        val downward = travelY > 0f && travelY >= abs(travelX) * SteepnessRatio
                        // Not ours: leave it unconsumed for the page's own detector to lock.
                        if (!downward) return@awaitEachGesture
                        claimed = true
                        change.consume()
                        continue
                    }

                    change.consume()
                    val drag = Offset(0f, delta.y)
                    val pre = dispatcher.dispatchPreScroll(drag, NestedScrollSource.UserInput)
                    dispatcher.dispatchPostScroll(
                        consumed = pre,
                        available = drag - pre,
                        source = NestedScrollSource.UserInput
                    )
                }

                if (!claimed) return@awaitEachGesture
                val velocity = Velocity(0f, tracker.calculateVelocity().y)
                scope.launch {
                    val used = dispatcher.dispatchPreFling(velocity)
                    dispatcher.dispatchPostFling(Velocity.Zero, velocity - used)
                }
            }
        }
}

/** How much of the touch slop a downward push needs before this claims it. */
private const val SlopShare = 0.6f

/**
 * The vertical travel has to be at least this share of the horizontal: 0.5 is about 63 degrees
 * either side of straight down, where the page's own detector stops at 45.
 */
private const val SteepnessRatio = 0.5f
