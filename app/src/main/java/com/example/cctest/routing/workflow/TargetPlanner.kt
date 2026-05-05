package com.example.cctest.routing.workflow

import com.example.cctest.navigation.DestinationContractRegistry
import com.example.cctest.navigation.DestinationKey
import com.example.cctest.navigation.HouseDashboardLaunchSpec
import com.example.cctest.navigation.HouseDashboardTab
import com.example.cctest.navigation.RouteTarget
import com.example.cctest.feature.personalinfo.data.PersonalInfoRecordResolver
import com.example.cctest.routing.model.ListFocusRequest
import com.example.cctest.routing.model.RecordRef
import com.example.cctest.routing.parser.ParseResult
import com.example.cctest.routing.parser.PersonalInfoFields
import com.example.cctest.routing.parser.UserGoal

data class TargetPlan(
    val target: RouteTarget? = null,
    val workflowId: String? = null,
    val currentStepId: String? = null,
    val routeIntent: UserGoal = UserGoal.Unknown,
    val activeTargetLabel: String? = null,
    val formFields: PersonalInfoFields? = null,
    val recordRef: RecordRef? = null,
    val listFocusRequest: ListFocusRequest? = null,
    val infoMessage: String? = null,
    val fallbackMessage: String? = null
)

class TargetPlanner(
    private val destinationContractRegistry: DestinationContractRegistry,
    private val recordResolver: PersonalInfoRecordResolver,
    private val workflowRegistry: WorkflowRegistry,
    private val workflowEngine: WorkflowEngine
) {
    fun plan(result: ParseResult, currentFields: PersonalInfoFields): TargetPlan {
        return when (result.userGoal) {
            UserGoal.FillPersonalInfo -> planPersonalInfoWorkflow(result, currentFields)
            UserGoal.BrowsePersonalInfoList -> planListTarget(result)
            UserGoal.OpenPersonalInfoDetail -> planDetailTarget(result)
            UserGoal.OpenHouseDashboard -> planHouseDashboard(result)
            UserGoal.Unknown -> TargetPlan(
                routeIntent = UserGoal.Unknown,
                fallbackMessage = "暂时无法识别这句请求，建议换成“查看第 12 条记录”或“打开家居看板”。"
            )
        }
    }

    private fun planPersonalInfoWorkflow(
        result: ParseResult,
        currentFields: PersonalInfoFields
    ): TargetPlan {
        val contract = destinationContractRegistry.require(DestinationKey.PERSONAL_INFO_FORM)
        val workflow = workflowRegistry.personalInfoWorkflow()
        val step = workflowEngine.start(workflow)
        val mergedFields = currentFields.mergeFrom(result.slots.personalInfoFields)
        return TargetPlan(
            target = RouteTarget.WorkflowTarget(
                workflowId = workflow.workflowId,
                entryDestinationId = step.destinationId,
                displayName = contract.screen?.displayName ?: "个人信息工作流"
            ),
            workflowId = workflow.workflowId,
            currentStepId = step.stepId,
            routeIntent = result.userGoal,
            activeTargetLabel = contract.screen?.displayName ?: contract.pageType.name,
            formFields = mergedFields,
            infoMessage = if (result.slots.personalInfoFields.hasAnyValue()) {
                "已带入解析出的个人信息字段。"
            } else {
                "已进入个人信息工作流。"
            }
        )
    }

    private fun planListTarget(result: ParseResult): TargetPlan {
        val contract = destinationContractRegistry.require(DestinationKey.PERSONAL_INFO_LIST)
        val resolution = recordResolver.resolveFromSlots(result.slots)
        val focusRequest = when {
            resolution.isUnique -> resolution.toListFocusRequest(autoOpenDetail = false)
            result.slots.listPosition != null -> ListFocusRequest(
                position = result.slots.listPosition,
                matchMode = ListFocusRequest.MATCH_MODE_POSITION,
                autoOpenDetail = false
            )

            !result.slots.personName.isNullOrBlank() ||
                !result.slots.phone.isNullOrBlank() ||
                !result.slots.city.isNullOrBlank() -> {
                if (resolution.candidates.isNotEmpty()) {
                    resolution.toListFocusRequest(autoOpenDetail = false)
                } else {
                    ListFocusRequest(
                        queryName = result.slots.personName,
                        queryPhone = result.slots.phone,
                        queryCity = result.slots.city,
                        matchMode = ListFocusRequest.MATCH_MODE_NONE
                    )
                }
            }

            else -> ListFocusRequest()
        }
        return TargetPlan(
            target = RouteTarget.ListTarget(focusRequest),
            routeIntent = result.userGoal,
            activeTargetLabel = contract.screen?.displayName ?: contract.pageType.name,
            recordRef = resolution.toRecordRef(),
            listFocusRequest = focusRequest.takeIf { it.hasLocator() },
            infoMessage = if (focusRequest.position != null || focusRequest.recordId != null) {
                "已生成列表定位请求。"
            } else {
                "已准备进入个人信息列表。"
            }
        )
    }

    private fun planDetailTarget(result: ParseResult): TargetPlan {
        val resolution = recordResolver.resolveFromSlots(result.slots)
        return if (resolution.isUnique) {
            val contract = destinationContractRegistry.require(DestinationKey.PERSONAL_INFO_DETAIL)
            val recordRef = resolution.toRecordRef() ?: RecordRef(
                displayName = result.slots.personName,
                phone = result.slots.phone,
                city = result.slots.city
            )
            TargetPlan(
                target = RouteTarget.DetailTarget(recordRef),
                routeIntent = result.userGoal,
                activeTargetLabel = contract.screen?.displayName ?: contract.pageType.name,
                recordRef = recordRef,
                listFocusRequest = resolution.toListFocusRequest(autoOpenDetail = false),
                infoMessage = "已唯一命中详情目标，将先在列表定位再打开详情。"
            )
        } else {
            val contract = destinationContractRegistry.require(DestinationKey.PERSONAL_INFO_LIST)
            val fallbackRequest = if (resolution.candidates.isNotEmpty()) {
                resolution.toListFocusRequest(autoOpenDetail = false)
            } else {
                ListFocusRequest(
                    queryName = result.slots.personName,
                    queryPhone = result.slots.phone,
                    queryCity = result.slots.city,
                    matchMode = ListFocusRequest.MATCH_MODE_NONE
                )
            }
            TargetPlan(
                target = RouteTarget.ListTarget(fallbackRequest),
                routeIntent = result.userGoal,
                activeTargetLabel = contract.screen?.displayName ?: contract.pageType.name,
                recordRef = resolution.toRecordRef(),
                listFocusRequest = fallbackRequest.takeIf { it.hasLocator() },
                fallbackMessage = if (resolution.candidates.isNotEmpty()) {
                    "详情目标存在歧义，已回退到列表页供你确认。"
                } else {
                    "未直接命中详情记录，已回退到列表页。"
                }
            )
        }
    }

    private fun planHouseDashboard(result: ParseResult): TargetPlan {
        val contract = destinationContractRegistry.require(DestinationKey.HOUSE_DASHBOARD)
        val launchSpec = HouseDashboardLaunchSpec(
            initialTab = result.slots.dashboardTab ?: HouseDashboardTab.HOME,
            entrySource = "intelligent-routing"
        )
        return TargetPlan(
            target = RouteTarget.HouseDashboardTarget(launchSpec),
            routeIntent = result.userGoal,
            activeTargetLabel = contract.screen?.displayName ?: contract.pageType.name,
            infoMessage = "已命中家居看板目标，将按既有页面路径打开。"
        )
    }
}
