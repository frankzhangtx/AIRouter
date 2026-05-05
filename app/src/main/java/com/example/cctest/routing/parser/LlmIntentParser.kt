package com.example.cctest.routing.parser

class LlmIntentParser(
    private val gateway: IntentParsingGateway
) : IntentParser {
    override suspend fun parse(request: ParseRequest): ParseOutcome {
        return gateway.parse(request)
    }
}
