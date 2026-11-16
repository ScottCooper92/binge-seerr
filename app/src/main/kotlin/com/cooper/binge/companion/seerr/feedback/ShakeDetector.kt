package com.cooper.binge.companion.seerr.feedback

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.cooper.homelab.core.feedback.ShakeFilter

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
