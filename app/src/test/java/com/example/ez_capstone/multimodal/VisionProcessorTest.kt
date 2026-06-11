package com.example.ez_capstone.multimodal

import com.example.ez_capstone.agent.GeminiApiClient
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class VisionProcessorTest {

    private lateinit var apiClient: GeminiApiClient
    private lateinit var processor: VisionProcessor

    @Before
    fun setUp() {
        apiClient = mockk()
        processor = VisionProcessor(apiClient, apiKey = "test_key")
    }

    @Test
    fun `processImageBase64 returns VisionResult with extracted place`() = runTest {
        coEvery { apiClient.generateContentWithImage(any(), any(), any()) } returns
            """{"placeName":"스타벅스 강남점","address":"서울 강남구","extractedText":"STARBUCKS","confidence":0.92}"""

        val result = processor.processImageBase64("base64data==")

        assertEquals("스타벅스 강남점", result.placeName)
        assertEquals("서울 강남구", result.address)
        assertTrue(result.confidence > 0.9f)
    }

    @Test
    fun `processImageBase64 handles malformed JSON gracefully`() = runTest {
        coEvery { apiClient.generateContentWithImage(any(), any(), any()) } returns "not json"

        val result = processor.processImageBase64("base64data==")

        assertNotNull(result)
        assertEquals("not json", result.extractedText)
        assertNull(result.placeName)
    }
}
