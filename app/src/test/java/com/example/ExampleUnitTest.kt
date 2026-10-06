package com.example

import com.example.jarvis.engine.NaturalLanguageParser
import com.example.jarvis.model.ActionType
import com.example.jarvis.model.ContactInfo
import com.example.jarvis.model.RiskLevel
import com.example.jarvis.model.SessionContext
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    private val parser = NaturalLanguageParser()

    private val sampleContacts = listOf(
        ContactInfo("1", "Rahul Sharma", "+919876543210", "Friend"),
        ContactInfo("2", "Rahul Verma", "+919811223344", "Work"),
        ContactInfo("3", "Mom", "+919988776655", "Family")
    )

    @Test
    fun parseHinglishCommands_createsVerifiedActionPlans() = runTest {
        val ytPlan = parser.parseCommandSuspend(
            rawInput = "Hey Jarvis, YouTube kholo",
            session = SessionContext(),
            confirmMediumRisk = true,
            agentModeEnabled = false,
            contactResolver = { emptyList() }
        )
        assertEquals(ActionType.LAUNCH_APP, ytPlan.steps.first().actionType)
        assertEquals("YouTube", ytPlan.steps.first().target)

        val volPlan = parser.parseCommandSuspend(
            rawInput = "Volume 40% kar do",
            session = SessionContext(),
            confirmMediumRisk = true,
            agentModeEnabled = false,
            contactResolver = { emptyList() }
        )
        assertEquals(ActionType.SET_VOLUME, volPlan.steps.first().actionType)
        assertEquals("40", volPlan.steps.first().value)
    }

    @Test
    fun parseAmbiguousContact_triggersFailSafeDisambiguation() = runTest {
        val plan = parser.parseCommandSuspend(
            rawInput = "WhatsApp pe Rahul ko message bhejo ki main 10 minute mein aa raha hoon",
            session = SessionContext(),
            confirmMediumRisk = true,
            agentModeEnabled = false,
            contactResolver = { query ->
                sampleContacts.filter { it.name.contains(query, ignoreCase = true) }
            }
        )
        assertTrue(plan.requiresClarification)
        assertEquals(2, plan.clarificationOptions.size)
    }

    @Test
    fun parseMultiStepAutomation_breaksIntoSequentialSteps() = runTest {
        val plan = parser.parseCommandSuspend(
            rawInput = "Chrome kholo, YouTube search karo, latest video open karo aur volume 50% kar do",
            session = SessionContext(),
            confirmMediumRisk = true,
            agentModeEnabled = false,
            contactResolver = { emptyList() }
        )
        assertTrue(plan.steps.size >= 3)
        assertEquals(ActionType.LAUNCH_APP, plan.steps.first().actionType)
        assertEquals(ActionType.SET_VOLUME, plan.steps.last().actionType)
    }

    @Test
    fun parseHighRiskCommand_requiresExplicitConfirmation() = runTest {
        val plan = parser.parseCommandSuspend(
            rawInput = "Saari files permanently delete kar do",
            session = SessionContext(),
            confirmMediumRisk = false,
            agentModeEnabled = false,
            contactResolver = { emptyList() }
        )
        assertEquals(RiskLevel.HIGH, plan.riskLevel)
        assertTrue(plan.requiresConfirmation)
    }
}
