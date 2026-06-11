package com.example.ez_capstone.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext

/**
 * 기기 회전 벡터 센서로 기울기를 측정하여 Offset(roll, pitch) 상태를 반환.
 *
 * @param active false이면 센서를 등록하지 않음 (배터리 절약).
 * @param maxTiltDeg 최대 기울기 각도 (degrees). 초과값은 clamp.
 */
@Composable
fun rememberGyroscopeTilt(
    active: Boolean = true,
    maxTiltDeg: Float = 12f
): State<Offset> {
    val tilt = remember { mutableStateOf(Offset.Zero) }
    val context = LocalContext.current

    DisposableEffect(active) {
        if (!active) return@DisposableEffect onDispose {}

        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val rotMatrix = FloatArray(9)
        val orientation = FloatArray(3)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)
                SensorManager.getOrientation(rotMatrix, orientation)
                val roll = Math.toDegrees(orientation[2].toDouble()).toFloat()
                    .coerceIn(-maxTiltDeg, maxTiltDeg)
                val pitch = Math.toDegrees(orientation[1].toDouble()).toFloat()
                    .coerceIn(-maxTiltDeg, maxTiltDeg)
                tilt.value = Offset(roll, pitch)
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }

        sensor?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        onDispose { sm.unregisterListener(listener) }
    }

    return tilt
}

/**
 * 기자이스코프 기울기에 따라 카드를 3D 시차 회전시키는 Modifier.
 *
 * @param tilt rememberGyroscopeTilt()로 얻은 기울기 상태.
 * @param depth 회전 강도 (기본 6도).
 */
fun Modifier.gyroscopeParallax(tilt: State<Offset>, depth: Float = 6f): Modifier =
    this.graphicsLayer {
        rotationX = -tilt.value.y / 12f * depth
        rotationY = tilt.value.x / 12f * depth
        cameraDistance = 8f * density
    }
