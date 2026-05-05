package com.example.cctest.routing.parser

sealed interface ParseOutcome {
    data class Success(val result: ParseResult) : ParseOutcome
    data class Failure(val error: ParseError) : ParseOutcome
}
