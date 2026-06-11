package com.example.ez_capstone.api

import com.example.ez_capstone.agent.FailureKind
import com.example.ez_capstone.agent.ToolFailureException
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 모든 API 클라이언트가 공유하는 중앙 HTTP 실행기.
 *
 * 설계 의도(Phase 2): silent failure 박멸의 "하나의 시스템"을 HTTP 계층에 둔다.
 * - 기존: 각 API가 `client.newCall(req).execute()` 후 `if(!isSuccessful) return emptyList()`로
 *   실패를 삼킴 → 호출자가 "데이터 없음"과 "실패"를 구분 못함.
 * - 이 헬퍼를 쓰면 실패(HTTP 에러/네트워크/빈응답)가 자동으로 [ToolFailureException]으로
 *   분류·전파되고, ToolExecutor 중앙 catch가 {error, error_kind}로 surface한다.
 * - 새 API도 이 한 줄(`client.getBody(req)`)만 쓰면 실패 처리가 공짜로 따라온다.
 *   (엣지케이스 도돌이표 X, 각 API에 try/catch 중복 X)
 *
 * 주의: 200이지만 결과가 비어있는 경우(예: 주변에 주유소 0개)는 '정당한 빈 결과'이므로
 * 여기서 던지지 않는다 — 그건 각 파서가 emptyList로 반환(성공). 여기서 던지는 건
 * '호출 자체가 실패'한 경우뿐.
 */
fun OkHttpClient.getBody(request: Request): String {
    val response = try {
        newCall(request).execute()
    } catch (e: java.io.IOException) {
        throw ToolFailureException(FailureKind.NETWORK, e.message)
    }
    response.use { resp ->
        val body = resp.body?.string()
        if (!resp.isSuccessful) {
            throw ToolFailureException(
                FailureKind.fromHttp(resp.code),
                "HTTP ${resp.code}: ${body?.take(120) ?: ""}"
            )
        }
        return body ?: throw ToolFailureException(FailureKind.UPSTREAM, "빈 응답")
    }
}
