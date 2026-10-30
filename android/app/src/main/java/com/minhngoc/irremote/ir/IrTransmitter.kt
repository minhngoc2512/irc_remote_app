package com.minhngoc.irremote.ir

import android.content.Context
import android.hardware.ConsumerIrManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import com.minhngoc.irremote.data.IrButton

/** Thin wrapper over ConsumerIrManager that sends buttons from the code database. */
class IrTransmitter(context: Context) {
    private val ir = context.getSystemService(Context.CONSUMER_IR_SERVICE) as ConsumerIrManager?
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?

    val available: Boolean = ir?.hasIrEmitter() == true

    /** Sends one short press. Returns false if the phone cannot transmit it. */
    fun send(button: IrButton, haptic: Boolean = true): Boolean = transmit(button.freq, button.pattern, haptic)

    /** Sends the hold-to-repeat frame (falls back to the full press pattern). */
    fun sendRepeat(button: IrButton): Boolean = transmit(button.freq, button.repeat ?: button.pattern, false)

    private fun transmit(freq: Int, pattern: IntArray, haptic: Boolean): Boolean {
        val manager = ir ?: return false
        if (!available || pattern.isEmpty()) return false
        if (haptic) vibrator?.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
        return try {
            manager.transmit(supportedFrequency(manager, freq), pattern)
            true
        } catch (e: RuntimeException) {
            Log.w(TAG, "transmit failed at $freq Hz, ${pattern.size} timings", e)
            false
        }
    }

    /** Clamps the carrier into a range the emitter reports; receivers tolerate a few kHz of error. */
    private fun supportedFrequency(manager: ConsumerIrManager, freq: Int): Int {
        val ranges = manager.carrierFrequencies ?: return freq
        if (ranges.isEmpty() || ranges.any { freq in it.minFrequency..it.maxFrequency }) return freq
        return ranges.map { freq.coerceIn(it.minFrequency, it.maxFrequency) }.minBy { kotlin.math.abs(it - freq) }
    }

    companion object {
        private const val TAG = "IrTransmitter"
    }
}
