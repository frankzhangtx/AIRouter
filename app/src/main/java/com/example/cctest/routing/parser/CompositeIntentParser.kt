package com.example.cctest.routing.parser

class CompositeIntentParser(
    private val ruleBasedIntentParser: IntentParser,
    private val llmIntentParser: IntentParser,
    private val config: IntentParsingConfig
) : IntentParser {
    override suspend fun parse(request: ParseRequest): ParseOutcome {
        val ruleOutcome = ruleBasedIntentParser.parse(request)
        if (ruleOutcome is ParseOutcome.Success &&
            ruleOutcome.result.userGoal != UserGoal.Unknown &&
            ruleOutcome.result.confidence >= config.minRuleConfidence
        ) {
            return ruleOutcome
        }

        if (!config.enableLlmParsing) {
            return ruleOutcome
        }

        val llmOutcome = llmIntentParser.parse(request)
        if (llmOutcome is ParseOutcome.Success) {
            val parserSource = if (ruleOutcome is ParseOutcome.Success) {
                ParserSource.RULE_THEN_LLM
            } else {
                ParserSource.LLM
            }
            return ParseOutcome.Success(
                llmOutcome.result.copy(
                    parserMetadata = llmOutcome.result.parserMetadata.copy(parserSource = parserSource)
                )
            )
        }

        return if (config.fallbackToRuleOnLlmFailure && ruleOutcome is ParseOutcome.Success) {
            ParseOutcome.Success(
                ruleOutcome.result.copy(
                    parserMetadata = ruleOutcome.result.parserMetadata.copy(
                        parserSource = ParserSource.FALLBACK
                    )
                )
            )
        } else {
            llmOutcome
        }
    }
}
