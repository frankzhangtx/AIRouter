package com.example.cctest

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.example.cctest.databinding.FragmentFirstBinding
import com.example.cctest.routing.AppContainer
import com.example.cctest.routing.RoutingSessionViewModel

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private val routingViewModel: RoutingSessionViewModel by lazy {
        ViewModelProvider(requireActivity(), AppContainer.routingViewModelFactory())[RoutingSessionViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonFirst.setOnClickListener {
            findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
        }
        binding.buttonFirstRoutingConfirm.setOnClickListener {
            routingViewModel.submitUserInput(
                text = binding.editTextFirstRoutingInput.text?.toString().orEmpty(),
                entrySource = "first_fragment",
                currentDestinationId = findNavController().currentDestination?.id
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
