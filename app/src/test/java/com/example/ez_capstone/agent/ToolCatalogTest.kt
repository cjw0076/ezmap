package com.example.ez_capstone.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 도구 등급 분류 게이트.
 * 핵심: 등록된 모든 도구가 READ/WRITE/SENSITIVE로 분류돼 있어야 한다.
 * 새 도구를 추가하고 분류를 빠뜨리면 이 테스트가 빌드를 실패시킨다 → "전체 접근"이 항상 통제됨.
 */
class ToolCatalogTest {

    @Test fun `등록된 모든 도구가 등급 분류돼 있다`() {
        val unclassified = ToolDeclarations.allToolNames().filter { it !in ToolCatalog.KIND }
        assertTrue("미분류 도구(ToolCatalog.KIND에 추가 필요): $unclassified", unclassified.isEmpty())
    }

    @Test fun `읽기 도구는 READ`() {
        assertEquals(ToolKind.READ, ToolCatalog.kindOf("lookup_contact"))
        assertEquals(ToolKind.READ, ToolCatalog.kindOf("get_schedule"))
        assertEquals(ToolKind.READ, ToolCatalog.kindOf("search_places"))
    }

    @Test fun `로컬 변경은 WRITE`() {
        assertEquals(ToolKind.WRITE, ToolCatalog.kindOf("manage_contacts"))
        assertEquals(ToolKind.WRITE, ToolCatalog.kindOf("create_schedule"))
        assertEquals(ToolKind.WRITE, ToolCatalog.kindOf("manage_favorites"))
    }

    @Test fun `외부 부작용은 SENSITIVE이고 확인 필요`() {
        assertEquals(ToolKind.SENSITIVE, ToolCatalog.kindOf("send_message"))
        assertEquals(ToolKind.SENSITIVE, ToolCatalog.kindOf("make_call"))
        assertTrue(ToolCatalog.requiresConfirmation("send_message"))
        assertTrue(ToolCatalog.requiresConfirmation("make_call"))
        assertEquals(false, ToolCatalog.requiresConfirmation("get_schedule"))
    }

    @Test fun `미분류 이름은 보수적으로 SENSITIVE 처리한다`() {
        assertEquals(ToolKind.SENSITIVE, ToolCatalog.kindOf("nonexistent_tool"))
        assertTrue(ToolCatalog.requiresConfirmation("nonexistent_tool"))
    }

    @Test fun `STATE_CHANGING은 READ를 포함하지 않는다`() {
        assertTrue(ToolCatalog.STATE_CHANGING.none { ToolCatalog.kindOf(it) == ToolKind.READ })
        assertTrue("send_message" in ToolCatalog.STATE_CHANGING)
        assertTrue("lookup_contact" !in ToolCatalog.STATE_CHANGING)
    }
}
