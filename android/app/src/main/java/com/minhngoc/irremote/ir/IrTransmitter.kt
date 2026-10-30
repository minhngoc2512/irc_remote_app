package com.minhngoc.irremote.ir

import android.content.Context
import android.hardware.ConsumerIrManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import com.minhngoc.irremote.data.IrButton
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thin wrapper over ConsumerIrManager that sends buttons from the code database.
 *
 * transmit() blocks for as long as the signal lasts (≈ 0.4 s for an A/C state),
 * so signals are sent on one background thread, in the order they were pressed.
 */
class IrTransmitter(context: Context) {
    private val ir = context.getSystemService(Context.CONSUMER_IR_SERVICE) as ConsumerIrManager?
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "ir-transmit") }
    private val repeatPending = AtomicBoolean(false)

    val available: Boolean = ir?.hasIrEmitter() == true

    init {
        Log.i(TAG, "emitter=$available carriers=" + ir?.carrierFrequencies?.joinToString { "${it.minFrequency}-${it.maxFrequency}" })
    }

    /** Queues one short press. Returns false if the phone cannot transmit at all. */
    fun send(button: IrButton, haptic: Boolean = true): Boolean {
        if (!available || button.pattern.isEmpty()) return false
        if (haptic) vibrator?.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
        worker.execute { transmit(button.freq, button.pattern, "${button.label} [${button.params ?: button.funcKey}]") }
        return true
    }

    /** Queues the hold-to-repeat frame unless the previous one is still waiting, so holds never pile up. */
    fun sendRepeat(button: IrButton): Boolean {
        if (!available) return false
        if (repeatPending.compareAndSet(false, true)) {
            worker.execute {
                repeatPending.set(false)
                transmit(button.freq, button.repeat ?: button.pattern, "${button.label} (repeat)")
            }
        }
        return true
    }

    private fun transmit(freq: Int, pattern: IntArray, what: String) {
        val manager = ir ?: return
        try {
            val carrier = supportedFrequency(manager, freq)
            Log.i(TAG, "send $what: $carrier Hz, ${pattern.size} timings, ${pattern.sum() / 1000} ms")
            manager.transmit(carrier, pattern)
        } catch (e: RuntimeException) {
            Log.w(TAG, "transmit failed at $freq Hz, ${pattern.size} timings", e)
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
