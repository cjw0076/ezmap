package com.example.ez_capstone.agent

import org.junit.Assert.*
import org.junit.Test

/**
 * 중앙 실패 분류 시스템 잠금 테스트 — 순수 JVM.
 *
 * 검증: silent failure를 없애기 위한 단일 실패 표현이 HTTP 코드/예외를 일관되게
 * 분류하고, 하류(fallback/trace/analytics)가 읽을 error_kind를 surface하는가.
 */
class ToolFailureTest {

    @Test fun `HTTP 401_403은 AUTH로 분류`() {
        assertEquals(FailureKind.AUTH, FailureKind.fromHttp(401))
        assertEquals(FailureKind.AUTH, FailureKind.fromHttp(403))
    }

    @Test fun `HTTP 429는 RATE_LIMIT`() {
        assertEquals(FailureKind.RATE_LIMIT, FailureKind.fromHttp(429))
    }

    @Test fun `5xx는 SERVER`() {
        assertEquals(FailureKind.SERVER, FailureKind.fromHttp(500))
        assertEquals(FailureKind.SERVER, FailureKind.fromHttp(503))
    }

    @Test fun `IOException은 NETWORK로 분류`() {
        assertEquals(FailureKind.NETWORK, FailureKind.fromThrowable(java.io.IOException("timeout")))
    }

    @Test fun `ToolFailureException은 자기 kind 유지`() {
        val ex = ToolFailureException(FailureKind.MISSING_KEY, "no key")
        assertEquals(FailureKind.MISSING_KEY, FailureKind.fromThrowable(ex))
    }

    @Test fun `failureJson은 error와 error_kind를 모두 담는다`() {
        val json = failureJson(FailureKind.AUTH)
        assertTrue("error 필드 필요", json.has("error"))
        assertEquals("AUTH_FAILED", json.optString("error_kind"))
    }

    @Test fun `MISSING_KEY 메시지는 사용자에게 키 등록을 안내`() {
        // 실패가 빈값으로 위장되지 않고, 사용자가 원인(키 미설정)을 알 수 있어야 함
        assertTrue(FailureKind.MISSING_KEY.userMessage.contains("키"))
    }
}
