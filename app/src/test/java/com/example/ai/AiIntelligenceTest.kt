package com.example.ai

import com.example.data.local.entities.ContactEntity
import com.example.domain.model.ActionType
import com.example.domain.model.AiTaskProposal
import com.example.domain.model.ExecutionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiIntelligenceTest {

    private val sampleContacts = listOf(
        ContactEntity(
            id = "c1",
            name = "Rahim Chowdhury",
            phoneNumber = "+8801711223344",
            nickname = "Rahim Bhai"
        ),
        ContactEntity(
            id = "c2",
            name = "Ayesha Siddiqua",
            phoneNumber = "+8801819556677",
            nickname = "Ammu"
        ),
        ContactEntity(
            id = "c3",
            name = "Tanvir Ahmed",
            phoneNumber = "+8801912345678",
            nickname = "Boss"
        )
    )

    @Test
    fun testTaskValidatorResolvesNicknameToRealContact() {
        val rawProposal = AiTaskProposal(
            intent = "CREATE_CALL_REMINDER",
            contactName = "Ammu",
            actionType = ActionType.CALL
        )

        val validated = TaskValidator.validateProposal(rawProposal, sampleContacts)
        assertEquals("Ayesha Siddiqua", validated.contactName)
        assertEquals("+8801819556677", validated.contactPhone)
        assertEquals(ExecutionMode.REMINDER, validated.executionMode)
        assertTrue(validated.requiresConfirmation)
    }

    @Test
    fun testTaskValidatorEnforcesFutureTime() {
        val pastTime = System.currentTimeMillis() - 100000L
        val rawProposal = AiTaskProposal(
            intent = "CREATE_REMINDER",
            actionType = ActionType.REMINDER,
            scheduledTimeMillis = pastTime
        )

        val validated = TaskValidator.validateProposal(rawProposal, sampleContacts)
        assertTrue(validated.scheduledTimeMillis > System.currentTimeMillis())
    }

    @Test
    fun testTaskValidatorFlagsMissingContactForCall() {
        val rawProposal = AiTaskProposal(
            intent = "CREATE_CALL_REMINDER",
            contactName = null,
            actionType = ActionType.CALL
        )

        val validated = TaskValidator.validateProposal(rawProposal, sampleContacts)
        assertTrue(validated.missingFields.contains("contact"))
        assertTrue(validated.missingFields.contains("phone"))
        assertNotNull(validated.clarificationQuestion)
    }

    @Test
    fun testTaskValidatorFlagsMissingPhoneForCall() {
        val rawProposal = AiTaskProposal(
            intent = "CREATE_CALL_REMINDER",
            contactName = "Unknown Person",
            contactPhone = null,
            actionType = ActionType.CALL
        )

        val validated = TaskValidator.validateProposal(rawProposal, emptyList())
        assertTrue(validated.missingFields.contains("phone"))
        assertNotNull(validated.clarificationQuestion)
    }

    @Test
    fun testBanglishVoiceCommandParsing() {
        val proposal = TaskParser.parseCommand("Ammu k rat 8 tay call dao", sampleContacts)
        assertEquals(ActionType.CALL, proposal.actionType)
        assertEquals("Ayesha Siddiqua", proposal.contactName)
    }

    @Test
    fun testBanglaVoiceCommandParsing() {
        val proposal = TaskParser.parseCommand("আম্মু কে ফোন কর", sampleContacts)
        assertEquals(ActionType.CALL, proposal.actionType)
        assertEquals("Ayesha Siddiqua", proposal.contactName)
    }

    @Test
    fun testWhatsAppMessageCommandParsing() {
        val proposal = TaskParser.parseCommand("WhatsApp boss I am submitting the report now", sampleContacts)
        assertEquals(ActionType.WHATSAPP, proposal.actionType)
        assertEquals("Tanvir Ahmed", proposal.contactName)
        assertEquals(ExecutionMode.ASSISTED, proposal.executionMode)
        assertNotNull(proposal.messageBody)
    }

    @Test
    fun testAutonomousAiMeetingCallProposalModeling() {
        val proposal = AiTaskProposal(
            intent = "MEETING_AUTO_CALL",
            contactName = "Tanvir Ahmed",
            contactPhone = "+8801912345678",
            actionType = ActionType.CALL,
            title = "Call Tanvir (Meeting Mode)",
            messageBody = "I am in a client meeting, will call you back at 5 PM",
            executionMode = ExecutionMode.AUTOMATIC
        )

        assertEquals(ActionType.CALL, proposal.actionType)
        assertEquals(ExecutionMode.AUTOMATIC, proposal.executionMode)
        assertEquals("+8801912345678", proposal.contactPhone)
        assertNotNull(proposal.messageBody)
    }
}

