package com.example.cctest.routing.parser

interface IntentParser {
    suspend fun parse(request: ParseRequest): ParseOutcome
}
