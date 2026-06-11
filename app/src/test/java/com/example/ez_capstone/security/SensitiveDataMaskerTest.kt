package com.example.ez_capstone.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class SensitiveDataMaskerTest {
    private lateinit var masker: SensitiveDataMasker

    @Before
    fun setup() { masker = SensitiveDataMasker() }

    @Test
    fun `phone number is masked`() {
        val result = masker.mask("번호는 010-1234-5678이에요")
        assertFalse(result.contains("1234"))
        assert(result.contains("****"))
    }

    @Test
    fun `card number is masked`() {
        val result = masker.mask("카드번호 1234-5678-9012-3456")
        assertFalse(result.contains("5678"))
        assert(result.contains("****"))
    }

    @Test
    fun `resident registration number is masked`() {
        val result = masker.mask("주민번호 900101-1234567")
        assertFalse(result.contains("1234567"))
        assert(result.contains("*"))
    }

    @Test
    fun `normal text is not modified`() {
        val text = "강남역 맛집 찾아줘"
        assertEquals(text, masker.mask(text))
    }

    @Test
    fun `multiple sensitive data in one string`() {
        val text = "연락처 010-9999-8888, 카드 1111-2222-3333-4444"
        val result = masker.mask(text)
        assertFalse(result.contains("9999"))
        assertFalse(result.contains("2222"))
    }
}
