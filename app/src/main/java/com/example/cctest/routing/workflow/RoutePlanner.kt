package com.example.cctest.routing.workflow

import com.example.cctest.navigation.JourneyPlan
import com.example.cctest.routing.parser.ParseResult
import com.example.cctest.routing.parser.PersonalInfoFields

data class PlanningDecision(
    val targetPlan: TargetPlan,
    val journeyPlan: JourneyPlan? = null
)

class RoutePlanner(
    private val targetPlanner: TargetPlanner,
    private val journeyPlanner: JourneyPlanner
) {
    fun plan(
        result: ParseResult,
        currentDestinationId: Int?,
        currentFields: PersonalInfoFields
    ): PlanningDecision {
        val targetPlan = targetPlanner.plan(
            result = result,
            currentFields = currentFields
        )
        return PlanningDecision(
            targetPlan = targetPlan,
            journeyPlan = targetPlan.target?.let {
                journeyPlanner.plan(
                    currentDestinationId = currentDestinationId,
                    targetPlan = targetPlan
                )
            }
        )
    }
}
