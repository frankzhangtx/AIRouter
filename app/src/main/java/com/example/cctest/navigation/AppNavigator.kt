package com.example.cctest.navigation

import android.content.Context
import androidx.navigation.NavController
import com.example.cctest.PersonalInfoDetailActivity
import com.example.cctest.R
import com.example.cctest.routing.model.ListFocusRequest

class AppNavigator(
    private val context: Context,
    private val navController: NavController
) {
    fun navigate(target: RouteTarget) {
        when (target) {
            is RouteTarget.NavTarget -> navigateToDestination(target.destinationId, target.args)
            is RouteTarget.WorkflowTarget -> navigateToDestination(
                destinationId = target.entryDestinationId,
                args = null
            )
            is RouteTarget.ListTarget -> navigateToDestination(
                destinationId = R.id.PersonalInfoListFragment,
                args = target.focusRequest.toBundle()
            )
            is RouteTarget.DetailTarget -> {
                context.startActivity(PersonalInfoDetailActivity.createIntent(context, target.recordRef))
            }
            is RouteTarget.HouseDashboardTarget -> {
                context.startActivity(com.example.cctest.HouseDashboardActivity.createIntent(context, target.launchSpec))
            }
        }
    }

    private fun navigateToDestination(destinationId: Int, args: android.os.Bundle?) {
        if (navController.currentDestination?.id == destinationId) {
            if (destinationId == R.id.PersonalInfoListFragment && args != null) {
                navController.currentBackStackEntry?.arguments?.putAll(args)
            }
            return
        }
        navController.navigate(destinationId, args)
    }
}
