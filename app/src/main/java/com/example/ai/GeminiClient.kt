package com.example.ai

import com.example.data.local.entities.ContactEntity
import com.example.domain.model.ActionType
import com.example.domain.model.AiTaskProposal
import com.example.domain.model.ExecutionMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiClient(
    private val apiKey: String
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun parseCommandWithGemini(
        prompt: String,
        contacts: List<ContactEntity>
    ): AiTaskProposal = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException("Valid Gemini API Key is required")
        }

        val systemPrompt = AiPrompts.buildSystemPrompt(contacts)
        val payload = buildRequestPayload(systemPrompt, prompt)

        // Try primary model (gemini-3.5-flash), with fallback to gemini-flash-lite-latest and gemini-3.8-flash
        val models = listOf("gemini-3.5-flash", "gemini-flash-lite-latest", "gemini-3.8-flash")
        var lastException: Exception? = null

        for (model in models) {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                val request = Request.Builder()
                    .url(url)
                    .post(payload.toString().toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()
                    if (!response.isSuccessful || responseBody == null) {
                        throw RuntimeException("Gemini API call failed ($model, HTTP ${response.code}): $responseBody")
                    }

                    return@withContext parseGeminiResponse(responseBody, prompt, contacts)
                }
            } catch (e: Exception) {
                lastException = e
            }
        }

        throw lastException ?: RuntimeException("Gemini API request failed on all candidate models")
    }

    private fun buildRequestPayload(systemPrompt: String, userPrompt: String): JSONObject {
        val root = JSONObject()

        // System Instruction
        val systemInstruction = JSONObject().apply {
            put("parts", JSONArray().apply {
                put(JSONObject().put("text", systemPrompt))
            })
        }
        root.put("system_instruction", systemInstruction)

        // User Content
        val contents = JSONArray().apply {
            val contentObj = JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().put("text", userPrompt))
                })
            }
            put(contentObj)
        }
        root.put("contents", contents)

        // Generation Config: JSON output
        val generationConfig = JSONObject().apply {
            put("response_mime_type", "application/json")
            put("temperature", 0.2)
        }
        root.put("generationConfig", generationConfig)

        return root
    }

    private fun parseGeminiResponse(
        responseJson: String,
        rawPrompt: String,
        contacts: List<ContactEntity>
    ): AiTaskProposal {
        val root = JSONObject(responseJson)
        val candidates = root.optJSONArray("candidates")
            ?: throw IllegalStateException("No candidates in Gemini response")

        if (candidates.length() == 0) {
            throw IllegalStateException("Empty candidates in Gemini response")
        }

        val candidate = candidates.getJSONObject(0)
        val content = candidate.getJSONObject("content")
        val parts = content.getJSONArray("parts")
        val textPart = parts.getJSONObject(0).getString("text")

        // Parse extracted JSON from the model
        val jsonOutput = JSONObject(textPart)

        val intent = jsonOutput.optString("intent", "CREATE_GENERAL_REMINDER")
        val contactName = jsonOutput.optString("contactName", "").ifBlank { null }
        val contactPhone = jsonOutput.optString("contactPhone", "").ifBlank { null }
        val actionTypeStr = jsonOutput.optString("actionType", "REMINDER")
        val actionType = try {
            ActionType.valueOf(actionTypeStr.uppercase())
        } catch (_: Exception) {
            ActionType.REMINDER
        }

        val title = jsonOutput.optString("title", rawPrompt)
        val messageBody = jsonOutput.optString("messageBody", "").ifBlank { null }
        val scheduledDateTimeStr = jsonOutput.optString("scheduledDateTime", "")
        val timezone = jsonOutput.optString("timezone", java.util.TimeZone.getDefault().id)
        val recurrence = jsonOutput.optString("recurrence", "").ifBlank { null }
        val executionModeStr = jsonOutput.optString("executionMode", "ASSISTED")
        val executionMode = try {
            ExecutionMode.valueOf(executionModeStr.uppercase())
        } catch (_: Exception) {
            ExecutionMode.ASSISTED
        }

        val missingFieldsList = mutableListOf<String>()
        val missingFieldsArray = jsonOutput.optJSONArray("missingFields")
        if (missingFieldsArray != null) {
            for (i in 0 until missingFieldsArray.length()) {
                missingFieldsList.add(missingFieldsArray.getString(i))
            }
        }

        val clarification = jsonOutput.optString("clarificationQuestion", "").ifBlank { null }

        val rawProposal = AiTaskProposal(
            intent = intent,
            contactName = contactName,
            contactPhone = contactPhone,
            actionType = actionType,
            title = title,
            messageBody = messageBody,
            timezone = timezone,
            recurrence = recurrence,
            executionMode = executionMode,
            missingFields = missingFieldsList,
            clarificationQuestion = clarification,
            requiresConfirmation = true,
            rawPrompt = rawPrompt
        )

        // Revalidate through TaskValidator
        return TaskValidator.validateProposal(rawProposal, contacts, scheduledDateTimeStr)
    }
}

