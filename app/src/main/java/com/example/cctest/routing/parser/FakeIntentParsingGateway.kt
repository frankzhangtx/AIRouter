package com.example.cctest.routing.parser

import com.example.cctest.navigation.HouseDashboardTab

class FakeIntentParsingGateway : IntentParsingGateway {
    override suspend fun parse(request: ParseRequest): ParseOutcome {
        val startedAt = System.currentTimeMillis()
        val input = request.inputText.normalizedInput()

        val result = when {
            input.contains("带我去") && input.contains("看板") -> {
                ParseResult(
                    userGoal = UserGoal.OpenHouseDashboard,
                    slots = ParseSlots(
                        dashboardTab = extractDashboardTab(input) ?: HouseDashboardTab.HOME
                    ),
                    confidence = 0.9f,
                    parserMetadata = buildMetadata(startedAt)
                )
            }

            input.contains("帮我看看") || input.contains("想看") -> {
                ParseResult(
                    userGoal = UserGoal.OpenPersonalInfoDetail,
                    slots = ParseSlots(
                        personName = extractLookupName(input),
                        phone = extractPhone(input),
                        city = extractCity(input)
                    ),
                    confidence = 0.78f,
                    parserMetadata = buildMetadata(startedAt)
                )
            }

            input.contains("定位到") || input.contains("帮我找第") -> {
                ParseResult(
                    userGoal = UserGoal.BrowsePersonalInfoList,
                    slots = ParseSlots(
                        listPosition = extractPosition(input),
                        personName = extractLookupName(input),
                        city = extractCity(input)
                    ),
                    confidence = 0.76f,
                    parserMetadata = buildMetadata(startedAt)
                )
            }

            input.contains("资料补一补") || input.contains("把资料弄全") || input.contains("补资料") -> {
                ParseResult(
                    userGoal = UserGoal.FillPersonalInfo,
                    slots = ParseSlots(
                        personalInfoFields = extractPersonalInfoFields(input)
                    ),
                    confidence = 0.79f,
                    parserMetadata = buildMetadata(startedAt)
                )
            }

            else -> {
                ParseResult(
                    userGoal = UserGoal.Unknown,
                    confidence = 0.3f,
                    ambiguityReason = "Fake LLM 未形成稳定结构化输出",
                    parserMetadata = buildMetadata(startedAt)
                )
            }
        }
        return ParseOutcome.Success(result)
    }

    private fun buildMetadata(startedAt: Long): ParserMetadata {
        return ParserMetadata(
            parserSource = ParserSource.LLM,
            providerName = "fake-gateway",
            modelName = "fake-intent-model",
            promptVersion = "fake-v1",
            schemaVersion = "v1",
            latencyMs = System.currentTimeMillis() - startedAt
        )
    }
}
