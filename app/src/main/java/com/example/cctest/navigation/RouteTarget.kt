package com.example.cctest.navigation

import android.os.Bundle
import com.example.cctest.routing.model.ListFocusRequest
import com.example.cctest.routing.model.RecordRef

sealed interface RouteTarget {
    val displayName: String

    data class NavTarget(
        val destinationId: Int,
        val args: Bundle? = null,
        override val displayName: String
    ) : RouteTarget

    data class WorkflowTarget(
        val workflowId: String,
        val entryDestinationId: Int,
        override val displayName: String = "个人信息工作流"
    ) : RouteTarget

    data class ListTarget(
        val focusRequest: ListFocusRequest,
        override val displayName: String = "个人信息列表"
    ) : RouteTarget

    data class DetailTarget(
        val recordRef: RecordRef,
        override val displayName: String = "个人信息详情"
    ) : RouteTarget

    data class HouseDashboardTarget(
        val launchSpec: HouseDashboardLaunchSpec,
        override val displayName: String = "家居看板"
    ) : RouteTarget
}
