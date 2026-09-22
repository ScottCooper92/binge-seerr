package io.github.scottcooper92.binge.seerr.feedback

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

private const val SHAKE_THRESHOLD_GRAVITY = 2.7f
private const val MIN_SHAKE_COUNT = 3
private const val SHAKE_WINDOW_NS = 750_000_000L
private const val DEBOUNCE_NS = 1_500_000_000L
private const val LOW_PASS_ALPHA = 0.8f

/**
 * A deliberate shake, read from the accelerometer. The decision is [ShakeFilter]'s, so it can be
 * tested against plain samples; this class only registers for them. A device with no accelerometer,
 * a television among them, never reports one.
 */
class ShakeDetector(
    context: Context,
) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val filter = ShakeFilter()
    private var listener: (() -> Unit)? = null

    /** False where there is no accelerometer to listen to. */
    fun start(onShake: () -> Unit): Boolean {
        val sensor = accelerometer ?: return false
        val manager = sensorManager ?: return false
        listener = onShake
        filter.reset()
        return manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
        listener = null
        filter.reset()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        if (filter.onSample(event.values[0], event.values[1], event.values[2], event.timestamp)) listener?.invoke()
    }

    override fun onAccuracyChanged(
        sensor: Sensor?,
        accuracy: Int,
    ) = Unit
}

/**
 * Takes raw accelerometer samples and answers true on the one that completes a shake: gravity is
 * estimated with a low-pass filter and subtracted, three peaks over [SHAKE_THRESHOLD_GRAVITY] g
 * inside [SHAKE_WINDOW_NS] make a shake, and one shake mutes the next for [DEBOUNCE_NS].
 */
internal class ShakeFilter {
    private var gravityX = 0f
    private var gravityY = 0f
    private var gravityZ = 0f
    private val recentPeaks = ArrayDeque<Long>()
    private var lastTriggerNs: Long? = null

    fun reset() {
        gravityX = 0f
        gravityY = 0f
        gravityZ = 0f
        recentPeaks.clear()
        lastTriggerNs = null
    }

    fun onSample(
        x: Float,
        y: Float,
        z: Float,
        timestampNs: Long,
    ): Boolean {
        gravityX = LOW_PASS_ALPHA * gravityX + (1 - LOW_PASS_ALPHA) * x
        gravityY = LOW_PASS_ALPHA * gravityY + (1 - LOW_PASS_ALPHA) * y
        gravityZ = LOW_PASS_ALPHA * gravityZ + (1 - LOW_PASS_ALPHA) * z
        val linearX = x - gravityX
        val linearY = y - gravityY
        val linearZ = z - gravityZ
        val gForce = sqrt(linearX * linearX + linearY * linearY + linearZ * linearZ) / SensorManager.GRAVITY_EARTH

        val debouncing = lastTriggerNs?.let { timestampNs - it < DEBOUNCE_NS } ?: false
        if (gForce < SHAKE_THRESHOLD_GRAVITY || debouncing) return false

        while (recentPeaks.isNotEmpty() && timestampNs - recentPeaks.first() > SHAKE_WINDOW_NS) recentPeaks.removeFirst()
        recentPeaks.addLast(timestampNs)
        val triggered = recentPeaks.size >= MIN_SHAKE_COUNT
        if (triggered) {
            lastTriggerNs = timestampNs
            recentPeaks.clear()
        }
        return triggered
    }
}
