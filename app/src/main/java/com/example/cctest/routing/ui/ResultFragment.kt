package com.example.cctest.routing.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.cctest.R
import com.example.cctest.databinding.FragmentResultBinding
import com.example.cctest.routing.AppContainer
import com.example.cctest.routing.RoutingSessionViewModel
import com.example.cctest.routing.model.SubmissionUiStatus
import kotlinx.coroutines.launch

class ResultFragment : Fragment() {
    private var _binding: FragmentResultBinding? = null
    private val binding get() = _binding!!

    private val routingViewModel: RoutingSessionViewModel by lazy {
        ViewModelProvider(requireActivity(), AppContainer.routingViewModelFactory())[RoutingSessionViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonRestartRouting.setOnClickListener {
            routingViewModel.restartRouting()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                routingViewModel.sessionState.collect { state ->
                    val titleRes = if (state.submissionStatus == SubmissionUiStatus.SUCCESS) {
                        R.string.routing_result_success_title
                    } else {
                        R.string.routing_result_failure_title
                    }
                    val fallbackBody = getString(R.string.routing_result_empty)
                    binding.textviewResultTitle.text = getString(titleRes)
                    binding.textviewResultBody.text = if (state.submissionStatus == SubmissionUiStatus.SUCCESS) {
                        getString(
                            R.string.routing_result_success_body_format,
                            state.submissionMessage.orEmpty().ifBlank { fallbackBody }
                        )
                    } else {
                        getString(
                            R.string.routing_result_failure_body_format,
                            state.submissionMessage.orEmpty().ifBlank { fallbackBody }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
