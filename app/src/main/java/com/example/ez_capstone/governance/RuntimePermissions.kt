package com.example.ez_capstone.governance

import android.Manifest

/**
 * 앱이 런타임에 요청해야 하는 dangerous 권한 전체.
 * 온보딩에서 일괄 요청하고, ConversationScreen 진입 시 누락분을 안전망으로 재요청한다.
 * (이전엔 화면별로 산발 요청 → CALL_PHONE·READ_CALENDAR·READ_CONTACTS가 영영 미요청되는 구멍이 있었음.)
 */
object RuntimePermissions {
    val ALL: Array<String> = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.CAMERA,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_CALENDAR
    )
}
