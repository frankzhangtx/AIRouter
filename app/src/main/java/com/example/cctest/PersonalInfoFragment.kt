package com.example.cctest

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.cctest.databinding.FragmentPersonalInfoBinding

class PersonalInfoFragment : Fragment() {

    private var _binding: FragmentPersonalInfoBinding? = null
    private val binding get() = _binding!!

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

        binding.buttonSavePersonalInfo.setOnClickListener {
            val emptyValue = getString(R.string.personal_info_empty_value)
            val name = binding.editTextName.text?.toString()?.trim().orEmpty()
            val age = binding.editTextAge.text?.toString()?.trim().orEmpty()
            val hobbies = binding.editTextHobbies.text?.toString()?.trim().orEmpty()
            val phone = binding.editTextPhone.text?.toString()?.trim().orEmpty()
            val email = binding.editTextEmail.text?.toString()?.trim().orEmpty()
            val address = binding.editTextAddress.text?.toString()?.trim().orEmpty()
            val occupation = binding.editTextOccupation.text?.toString()?.trim().orEmpty()
            val company = binding.editTextCompany.text?.toString()?.trim().orEmpty()
            val emergencyContact = binding.editTextEmergencyContact.text?.toString()?.trim().orEmpty()

            val displayName = name.ifEmpty { emptyValue }
            val displayAge = age.ifEmpty { emptyValue }
            val displayHobbies = hobbies.ifEmpty { emptyValue }
            val displayPhone = phone.ifEmpty { emptyValue }
            val displayEmail = email.ifEmpty { emptyValue }
            val displayAddress = address.ifEmpty { emptyValue }
            val displayOccupation = occupation.ifEmpty { emptyValue }
            val displayCompany = company.ifEmpty { emptyValue }
            val displayEmergencyContact = emergencyContact.ifEmpty { emptyValue }

            binding.textviewSummary.text = getString(
                R.string.personal_info_summary_format,
                displayName,
                displayAge,
                displayHobbies,
                displayPhone,
                displayEmail,
                displayAddress,
                displayOccupation,
                displayCompany,
                displayEmergencyContact
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
