package com.example.cctest.routing.parser

interface IntentParsingGateway {
    suspend fun parse(request: ParseRequest): ParseOutcome
}
