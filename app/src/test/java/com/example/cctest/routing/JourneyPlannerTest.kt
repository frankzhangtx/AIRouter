package com.example.cctest.routing

import com.example.cctest.R
import com.example.cctest.navigation.AppScreen
import com.example.cctest.navigation.EntryActionRegistry
import com.example.cctest.navigation.HouseDashboardLaunchSpec
import com.example.cctest.navigation.HouseDashboardTab
import com.example.cctest.navigation.JourneyStep
import com.example.cctest.navigation.RouteTarget
import com.example.cctest.routing.parser.UserGoal
import com.example.cctest.routing.workflow.JourneyPlanner
import com.example.cctest.routing.workflow.TargetPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JourneyPlannerTest {
    private val planner = JourneyPlanner(EntryActionRegistry())

    @Test
    fun planDashboardFromIntentEntry_replaysSecondFragmentFirst() {
        val journeyPlan = requireNotNull(
            planner.plan(
                currentDestinationId = R.id.intentEntryFragment,
                targetPlan = TargetPlan(
                    target = RouteTarget.HouseDashboardTarget(
                        HouseDashboardLaunchSpec(
                            initialTab = HouseDashboardTab.WORK,
                            entrySource = "intelligent-routing"
                        )
                    ),
                    routeIntent = UserGoal.OpenHouseDashboard,
                    activeTargetLabel = "家居看板"
                )
            )
        )

        assertEquals(AppScreen.INTENT_ENTRY, journeyPlan.entryScreen)
        assertEquals(R.id.SecondFragment, (journeyPlan.steps[0] as JourneyStep.NavigateToFragment).destinationId)
        assertTrue(journeyPlan.steps[1] is JourneyStep.OpenHouseDashboard)
    }
}
