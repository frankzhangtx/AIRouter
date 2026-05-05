package com.example.cctest

import android.os.Bundle
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import android.view.Menu
import android.view.MenuItem
import com.example.cctest.databinding.ActivityMainBinding
import com.example.cctest.navigation.AppNavigator
import com.example.cctest.navigation.JourneyExecutor
import com.example.cctest.routing.AppContainer
import com.example.cctest.routing.RoutingSessionViewModel
import com.example.cctest.routing.model.UiEffect
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private val routingViewModel: RoutingSessionViewModel by lazy {
        ViewModelProvider(this, AppContainer.routingViewModelFactory())[RoutingSessionViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        val appNavigator = AppNavigator(this, navController)
        val journeyExecutor = JourneyExecutor(
            context = this,
            navController = navController,
            appNavigator = appNavigator,
            scope = lifecycleScope,
            onJourneyStarted = { plan ->
                routingViewModel.onJourneyExecutionStarted(plan.journeyId)
            },
            onJourneyStepChanged = { journeyId, stepIndex, step ->
                routingViewModel.onJourneyStepStarted(journeyId, stepIndex, step.displayLabel)
            },
            onJourneyCompleted = routingViewModel::onJourneyCompleted,
            onJourneyFailed = routingViewModel::onJourneyFailed,
            onMessage = ::showMessage
        )
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        binding.fab.setOnClickListener { view ->
            Snackbar.make(view, getString(R.string.snackbar_placeholder_message), Snackbar.LENGTH_LONG)
                .setAction(getString(R.string.snackbar_placeholder_action), null)
                .setAnchorView(R.id.fab).show()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                routingViewModel.uiEffects.collect { effect ->
                    when (effect) {
                        is UiEffect.ExecuteJourney -> journeyExecutor.execute(effect.plan)
                        is UiEffect.NavigateTo -> appNavigator.navigate(effect.target)
                        is UiEffect.ShowParsingUnavailable -> showMessage(effect.message)
                        is UiEffect.ShowRoutingFallback -> showMessage(effect.message)
                        is UiEffect.ShowValidationError -> showMessage(effect.message)
                        is UiEffect.ShowJourneyStepHint -> showMessage(effect.message)
                    }
                }
            }
        }
    }

    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAnchorView(R.id.fab)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}
