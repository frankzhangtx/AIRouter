package com.example.cctest.routing.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.cctest.databinding.FragmentIntentEntryBinding
import com.example.cctest.routing.AppContainer
import com.example.cctest.routing.RoutingSessionViewModel
import com.example.cctest.routing.model.ParsingUiStatus
import kotlinx.coroutines.launch

class IntentEntryFragment : Fragment() {
    private var _binding: FragmentIntentEntryBinding? = null
    private val binding get() = _binding!!

    private val routingViewModel: RoutingSessionViewModel by lazy {
        ViewModelProvider(requireActivity(), AppContainer.routingViewModelFactory())[RoutingSessionViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentIntentEntryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonRunRouting.setOnClickListener {
            routingViewModel.submitUserInput(
                text = binding.editTextIntentInput.text?.toString().orEmpty(),
                entrySource = "intent_entry",
                currentDestinationId = findNavController().currentDestination?.id
            )
        }
        binding.buttonPromptList.setOnClickListener {
            binding.editTextIntentInput.setText(getString(com.example.cctest.R.string.routing_prompt_list))
        }
        binding.buttonPromptDetail.setOnClickListener {
            binding.editTextIntentInput.setText(getString(com.example.cctest.R.string.routing_prompt_detail))
        }
        binding.buttonPromptDashboard.setOnClickListener {
            binding.editTextIntentInput.setText(getString(com.example.cctest.R.string.routing_prompt_dashboard))
        }
        binding.buttonPromptForm.setOnClickListener {
            binding.editTextIntentInput.setText(getString(com.example.cctest.R.string.routing_prompt_form))
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                routingViewModel.sessionState.collect { state ->
                    binding.progressRouting.isVisible = state.parsingStatus == ParsingUiStatus.PARSING
                    binding.textviewRoutingStatus.text = buildStatusText(
                        rawInput = state.rawInputText,
                        parsingStatus = state.parsingStatus.name,
                        parserSummary = state.parserSummary(),
                        targetLabel = state.activeRouteTarget?.displayName ?: state.activeTargetLabel.orEmpty(),
                        errorSummary = state.lastErrorSummary.orEmpty(),
                        journeyStatus = state.journeyStatus.name,
                        journeyStep = state.currentJourneyStepLabel.orEmpty(),
                        journeyError = state.lastJourneyError.orEmpty()
                    )
                    binding.textviewLatestDecision.text = when {
                        state.parseResult != null -> {
                            "当前意图：${state.routeIntent.name} · 目标：${
                                (state.activeRouteTarget?.displayName ?: state.activeTargetLabel).orEmpty().ifBlank { "待决策" }
                            } · Journey：${state.journeyStatus.name}"
                        }
                        else -> getString(com.example.cctest.R.string.routing_idle_decision)
                    }
                }
            }
        }
    }

    private fun buildStatusText(
        rawInput: String,
        parsingStatus: String,
        parserSummary: String,
        targetLabel: String,
        errorSummary: String,
        journeyStatus: String,
        journeyStep: String,
        journeyError: String
    ): String {
        return buildString {
            appendLine("最近输入：${rawInput.ifBlank { "暂无" }}")
            appendLine("解析状态：$parsingStatus")
            appendLine("解析来源：$parserSummary")
            appendLine("当前目标：${targetLabel.ifBlank { "未决策" }}")
            appendLine("Journey 状态：$journeyStatus")
            appendLine("当前步骤：${journeyStep.ifBlank { "无" }}")
            appendLine("错误摘要：${errorSummary.ifBlank { "无" }}")
            append("Journey 错误：${journeyError.ifBlank { "无" }}")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
