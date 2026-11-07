package com.example.ai

import android.util.Log
import com.example.BuildConfig
import com.example.data.local.entities.ContactEntity
import com.example.domain.model.AiTaskProposal

interface GeminiRepository {
    suspend fun parseCommand(input: String, contacts: List<ContactEntity>): AiTaskProposal
}

class DefaultGeminiRepository(
    private val apiKey: String = BuildConfig.GEMINI_API_KEY
) : GeminiRepository {

    private val geminiClient: GeminiClient? = if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
        GeminiClient(apiKey)
    } else {
        null
    }

    override suspend fun parseCommand(input: String, contacts: List<ContactEntity>): AiTaskProposal {
        // If Gemini client is configured, attempt online intelligence first
        if (geminiClient != null) {
            try {
                Log.d("GeminiRepository", "Parsing command via Gemini Cloud API: $input")
                return geminiClient.parseCommandWithGemini(input, contacts)
            } catch (e: Exception) {
                Log.w("GeminiRepository", "Gemini API unavailable or failed (${e.message}). Falling back to local TaskParser.", e)
            }
        } else {
            Log.d("GeminiRepository", "Gemini API key not configured or placeholder. Using local TaskParser.")
        }

        // Offline Heuristic Fallback
        return TaskParser.parseCommand(input, contacts)
    }
}

