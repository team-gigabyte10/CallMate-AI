package com.example

import com.example.ai.TaskParser
import com.example.data.local.entities.ContactEntity
import com.example.domain.model.ActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ExampleUnitTest {

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
    fun testEnglishCallCommandParsing() {
        val proposal = TaskParser.parseCommand("Call Rahim tomorrow at 2 PM", sampleContacts)
        assertEquals(ActionType.CALL, proposal.actionType)
        assertEquals("Rahim Chowdhury", proposal.contactName)
        assertEquals("+8801711223344", proposal.contactPhone)
    }

    @Test
    fun testBanglishCallCommandParsing() {
        val proposal = TaskParser.parseCommand("Ammu k rat 8 tay call dao", sampleContacts)
        assertEquals(ActionType.CALL, proposal.actionType)
        assertEquals("Ayesha Siddiqua", proposal.contactName)
    }

    @Test
    fun testWhatsAppCommandParsing() {
        val proposal = TaskParser.parseCommand("WhatsApp boss with sprint report at 5 PM", sampleContacts)
        assertEquals(ActionType.WHATSAPP, proposal.actionType)
        assertEquals("Tanvir Ahmed", proposal.contactName)
        assertNotNull(proposal.messageBody)
    }

    @Test
    fun testReminderParsing() {
        val proposal = TaskParser.parseCommand("Remind me to drink water in 30 minutes", sampleContacts)
        assertEquals(ActionType.REMINDER, proposal.actionType)
    }
}
