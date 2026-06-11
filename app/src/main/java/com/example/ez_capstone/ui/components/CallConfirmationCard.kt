package com.example.ez_capstone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ez_capstone.ui.theme.Accent
import com.example.ez_capstone.ui.theme.CardBackground
import com.example.ez_capstone.ui.theme.Secondary
import com.example.ez_capstone.ui.theme.TextPrimary
import com.example.ez_capstone.ui.theme.TextSecondary

@Composable
fun CallConfirmationCard(
    name: String,
    phone: String,
    onCall: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CardBackground.copy(alpha = 0.95f))
            .padding(16.dp)
    ) {
        Text(
            text = "전화 걸기",
            style = MaterialTheme.typography.titleLarge,
            color = Secondary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(text = "연락처", fontSize = 12.sp, color = TextSecondary)
        Text(text = name, fontSize = 18.sp, color = TextPrimary, fontWeight = FontWeight.Medium)

        Spacer(modifier = Modifier.height(8.dp))

        Text(text = "전화번호", fontSize = 12.sp, color = TextSecondary)
        Text(text = phone, fontSize = 15.sp, color = Accent)

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            ) {
                Text("취소", color = TextSecondary)
            }

            Button(
                onClick = onCall,
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                modifier = Modifier.weight(1f)
            ) {
                Text("전화하기", color = TextPrimary)
            }
        }
    }
}
