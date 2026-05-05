package com.example.cctest.navigation

import android.content.Context
import com.example.cctest.HouseDashboardActivity
import com.example.cctest.PersonalInfoDetailActivity
import com.example.cctest.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class JourneyExecutor(
    private val context: Context,
    private val navController: androidx.navigation.NavController,
    private val appNavigator: AppNavigator,
    private val scope: CoroutineScope,
    private val onJourneyStarted: (JourneyPlan) -> Unit = {},
    private val onJourneyStepChanged: (String, Int, JourneyStep) -> Unit = { _, _, _ -> },
    private val onJourneyCompleted: (String) -> Unit = {},
    private val onJourneyFailed: (String, String) -> Unit = { _, _ -> },
    private val onMessage: (String) -> Unit = {}
) {
    private var activeJob: Job? = null

    fun execute(plan: JourneyPlan) {
        activeJob?.cancel()
        activeJob = scope.launch {
            try {
                onJourneyStarted(plan)
                plan.steps.forEachIndexed { index, step ->
                    onJourneyStepChanged(plan.journeyId, index, step)
                    executeStep(step)
                }
                onJourneyCompleted(plan.journeyId)
            } catch (_: CancellationException) {
                // Ignore cancelled journeys; a newer plan has taken over.
            } catch (throwable: Throwable) {
                val message = throwable.message ?: "智能路由执行失败。"
                onJourneyFailed(plan.journeyId, message)
            }
        }
    }

    private suspend fun executeStep(step: JourneyStep) {
        when (step) {
            is JourneyStep.NavigateToFragment -> {
                appNavigator.navigate(
                    RouteTarget.NavTarget(
                        destinationId = step.destinationId,
                        args = step.args,
                        displayName = step.displayLabel
                    )
                )
                awaitDestination(step.destinationId)
                delay(NAVIGATION_STEP_DELAY_MS)
            }

            is JourneyStep.FocusList -> {
                check(navController.currentDestination?.id == R.id.PersonalInfoListFragment) {
                    "当前不在个人信息列表页，无法执行列表定位。"
                }
                navController.currentBackStackEntry?.savedStateHandle?.set(
                    JourneyEventKeys.KEY_LIST_FOCUS_REQUEST,
                    step.focusRequest.toBundle()
                )
                delay(LIST_ACTION_DELAY_MS)
            }

            is JourneyStep.OpenFocusedRecordDetail -> {
                context.startActivity(
                    PersonalInfoDetailActivity.createIntent(context, step.recordRef)
                )
                delay(ACTIVITY_STEP_DELAY_MS)
            }

            is JourneyStep.OpenHouseDashboard -> {
                context.startActivity(
                    HouseDashboardActivity.createIntent(context, step.launchSpec)
                )
                delay(ACTIVITY_STEP_DELAY_MS)
            }

            is JourneyStep.ShowFallbackMessage -> {
                onMessage(step.message)
                delay(MESSAGE_STEP_DELAY_MS)
            }

            is JourneyStep.StopAtCurrentScreen -> {
                delay(STOP_STEP_DELAY_MS)
            }
        }
    }

    private suspend fun awaitDestination(destinationId: Int) {
        if (navController.currentDestination?.id == destinationId) {
            return
        }

        suspendCancellableCoroutine<Unit> { continuation ->
            var listener: androidx.navigation.NavController.OnDestinationChangedListener? = null
            listener = androidx.navigation.NavController.OnDestinationChangedListener { _, destination, _ ->
                if (destination.id == destinationId && continuation.isActive) {
                    listener?.let(navController::removeOnDestinationChangedListener)
                    continuation.resume(Unit)
                }
            }
            navController.addOnDestinationChangedListener(listener)
            continuation.invokeOnCancellation {
                listener?.let(navController::removeOnDestinationChangedListener)
            }
        }
    }

    private companion object {
        private const val NAVIGATION_STEP_DELAY_MS = 160L
        private const val LIST_ACTION_DELAY_MS = 220L
        private const val ACTIVITY_STEP_DELAY_MS = 180L
        private const val MESSAGE_STEP_DELAY_MS = 80L
        private const val STOP_STEP_DELAY_MS = 60L
    }
}
