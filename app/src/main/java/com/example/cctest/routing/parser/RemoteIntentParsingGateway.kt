package com.example.cctest.routing.parser

import com.example.cctest.navigation.HouseDashboardTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

class RemoteIntentParsingGateway(
    private val config: IntentParsingConfig,
    private val connectionFactory: (URL) -> HttpURLConnection = { url ->
        url.openConnection() as HttpURLConnection
    },
    private val timeProvider: () -> Long = System::currentTimeMillis
) : IntentParsingGateway {

    override suspend fun parse(request: ParseRequest): ParseOutcome {
        if (!config.enableLlmParsing || !config.isRemoteLlmConfigured) {
            return ParseOutcome.Failure(ParseError.Unavailable)
        }

        val startedAt = timeProvider()
        return try {
            withTimeout(config.llmRequestTimeoutMs) {
                withContext(Dispatchers.IO) {
                    executeRequest(request, startedAt)
                }
            }
        } catch (_: TimeoutCancellationException) {
            ParseOutcome.Failure(ParseError.Timeout)
        } catch (_: SocketTimeoutException) {
            ParseOutcome.Failure(ParseError.Timeout)
        } catch (_: IOException) {
            ParseOutcome.Failure(ParseError.Unavailable)
        } catch (error: UnsupportedGoalException) {
            ParseOutcome.Failure(ParseError.UnsupportedGoal(error.goal))
        } catch (error: SchemaException) {
            ParseOutcome.Failure(ParseError.InvalidSchema(error.message ?: "模型返回结构不合法"))
        } catch (error: JSONException) {
            ParseOutcome.Failure(ParseError.InvalidSchema(error.message ?: "解析响应 JSON 失败"))
        } catch (error: Exception) {
            ParseOutcome.Failure(ParseError.InternalError(error.message ?: "解析网关出现未知异常"))
        }
    }

    private fun executeRequest(request: ParseRequest, startedAt: Long): ParseOutcome {
        val connection = connectionFactory(URL(config.llmBaseUrl))
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = config.llmConnectTimeoutMs
            connection.readTimeout = config.llmReadTimeoutMs
            connection.doInput = true
            connection.doOutput = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Authorization", "Bearer ${config.llmApiKey}")

            val requestBody = buildRequestBody(request)
            connection.outputStream.use { outputStream ->
                outputStream.write(requestBody.toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            val responseBody = readResponseBody(connection, responseCode)
            if (responseCode !in 200..299) {
                return mapHttpFailure(responseCode, responseBody)
            }

            ParseOutcome.Success(parseResult(responseBody, startedAt))
        } finally {
            connection.disconnect()
        }
    }

    private fun buildRequestBody(request: ParseRequest): String {
        val systemPrompt = """
            你是智能路由解析器。你只输出 JSON，对自然语言请求做结构化意图识别。
            输出必须满足以下约束：
            1. userGoal 只能是 FillPersonalInfo、BrowsePersonalInfoList、OpenPersonalInfoDetail、OpenHouseDashboard、Unknown。
            2. confidence 返回 0 到 1 的小数。
            3. slots 必须始终存在；没有值的字段返回 null 或 false。
            4. dashboardTab 只能是 HOME 或 WORK。
            5. personalInfoFields 中只填写明确提到的字段，不要猜测。
            6. 如果目标不明确，返回 Unknown 并给 ambiguityReason。
            输出 schema：
            {
              "userGoal": "BrowsePersonalInfoList",
              "confidence": 0.93,
              "ambiguityReason": null,
              "slots": {
                "listPosition": 12,
                "personName": null,
                "phone": null,
                "city": null,
                "dashboardTab": null,
                "autoOpenDetail": false,
                "personalInfoFields": {
                  "name": null,
                  "age": null,
                  "phone": null,
                  "email": null,
                  "address": null,
                  "occupation": null,
                  "company": null,
                  "hobbies": null,
                  "emergencyContact": null
                }
              }
            }
        """.trimIndent()

        val userPayload = JSONObject().apply {
            put("inputText", request.inputText)
            put("entrySource", request.entrySource)
            put("currentDestination", request.currentDestination)
            put("sessionSnapshot", request.sessionSnapshot)
        }

        return JSONObject().apply {
            put("model", config.llmModel)
            put("temperature", 0.1)
            put("response_format", JSONObject().put("type", "json_object"))
            put(
                "messages",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("role", "system")
                            .put("content", systemPrompt)
                    )
                    .put(
                        JSONObject()
                            .put("role", "user")
                            .put("content", userPayload.toString())
                    )
            )
        }.toString()
    }

    private fun parseResult(responseBody: String, startedAt: Long): ParseResult {
        val rootJson = JSONObject(responseBody)
        val payloadJson = when {
            rootJson.has("choices") -> JSONObject(extractJsonObject(extractAssistantContent(rootJson)))
            rootJson.has("userGoal") -> rootJson
            else -> throw SchemaException("响应中缺少可解析结果")
        }

        val goal = parseGoal(payloadJson.optString("userGoal"))
        val slotsJson = payloadJson.optJSONObject("slots")
        return ParseResult(
            userGoal = goal,
            slots = parseSlots(slotsJson),
            confidence = payloadJson.optDouble("confidence", 0.0).toFloat(),
            ambiguityReason = payloadJson.optString("ambiguityReason").ifBlank { null },
            parserMetadata = ParserMetadata(
                parserSource = ParserSource.LLM,
                providerName = config.llmProviderName,
                modelName = config.llmModel,
                promptVersion = config.llmPromptVersion,
                schemaVersion = config.llmSchemaVersion,
                latencyMs = timeProvider() - startedAt
            )
        )
    }

    private fun parseGoal(rawGoal: String): UserGoal {
        val normalizedGoal = rawGoal.trim()
        return runCatching { UserGoal.valueOf(normalizedGoal) }
            .getOrElse { throw UnsupportedGoalException(normalizedGoal.ifBlank { "UNKNOWN" }) }
    }

    private fun parseSlots(slotsJson: JSONObject?): ParseSlots {
        val personalInfoFieldsJson = slotsJson?.optJSONObject("personalInfoFields")
        return ParseSlots(
            personalInfoFields = PersonalInfoFields(
                name = personalInfoFieldsJson.optStringOrNull("name"),
                age = personalInfoFieldsJson.optIntOrNull("age"),
                phone = personalInfoFieldsJson.optStringOrNull("phone"),
                email = personalInfoFieldsJson.optStringOrNull("email"),
                address = personalInfoFieldsJson.optStringOrNull("address"),
                occupation = personalInfoFieldsJson.optStringOrNull("occupation"),
                company = personalInfoFieldsJson.optStringOrNull("company"),
                hobbies = personalInfoFieldsJson.optStringOrNull("hobbies"),
                emergencyContact = personalInfoFieldsJson.optStringOrNull("emergencyContact")
            ),
            listPosition = slotsJson.optIntOrNull("listPosition"),
            personName = slotsJson.optStringOrNull("personName"),
            phone = slotsJson.optStringOrNull("phone"),
            city = slotsJson.optStringOrNull("city"),
            dashboardTab = slotsJson.optStringOrNull("dashboardTab")?.toHouseDashboardTab(),
            autoOpenDetail = slotsJson?.optBoolean("autoOpenDetail", false) ?: false
        )
    }

    private fun extractAssistantContent(rootJson: JSONObject): String {
        val choices = rootJson.optJSONArray("choices")
            ?: throw SchemaException("choices 为空")
        val firstChoice = choices.optJSONObject(0)
            ?: throw SchemaException("choices[0] 为空")
        val message = firstChoice.optJSONObject("message")
            ?: throw SchemaException("message 为空")
        val content = message.opt("content") ?: throw SchemaException("content 为空")
        return when (content) {
            is String -> content
            is JSONArray -> buildString {
                for (index in 0 until content.length()) {
                    val part = content.optJSONObject(index)
                    val text = part?.optString("text").orEmpty()
                    if (text.isNotBlank()) {
                        append(text)
                    }
                }
            }
            else -> throw SchemaException("content 结构不受支持")
        }
    }

    private fun extractJsonObject(content: String): String {
        val trimmed = content.trim()
        val withoutFence = if (trimmed.startsWith("```")) {
            trimmed
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
        } else {
            trimmed
        }
        val start = withoutFence.indexOf('{')
        val end = withoutFence.lastIndexOf('}')
        if (start == -1 || end <= start) {
            throw SchemaException("模型返回中未找到 JSON 对象")
        }
        return withoutFence.substring(start, end + 1)
    }

    private fun mapHttpFailure(responseCode: Int, responseBody: String): ParseOutcome {
        return when (responseCode) {
            408, 504 -> ParseOutcome.Failure(ParseError.Timeout)
            401, 403, 429 -> ParseOutcome.Failure(ParseError.Unavailable)
            in 500..599 -> ParseOutcome.Failure(ParseError.Unavailable)
            else -> ParseOutcome.Failure(
                ParseError.InternalError(
                    "LLM provider returned HTTP $responseCode: ${responseBody.truncateForMessage()}"
                )
            )
        }
    }

    private fun readResponseBody(connection: HttpURLConnection, responseCode: Int): String {
        val stream = if (responseCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }
        return stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
    }

    private fun JSONObject?.optStringOrNull(key: String): String? {
        if (this == null || this.isNull(key)) {
            return null
        }
        return this.optString(key).trim().ifBlank { null }
    }

    private fun JSONObject?.optIntOrNull(key: String): Int? {
        if (this == null || this.isNull(key)) {
            return null
        }
        val value = this.opt(key) ?: return null
        return when (value) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        }
    }

    private fun String.toHouseDashboardTab(): HouseDashboardTab? {
        return when (trim().uppercase()) {
            HouseDashboardTab.HOME.name -> HouseDashboardTab.HOME
            HouseDashboardTab.WORK.name -> HouseDashboardTab.WORK
            else -> null
        }
    }

    private fun String.truncateForMessage(maxLength: Int = 120): String {
        val compact = trim().replace(Regex("\\s+"), " ")
        return if (compact.length <= maxLength) compact else compact.take(maxLength) + "..."
    }

    private class UnsupportedGoalException(val goal: String) : IllegalArgumentException(goal)

    private class SchemaException(message: String) : IllegalArgumentException(message)
}
