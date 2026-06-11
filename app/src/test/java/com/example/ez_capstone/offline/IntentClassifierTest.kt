package com.example.ez_capstone.offline

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class IntentClassifierTest {

    private lateinit var classifier: IntentClassifier

    @Before
    fun setUp() {
        classifier = IntentClassifier()
    }

    @Test
    fun `집 가자 returns NAVIGATE_HOME`() {
        assertEquals(OfflineIntent.NAVIGATE_HOME, classifier.classify("집 가자"))
    }

    @Test
    fun `회사 가자 returns NAVIGATE_WORK`() {
        assertEquals(OfflineIntent.NAVIGATE_WORK, classifier.classify("회사 출근해야 해"))
    }

    @Test
    fun `날씨 어때 returns CHECK_WEATHER`() {
        assertEquals(OfflineIntent.CHECK_WEATHER, classifier.classify("오늘 날씨 어때"))
    }

    @Test
    fun `unknown input returns GENERAL_CHAT`() {
        assertEquals(OfflineIntent.GENERAL_CHAT, classifier.classify("치킨 맛있어?"))
    }

    @Test
    fun `case insensitive HOME match`() {
        assertEquals(OfflineIntent.NAVIGATE_HOME, classifier.classify("HOME 가고 싶어"))
    }
}
