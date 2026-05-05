package com.example.cctest.routing.parser

enum class ParserSource {
    RULE,
    LLM,
    RULE_THEN_LLM,
    FALLBACK
}

data class ParserMetadata(
    val parserSource: ParserSource,
    val providerName: String = "local",
    val modelName: String = "rule-based",
    val promptVersion: String = "v1",
    val schemaVersion: String = "v1",
    val latencyMs: Long = 0L
)
