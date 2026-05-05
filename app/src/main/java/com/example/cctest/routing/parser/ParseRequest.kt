package com.example.cctest.routing.parser

data class ParseRequest(
    val inputText: String,
    val entrySource: String,
    val locale: String = "zh-CN",
    val currentDestination: String? = null,
    val sessionSnapshot: String? = null,
    val supportedGoals: Set<UserGoal> = UserGoal.entries.toSet(),
    val timeContext: String? = null
)
