package com.callscribe.data.api

import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

data class UploadedGeminiFile(
    val name: String,
    val uri: String,
    val mimeType: String
)

@Serializable
data class SummaryPayload(
    val title: String = "通話紀錄",
    val summaryBullets: List<String> = emptyList(),
    val actionItems: List<String> = emptyList()
)

class GeminiApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun uploadAudioFile(
        apiKey: String,
        audioFile: File,
        mimeType: String,
        displayName: String
    ): UploadedGeminiFile {
        val fileSize = audioFile.length()
        if (fileSize <= 0L) throw IOException("Audio file is empty")

        // Step 1: Initiate resumable upload session
        val metadataJson = buildJsonObject {
            put("file", buildJsonObject {
                put("display_name", displayName)
            })
        }.toString()

        val startRequest = Request.Builder()
            .url("https://generativelanguage.googleapis.com/upload/v1beta/files")
            .header("x-goog-api-key", apiKey)
            .header("X-Goog-Upload-Protocol", "resumable")
            .header("X-Goog-Upload-Command", "start")
            .header("X-Goog-Upload-Header-Content-Length", fileSize.toString())
            .header("X-Goog-Upload-Header-Content-Type", mimeType)
            .post(metadataJson.toRequestBody(jsonMediaType))
            .build()

        val uploadUrl = client.newCall(startRequest).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("Files API init failed (${resp.code}): ${resp.body?.string()}")
            }
            resp.header("X-Goog-Upload-URL")
                ?: resp.header("x-goog-upload-url")
                ?: throw IOException("Missing X-Goog-Upload-URL header")
        }

        // Step 2: Upload audio bytes and finalize
        val uploadRequest = Request.Builder()
            .url(uploadUrl)
            .header("X-Goog-Upload-Offset", "0")
            .header("X-Goog-Upload-Command", "upload, finalize")
            .post(audioFile.asRequestBody(mimeType.toMediaType()))
            .build()

        val uploaded = client.newCall(uploadRequest).execute().use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IOException("Files API upload failed (${resp.code}): $bodyStr")
            }
            val root = json.parseToJsonElement(bodyStr).jsonObject
            val fileObj = root["file"]?.jsonObject ?: root
            val name = fileObj["name"]?.jsonPrimitive?.contentOrNull
                ?: throw IOException("Missing file.name in upload response")
            val uri = fileObj["uri"]?.jsonPrimitive?.contentOrNull
                ?: throw IOException("Missing file.uri in upload response")
            val actualMime = fileObj["mimeType"]?.jsonPrimitive?.contentOrNull ?: mimeType
            UploadedGeminiFile(name = name, uri = uri, mimeType = actualMime)
        }

        // Step 3: Poll until file state is ACTIVE
        waitUntilFileActive(apiKey, uploaded.name)
        return uploaded
    }

    private suspend fun waitUntilFileActive(apiKey: String, fileName: String) {
        repeat(30) {
            val req = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/$fileName")
                .header("x-goog-api-key", apiKey)
                .get()
                .build()

            val state = client.newCall(req).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IOException("Files API status check failed (${resp.code}): $bodyStr")
                }
                val root = json.parseToJsonElement(bodyStr).jsonObject
                root["state"]?.jsonPrimitive?.contentOrNull ?: "ACTIVE"
            }

            when (state) {
                "ACTIVE" -> return
                "FAILED" -> throw IOException("Gemini audio processing failed on server")
                else -> delay(2000)
            }
        }
        throw IOException("Timed out waiting for uploaded audio to become ACTIVE")
    }

    fun transcribeWithDiarization(apiKey: String, fileUri: String, mimeType: String): String {
        val body = buildJsonObject {
            put("model", "gemini-3.5-transcribe")
            put("input", buildJsonArray {
                add(buildJsonObject {
                    put("type", "audio")
                    put("uri", fileUri)
                    put("mime_type", mimeType)
                })
            })
            put("generation_config", buildJsonObject {
                put("transcription_config", buildJsonObject {
                    put("mode", buildJsonObject {
                        put("type", "verbatim")
                        put("diarization_mode", "speaker")
                    })
                })
            })
        }.toString()

        return callInteractionsApi(apiKey, body)
    }

    fun summarizeTranscriptZhTw(apiKey: String, rawTranscript: String): SummaryPayload {
        val prompt = """
            你是一位精準的台灣繁體中文秘書。請閱讀以下通話逐字稿，輸出 JSON 格式的整理結果：
            1. title：15 字以內的簡短繁體中文通話主題標題（不含標點符號）
            2. summaryBullets：3 到 5 條通話重點摘要（台灣繁體中文，精簡具體）
            3. actionItems：明確的後續待辦事項清單（若無則回傳空陣列）

            逐字稿如下：
            $rawTranscript
        """.trimIndent()

        val body = buildJsonObject {
            put("model", "gemini-3.6-flash")
            put("input", prompt)
            put("response_format", buildJsonObject {
                put("type", "text")
                put("mime_type", "application/json")
                put("schema", buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("title", buildJsonObject { put("type", "string") })
                        put("summaryBullets", buildJsonObject {
                            put("type", "array")
                            put("items", buildJsonObject { put("type", "string") })
                        })
                        put("actionItems", buildJsonObject {
                            put("type", "array")
                            put("items", buildJsonObject { put("type", "string") })
                        })
                    })
                    put("required", buildJsonArray {
                        add(kotlinx.serialization.json.JsonPrimitive("title"))
                        add(kotlinx.serialization.json.JsonPrimitive("summaryBullets"))
                        add(kotlinx.serialization.json.JsonPrimitive("actionItems"))
                    })
                })
            })
        }.toString()

        val text = callInteractionsApi(apiKey, body)
        return runCatching { json.decodeFromString<SummaryPayload>(text) }
            .getOrElse {
                SummaryPayload(
                    title = "通話紀錄",
                    summaryBullets = listOf(text.take(300)),
                    actionItems = emptyList()
                )
            }
    }

    fun deleteRemoteFileSafely(apiKey: String, fileName: String) {
        runCatching {
            val req = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/$fileName")
                .header("x-goog-api-key", apiKey)
                .delete()
                .build()
            client.newCall(req).execute().close()
        }
    }

    private fun callInteractionsApi(apiKey: String, jsonBody: String): String {
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/interactions")
            .header("x-goog-api-key", apiKey)
            .header("Api-Revision", "2026-05-20")
            .post(jsonBody.toRequestBody(jsonMediaType))
            .build()

        return client.newCall(request).execute().use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IOException("Interactions API error (${resp.code}): $bodyStr")
            }
            extractTextFromInteraction(bodyStr)
        }
    }

    private fun extractTextFromInteraction(bodyStr: String): String {
        val root = json.parseToJsonElement(bodyStr).jsonObject
        val textParts = mutableListOf<String>()

        fun collectTextBlocks(arr: JsonArray?) {
            arr?.forEach { elem ->
                val obj = elem as? JsonObject ?: return@forEach
                if (obj["type"]?.jsonPrimitive?.contentOrNull == "text") {
                    obj["text"]?.jsonPrimitive?.contentOrNull?.let { textParts.add(it) }
                }
            }
        }

        // Support both legacy `outputs` and revised `steps` response schemas
        collectTextBlocks(root["outputs"] as? JsonArray)
        if (textParts.isEmpty()) {
            (root["steps"] as? JsonArray)?.forEach { stepElem ->
                val stepObj = stepElem as? JsonObject ?: return@forEach
                collectTextBlocks(stepObj["content"] as? JsonArray)
                collectTextBlocks(stepObj["outputs"] as? JsonArray)
                if (stepObj["type"]?.jsonPrimitive?.contentOrNull == "text") {
                    stepObj["text"]?.jsonPrimitive?.contentOrNull?.let { textParts.add(it) }
                }
            }
        }

        val joined = textParts.joinToString("\n").trim()
        if (joined.isNotBlank()) return joined

        val outputTokens = root["usage"]
            ?.let { it as? JsonObject }
            ?.get("total_output_tokens")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.toIntOrNull()
        if (outputTokens == 0) {
            throw IOException("未偵測到清晰人聲（音檔過短或無語音內容）")
        }

        throw IOException("API 回傳格式無法解析：${bodyStr.take(300)}")
    }
}
