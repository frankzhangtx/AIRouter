package com.example.cctest.navigation

import android.os.Bundle

enum class HouseDashboardTab {
    HOME,
    WORK
}

data class HouseDashboardLaunchSpec(
    val initialTab: HouseDashboardTab = HouseDashboardTab.HOME,
    val entrySource: String = "manual"
) {
    fun toBundle(): Bundle {
        return Bundle().apply {
            putString(KEY_INITIAL_TAB, initialTab.name)
            putString(KEY_ENTRY_SOURCE, entrySource)
        }
    }

    companion object {
        const val KEY_INITIAL_TAB = "house_dashboard_initial_tab"
        const val KEY_ENTRY_SOURCE = "house_dashboard_entry_source"

        fun fromBundle(bundle: Bundle?): HouseDashboardLaunchSpec {
            val tab = bundle?.getString(KEY_INITIAL_TAB)
                ?.let { runCatching { HouseDashboardTab.valueOf(it) }.getOrNull() }
                ?: HouseDashboardTab.HOME
            val entrySource = bundle?.getString(KEY_ENTRY_SOURCE).orEmpty().ifBlank { "manual" }
            return HouseDashboardLaunchSpec(
                initialTab = tab,
                entrySource = entrySource
            )
        }
    }
}
