package com.example.cctest.routing

import com.example.cctest.routing.parser.CompositeIntentParser
import com.example.cctest.routing.parser.IntentParser
import com.example.cctest.routing.parser.IntentParsingConfig
import com.example.cctest.routing.parser.ParseError
import com.example.cctest.routing.parser.ParseOutcome
import com.example.cctest.routing.parser.ParseRequest
import com.example.cctest.routing.parser.ParseResult
import com.example.cctest.routing.parser.ParserMetadata
import com.example.cctest.routing.parser.ParserSource
import com.example.cctest.routing.parser.UserGoal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositeIntentParserTest {

    @Test
    fun parse_returnsRuleResultWhenConfidenceExceedsThreshold() = runBlocking {
        val parser = createParser(
            ruleOutcome = successOutcome(UserGoal.BrowsePersonalInfoList, confidence = 0.9f),
            llmOutcome = successOutcome(UserGoal.OpenHouseDashboard, confidence = 0.95f)
        )

        val outcome = parser.parse(ParseRequest(inputText = "查看第 12 条记录", entrySource = "test"))

        assertTrue(outcome is ParseOutcome.Success)
        val result = (outcome as ParseOutcome.Success).result
        assertEquals(UserGoal.BrowsePersonalInfoList, result.userGoal)
        assertEquals(ParserSource.RULE, result.parserMetadata.parserSource)
    }

    @Test
    fun parse_usesLlmWhenRuleConfidenceFallsBelowThreshold() = runBlocking {
        val parser = createParser(
            ruleOutcome = successOutcome(UserGoal.OpenPersonalInfoDetail, confidence = 0.5f),
            llmOutcome = successOutcome(UserGoal.OpenHouseDashboard, confidence = 0.91f)
        )

        val outcome = parser.parse(ParseRequest(inputText = "打开 Work 看板", entrySource = "test"))

        assertTrue(outcome is ParseOutcome.Success)
        val result = (outcome as ParseOutcome.Success).result
        assertEquals(UserGoal.OpenHouseDashboard, result.userGoal)
        assertEquals(ParserSource.RULE_THEN_LLM, result.parserMetadata.parserSource)
    }

    @Test
    fun parse_returnsRuleFallbackWhenLlmFailsAndFallbackEnabled() = runBlocking {
        val parser = createParser(
            ruleOutcome = successOutcome(UserGoal.OpenPersonalInfoDetail, confidence = 0.4f),
            llmOutcome = ParseOutcome.Failure(ParseError.Timeout),
            fallbackToRuleOnLlmFailure = true
        )

        val outcome = parser.parse(ParseRequest(inputText = "查看张雨桐的信息", entrySource = "test"))

        assertTrue(outcome is ParseOutcome.Success)
        val result = (outcome as ParseOutcome.Success).result
        assertEquals(UserGoal.OpenPersonalInfoDetail, result.userGoal)
        assertEquals(ParserSource.FALLBACK, result.parserMetadata.parserSource)
    }

    @Test
    fun parse_returnsLlmFailureWhenFallbackDisabled() = runBlocking {
        val parser = createParser(
            ruleOutcome = successOutcome(UserGoal.OpenPersonalInfoDetail, confidence = 0.4f),
            llmOutcome = ParseOutcome.Failure(ParseError.Timeout),
            fallbackToRuleOnLlmFailure = false
        )

        val outcome = parser.parse(ParseRequest(inputText = "查看张雨桐的信息", entrySource = "test"))

        assertTrue(outcome is ParseOutcome.Failure)
        assertEquals(ParseError.Timeout, (outcome as ParseOutcome.Failure).error)
    }

    private fun createParser(
        ruleOutcome: ParseOutcome,
        llmOutcome: ParseOutcome,
        fallbackToRuleOnLlmFailure: Boolean = true
    ): CompositeIntentParser {
        return CompositeIntentParser(
            ruleBasedIntentParser = StubIntentParser(ruleOutcome),
            llmIntentParser = StubIntentParser(llmOutcome),
            config = IntentParsingConfig(
                enableLlmParsing = true,
                fallbackToRuleOnLlmFailure = fallbackToRuleOnLlmFailure,
                minRuleConfidence = 0.82f,
                llmRequestTimeoutMs = 12_000L,
                llmConnectTimeoutMs = 4_000,
                llmReadTimeoutMs = 8_000,
                llmBaseUrl = "https://example.com/v1/chat/completions",
                llmApiKey = "test-key",
                llmModel = "test-model",
                llmProviderName = "test-provider",
                llmPromptVersion = "intent-routing-v2",
                llmSchemaVersion = "v1"
            )
        )
    }

    private fun successOutcome(userGoal: UserGoal, confidence: Float): ParseOutcome.Success {
        val source = if (confidence >= 0.82f) ParserSource.RULE else ParserSource.LLM
        return ParseOutcome.Success(
            ParseResult(
                userGoal = userGoal,
                confidence = confidence,
                parserMetadata = ParserMetadata(parserSource = source)
            )
        )
    }

    private class StubIntentParser(
        private val outcome: ParseOutcome
    ) : IntentParser {
        override suspend fun parse(request: ParseRequest): ParseOutcome = outcome
    }
}
