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
import com.example.cctest.databinding.FragmentReviewSubmitBinding
import com.example.cctest.routing.AppContainer
import com.example.cctest.routing.RoutingSessionViewModel
import com.example.cctest.routing.model.SubmissionUiStatus
import kotlinx.coroutines.launch

class ReviewSubmitFragment : Fragment() {
    private var _binding: FragmentReviewSubmitBinding? = null
    private val binding get() = _binding!!

    private val routingViewModel: RoutingSessionViewModel by lazy {
        ViewModelProvider(requireActivity(), AppContainer.routingViewModelFactory())[RoutingSessionViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReviewSubmitBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonBackToEdit.setOnClickListener {
            routingViewModel.onReviewBackToForm()
        }
        binding.buttonSubmitReview.setOnClickListener {
            routingViewModel.submitReview()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                routingViewModel.sessionState.collect { state ->
                    binding.textviewReviewSummary.text = state.formFields.toSummary(
                        emptyValue = getString(R.string.personal_info_empty_value)
                    )
                    binding.textviewReviewMeta.text = getString(
                        R.string.routing_review_meta_format,
                        state.routeIntent.name,
                        state.parserSummary(),
                        reviewStatusLabel(state.submissionStatus)
                    )
                }
            }
        }
    }

    private fun reviewStatusLabel(status: SubmissionUiStatus): String {
        return when (status) {
            SubmissionUiStatus.IDLE -> getString(R.string.routing_review_status_ready)
            SubmissionUiStatus.SUBMITTING -> getString(R.string.routing_review_status_submitting)
            SubmissionUiStatus.SUCCESS -> getString(R.string.routing_review_status_success)
            SubmissionUiStatus.FAILURE -> getString(R.string.routing_review_status_failure)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
