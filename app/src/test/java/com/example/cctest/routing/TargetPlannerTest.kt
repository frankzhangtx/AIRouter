package com.example.cctest.routing

import com.example.cctest.feature.personalinfo.data.PersonalInfoRecordResolver
import com.example.cctest.feature.personalinfo.data.PersonalInfoRepository
import com.example.cctest.navigation.DestinationContractRegistry
import com.example.cctest.routing.parser.ParseResult
import com.example.cctest.routing.parser.ParseSlots
import com.example.cctest.routing.parser.ParserMetadata
import com.example.cctest.routing.parser.ParserSource
import com.example.cctest.routing.parser.PersonalInfoFields
import com.example.cctest.routing.parser.UserGoal
import com.example.cctest.routing.workflow.TargetPlanner
import com.example.cctest.routing.workflow.WorkflowEngine
import com.example.cctest.routing.workflow.WorkflowRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class TargetPlannerTest {
    private val planner = TargetPlanner(
        destinationContractRegistry = DestinationContractRegistry(),
        recordResolver = PersonalInfoRecordResolver(PersonalInfoRepository()),
        workflowRegistry = WorkflowRegistry(),
        workflowEngine = WorkflowEngine()
    )

    @Test
    fun planListTarget_uniquePositionWithAutoOpenDetail_keepsAutoOpenDetail() {
        val plan = planner.plan(
            result = parseResult(
                userGoal = UserGoal.BrowsePersonalInfoList,
                slots = ParseSlots(listPosition = 12, autoOpenDetail = true)
            ),
            currentFields = PersonalInfoFields()
        )

        assertEquals(12, plan.listFocusRequest?.position)
        assertEquals(true, plan.listFocusRequest?.autoOpenDetail)
    }

    @Test
    fun planListTarget_outOfRangePositionNeverAutoOpensDetail() {
        val plan = planner.plan(
            result = parseResult(
                userGoal = UserGoal.BrowsePersonalInfoList,
                slots = ParseSlots(listPosition = 99, autoOpenDetail = true)
            ),
            currentFields = PersonalInfoFields()
        )

        assertEquals(99, plan.listFocusRequest?.position)
        assertEquals(false, plan.listFocusRequest?.autoOpenDetail)
    }

    @Test
    fun planListTarget_plainPositionDoesNotAutoOpenDetail() {
        val plan = planner.plan(
            result = parseResult(
                userGoal = UserGoal.BrowsePersonalInfoList,
                slots = ParseSlots(listPosition = 12, autoOpenDetail = false)
            ),
            currentFields = PersonalInfoFields()
        )

        assertEquals(12, plan.listFocusRequest?.position)
        assertEquals(false, plan.listFocusRequest?.autoOpenDetail)
    }

    @Test
    fun planListTarget_ambiguousResolutionNeverAutoOpensDetail() {
        val plan = planner.plan(
            result = parseResult(
                userGoal = UserGoal.BrowsePersonalInfoList,
                slots = ParseSlots(personName = "张", autoOpenDetail = true)
            ),
            currentFields = PersonalInfoFields()
        )

        assertEquals(false, plan.listFocusRequest?.autoOpenDetail)
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
