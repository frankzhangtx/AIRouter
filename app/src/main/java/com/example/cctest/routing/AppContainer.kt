package com.example.cctest.routing

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import com.example.cctest.BuildConfig
import com.example.cctest.feature.personalinfo.data.PersonalInfoRecordResolver
import com.example.cctest.feature.personalinfo.data.PersonalInfoRepository
import com.example.cctest.navigation.DestinationContractRegistry
import com.example.cctest.navigation.EntryActionRegistry
import com.example.cctest.routing.parser.CompositeIntentParser
import com.example.cctest.routing.parser.IntentParsingConfig
import com.example.cctest.routing.parser.LlmIntentParser
import com.example.cctest.routing.parser.ParseResultValidator
import com.example.cctest.routing.parser.RemoteIntentParsingGateway
import com.example.cctest.routing.parser.RuleBasedIntentParser
import com.example.cctest.routing.parser.SlotNormalizer
import com.example.cctest.routing.submission.FakeSubmissionPort
import com.example.cctest.routing.submission.SubmissionCoordinator
import com.example.cctest.routing.workflow.JourneyPlanner
import com.example.cctest.routing.workflow.RoutePlanner
import com.example.cctest.routing.workflow.TargetPlanner
import com.example.cctest.routing.workflow.WorkflowEngine
import com.example.cctest.routing.workflow.WorkflowRegistry
import java.lang.IllegalArgumentException

object AppContainer {
    private val repository by lazy { PersonalInfoRepository() }
    private val recordResolver by lazy { PersonalInfoRecordResolver(repository) }
    private val destinationContractRegistry by lazy { DestinationContractRegistry() }
    private val entryActionRegistry by lazy { EntryActionRegistry() }
    private val workflowRegistry by lazy { WorkflowRegistry() }
    private val workflowEngine by lazy { WorkflowEngine() }
    private val validator by lazy { ParseResultValidator() }
    private val normalizer by lazy { SlotNormalizer() }
    private val intentParsingConfig by lazy {
        IntentParsingConfig(
            enableLlmParsing = BuildConfig.ROUTING_ENABLE_LLM_PARSING,
            fallbackToRuleOnLlmFailure = BuildConfig.ROUTING_FALLBACK_TO_RULE_ON_LLM_FAILURE,
            minRuleConfidence = BuildConfig.ROUTING_MIN_RULE_CONFIDENCE,
            llmRequestTimeoutMs = BuildConfig.ROUTING_LLM_REQUEST_TIMEOUT_MS,
            llmConnectTimeoutMs = BuildConfig.ROUTING_LLM_CONNECT_TIMEOUT_MS,
            llmReadTimeoutMs = BuildConfig.ROUTING_LLM_READ_TIMEOUT_MS,
            llmBaseUrl = BuildConfig.ROUTING_LLM_BASE_URL,
            llmApiKey = BuildConfig.ROUTING_LLM_API_KEY,
            llmModel = BuildConfig.ROUTING_LLM_MODEL,
            llmProviderName = BuildConfig.ROUTING_LLM_PROVIDER_NAME,
            llmPromptVersion = BuildConfig.ROUTING_LLM_PROMPT_VERSION,
            llmSchemaVersion = BuildConfig.ROUTING_LLM_SCHEMA_VERSION
        )
    }
    private val compositeIntentParser by lazy {
        CompositeIntentParser(
            ruleBasedIntentParser = RuleBasedIntentParser(),
            llmIntentParser = LlmIntentParser(RemoteIntentParsingGateway(intentParsingConfig)),
            config = intentParsingConfig
        )
    }
    private val targetPlanner by lazy {
        TargetPlanner(
            destinationContractRegistry = destinationContractRegistry,
            recordResolver = recordResolver,
            workflowRegistry = workflowRegistry,
            workflowEngine = workflowEngine
        )
    }
    private val journeyPlanner by lazy {
        JourneyPlanner(
            entryActionRegistry = entryActionRegistry
        )
    }
    private val routePlanner by lazy {
        RoutePlanner(
            targetPlanner = targetPlanner,
            journeyPlanner = journeyPlanner
        )
    }
    private val submissionCoordinator by lazy { SubmissionCoordinator(FakeSubmissionPort()) }

    fun repository(): PersonalInfoRepository = repository

    fun recordResolver(): PersonalInfoRecordResolver = recordResolver

    fun routingViewModelFactory(): ViewModelProvider.Factory {
        return object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(
                modelClass: Class<T>,
                extras: androidx.lifecycle.viewmodel.CreationExtras
            ): T {
                if (!modelClass.isAssignableFrom(RoutingSessionViewModel::class.java)) {
                    throw IllegalArgumentException("Unsupported ViewModel class: ${modelClass.name}")
                }
                val savedStateHandle = extras.createSavedStateHandle()
                val viewModel = RoutingSessionViewModel(
                    savedStateHandle = savedStateHandle,
                    intentParser = compositeIntentParser,
                    slotNormalizer = normalizer,
                    parseResultValidator = validator,
                    routePlanner = routePlanner,
                    workflowRegistry = workflowRegistry,
                    workflowEngine = workflowEngine,
                    submissionCoordinator = submissionCoordinator
                )
                return modelClass.cast(viewModel)
            }
        }
    }
}
