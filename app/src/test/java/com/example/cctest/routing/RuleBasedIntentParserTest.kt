package com.example.cctest.routing

import com.example.cctest.navigation.HouseDashboardTab
import com.example.cctest.routing.parser.ParseOutcome
import com.example.cctest.routing.parser.ParseRequest
import com.example.cctest.routing.parser.RuleBasedIntentParser
import com.example.cctest.routing.parser.UserGoal
import com.example.cctest.routing.parser.extractPhone
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleBasedIntentParserTest {
    private val parser = RuleBasedIntentParser()

    @Test
    fun parseListIntent_extractsPosition() = runBlocking {
        val outcome = parser.parse(ParseRequest(inputText = "查看第 12 条记录", entrySource = "test"))
        assertTrue(outcome is ParseOutcome.Success)
        val result = (outcome as ParseOutcome.Success).result
        assertEquals(UserGoal.BrowsePersonalInfoList, result.userGoal)
        assertEquals(12, result.slots.listPosition)
    }

    @Test
    fun parseListIntent_extractsChongqingCity() = runBlocking {
        val outcome = parser.parse(ParseRequest(inputText = "查看重庆的记录列表", entrySource = "test"))
        assertTrue(outcome is ParseOutcome.Success)
        val result = (outcome as ParseOutcome.Success).result
        assertEquals(UserGoal.BrowsePersonalInfoList, result.userGoal)
        assertEquals("重庆", result.slots.city)
    }

    @Test
    fun parseListIntent_keepsShanghaiCity() = runBlocking {
        val outcome = parser.parse(ParseRequest(inputText = "查看上海的记录列表", entrySource = "test"))
        assertTrue(outcome is ParseOutcome.Success)
        val result = (outcome as ParseOutcome.Success).result
        assertEquals(UserGoal.BrowsePersonalInfoList, result.userGoal)
        assertEquals("上海", result.slots.city)
    }

    @Test
    fun parseDashboardIntent_extractsWorkTab() = runBlocking {
        val outcome = parser.parse(ParseRequest(inputText = "打开 Work 看板", entrySource = "test"))
        assertTrue(outcome is ParseOutcome.Success)
        val result = (outcome as ParseOutcome.Success).result
        assertEquals(UserGoal.OpenHouseDashboard, result.userGoal)
        assertEquals(HouseDashboardTab.WORK, result.slots.dashboardTab)
    }

    @Test
    fun parseFormIntent_extractsFields() = runBlocking {
        val outcome = parser.parse(
            ParseRequest(
                inputText = "帮我补全个人资料，我叫李晨曦，29岁，电话 13800001022",
                entrySource = "test"
            )
        )
        assertTrue(outcome is ParseOutcome.Success)
        val result = (outcome as ParseOutcome.Success).result
        assertEquals(UserGoal.FillPersonalInfo, result.userGoal)
        assertEquals("李晨曦", result.slots.personalInfoFields.name)
        assertEquals(29, result.slots.personalInfoFields.age)
        assertEquals("13800001022", result.slots.personalInfoFields.phone)
    }

    @Test
    fun parseFormIntent_normalizesHyphenatedPhone() = runBlocking {
        val outcome = parser.parse(
            ParseRequest(
                inputText = "帮我更新个人资料，电话 138-0000-1022",
                entrySource = "test"
            )
        )
        assertTrue(outcome is ParseOutcome.Success)
        val result = (outcome as ParseOutcome.Success).result
        assertEquals(UserGoal.FillPersonalInfo, result.userGoal)
        assertEquals("13800001022", result.slots.personalInfoFields.phone)
    }

    @Test
    fun parseFormIntent_normalizesSpaceSeparatedPhone() = runBlocking {
        val outcome = parser.parse(
            ParseRequest(
                inputText = "帮我更新个人资料，电话 138 0000 1022",
                entrySource = "test"
            )
        )
        assertTrue(outcome is ParseOutcome.Success)
        val result = (outcome as ParseOutcome.Success).result
        assertEquals(UserGoal.FillPersonalInfo, result.userGoal)
        assertEquals("13800001022", result.slots.personalInfoFields.phone)
    }

    @Test
    fun extractPhone_rejectsMalformedSeparatedNumbers() {
        assertNull(extractPhone("电话 138-000-1022"))
        assertNull(extractPhone("电话 138-0000-10222"))
        assertNull(extractPhone("电话 138a0000a1022"))
    }
}
