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
import com.example.cctest.databinding.FragmentPersonalInfoBinding
import com.example.cctest.routing.AppContainer
import com.example.cctest.routing.RoutingSessionViewModel
import com.example.cctest.routing.parser.PersonalInfoFields
import kotlinx.coroutines.launch

class PersonalInfoFormStepFragment : Fragment() {
    private var _binding: FragmentPersonalInfoBinding? = null
    private val binding get() = _binding!!

    private val routingViewModel: RoutingSessionViewModel by lazy {
        ViewModelProvider(requireActivity(), AppContainer.routingViewModelFactory())[RoutingSessionViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPersonalInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.textviewPersonalInfoTitle.text = getString(R.string.routing_form_title)
        binding.textviewSummaryTitle.text = getString(R.string.routing_form_summary_title)
        binding.buttonSavePersonalInfo.text = getString(R.string.routing_continue_to_review)

        binding.buttonSavePersonalInfo.setOnClickListener {
            routingViewModel.onFormContinue(readFormFields())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                routingViewModel.sessionState.collect { state ->
                    renderFormFields(state.formFields)
                    binding.textviewSummary.text = state.formFields.toSummary(
                        emptyValue = getString(R.string.personal_info_empty_value)
                    )
                }
            }
        }
    }

    private fun renderFormFields(fields: PersonalInfoFields) {
        setTextIfDifferent(binding.editTextName, fields.name)
        setTextIfDifferent(binding.editTextAge, fields.age?.toString())
        setTextIfDifferent(binding.editTextPhone, fields.phone)
        setTextIfDifferent(binding.editTextEmail, fields.email)
        setTextIfDifferent(binding.editTextAddress, fields.address)
        setTextIfDifferent(binding.editTextOccupation, fields.occupation)
        setTextIfDifferent(binding.editTextCompany, fields.company)
        setTextIfDifferent(binding.editTextHobbies, fields.hobbies)
        setTextIfDifferent(binding.editTextEmergencyContact, fields.emergencyContact)
    }

    private fun readFormFields(): PersonalInfoFields {
        return PersonalInfoFields(
            name = binding.editTextName.text?.toString()?.trim().orEmpty().ifBlank { null },
            age = binding.editTextAge.text?.toString()?.trim()?.toIntOrNull(),
            phone = binding.editTextPhone.text?.toString()?.trim().orEmpty().ifBlank { null },
            email = binding.editTextEmail.text?.toString()?.trim().orEmpty().ifBlank { null },
            address = binding.editTextAddress.text?.toString()?.trim().orEmpty().ifBlank { null },
            occupation = binding.editTextOccupation.text?.toString()?.trim().orEmpty().ifBlank { null },
            company = binding.editTextCompany.text?.toString()?.trim().orEmpty().ifBlank { null },
            hobbies = binding.editTextHobbies.text?.toString()?.trim().orEmpty().ifBlank { null },
            emergencyContact = binding.editTextEmergencyContact.text?.toString()?.trim().orEmpty().ifBlank { null }
        )
    }

    private fun setTextIfDifferent(textField: com.google.android.material.textfield.TextInputEditText, value: String?) {
        val currentValue = textField.text?.toString().orEmpty()
        val nextValue = value.orEmpty()
        if (currentValue != nextValue) {
            textField.setText(nextValue)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
