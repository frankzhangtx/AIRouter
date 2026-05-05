package com.example.cctest

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.cctest.databinding.ActivityHouseDashboardBinding
import com.example.cctest.navigation.HouseDashboardLaunchSpec
import com.example.cctest.navigation.HouseDashboardTab

class HouseDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHouseDashboardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityHouseDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val launchSpec = HouseDashboardLaunchSpec.fromBundle(intent.extras)
        renderSelectedTab(launchSpec.initialTab)
        binding.textviewDashboardRouteHint.text = getString(
            R.string.house_dashboard_route_hint,
            launchSpec.entrySource,
            launchSpec.initialTab.name
        )

        binding.buttonHomeTab.setOnClickListener {
            renderSelectedTab(HouseDashboardTab.HOME)
        }
        binding.buttonWorkTab.setOnClickListener {
            renderSelectedTab(HouseDashboardTab.WORK)
        }

        binding.buttonRefreshWeather.setOnClickListener {
            Toast.makeText(this, R.string.house_dashboard_refresh_message, Toast.LENGTH_SHORT).show()
        }

        binding.fabEditTasks.setOnClickListener {
            Toast.makeText(this, R.string.house_dashboard_edit_message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun renderSelectedTab(tab: HouseDashboardTab) {
        val selectedStrokeColor = ContextCompat.getColor(this, R.color.purple_700)
        binding.buttonHomeTab.isChecked = tab == HouseDashboardTab.HOME
        binding.buttonWorkTab.isChecked = tab == HouseDashboardTab.WORK
        binding.buttonHomeTab.strokeWidth = if (tab == HouseDashboardTab.HOME) resources.getDimensionPixelSize(R.dimen.house_dashboard_selected_stroke) else 0
        binding.buttonWorkTab.strokeWidth = if (tab == HouseDashboardTab.WORK) resources.getDimensionPixelSize(R.dimen.house_dashboard_selected_stroke) else 0
        binding.buttonHomeTab.setStrokeColorResource(if (tab == HouseDashboardTab.HOME) R.color.purple_700 else android.R.color.transparent)
        binding.buttonWorkTab.setStrokeColorResource(if (tab == HouseDashboardTab.WORK) R.color.purple_700 else android.R.color.transparent)
        binding.buttonHomeTab.iconTint = android.content.res.ColorStateList.valueOf(selectedStrokeColor)
        binding.buttonWorkTab.iconTint = android.content.res.ColorStateList.valueOf(selectedStrokeColor)
    }

    companion object {
        fun createIntent(context: Context, launchSpec: HouseDashboardLaunchSpec): Intent {
            return Intent(context, HouseDashboardActivity::class.java).apply {
                putExtras(launchSpec.toBundle())
            }
        }
    }
}
