package com.gios.webtools.hw

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * A bus of wheel notches, published by the activity's `dispatchKeyEvent` and collected by
 * whatever screen is up. Buffered with DROP_OLDEST so a notch with nobody listening is dropped
 * rather than blocking the main thread.
 */
class WheelBus {
    private val _notches = MutableSharedFlow<Int>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val notches: SharedFlow<Int> = _notches

    /** +1 for one notch up, -1 for one notch down. */
    fun send(delta: Int) {
        _notches.tryEmit(delta)
    }
}

val LocalWheelBus = staticCompositionLocalOf { WheelBus() }

/** Scrolls [state] by a fixed distance per notch. Keyed on state, never on a lambda. */
@Composable
fun WheelScroll(state: ScrollableState, pixelsPerNotch: Float = 220f) {
    val bus = LocalWheelBus.current
    LaunchedEffect(state, bus) {
        bus.notches.collect { delta -> state.animateScrollBy(-delta * pixelsPerNotch) }
    }
}

/** The wheel as a stepper. The collector survives recomposition; the lambda stays current. */
@Composable
fun WheelSteps(onStep: (Int) -> Unit) {
    val bus = LocalWheelBus.current
    val current by rememberUpdatedState(onStep)
    LaunchedEffect(bus) {
        bus.notches.collect { delta -> current(delta) }
    }
}
