package com.example.ez_capstone.ui.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ez_capstone.ui.components.GlassCard
import com.example.ez_capstone.ui.theme.*

data class HourlyForecast(
    val hour: Int,         // 9, 10, 11...
    val icon: String,      // "☀", "⛅", "🌧"
    val tempC: Int
)

/**
 * WEATHER_WIDGET — 현재 날씨 + 6시간 예보 가로 스크롤.
 */
@Composable
fun WeatherWidgetCard(
    currentTemp: Int,
    condition: String,           // "맑음", "흐림"
    feelsLike: Int,
    humidity: Int,
    airQuality: String?,         // "좋음", "나쁨"
    hourly: List<HourlyForecast>,
    alertMessage: String? = null, // "12시부터 비 시작"
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        glowColor = Accent,
        glowEnabled = false,
        borderAlpha = 0.1f
    ) {
        Column {
            // 드래그 핸들
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 12.dp)
                    .width(32.dp).height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(TextDim)
            )

            // 현재 날씨 (큰 글씨)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$currentTemp°C", color = TextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(12.dp))
                Text(condition, color = TextSecondary, fontSize = 16.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "체감 ${feelsLike}° · 습도 ${humidity}%${airQuality?.let { " · 미세먼지 $it" } ?: ""}",
                color = TextSecondary, fontSize = 12.sp
            )

            Spacer(Modifier.height(16.dp))

            // 시간별 예보
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                hourly.forEach { h ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${h.hour}시", color = TextSecondary, fontSize = 11.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(h.icon, fontSize = 20.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("${h.tempC}°", color = TextPrimary, fontSize = 13.sp)
                    }
                }
            }

            // 주의 포인트
            if (alertMessage != null) {
                Spacer(Modifier.height(12.dp))
                Text("⚠ $alertMessage", color = Warning, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
