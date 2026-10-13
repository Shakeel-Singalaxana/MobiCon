package io.rojx.client.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.atan2

class SteeringSensorManager(
    context: Context,
    private val onAngleChanged: (degrees: Float, normalized: Float, axisShort: Short) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVectorSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometerSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val rotationMatrix = FloatArray(9)
    private val remappedMatrix = FloatArray(9)
    private val orientationValues = FloatArray(3)

    private var centerOffsetDeg = 0f
    private var rawAngleDeg = 0f
    private var currentFilteredDeg = 0f

    var maxWheelAngle: Float = 900f // 900° total (-450° to +450°)
    var invertSteering: Boolean = false
    var sensitivity: Float = 1.0f
    var deadzone: Float = 0.02f
    var isRegistered: Boolean = false
        private set

    fun start() {
        if (isRegistered) return

        if (rotationVectorSensor != null) {
            sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_FASTEST)
        } else if (accelerometerSensor != null) {
            sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_GAME)
        }
        isRegistered = true
    }

    fun stop() {
        if (!isRegistered) return
        sensorManager.unregisterListener(this)
        isRegistered = false
    }

    fun calibrateCenter() {
        centerOffsetDeg = rawAngleDeg
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            // Remap for landscape orientation (device held horizontally like a steering wheel)
            SensorManager.remapCoordinateSystem(
                rotationMatrix,
                SensorManager.AXIS_Y,
                SensorManager.AXIS_MINUS_X,
                remappedMatrix
            )
            SensorManager.getOrientation(remappedMatrix, orientationValues)

            // Roll angle is in orientationValues[2] (radians)
            val rollRad = orientationValues[2]
            rawAngleDeg = Math.toDegrees(rollRad.toDouble()).toFloat()
        } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val ax = event.values[0]
            val ay = event.values[1]
            // Landscape tilt angle
            val rad = atan2(ax.toDouble(), ay.toDouble())
            rawAngleDeg = (Math.toDegrees(rad).toFloat() - 90f) * 2.5f
        }

        // Apply zero calibration offset
        var relativeAngle = rawAngleDeg - centerOffsetDeg

        // Wrap around -180 to 180 or allow continuous tilt
        if (relativeAngle > 180f) relativeAngle -= 360f
        if (relativeAngle < -180f) relativeAngle += 360f

        // Apply sensitivity and scaling to full wheel range
        val halfRange = maxWheelAngle / 2f
        var scaledAngle = (relativeAngle * sensitivity * 2.5f).coerceIn(-halfRange, halfRange)

        if (invertSteering) {
            scaledAngle = -scaledAngle
        }

        // Low-pass EMA filter (smooth 10% smoothing)
        currentFilteredDeg = 0.85f * scaledAngle + 0.15f * currentFilteredDeg

        // Normalized to -1.0 .. +1.0
        var norm = (currentFilteredDeg / halfRange).coerceIn(-1.0f, 1.0f)

        // Apply Deadzone
        if (kotlin.math.abs(norm) < deadzone) {
            norm = 0f
        } else {
            val sign = if (norm > 0) 1f else -1f
            norm = sign * ((kotlin.math.abs(norm) - deadzone) / (1f - deadzone))
        }

        val axisShort = (norm * 32767f).toInt().coerceIn(-32768, 32767).toShort()
        onAngleChanged(currentFilteredDeg, norm, axisShort)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
