package com.example.ez_capstone.profile

import android.content.SharedPreferences
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MultiProfileManagerTest {

    private val prefs: SharedPreferences = mockk(relaxed = true)
    private val editor: SharedPreferences.Editor = mockk(relaxed = true)
    private lateinit var manager: MultiProfileManager

    @Before
    fun setup() {
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.apply() } just Runs
    }

    @Test
    fun `default profile is OWNER with all features enabled`() {
        every { prefs.getString(any(), any()) } returns ProfileType.OWNER.name
        manager = MultiProfileManager(prefs)
        val p = manager.getActiveProfile()
        assertEquals(ProfileType.OWNER, p.type)
        assertTrue(p.memoryEnabled)
        assertTrue(p.proactiveEnabled)
        assertTrue(p.blockedSkills.isEmpty())
    }

    @Test
    fun `GUEST profile disables memory and proactive`() {
        every { prefs.getString(any(), any()) } returns ProfileType.GUEST.name
        manager = MultiProfileManager(prefs)
        val p = manager.getActiveProfile()
        assertFalse(p.memoryEnabled)
        assertFalse(p.proactiveEnabled)
    }

    @Test
    fun `VALET profile blocks personal skills`() {
        every { prefs.getString(any(), any()) } returns ProfileType.VALET.name
        manager = MultiProfileManager(prefs)
        val p = manager.getActiveProfile()
        assertTrue("get_schedule" in p.blockedSkills)
        assertTrue("manage_contacts" in p.blockedSkills)
        assertFalse("get_directions" in p.blockedSkills)
    }

    @Test
    fun `setProfileType saves to prefs`() {
        every { prefs.getString(any(), any()) } returns ProfileType.OWNER.name
        manager = MultiProfileManager(prefs)
        manager.setProfileType(ProfileType.GUEST)
        verify { editor.putString(any(), ProfileType.GUEST.name) }
    }
}
