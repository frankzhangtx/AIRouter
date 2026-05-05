package com.example.cctest.routing.parser

import com.example.cctest.navigation.HouseDashboardTab

class RuleBasedIntentParser : IntentParser {
    override suspend fun parse(request: ParseRequest): ParseOutcome {
        val startedAt = System.currentTimeMillis()
        val input = request.inputText.normalizedInput()
        if (input.isBlank()) {
            return ParseOutcome.Failure(ParseError.InvalidSchema("输入为空"))
        }

        val parseResult = when {
            looksLikeDashboard(input) -> {
                ParseResult(
                    userGoal = UserGoal.OpenHouseDashboard,
                    slots = ParseSlots(
                        dashboardTab = extractDashboardTab(input) ?: HouseDashboardTab.HOME
                    ),
                    confidence = 0.96f,
                    parserMetadata = buildMetadata(startedAt)
                )
            }

            looksLikeForm(input) -> {
                ParseResult(
                    userGoal = UserGoal.FillPersonalInfo,
                    slots = ParseSlots(
                        personalInfoFields = extractPersonalInfoFields(input)
                    ),
                    confidence = 0.9f,
                    parserMetadata = buildMetadata(startedAt)
                )
            }

            looksLikeList(input) -> {
                ParseResult(
                    userGoal = UserGoal.BrowsePersonalInfoList,
                    slots = ParseSlots(
                        listPosition = extractPosition(input),
                        personName = extractLookupName(input),
                        phone = extractPhone(input),
                        city = extractCity(input),
                        autoOpenDetail = input.contains("打开详情") || input.contains("直接看详情")
                    ),
                    confidence = 0.88f,
                    parserMetadata = buildMetadata(startedAt)
                )
            }

            looksLikeDetail(input) -> {
                ParseResult(
                    userGoal = UserGoal.OpenPersonalInfoDetail,
                    slots = ParseSlots(
                        personName = extractLookupName(input),
                        phone = extractPhone(input),
                        city = extractCity(input)
                    ),
                    confidence = 0.84f,
                    parserMetadata = buildMetadata(startedAt)
                )
            }

            else -> {
                ParseResult(
                    userGoal = UserGoal.Unknown,
                    confidence = 0.24f,
                    ambiguityReason = "规则解析未命中明确意图",
                    parserMetadata = buildMetadata(startedAt)
                )
            }
        }
        return ParseOutcome.Success(parseResult)
    }

    private fun buildMetadata(startedAt: Long): ParserMetadata {
        return ParserMetadata(
            parserSource = ParserSource.RULE,
            providerName = "local-rule",
            modelName = "keyword-v1",
            promptVersion = "rule-v1",
            schemaVersion = "v1",
            latencyMs = System.currentTimeMillis() - startedAt
        )
    }

    private fun looksLikeDashboard(text: String): Boolean {
        return text.contains("看板") || text.contains("dashboard", ignoreCase = true)
    }

    private fun looksLikeForm(text: String): Boolean {
        val formKeywords = listOf("填写", "补全", "完善", "修改", "更新", "个人资料", "个人信息")
        val hasStructuredFields = extractPersonalInfoFields(text).hasAnyValue()
        return formKeywords.any(text::contains) || hasStructuredFields
    }

    private fun looksLikeList(text: String): Boolean {
        val hasPosition = extractPosition(text) != null
        return text.contains("列表") || text.contains("记录") || hasPosition
    }

    private fun looksLikeDetail(text: String): Boolean {
        val hasLookupName = !extractLookupName(text).isNullOrBlank()
        val hasPhone = !extractPhone(text).isNullOrBlank()
        val detailKeywords = listOf("详情", "资料", "信息")
        return (detailKeywords.any(text::contains) && (hasLookupName || hasPhone)) ||
            (text.contains("查看") && (hasLookupName || hasPhone))
    }
}
