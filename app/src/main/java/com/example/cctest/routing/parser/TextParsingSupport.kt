package com.example.cctest.routing.parser

import com.example.cctest.navigation.HouseDashboardTab

private val chineseNumberMap = mapOf(
    '零' to 0,
    '一' to 1,
    '二' to 2,
    '两' to 2,
    '三' to 3,
    '四' to 4,
    '五' to 5,
    '六' to 6,
    '七' to 7,
    '八' to 8,
    '九' to 9
)

private val knownCities = listOf("上海", "北京", "深圳", "杭州", "成都", "南京", "武汉", "苏州", "厦门", "广州")
private val knownOccupations = listOf("设计师", "工程师", "教师", "产品经理", "医生", "律师", "分析师", "顾问", "作家", "建筑师")

fun String.normalizedInput(): String {
    return trim()
        .replace('，', ' ')
        .replace('。', ' ')
        .replace('：', ' ')
        .replace('\n', ' ')
        .replace(Regex("\\s+"), " ")
}

fun extractPosition(text: String): Int? {
    val normalized = text.normalizedInput()
    Regex("第\\s*(\\d{1,2})\\s*[条个项]?").find(normalized)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
        return it
    }
    val chineseMatch = Regex("第\\s*([零一二两三四五六七八九十]{1,3})\\s*[条个项]?").find(normalized)
        ?.groupValues
        ?.getOrNull(1)
    return chineseMatch?.let(::parseChineseNumber)
}

fun extractPhone(text: String): String? {
    return Regex("1\\d{10}").find(text)?.value
}

fun extractEmail(text: String): String? {
    return Regex("[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+").find(text)?.value
}

fun extractAge(text: String): Int? {
    return Regex("(\\d{1,2})\\s*岁").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
}

fun extractIdentityName(text: String): String? {
    val normalized = text.normalizedInput()
    val directPattern = Regex("(?:我叫|我是|姓名是|姓名为)\\s*([\\u4e00-\\u9fa5]{2,4})")
    return directPattern.find(normalized)?.groupValues?.getOrNull(1)
}

fun extractLookupName(text: String): String? {
    val normalized = text.normalizedInput()
    val patterns = listOf(
        Regex("(?:查看|看看|想看|帮我看|帮我看看|打开|进入)\\s*([\\u4e00-\\u9fa5]{1,4})\\s*(?:的)?(?:信息|资料|详情)"),
        Regex("(?:查看|看看|想看|帮我看|帮我看看)\\s*([\\u4e00-\\u9fa5]{1,4})")
    )
    return patterns.firstNotNullOfOrNull { pattern ->
        pattern.find(normalized)?.groupValues?.getOrNull(1)
    }
}

fun extractCity(text: String): String? {
    return knownCities.firstOrNull { text.contains(it) }
}

fun extractOccupation(text: String): String? {
    return knownOccupations.firstOrNull { text.contains(it) }
}

fun extractAddress(text: String): String? {
    val normalized = text.normalizedInput()
    val match = Regex("(?:住址|地址)(?:是|为)?\\s*([^ ]{4,})").find(normalized)?.groupValues?.getOrNull(1)
    return match?.trim()
}

fun extractCompany(text: String): String? {
    val normalized = text.normalizedInput()
    return Regex("(?:公司|单位)(?:是|为)?\\s*([^ ]{2,})").find(normalized)?.groupValues?.getOrNull(1)?.trim()
}

fun extractEmergencyContact(text: String): String? {
    val normalized = text.normalizedInput()
    return Regex("(?:紧急联系人)(?:是|为)?\\s*([\\u4e00-\\u9fa5]{2,4})").find(normalized)?.groupValues?.getOrNull(1)
}

fun extractDashboardTab(text: String): HouseDashboardTab? {
    return when {
        text.contains("work", ignoreCase = true) || text.contains("工作") -> HouseDashboardTab.WORK
        text.contains("home", ignoreCase = true) || text.contains("家庭") || text.contains("家居") -> HouseDashboardTab.HOME
        else -> null
    }
}

fun extractPersonalInfoFields(text: String): PersonalInfoFields {
    return PersonalInfoFields(
        name = extractIdentityName(text),
        age = extractAge(text),
        phone = extractPhone(text),
        email = extractEmail(text),
        address = extractAddress(text),
        occupation = extractOccupation(text),
        company = extractCompany(text),
        hobbies = if (text.contains("兴趣") || text.contains("爱好")) Regex("(?:兴趣爱好|爱好)(?:是|为)?\\s*([^ ]{2,})")
            .find(text.normalizedInput())
            ?.groupValues
            ?.getOrNull(1) else null,
        emergencyContact = extractEmergencyContact(text)
    )
}

fun parseChineseNumber(text: String): Int? {
    if (text.isBlank()) {
        return null
    }
    if (text == "十") {
        return 10
    }
    if (text.length == 1) {
        return chineseNumberMap[text.first()]
    }
    if (text.contains('十')) {
        val parts = text.split('十')
        val tens = parts[0].takeIf { it.isNotEmpty() }?.firstOrNull()?.let { chineseNumberMap[it] } ?: 1
        val ones = parts.getOrNull(1)?.takeIf { it.isNotEmpty() }?.firstOrNull()?.let { chineseNumberMap[it] } ?: 0
        return tens * 10 + ones
    }
    return null
}
