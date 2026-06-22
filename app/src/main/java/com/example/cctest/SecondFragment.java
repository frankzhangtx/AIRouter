package com.example.cctest;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import com.example.cctest.databinding.FragmentSecondBinding;

/**
 * A simple {@link Fragment} subclass as the second destination in the navigation.
 */
public class SecondFragment extends Fragment {

    private FragmentSecondBinding binding;

    @Override
    public View onCreateView(
        @NonNull LayoutInflater inflater,
        @Nullable ViewGroup container,
        @Nullable Bundle savedInstanceState
    ) {
        binding = FragmentSecondBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.buttonSecond.setOnClickListener(
            clickedView -> NavHostFragment.findNavController(this)
                .navigate(R.id.action_SecondFragment_to_FirstFragment)
        );

        binding.buttonPersonalInfo.setOnClickListener(
            clickedView -> NavHostFragment.findNavController(this)
                .navigate(R.id.action_SecondFragment_to_PersonalInfoFragment)
        );

        if (binding.buttonPersonalInfoList != null) {
            binding.buttonPersonalInfoList.setOnClickListener(
                clickedView -> NavHostFragment.findNavController(this)
                    .navigate(R.id.action_SecondFragment_to_PersonalInfoListFragment)
            );
        }

        binding.buttonIntelligentRouting.setOnClickListener(
            clickedView -> NavHostFragment.findNavController(this)
                .navigate(R.id.intentEntryFragment)
        );

        binding.buttonHouseDashboard.setOnClickListener(
            clickedView -> startActivity(
                new Intent(requireContext(), HouseDashboardActivity.class)
            )
        );

        binding.buttonInsuranceMall.setOnClickListener(
            clickedView -> startActivity(
                new Intent(requireContext(), InsuranceMallActivity.class)
            )
        );
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
