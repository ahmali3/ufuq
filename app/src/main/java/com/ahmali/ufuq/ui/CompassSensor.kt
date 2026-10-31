package com.ahmali.ufuq.ui

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Phone heading from true north. Uses the rotation-vector sensor, falling back to
 * accelerometer + magnetometer. True north comes from the offline geomagnetic model.
 * Listens only while started (i.e. while the Qibla screen is visible).
 */
class CompassSensor(private val context: Context, private val onUpdate: (heading: Float, accuracy: Int) -> Unit) :
    SensorEventListener {

    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val rotation = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accel = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnet = sm?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    val available: Boolean get() = magnet != null && (rotation != null || accel != null)

    private var declination = 0f
    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var haveG = false
    private var haveM = false
    private val r = FloatArray(9)
    private val orientation = FloatArray(3)
    private var magAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
    private var smoothSin = 0.0
    private var smoothCos = 1.0
    private var started = false

    fun setLocation(latitude: Double, longitude: Double) {
        declination = GeomagneticField(
            latitude.toFloat(), longitude.toFloat(), 0f, System.currentTimeMillis()
        ).declination
    }

    fun start() {
        if (started || sm == null || !available) return
        started = true
        haveG = false
        haveM = false
        if (rotation != null) {
            // GAME rate (~50 Hz) so the dial has fresh readings for every frame it animates.
            sm.registerListener(this, rotation, SensorManager.SENSOR_DELAY_GAME)
            // Magnetometer only for its accuracy reports (calibration prompt).
            sm.registerListener(this, magnet, SensorManager.SENSOR_DELAY_NORMAL)
        } else {
            sm.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME)
            sm.registerListener(this, magnet, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        if (!started) return
        started = false
        sm?.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD) magAccuracy = accuracy
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(r, event.values)
                publish()
            }
            Sensor.TYPE_ACCELEROMETER -> {
                lowPass(event.values, gravity, haveG); haveG = true
                if (haveM && SensorManager.getRotationMatrix(r, null, gravity, geomagnetic)) publish()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                magAccuracy = event.accuracy
                if (rotation == null) {
                    lowPass(event.values, geomagnetic, haveM); haveM = true
                }
            }
        }
    }

    private fun lowPass(input: FloatArray, out: FloatArray, primed: Boolean) {
        val a = if (primed) 0.1f else 1f
        for (i in 0..2) out[i] = out[i] + a * (input[i] - out[i])
    }

    private fun publish() {
        SensorManager.getOrientation(r, orientation)
        var heading = Math.toDegrees(orientation[0].toDouble()) + declination + displayRotation()
        heading = (heading + 360.0) % 360.0
        // Light noise filter on the unit circle (so 359° -> 1° doesn't spin the dial). The view
        // does the visible smoothing, frame by frame.
        val rad = Math.toRadians(heading)
        smoothSin += 0.25 * (sin(rad) - smoothSin)
        smoothCos += 0.25 * (cos(rad) - smoothCos)
        val smoothed = (Math.toDegrees(atan2(smoothSin, smoothCos)) + 360.0) % 360.0
        onUpdate(smoothed.toFloat(), magAccuracy)
    }

    @Suppress("DEPRECATION")
    private fun displayRotation(): Int {
        val rot = try {
            if (Build.VERSION.SDK_INT >= 30) {
                context.display?.rotation ?: Surface.ROTATION_0
            } else {
                (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
            }
        } catch (e: Exception) {
            Surface.ROTATION_0
        }
        return when (rot) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    }
}
