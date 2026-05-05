package com.example.cctest.routing

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cctest.R
import com.example.cctest.navigation.AppScreen
import com.example.cctest.navigation.RouteTarget
import com.example.cctest.routing.model.JourneyUiStatus
import com.example.cctest.routing.model.ParsingUiStatus
import com.example.cctest.routing.model.SessionState
import com.example.cctest.routing.model.SubmissionUiStatus
import com.example.cctest.routing.model.UiEffect
import com.example.cctest.routing.parser.IntentParser
import com.example.cctest.routing.parser.ParseError
import com.example.cctest.routing.parser.ParseOutcome
import com.example.cctest.routing.parser.ParseRequest
import com.example.cctest.routing.parser.ParseResultValidator
import com.example.cctest.routing.parser.PersonalInfoFields
import com.example.cctest.routing.parser.SlotNormalizer
import com.example.cctest.routing.parser.UserGoal
import com.example.cctest.routing.submission.SubmissionCoordinator
import com.example.cctest.routing.workflow.RoutePlanner
import com.example.cctest.routing.workflow.WorkflowEngine
import com.example.cctest.routing.workflow.WorkflowRegistry
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RoutingSessionViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val intentParser: IntentParser,
    private val slotNormalizer: SlotNormalizer,
    private val parseResultValidator: ParseResultValidator,
    private val routePlanner: RoutePlanner,
    private val workflowRegistry: WorkflowRegistry,
    private val workflowEngine: WorkflowEngine,
    private val submissionCoordinator: SubmissionCoordinator
) : ViewModel() {

    private val _sessionState = MutableStateFlow(
        SessionState(
            rawInputText = savedStateHandle.get<String>(KEY_RAW_INPUT).orEmpty(),
            workflowId = savedStateHandle.get<String>(KEY_WORKFLOW_ID),
            currentStepId = savedStateHandle.get<String>(KEY_STEP_ID)
        )
    )
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _uiEffects = MutableSharedFlow<UiEffect>(extraBufferCapacity = 8)
    val uiEffects: SharedFlow<UiEffect> = _uiEffects.asSharedFlow()

    fun submitUserInput(
        text: String,
        entrySource: String = "manual_input",
        currentDestinationId: Int? = null
    ) {
        val trimmedInput = text.trim()
        if (trimmedInput.isBlank()) {
            emitEffect(UiEffect.ShowValidationError("请输入智能路由指令。"))
            return
        }
        updateState {
            it.copy(
                rawInputText = trimmedInput,
                parsingStatus = ParsingUiStatus.PARSING,
                lastErrorSummary = null,
                submissionStatus = SubmissionUiStatus.IDLE,
                submissionMessage = null
            )
        }
        viewModelScope.launch {
            val currentScreen = AppScreen.fromDestinationId(currentDestinationId)
            val parseRequest = ParseRequest(
                inputText = trimmedInput,
                entrySource = entrySource,
                currentDestination = currentScreen
                    .takeUnless { it == AppScreen.UNKNOWN }
                    ?.displayName
                    ?: sessionState.value.activeTargetLabel,
                sessionSnapshot = sessionState.value.currentStepId
            )
            when (val parseOutcome = intentParser.parse(parseRequest)) {
                is ParseOutcome.Failure -> {
                    updateState {
                        it.copy(
                            parsingStatus = ParsingUiStatus.FAILED,
                            lastErrorSummary = parseErrorMessage(parseOutcome.error)
                        )
                    }
                    emitEffect(UiEffect.ShowParsingUnavailable(parseErrorMessage(parseOutcome.error)))
                }

                is ParseOutcome.Success -> {
                    val normalizedResult = slotNormalizer.normalize(parseOutcome.result)
                    val validationError = parseResultValidator.validate(normalizedResult)
                    if (validationError != null) {
                        updateState {
                            it.copy(
                                parsingStatus = ParsingUiStatus.FAILED,
                                lastErrorSummary = parseErrorMessage(validationError)
                            )
                        }
                        emitEffect(UiEffect.ShowValidationError(parseErrorMessage(validationError)))
                        return@launch
                    }

                    val planningDecision = routePlanner.plan(
                        result = normalizedResult,
                        currentDestinationId = currentDestinationId,
                        currentFields = sessionState.value.formFields
                    )
                    val targetPlan = planningDecision.targetPlan
                    updateState {
                        it.copy(
                            parsingStatus = ParsingUiStatus.PARSED,
                            parseResult = normalizedResult,
                            workflowId = targetPlan.workflowId,
                            routeIntent = targetPlan.routeIntent,
                            currentStepId = targetPlan.currentStepId,
                            activeTargetLabel = targetPlan.activeTargetLabel ?: targetPlan.target?.displayName,
                            activeRouteTarget = targetPlan.target,
                            activeJourneyId = planningDecision.journeyPlan?.journeyId,
                            currentJourneyStepIndex = null,
                            currentJourneyStepLabel = null,
                            journeyStatus = if (planningDecision.journeyPlan != null) {
                                JourneyUiStatus.PLANNED
                            } else {
                                JourneyUiStatus.IDLE
                            },
                            lastJourneyError = null,
                            formFields = targetPlan.formFields ?: it.formFields,
                            recordRef = targetPlan.recordRef,
                            lastErrorSummary = null
                        )
                    }
                    targetPlan.infoMessage?.let { message ->
                        emitEffect(UiEffect.ShowJourneyStepHint(message))
                    }
                    targetPlan.fallbackMessage?.let { message ->
                        emitEffect(UiEffect.ShowRoutingFallback(message))
                    }
                    planningDecision.journeyPlan?.let { plan ->
                        emitEffect(UiEffect.ExecuteJourney(plan))
                    } ?: targetPlan.target?.let { target ->
                        emitEffect(UiEffect.NavigateTo(target))
                    }
                }
            }
        }
    }

    fun onFormContinue(fields: PersonalInfoFields) {
        val workflow = workflowRegistry.personalInfoWorkflow()
        val reviewStep = workflowEngine.findStep(workflow, WorkflowRegistry.STEP_REVIEW) ?: return
        updateState {
            it.copy(
                workflowId = workflow.workflowId,
                currentStepId = reviewStep.stepId,
                formFields = fields,
                activeTargetLabel = "REVIEW"
            )
        }
        emitEffect(
            UiEffect.NavigateTo(
                RouteTarget.NavTarget(
                    destinationId = reviewStep.destinationId,
                    displayName = "信息确认"
                )
            )
        )
    }

    fun onReviewBackToForm() {
        val workflow = workflowRegistry.personalInfoWorkflow()
        val formStep = workflowEngine.findStep(workflow, WorkflowRegistry.STEP_FORM) ?: return
        updateState {
            it.copy(
                currentStepId = formStep.stepId,
                activeTargetLabel = "FORM"
            )
        }
        emitEffect(
            UiEffect.NavigateTo(
                RouteTarget.NavTarget(
                    destinationId = formStep.destinationId,
                    displayName = "个人信息表单"
                )
            )
        )
    }

    fun submitReview() {
        viewModelScope.launch {
            updateState {
                it.copy(
                    submissionStatus = SubmissionUiStatus.SUBMITTING,
                    submissionMessage = "正在提交个人信息..."
                )
            }
            val submissionResult = submissionCoordinator.submit(sessionState.value.formFields)
            val workflow = workflowRegistry.personalInfoWorkflow()
            val resultStep = workflowEngine.findStep(workflow, WorkflowRegistry.STEP_RESULT) ?: return@launch
            updateState {
                it.copy(
                    currentStepId = resultStep.stepId,
                    activeTargetLabel = "RESULT",
                    submissionStatus = if (submissionResult.isSuccess) {
                        SubmissionUiStatus.SUCCESS
                    } else {
                        SubmissionUiStatus.FAILURE
                    },
                    submissionMessage = submissionResult.message
                )
            }
            emitEffect(
                UiEffect.NavigateTo(
                    RouteTarget.NavTarget(
                        destinationId = resultStep.destinationId,
                        displayName = "结果页"
                    )
                )
            )
        }
    }

    fun restartRouting() {
        updateState {
            SessionState()
        }
        emitEffect(
            UiEffect.NavigateTo(
                RouteTarget.NavTarget(
                    destinationId = R.id.intentEntryFragment,
                    displayName = "智能路由入口"
                )
            )
        )
    }

    fun onJourneyExecutionStarted(journeyId: String) {
        updateJourneyState(journeyId) { state ->
            state.copy(
                journeyStatus = JourneyUiStatus.EXECUTING,
                lastJourneyError = null
            )
        }
    }

    fun onJourneyStepStarted(journeyId: String, stepIndex: Int, stepLabel: String) {
        updateJourneyState(journeyId) { state ->
            state.copy(
                journeyStatus = JourneyUiStatus.EXECUTING,
                currentJourneyStepIndex = stepIndex + 1,
                currentJourneyStepLabel = stepLabel,
                lastJourneyError = null
            )
        }
    }

    fun onJourneyCompleted(journeyId: String) {
        updateJourneyState(journeyId) { state ->
            state.copy(
                journeyStatus = JourneyUiStatus.COMPLETED,
                currentJourneyStepLabel = null,
                lastJourneyError = null
            )
        }
    }

    fun onJourneyFailed(journeyId: String, message: String) {
        updateJourneyState(journeyId) { state ->
            state.copy(
                journeyStatus = JourneyUiStatus.FAILED,
                lastJourneyError = message,
                lastErrorSummary = message
            )
        }
        emitEffect(UiEffect.ShowRoutingFallback(message))
    }

    private fun parseErrorMessage(parseError: ParseError): String {
        return when (parseError) {
            ParseError.Timeout -> "解析超时，请稍后重试。"
            ParseError.Unavailable -> "解析服务暂时不可用。"
            is ParseError.InvalidSchema -> parseError.reason
            is ParseError.UnsupportedGoal -> "暂不支持该意图：${parseError.goal}"
            is ParseError.InternalError -> parseError.message
        }
    }

    private fun updateState(transform: (SessionState) -> SessionState) {
        _sessionState.update { currentState ->
            transform(currentState).also { nextState ->
                savedStateHandle[KEY_RAW_INPUT] = nextState.rawInputText
                savedStateHandle[KEY_WORKFLOW_ID] = nextState.workflowId
                savedStateHandle[KEY_STEP_ID] = nextState.currentStepId
            }
        }
    }

    private fun updateJourneyState(
        journeyId: String,
        transform: (SessionState) -> SessionState
    ) {
        updateState { currentState ->
            if (currentState.activeJourneyId != journeyId) {
                currentState
            } else {
                transform(currentState)
            }
        }
    }

    private fun emitEffect(effect: UiEffect) {
        _uiEffects.tryEmit(effect)
    }

    companion object {
        private const val KEY_RAW_INPUT = "routing_raw_input"
        private const val KEY_WORKFLOW_ID = "routing_workflow_id"
        private const val KEY_STEP_ID = "routing_step_id"
    }
}
