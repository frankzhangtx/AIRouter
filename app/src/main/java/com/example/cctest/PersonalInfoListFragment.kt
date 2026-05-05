package com.example.cctest

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.cctest.navigation.JourneyEventKeys
import com.example.cctest.databinding.FragmentPersonalInfoListBinding
import com.example.cctest.feature.personalinfo.data.PersonalInfoRecord
import com.example.cctest.routing.AppContainer
import com.example.cctest.routing.model.ListFocusRequest
import kotlinx.coroutines.launch

class PersonalInfoListFragment : Fragment() {

    private var _binding: FragmentPersonalInfoListBinding? = null
    private val binding get() = _binding!!
    private val repository = AppContainer.repository()
    private val recordResolver = AppContainer.recordResolver()
    private lateinit var records: List<PersonalInfoRecord>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPersonalInfoListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        records = repository.getRecords()
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_activated_1,
            records.mapIndexed { index, item -> item.toDisplayText(index) }
        )
        binding.listviewPersonalInfo.choiceMode = ListView.CHOICE_MODE_SINGLE
        binding.listviewPersonalInfo.adapter = adapter
        binding.listviewPersonalInfo.setOnItemClickListener { _, _, position, _ ->
            startActivity(PersonalInfoDetailActivity.createIntent(requireContext(), records[position]))
        }

        observeJourneyFocusRequests()

        val focusRequest = ListFocusRequest.fromBundle(arguments)
        if (focusRequest != null) {
            applyFocusRequest(focusRequest)
        } else {
            binding.textviewFocusStatus.text = getString(R.string.personal_info_list_idle_status)
        }
    }

    private fun applyFocusRequest(focusRequest: ListFocusRequest) {
        val resolution = recordResolver.resolveFromFocusRequest(focusRequest)
        val targetRecord = resolution.matchedRecord ?: resolution.candidates.firstOrNull()
        if (targetRecord == null && focusRequest.position == null) {
            binding.textviewFocusStatus.text = getString(
                R.string.personal_info_list_focus_not_found,
                focusRequest.summaryLabel()
            )
            return
        }
        val targetIndex = when {
            targetRecord != null -> records.indexOfFirst { it.recordId == targetRecord.recordId }
            focusRequest.position != null -> focusRequest.position - 1
            else -> -1
        }
        if (targetIndex !in records.indices) {
            binding.textviewFocusStatus.text = getString(
                R.string.personal_info_list_focus_not_found,
                focusRequest.summaryLabel()
            )
            return
        }
        binding.listviewPersonalInfo.post {
            binding.listviewPersonalInfo.clearChoices()
            binding.listviewPersonalInfo.setSelection(targetIndex)
            binding.listviewPersonalInfo.smoothScrollToPosition(targetIndex)
            binding.listviewPersonalInfo.setItemChecked(targetIndex, true)
        }
        binding.textviewFocusStatus.text = getString(
            R.string.personal_info_list_focus_status,
            focusRequest.summaryLabel(),
            targetIndex + 1,
            if (resolution.candidates.size > 1) {
                getString(R.string.personal_info_list_focus_ambiguous, resolution.candidates.size)
            } else {
                getString(R.string.personal_info_list_focus_unique)
            }
        )
        if (focusRequest.autoOpenDetail && resolution.isUnique) {
            startActivity(PersonalInfoDetailActivity.createIntent(requireContext(), resolution.matchedRecord!!))
        }
    }

    private fun observeJourneyFocusRequests() {
        val backStackEntry = findNavController().currentBackStackEntry ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                backStackEntry.savedStateHandle
                    .getStateFlow<Bundle?>(JourneyEventKeys.KEY_LIST_FOCUS_REQUEST, null)
                    .collect { bundle ->
                        val focusRequest = ListFocusRequest.fromBundle(bundle) ?: return@collect
                        applyFocusRequest(focusRequest)
                        backStackEntry.savedStateHandle.set(
                            JourneyEventKeys.KEY_LIST_FOCUS_REQUEST,
                            null as Bundle?
                        )
                    }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
