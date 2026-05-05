package com.example.cctest.routing.parser

class ParseResultValidator {
    fun validate(result: ParseResult): ParseError? {
        if (result.confidence !in 0f..1f) {
            return ParseError.InvalidSchema("confidence 超出范围")
        }
        if (result.parserMetadata.schemaVersion.isBlank()) {
            return ParseError.InvalidSchema("schemaVersion 不能为空")
        }
        return when (result.userGoal) {
            UserGoal.OpenPersonalInfoDetail -> {
                val hasIdentifier = !result.slots.personName.isNullOrBlank() ||
                    !result.slots.phone.isNullOrBlank() ||
                    result.slots.listPosition != null
                if (hasIdentifier) null else ParseError.InvalidSchema("详情意图缺少识别条件")
            }

            UserGoal.OpenHouseDashboard -> {
                if (result.slots.dashboardTab == null) {
                    ParseError.InvalidSchema("看板意图缺少 tab 条件")
                } else {
                    null
                }
            }

            else -> null
        }
    }
}
