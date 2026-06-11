package com.example.ez_capstone.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.example.ez_capstone.ui.theme.Accent
import com.example.ez_capstone.ui.theme.AccentDim
import com.example.ez_capstone.ui.theme.TextPrimary
import kotlinx.coroutines.delay

@Composable
fun AgentBubble(
    text: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    autoDismissMs: Long = 4000L
) {
    var visible by remember { mutableStateOf(true) }

    LaunchedEffect(text) {
        visible = true
        delay(autoDismissMs)
        visible = false
        delay(300)
        onDismiss()
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
    ) {
        GlassCard(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            glowColor = Accent,
            backgroundAlpha = 0.7f,
            contentPadding = 14.dp
        ) {
            Row(verticalAlignment = Alignment.Top) {
                // Agent orb indicator
                Canvas(modifier = Modifier.size(10.dp).padding(top = 2.dp)) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Accent, AccentDim),
                            radius = size.minDimension / 2
                        ),
                        radius = size.minDimension / 2
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary
                )
            }
        }
    }
}
