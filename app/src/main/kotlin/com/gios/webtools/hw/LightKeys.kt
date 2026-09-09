package com.gios.webtools.hw

import android.view.KeyEvent

/**
 * The Light Phone III's physical controls, recognised from an ordinary [KeyEvent].
 *
 * Light patched `/system/usr/keylayout/Generic.kl`, so the wheel and the camera button arrive at
 * the focused window as normal key events. `WHEEL_CW`, `WHEEL_CCW` and `WHEEL_CLICK` are not AOSP
 * keycodes, so resolve them by name and fall back to the scancodes, which come from the hardware.
 *
 * | Control                    | scancode | label         |
 * |----------------------------|----------|---------------|
 * | Wheel one way              | 19       | `WHEEL_CCW`   |
 * | Wheel other way            | 20       | `WHEEL_CW`    |
 * | Wheel press (flashlight)   | 66       | `WHEEL_CLICK` |
 * | Camera button, first stage | 80       | `FOCUS`       |
 * | Camera button, second      | 27       | `CAMERA`      |
 */
enum class LightKey { WheelUp, WheelDown, WheelClick, Camera, Focus }

object LightKeys {
    private const val SCAN_WHEEL_CCW = 19
    private const val SCAN_WHEEL_CW = 20
    private const val SCAN_WHEEL_CLICK = 66
    private const val SCAN_CAMERA = 27
    private const val SCAN_FOCUS = 80

    private val wheelCwCode: Int by lazy { codeOf("WHEEL_CW") }
    private val wheelCcwCode: Int by lazy { codeOf("WHEEL_CCW") }
    private val wheelClickCode: Int by lazy { codeOf("WHEEL_CLICK") }

    /** `keyCodeFromString` answers 0 for an unknown label, and 0 is a real keycode; map to -1. */
    private fun codeOf(label: String): Int {
        val code = runCatching { KeyEvent.keyCodeFromString(label) }.getOrDefault(KeyEvent.KEYCODE_UNKNOWN)
        return if (code == KeyEvent.KEYCODE_UNKNOWN) -1 else code
    }

    fun of(event: KeyEvent): LightKey? {
        when (event.keyCode) {
            wheelCwCode -> return LightKey.WheelUp
            wheelCcwCode -> return LightKey.WheelDown
            wheelClickCode -> return LightKey.WheelClick
        }
        return when (event.scanCode) {
            SCAN_WHEEL_CW -> LightKey.WheelUp
            SCAN_WHEEL_CCW -> LightKey.WheelDown
            SCAN_WHEEL_CLICK -> LightKey.WheelClick
            SCAN_CAMERA -> LightKey.Camera
            SCAN_FOCUS -> LightKey.Focus
            else -> null
        }
    }
}
