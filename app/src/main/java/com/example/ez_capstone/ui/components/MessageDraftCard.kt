package com.example.ez_capstone.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.SmsManager
import android.widget.Toast
import androidx.core.content.ContextCompat
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ez_capstone.ui.theme.AccentEnd
import com.example.ez_capstone.ui.theme.CardBackground
import com.example.ez_capstone.ui.theme.Secondary
import com.example.ez_capstone.ui.theme.TextPrimary
import com.example.ez_capstone.ui.theme.TextSecondary

@Composable
fun MessageDraftCard(
    recipient: String,
    phone: String,
    message: String,
    method: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CardBackground.copy(alpha = 0.95f))
            .padding(16.dp)
    ) {
        Text(
            text = "메시지 전송",
            style = MaterialTheme.typography.titleLarge,
            color = Secondary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(text = "받는 사람", fontSize = 12.sp, color = TextSecondary)
        Text(text = recipient, fontSize = 15.sp, color = TextPrimary)

        Spacer(modifier = Modifier.height(8.dp))

        Text(text = "내용", fontSize = 12.sp, color = TextSecondary)
        Text(
            text = message,
            fontSize = 15.sp,
            color = TextPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(CardBackground)
                .padding(10.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        val methodLabel = when (method) {
            "kakao", "kakaotalk" -> "카카오톡"
            else -> "문자 (SMS)"
        }
        Text(text = "전송 방법: $methodLabel", fontSize = 12.sp, color = TextSecondary)

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
                onClick = {
                    // phone이 비어있으면 버튼이 비활성(아래 enabled) → 이름을 번호로 보내는 잔재 제거
                    sendMessage(context, phone, message, method)
                    onDismiss()
                },
                enabled = phone.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentEnd),
                modifier = Modifier.weight(1f)
            ) {
                Text("전송", color = TextPrimary)
            }
        }
    }
}

private fun sendMessage(context: Context, recipient: String, message: String, method: String) {
    when (method) {
        "kakao", "kakaotalk" -> {
            // KakaoTalk 공유 인텐트
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
                setPackage("com.kakao.talk")
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                // 카카오톡 미설치시 SMS로 폴백
                sendSms(context, recipient, message)
            }
        }
        else -> sendSms(context, recipient, message)
    }
}

private fun sendSms(context: Context, recipient: String, message: String) {
    val hasPerm = ContextCompat.checkSelfPermission(
        context, Manifest.permission.SEND_SMS
    ) == PackageManager.PERMISSION_GRANTED

    if (hasPerm) {
        try {
            val smsManager = SmsManager.getDefault()
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(recipient, null, parts, null, null)
            Toast.makeText(context, "메시지를 전송했습니다", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "전송 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    } else {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$recipient")
            putExtra("sms_body", message)
        }
        context.startActivity(intent)
    }
}
