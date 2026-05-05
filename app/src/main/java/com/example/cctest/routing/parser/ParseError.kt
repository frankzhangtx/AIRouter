package com.example.cctest.routing.parser

sealed interface ParseError {
    data object Timeout : ParseError
    data object Unavailable : ParseError
    data class InvalidSchema(val reason: String) : ParseError
    data class UnsupportedGoal(val goal: String) : ParseError
    data class InternalError(val message: String) : ParseError
}
