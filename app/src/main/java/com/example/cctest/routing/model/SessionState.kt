package com.example.cctest.routing.model

import com.example.cctest.navigation.RouteTarget
import com.example.cctest.routing.parser.ParseResult
import com.example.cctest.routing.parser.PersonalInfoFields
import com.example.cctest.routing.parser.UserGoal

enum class ParsingUiStatus {
    IDLE,
    PARSING,
    PARSED,
    FAILED
}

enum class SubmissionUiStatus {
    IDLE,
    SUBMITTING,
    SUCCESS,
    FAILURE
}

enum class JourneyUiStatus {
    IDLE,
    PLANNED,
    EXECUTING,
    PAUSED,
    COMPLETED,
    FAILED
}

data class SessionState(
    val rawInputText: String = "",
    val parsingStatus: ParsingUiStatus = ParsingUiStatus.IDLE,
    val parseResult: ParseResult? = null,
    val workflowId: String? = null,
    val routeIntent: UserGoal = UserGoal.Unknown,
    val currentStepId: String? = null,
    val activeTargetLabel: String? = null,
    val activeRouteTarget: RouteTarget? = null,
    val activeJourneyId: String? = null,
    val currentJourneyStepIndex: Int? = null,
    val currentJourneyStepLabel: String? = null,
    val journeyStatus: JourneyUiStatus = JourneyUiStatus.IDLE,
    val lastJourneyError: String? = null,
    val formFields: PersonalInfoFields = PersonalInfoFields(),
    val recordRef: RecordRef? = null,
    val lastErrorSummary: String? = null,
    val submissionStatus: SubmissionUiStatus = SubmissionUiStatus.IDLE,
    val submissionMessage: String? = null
) {
    fun parserSummary(): String {
        val metadata = parseResult?.parserMetadata ?: return "尚未解析"
        return when (metadata.parserSource.name) {
            "RULE" -> "当前由规则解析命中"
            "LLM" -> "当前由 LLM 解析命中"
            "RULE_THEN_LLM" -> "当前由规则转 LLM 命中"
            "FALLBACK" -> "当前使用本地回退结果"
            else -> "当前解析来源未知"
        }
    }
}
