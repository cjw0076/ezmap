package com.example.ez_capstone.ui.components

import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import com.example.ez_capstone.ui.theme.Accent
import com.example.ez_capstone.ui.theme.EzEffects
import com.example.ez_capstone.ui.theme.EzSpacing
import com.example.ez_capstone.ui.theme.SurfaceHigh
import com.example.ez_capstone.ui.theme.SurfaceTop

private val HOLO_AGSL = """
    uniform float2 resolution;
    uniform float time;
    uniform shader content;
    half4 main(float2 coord) {
        half4 base = content.eval(coord);
        float2 uv = coord / resolution;
        float wave = sin(uv.x * 8.0 + time * 2.5) * sin(uv.y * 6.0 - time * 1.8);
        float3 holo = float3(
            sin(wave + 0.0) * 0.06,
            sin(wave + 2.094) * 0.04,
            sin(wave + 4.189) * 0.07
        );
        return half4(base.rgb + half3(holo), base.a);
    }
""".trimIndent()

/**
 * OBSIDIAN Glass Card — 그래디언트 배경 + 정제된 보더 + 은은한 글로우.
 *
 * @param holoEnabled API 33+ 에서 홀로그래픽 빛 굴절 셰이더 적용.
 * @param parallaxEnabled 자이로스코프 기울기에 따른 3D 시차 회전 (센서 없으면 무효).
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    glowColor: Color = Accent,
    glowEnabled: Boolean = true,
    borderAlpha: Float = EzEffects.cardBorderAlpha,
    backgroundAlpha: Float = EzEffects.cardBackgroundAlpha,
    cornerRadius: Dp = EzSpacing.cardRadius,
    contentPadding: Dp = EzSpacing.cardPadding,
    holoEnabled: Boolean = false,
    parallaxEnabled: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)

    // 보더 펄스 애니메이션
    val glowTransition = rememberInfiniteTransition(label = "obsidian_glow")
    val animatedBorderAlpha by glowTransition.animateFloat(
        initialValue = borderAlpha * 0.7f,
        targetValue = borderAlpha * 1.3f,
        animationSpec = infiniteRepeatable(tween(2500), RepeatMode.Reverse),
        label = "border_pulse"
    )

    // AGSL 홀로그램 시간 uniform (API 33+ 조건부 사용)
    val holoTransition = rememberInfiniteTransition(label = "holo")
    val holoTime by holoTransition.animateFloat(
        initialValue = 0f, targetValue = 100f,
        animationSpec = infiniteRepeatable(tween(100_000, easing = LinearEasing)),
        label = "holoTime"
    )

    // 자이로스코프 기울기 (parallaxEnabled=false이면 센서 비활성)
    val tilt = rememberGyroscopeTilt(active = parallaxEnabled)

    // API 33+ 셰이더 (한 번만 생성)
    val holoShader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RuntimeShader(HOLO_AGSL)
        else null
    }

    Box(
        modifier = modifier
            .clip(shape)
            .drawBehind {
                if (glowEnabled) {
                    drawRoundRect(
                        color = glowColor.copy(alpha = EzEffects.cardGlowAlpha),
                        topLeft = Offset(-EzSpacing.glowInset.toPx(), -EzSpacing.glowInset.toPx()),
                        size = Size(
                            size.width + EzSpacing.glowInset.toPx() * 2,
                            size.height + EzSpacing.glowInset.toPx() * 2
                        ),
                        cornerRadius = CornerRadius((cornerRadius + EzSpacing.glowInset).toPx())
                    )
                }
            }
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        SurfaceHigh.copy(alpha = backgroundAlpha),
                        SurfaceTop.copy(alpha = backgroundAlpha * 0.8f)
                    )
                ),
                shape = shape
            )
            .border(
                width = EzSpacing.hairlineBorder,
                color = glowColor.copy(alpha = if (glowEnabled) animatedBorderAlpha.coerceIn(0f, 1f) else borderAlpha),
                shape = shape
            )
            // AGSL 홀로그램 (API 33+, holoEnabled=true)
            .then(
                if (holoEnabled && holoShader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Modifier.graphicsLayer {
                        holoShader.setFloatUniform("time", holoTime)
                        holoShader.setFloatUniform("resolution", size.width, size.height)
                        renderEffect = AndroidRenderEffect
                            .createRuntimeShaderEffect(holoShader, "content")
                            .asComposeRenderEffect()
                    }
                } else Modifier
            )
            // 자이로스코프 패럴렉스 (parallaxEnabled=true)
            .then(
                if (parallaxEnabled) Modifier.gyroscopeParallax(tilt)
                else Modifier
            )
            .padding(contentPadding),
        content = content
    )
}
