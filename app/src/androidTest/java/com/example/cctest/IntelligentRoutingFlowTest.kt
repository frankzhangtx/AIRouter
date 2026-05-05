package com.example.cctest

import android.os.SystemClock
import android.view.View
import android.widget.ListView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents.init
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.release
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.matcher.BoundedMatcher
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IntelligentRoutingFlowTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Before
    fun setUp() {
        init()
    }

    @After
    fun tearDown() {
        release()
    }

    @Test
    fun inputListIntent_focusesRequestedRecord() {
        openRoutingEntry()
        submitPrompt("查看第 12 条记录")

        onView(isRoot()).perform(waitFor(800))
        onView(withId(R.id.textview_focus_status))
            .check(matches(withText(containsString("第 12 条"))))
        onView(withId(R.id.listview_personal_info))
            .check(matches(hasCheckedItemPosition(11)))
    }

    @Test
    fun inputDetailIntent_opensDetailActivity() {
        openRoutingEntry()
        submitPrompt("查看张雨桐的信息")

        onView(isRoot()).perform(waitFor(1000))
        intended(hasComponent(PersonalInfoDetailActivity::class.java.name))
        onView(withId(R.id.textview_detail_content))
            .check(matches(withText(containsString("张雨桐"))))
    }

    @Test
    fun inputDashboardIntent_opensWorkDashboard() {
        openRoutingEntry()
        submitPrompt("打开 Work 看板")

        onView(isRoot()).perform(waitFor(700))
        intended(hasComponent(HouseDashboardActivity::class.java.name))
        onView(withId(R.id.textview_dashboard_route_hint))
            .check(matches(withText(containsString("WORK"))))
    }

    @Test
    fun formWorkflow_runsFromEntryToReviewToResult() {
        openRoutingEntry()
        submitPrompt("帮我补全个人资料，我叫李晨曦，29岁，电话 13800001022")

        onView(isRoot()).perform(waitFor(700))
        onView(withId(R.id.edit_text_name)).check(matches(withText("李晨曦")))
        onView(withId(R.id.edit_text_phone)).check(matches(withText("13800001022")))
        onView(withId(R.id.button_save_personal_info)).perform(scrollTo(), click())

        onView(withId(R.id.textview_review_summary))
            .check(matches(withText(containsString("姓名：李晨曦"))))
        onView(withId(R.id.button_submit_review)).perform(scrollTo(), click())

        onView(isRoot()).perform(waitFor(500))
        onView(withId(R.id.textview_result_title))
            .check(matches(withText(R.string.routing_result_success_title)))
        onView(withId(R.id.textview_result_body))
            .check(matches(withText(containsString("模拟提交完成"))))
    }

    private fun openRoutingEntry() {
        onView(withId(R.id.button_first)).perform(click())
        onView(withId(R.id.button_intelligent_routing)).perform(scrollTo(), click())
        onView(withId(R.id.edit_text_intent_input)).check(matches(isDisplayed()))
    }

    private fun submitPrompt(prompt: String) {
        onView(withId(R.id.edit_text_intent_input)).perform(replaceText(prompt), closeSoftKeyboard())
        onView(withId(R.id.button_run_routing)).perform(click())
    }

    private fun hasCheckedItemPosition(expectedPosition: Int): Matcher<View> {
        return object : BoundedMatcher<View, ListView>(ListView::class.java) {
            override fun describeTo(description: Description) {
                description.appendText("has checked item position: $expectedPosition")
            }

            override fun matchesSafely(listView: ListView): Boolean {
                return listView.checkedItemPosition == expectedPosition
            }
        }
    }

    private fun waitFor(delayMs: Long): ViewAction {
        return object : ViewAction {
            override fun getConstraints(): Matcher<View> = isRoot()

            override fun getDescription(): String = "wait for $delayMs milliseconds"

            override fun perform(
                uiController: androidx.test.espresso.UiController,
                view: View
            ) {
                SystemClock.sleep(delayMs)
                uiController.loopMainThreadUntilIdle()
            }
        }
    }
}
