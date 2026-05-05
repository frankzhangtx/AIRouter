package com.example.cctest.routing.workflow

import com.example.cctest.R
import com.example.cctest.navigation.AppScreen
import com.example.cctest.navigation.EntryAction
import com.example.cctest.navigation.EntryActionKey
import com.example.cctest.navigation.EntryActionRegistry
import com.example.cctest.navigation.JourneyPlan
import com.example.cctest.navigation.JourneyStep
import com.example.cctest.navigation.RouteTarget
import com.example.cctest.routing.model.ListFocusRequest
import java.util.UUID

class JourneyPlanner(
    private val entryActionRegistry: EntryActionRegistry
) {
    fun plan(currentDestinationId: Int?, targetPlan: TargetPlan): JourneyPlan? {
        val target = targetPlan.target ?: return null
        val entryScreen = normalizeEntryScreen(AppScreen.fromDestinationId(currentDestinationId))
        val steps = when (target) {
            is RouteTarget.WorkflowTarget -> buildWorkflowJourney(entryScreen, targetPlan, target)
            is RouteTarget.ListTarget -> buildListJourney(entryScreen, targetPlan)
            is RouteTarget.DetailTarget -> buildDetailJourney(entryScreen, targetPlan, target)
            is RouteTarget.HouseDashboardTarget -> buildHouseDashboardJourney(entryScreen, targetPlan, target)
            is RouteTarget.NavTarget -> listOf(
                JourneyStep.NavigateToFragment(
                    destinationId = target.destinationId,
                    args = target.args,
                    displayLabel = target.displayName
                )
            )
        }
        return JourneyPlan(
            journeyId = UUID.randomUUID().toString(),
            entryScreen = entryScreen,
            target = target,
            steps = steps,
            fallbackPolicy = targetPlan.fallbackMessage ?: "stop_on_current_screen",
            completionCondition = target.displayName
        )
    }

    private fun buildWorkflowJourney(
        entryScreen: AppScreen,
        targetPlan: TargetPlan,
        target: RouteTarget.WorkflowTarget
    ): List<JourneyStep> {
        return resolvePathSteps(
            entryScreen = entryScreen,
            targetScreen = AppScreen.PERSONAL_INFO_FORM,
            targetPlan = targetPlan,
            directStep = JourneyStep.NavigateToFragment(
                destinationId = target.entryDestinationId,
                displayLabel = "进入个人信息工作流"
            )
        )
    }

    private fun buildListJourney(
        entryScreen: AppScreen,
        targetPlan: TargetPlan
    ): List<JourneyStep> {
        val steps = resolvePathSteps(
            entryScreen = entryScreen,
            targetScreen = AppScreen.PERSONAL_INFO_LIST,
            targetPlan = targetPlan,
            directStep = JourneyStep.NavigateToFragment(
                destinationId = R.id.PersonalInfoListFragment,
                displayLabel = "打开个人信息列表"
            )
        ).toMutableList()
        targetPlan.listFocusRequest
            ?.takeIf { it.hasLocator() }
            ?.let { steps += JourneyStep.FocusList(it) }
        targetPlan.fallbackMessage?.let { steps += JourneyStep.ShowFallbackMessage(it) }
        steps += JourneyStep.StopAtCurrentScreen(
            reason = if (targetPlan.fallbackMessage != null) "fallback_list" else "list_ready"
        )
        return steps
    }

    private fun buildDetailJourney(
        entryScreen: AppScreen,
        targetPlan: TargetPlan,
        target: RouteTarget.DetailTarget
    ): List<JourneyStep> {
        val steps = resolvePathSteps(
            entryScreen = entryScreen,
            targetScreen = AppScreen.PERSONAL_INFO_LIST,
            targetPlan = targetPlan,
            directStep = JourneyStep.NavigateToFragment(
                destinationId = R.id.PersonalInfoListFragment,
                displayLabel = "打开个人信息列表"
            )
        ).toMutableList()
        val focusRequest = targetPlan.listFocusRequest ?: ListFocusRequest(
            recordId = target.recordRef.recordId,
            queryName = target.recordRef.displayName,
            queryPhone = target.recordRef.phone,
            queryCity = target.recordRef.city,
            matchMode = ListFocusRequest.MATCH_MODE_RECORD_ID
        )
        if (focusRequest.hasLocator()) {
            steps += JourneyStep.FocusList(focusRequest)
        }
        steps += JourneyStep.OpenFocusedRecordDetail(target.recordRef)
        return steps
    }

    private fun buildHouseDashboardJourney(
        entryScreen: AppScreen,
        targetPlan: TargetPlan,
        target: RouteTarget.HouseDashboardTarget
    ): List<JourneyStep> {
        return resolvePathSteps(
            entryScreen = entryScreen,
            targetScreen = AppScreen.HOUSE_DASHBOARD,
            targetPlan = targetPlan,
            directStep = JourneyStep.OpenHouseDashboard(
                launchSpec = target.launchSpec,
                displayLabel = "打开家居看板"
            )
        )
    }

    private fun resolvePathSteps(
        entryScreen: AppScreen,
        targetScreen: AppScreen,
        targetPlan: TargetPlan,
        directStep: JourneyStep
    ): List<JourneyStep> {
        if (entryScreen == targetScreen) {
            return emptyList()
        }

        val actions = entryActionRegistry.resolvePath(entryScreen, targetScreen)
        if (actions.isNotEmpty()) {
            return actions.flatMap { action ->
                actionToSteps(action, targetPlan)
            }
        }

        return listOf(directStep)
    }

    private fun actionToSteps(
        action: EntryAction,
        targetPlan: TargetPlan
    ): List<JourneyStep> {
        return when (action.key) {
            EntryActionKey.GO_TO_SECOND,
            EntryActionKey.RETURN_TO_SECOND -> listOf(
                JourneyStep.NavigateToFragment(
                    destinationId = R.id.SecondFragment,
                    displayLabel = action.displayLabel
                )
            )

            EntryActionKey.OPEN_PERSONAL_INFO_LIST -> listOf(
                JourneyStep.NavigateToFragment(
                    destinationId = R.id.PersonalInfoListFragment,
                    displayLabel = action.displayLabel
                )
            )

            EntryActionKey.ENTER_PERSONAL_INFO_WORKFLOW -> {
                val workflowTarget = targetPlan.target as? RouteTarget.WorkflowTarget
                listOf(
                    JourneyStep.NavigateToFragment(
                        destinationId = workflowTarget?.entryDestinationId ?: R.id.personalInfoFormStepFragment,
                        displayLabel = action.displayLabel
                    )
                )
            }

            EntryActionKey.OPEN_HOUSE_DASHBOARD -> {
                val houseTarget = targetPlan.target as? RouteTarget.HouseDashboardTarget ?: return emptyList()
                listOf(
                    JourneyStep.OpenHouseDashboard(
                        launchSpec = houseTarget.launchSpec,
                        displayLabel = action.displayLabel
                    )
                )
            }

            EntryActionKey.OPEN_FOCUSED_DETAIL -> {
                val detailTarget = targetPlan.target as? RouteTarget.DetailTarget ?: return emptyList()
                listOf(
                    JourneyStep.OpenFocusedRecordDetail(
                        recordRef = detailTarget.recordRef,
                        displayLabel = action.displayLabel
                    )
                )
            }
        }
    }

    private fun normalizeEntryScreen(screen: AppScreen): AppScreen {
        return if (screen == AppScreen.UNKNOWN) AppScreen.SECOND else screen
    }
}
