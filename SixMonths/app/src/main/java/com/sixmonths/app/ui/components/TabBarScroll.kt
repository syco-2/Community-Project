package com.sixmonths.app.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Whether the bottom tab bar is currently slid off-screen.
 *
 * The shell owns the [TabBarState]; every scrolling tab screen reports its scroll position
 * through [TabBarScrollTracker]. Scrolling down hides the bar, scrolling up or reaching the
 * top brings it back.
 */
class TabBarState {
    var hidden by mutableStateOf(false)
        internal set

    /** Lets a full-screen celebration keep the floating bar out of the way. */
    fun setHidden(value: Boolean) {
        hidden = value
    }
}

val LocalTabBarState = staticCompositionLocalOf { TabBarState() }

@Composable
fun rememberTabBarState(): TabBarState = remember { TabBarState() }

/**
 * Call once per scrolling tab screen with its [LazyListState]. Uses a small delta
 * threshold so the bar does not flicker on tiny scrolls.
 */
@Composable
fun TabBarScrollTracker(
    listState: LazyListState,
    thresholdPx: Int = 12,
) {
    val barState = LocalTabBarState.current
    LaunchedEffect(listState) {
        var previousOffset = listState.firstVisibleItemScrollOffset
        var previousIndex = listState.firstVisibleItemIndex
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                val atTop = index == 0 && offset <= thresholdPx
                val delta = offset - previousOffset
                val indexChanged = index != previousIndex
                previousOffset = offset
                previousIndex = index
                when {
                    atTop -> barState.hidden = false
                    indexChanged -> Unit
                    delta > thresholdPx -> barState.hidden = true
                    delta < -thresholdPx -> barState.hidden = false
                }
            }
    }
}

/** Grid variant, used by the monster cabinet. */
@Composable
fun TabBarScrollTracker(
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    thresholdPx: Int = 12,
) {
    val barState = LocalTabBarState.current
    LaunchedEffect(gridState) {
        var previousOffset = gridState.firstVisibleItemScrollOffset
        var previousIndex = gridState.firstVisibleItemIndex
        snapshotFlow {
            gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
        }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                val atTop = index == 0 && offset <= thresholdPx
                val delta = offset - previousOffset
                val indexChanged = index != previousIndex
                previousOffset = offset
                previousIndex = index
                when {
                    atTop -> barState.hidden = false
                    indexChanged -> Unit
                    delta > thresholdPx -> barState.hidden = true
                    delta < -thresholdPx -> barState.hidden = false
                }
            }
    }
}

/** Same idea for plain scrollable columns (details screens without a LazyList). */
@Composable
fun TabBarScrollTrackerForColumn(
    scrollState: androidx.compose.foundation.ScrollState,
    thresholdPx: Int = 12,
) {
    val barState = LocalTabBarState.current
    LaunchedEffect(scrollState) {
        var previous = scrollState.value
        snapshotFlow { scrollState.value }
            .distinctUntilChanged()
            .collect { value ->
                val delta = value - previous
                previous = value
                when {
                    value <= thresholdPx -> barState.hidden = false
                    delta > thresholdPx -> barState.hidden = true
                    delta < -thresholdPx -> barState.hidden = false
                }
            }
    }
}
