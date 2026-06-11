package com.example.ez_capstone.helpers

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class MessageDraft(
        val recipient: String,
        val phone: String,
        val message: String,
        val method: String  // "sms", "kakaotalk"
    )

    /** SMS 전송 Intent (사용자 확인 필수) */
    fun createSmsIntent(phone: String, message: String): Intent =
        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone")).apply {
            putExtra("sms_body", message)
        }

    /** 카카오톡 전송 Intent (설치 여부 확인 후 사용) */
    fun createKakaoTalkIntent(message: String): Intent? {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
            setPackage("com.kakao.talk")
        }
        return if (intent.resolveActivity(context.packageManager) != null) intent else null
    }

    /** 범용 메시지 공유 Intent */
    fun createShareIntent(message: String): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
        }

    fun isKakaoTalkInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo("com.kakao.talk", 0)
            true
        } catch (_: Exception) {
            false
        }
    }
}
