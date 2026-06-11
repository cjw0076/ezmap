package com.example.ez_capstone.agent

import com.example.ez_capstone.agent.models.AgentDrivingState
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentDrivingStateTest {

    @Test
    fun `driving aliases normalize to navigating for agent routing`() {
        assertEquals(AgentDrivingState.NAVIGATING, AgentDrivingState.normalize("driving"))
        assertEquals(AgentDrivingState.NAVIGATING, AgentDrivingState.normalize("navigating"))
        assertEquals(AgentDrivingState.NAVIGATING, AgentDrivingState.normalize("DRIVING"))
    }

    @Test
    fun `blank or unknown state normalizes to idle`() {
        assertEquals(AgentDrivingState.IDLE, AgentDrivingState.normalize(null))
        assertEquals(AgentDrivingState.IDLE, AgentDrivingState.normalize(""))
        assertEquals(AgentDrivingState.IDLE, AgentDrivingState.normalize("processing"))
    }
}
