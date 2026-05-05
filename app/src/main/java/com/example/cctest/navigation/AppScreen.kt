package com.example.cctest.navigation

import com.example.cctest.R

enum class AppScreen(
    val destinationId: Int?,
    val displayName: String
) {
    FIRST(
        destinationId = R.id.FirstFragment,
        displayName = "第一页"
    ),
    SECOND(
        destinationId = R.id.SecondFragment,
        displayName = "第二页"
    ),
    INTENT_ENTRY(
        destinationId = R.id.intentEntryFragment,
        displayName = "智能路由入口"
    ),
    PERSONAL_INFO_FORM(
        destinationId = R.id.personalInfoFormStepFragment,
        displayName = "个人信息工作流"
    ),
    REVIEW_SUBMIT(
        destinationId = R.id.reviewSubmitFragment,
        displayName = "审核确认"
    ),
    RESULT(
        destinationId = R.id.resultFragment,
        displayName = "提交结果"
    ),
    PERSONAL_INFO_LIST(
        destinationId = R.id.PersonalInfoListFragment,
        displayName = "个人信息列表"
    ),
    PERSONAL_INFO_DETAIL(
        destinationId = null,
        displayName = "个人信息详情"
    ),
    HOUSE_DASHBOARD(
        destinationId = null,
        displayName = "家居看板"
    ),
    UNKNOWN(
        destinationId = null,
        displayName = "未知页面"
    );

    companion object {
        fun fromDestinationId(destinationId: Int?): AppScreen {
            return entries.firstOrNull { it.destinationId == destinationId } ?: UNKNOWN
        }
    }
}
