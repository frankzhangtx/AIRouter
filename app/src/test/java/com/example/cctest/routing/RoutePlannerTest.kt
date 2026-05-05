package com.example.cctest.routing

import com.example.cctest.R
import com.example.cctest.feature.personalinfo.data.PersonalInfoRecordResolver
import com.example.cctest.feature.personalinfo.data.PersonalInfoRepository
import com.example.cctest.navigation.DestinationContractRegistry
import com.example.cctest.navigation.EntryActionRegistry
import com.example.cctest.navigation.JourneyStep
import com.example.cctest.navigation.RouteTarget
import com.example.cctest.routing.parser.ParseResult
import com.example.cctest.routing.parser.ParseSlots
import com.example.cctest.routing.parser.ParserMetadata
import com.example.cctest.routing.parser.ParserSource
import com.example.cctest.routing.parser.PersonalInfoFields
import com.example.cctest.routing.parser.UserGoal
import com.example.cctest.routing.workflow.JourneyPlanner
import com.example.cctest.routing.workflow.RoutePlanner
import com.example.cctest.routing.workflow.TargetPlanner
import com.example.cctest.routing.workflow.WorkflowEngine
import com.example.cctest.routing.workflow.WorkflowRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePlannerTest {
    private val planner = RoutePlanner(
        targetPlanner = TargetPlanner(
            destinationContractRegistry = DestinationContractRegistry(),
            recordResolver = PersonalInfoRecordResolver(PersonalInfoRepository()),
            workflowRegistry = WorkflowRegistry(),
            workflowEngine = WorkflowEngine()
        ),
        journeyPlanner = JourneyPlanner(EntryActionRegistry())
    )

    @Test
    fun planFormGoal_routesThroughSecondThenForm() {
        val decision = planner.plan(
            result = parseResult(
                userGoal = UserGoal.FillPersonalInfo,
                slots = ParseSlots(personalInfoFields = PersonalInfoFields(name = "李晨曦"))
            ),
            currentDestinationId = R.id.FirstFragment,
            currentFields = PersonalInfoFields()
        )
        val target = decision.targetPlan.target as RouteTarget.WorkflowTarget
        val journeyPlan = requireNotNull(decision.journeyPlan)

        assertEquals(R.id.personalInfoFormStepFragment, target.entryDestinationId)
        assertEquals("李晨曦", decision.targetPlan.formFields?.name)
        assertEquals(R.id.SecondFragment, (journeyPlan.steps[0] as JourneyStep.NavigateToFragment).destinationId)
        assertEquals(R.id.personalInfoFormStepFragment, (journeyPlan.steps[1] as JourneyStep.NavigateToFragment).destinationId)
    }

    @Test
    fun planUniqueDetailGoal_routesThroughListBeforeOpeningDetail() {
        val decision = planner.plan(
            result = parseResult(
                userGoal = UserGoal.OpenPersonalInfoDetail,
                slots = ParseSlots(personName = "张雨桐")
            ),
            currentDestinationId = R.id.FirstFragment,
            currentFields = PersonalInfoFields()
        )
        val journeyPlan = requireNotNull(decision.journeyPlan)

        assertTrue(decision.targetPlan.target is RouteTarget.DetailTarget)
        assertEquals(R.id.SecondFragment, (journeyPlan.steps[0] as JourneyStep.NavigateToFragment).destinationId)
        assertEquals(R.id.PersonalInfoListFragment, (journeyPlan.steps[1] as JourneyStep.NavigateToFragment).destinationId)
        assertTrue(journeyPlan.steps[2] is JourneyStep.FocusList)
        assertTrue(journeyPlan.steps[3] is JourneyStep.OpenFocusedRecordDetail)
    }

    @Test
    fun planAmbiguousDetailGoal_fallsBackToList() {
        val decision = planner.plan(
            result = parseResult(
                userGoal = UserGoal.OpenPersonalInfoDetail,
                slots = ParseSlots(personName = "张")
            ),
            currentDestinationId = R.id.FirstFragment,
            currentFields = PersonalInfoFields()
        )
        val journeyPlan = requireNotNull(decision.journeyPlan)

        assertTrue(decision.targetPlan.target is RouteTarget.ListTarget)
        assertNotNull(decision.targetPlan.fallbackMessage)
        assertTrue(journeyPlan.steps[journeyPlan.steps.size - 2] is JourneyStep.ShowFallbackMessage)
        assertTrue(journeyPlan.steps.last() is JourneyStep.StopAtCurrentScreen)
    }

    private fun parseResult(userGoal: UserGoal, slots: ParseSlots): ParseResult {
        return ParseResult(
            userGoal = userGoal,
            slots = slots,
            confidence = 0.9f,
            parserMetadata = ParserMetadata(
                parserSource = ParserSource.RULE,
                latencyMs = 1L
            )
        )
    }
}
