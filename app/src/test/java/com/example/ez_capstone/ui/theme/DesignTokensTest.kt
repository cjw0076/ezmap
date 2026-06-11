package com.example.ez_capstone.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignTokensTest {

    @Test
    fun `card tokens keep core surfaces compact and consistent`() {
        assertEquals(14f, EzSpacing.cardRadius.value)
        assertEquals(16f, EzSpacing.cardPadding.value)
        assertEquals(1f, EzSpacing.hairlineBorder.value)
        assertEquals(0.15f, EzEffects.cardBorderAlpha, 0.001f)
        assertEquals(0.95f, EzEffects.cardBackgroundAlpha, 0.001f)
    }

    @Test
    fun `mic tokens define normal and compact touch geometry`() {
        assertEquals(80f, EzMicSize.normalTouchTarget.value)
        assertEquals(56f, EzMicSize.normalCore.value)
        assertEquals(24f, EzMicSize.normalIcon.value)

        assertEquals(64f, EzMicSize.compactTouchTarget.value)
        assertEquals(48f, EzMicSize.compactCore.value)
        assertEquals(20f, EzMicSize.compactIcon.value)

        assertTrue(EzMicSize.normalTouchTarget > EzMicSize.normalCore)
        assertTrue(EzMicSize.compactTouchTarget > EzMicSize.compactCore)
    }

    @Test
    fun `motion tokens keep voice feedback quick but readable`() {
        assertEquals(300, EzMotion.ttsCooldownMs)
        assertEquals(500, EzMotion.tapPulseMs)
        assertEquals(1500, EzMotion.voiceRippleMs)
        assertEquals(1100, EzMotion.processingSpinMs)
        assertEquals(600, EzMotion.speakingPulseMs)
    }
}
