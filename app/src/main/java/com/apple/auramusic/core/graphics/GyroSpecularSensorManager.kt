package com.apple.auramusic.core.graphics

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class GyroSpecularSensorManager constructor(
    context: Context
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    // Default top-left 45-degree specular light vector when stationary
    private var filteredLightX = -0.65f
    private var filteredLightY = -0.75f
    private val lowPassAlpha = 0.15f

    private val _lightDirection = MutableStateFlow(Offset(filteredLightX, filteredLightY))
    val lightDirection: StateFlow<Offset> = _lightDirection.asStateFlow()

    private var activeListeners = 0

    @Synchronized
    fun startListening() {
        activeListeners++
        if (activeListeners == 1 && rotationSensor != null) {
            sensorManager.registerListener(
                this,
                rotationSensor,
                SensorManager.SENSOR_DELAY_GAME
            )
        }
    }

    @Synchronized
    fun stopListening() {
        activeListeners = (activeListeners - 1).coerceAtLeast(0)
        if (activeListeners == 0) {
            sensorManager.unregisterListener(this)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        val pitch = orientationAngles[1] // [-PI/2, PI/2]
        val roll = orientationAngles[2]  // [-PI, PI]

        // Map roll and pitch to normalized 2D light vector on screen plane
        val rawX = sin(roll).coerceIn(-1f, 1f) - 0.25f
        val rawY = (-cos(pitch) * 0.85f).coerceIn(-1f, 1f) - 0.35f

        filteredLightX += lowPassAlpha * (rawX - filteredLightX)
        filteredLightY += lowPassAlpha * (rawY - filteredLightY)

        val mag = sqrt(filteredLightX * filteredLightX + filteredLightY * filteredLightY)
            .coerceAtLeast(0.001f)
        _lightDirection.value = Offset(filteredLightX / mag, filteredLightY / mag)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

@Composable
fun rememberGyroLightDirection(sensorManager: GyroSpecularSensorManager): State<Offset> {
    val state = sensorManager.lightDirection.collectAsState()
    DisposableEffect(sensorManager) {
        sensorManager.startListening()
        onDispose {
            sensorManager.stopListening()
        }
    }
    return state
}
