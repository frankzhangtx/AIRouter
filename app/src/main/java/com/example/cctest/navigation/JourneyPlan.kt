package com.example.cctest.navigation

import android.os.Bundle
import com.example.cctest.routing.model.ListFocusRequest
import com.example.cctest.routing.model.RecordRef

data class JourneyPlan(
    val journeyId: String,
    val entryScreen: AppScreen,
    val target: RouteTarget,
    val steps: List<JourneyStep>,
    val fallbackPolicy: String = "stop_on_current_screen",
    val completionCondition: String = target.displayName
)

sealed interface JourneyStep {
    val displayLabel: String

    data class NavigateToFragment(
        val destinationId: Int,
        val args: Bundle? = null,
        override val displayLabel: String
    ) : JourneyStep

    data class FocusList(
        val focusRequest: ListFocusRequest,
        override val displayLabel: String = "定位个人信息列表"
    ) : JourneyStep

    data class OpenFocusedRecordDetail(
        val recordRef: RecordRef,
        override val displayLabel: String = "打开已定位详情"
    ) : JourneyStep

    data class OpenHouseDashboard(
        val launchSpec: HouseDashboardLaunchSpec,
        override val displayLabel: String = "打开家居看板"
    ) : JourneyStep

    data class ShowFallbackMessage(
        val message: String,
        override val displayLabel: String = "展示回退提示"
    ) : JourneyStep

    data class StopAtCurrentScreen(
        val reason: String,
        override val displayLabel: String = "停留当前页面"
    ) : JourneyStep
}
