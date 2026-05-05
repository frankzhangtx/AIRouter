package com.example.cctest.routing.model

import com.example.cctest.navigation.JourneyPlan
import com.example.cctest.navigation.RouteTarget

sealed interface UiEffect {
    data class ExecuteJourney(val plan: JourneyPlan) : UiEffect
    data class NavigateTo(val target: RouteTarget) : UiEffect
    data class ShowValidationError(val message: String) : UiEffect
    data class ShowRoutingFallback(val message: String) : UiEffect
    data class ShowParsingUnavailable(val message: String) : UiEffect
    data class ShowJourneyStepHint(val message: String) : UiEffect
}
