package com.example.cctest.routing.parser

data class IntentParsingConfig(
    val enableLlmParsing: Boolean,
    val fallbackToRuleOnLlmFailure: Boolean,
    val minRuleConfidence: Float,
    val llmRequestTimeoutMs: Long,
    val llmConnectTimeoutMs: Int,
    val llmReadTimeoutMs: Int,
    val llmBaseUrl: String,
    val llmApiKey: String,
    val llmModel: String,
    val llmProviderName: String,
    val llmPromptVersion: String,
    val llmSchemaVersion: String
) {
    val isRemoteLlmConfigured: Boolean
        get() = llmBaseUrl.isNotBlank() && llmApiKey.isNotBlank() && llmModel.isNotBlank()
}
