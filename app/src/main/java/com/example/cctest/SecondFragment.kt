package com.example.cctest

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.example.cctest.databinding.FragmentSecondBinding

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class SecondFragment : Fragment() {

    private var _binding: FragmentSecondBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentSecondBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonSecond.setOnClickListener {
            findNavController().navigate(R.id.action_SecondFragment_to_FirstFragment)
        }

        binding.buttonPersonalInfo.setOnClickListener {
            findNavController().navigate(R.id.action_SecondFragment_to_PersonalInfoFragment)
        }

        binding.buttonPersonalInfoList?.setOnClickListener {
            findNavController().navigate(R.id.action_SecondFragment_to_PersonalInfoListFragment)
        }

        binding.buttonIntelligentRouting.setOnClickListener {
            findNavController().navigate(R.id.intentEntryFragment)
        }

        binding.buttonHouseDashboard.setOnClickListener {
            startActivity(Intent(requireContext(), HouseDashboardActivity::class.java))
        }

        binding.buttonInsuranceMall.setOnClickListener {
            startActivity(Intent(requireContext(), InsuranceMallActivity::class.java))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
