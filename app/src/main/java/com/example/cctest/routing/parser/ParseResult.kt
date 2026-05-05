package com.example.cctest.routing.parser

import com.example.cctest.navigation.HouseDashboardTab

data class ParseResult(
    val userGoal: UserGoal,
    val slots: ParseSlots = ParseSlots(),
    val confidence: Float,
    val ambiguityReason: String? = null,
    val parserMetadata: ParserMetadata
)

data class ParseSlots(
    val personalInfoFields: PersonalInfoFields = PersonalInfoFields(),
    val listPosition: Int? = null,
    val personName: String? = null,
    val phone: String? = null,
    val city: String? = null,
    val dashboardTab: HouseDashboardTab? = null,
    val autoOpenDetail: Boolean = false
)

data class PersonalInfoFields(
    val name: String? = null,
    val age: Int? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val occupation: String? = null,
    val company: String? = null,
    val hobbies: String? = null,
    val emergencyContact: String? = null
) {
    fun hasAnyValue(): Boolean {
        return listOf(name, phone, email, address, occupation, company, hobbies, emergencyContact)
            .any { !it.isNullOrBlank() } || age != null
    }

    fun mergeFrom(other: PersonalInfoFields): PersonalInfoFields {
        return copy(
            name = other.name ?: name,
            age = other.age ?: age,
            phone = other.phone ?: phone,
            email = other.email ?: email,
            address = other.address ?: address,
            occupation = other.occupation ?: occupation,
            company = other.company ?: company,
            hobbies = other.hobbies ?: hobbies,
            emergencyContact = other.emergencyContact ?: emergencyContact
        )
    }

    fun toSummary(emptyValue: String = "未填写"): String {
        val ageText = age?.let { "$it 岁" } ?: emptyValue
        return buildString {
            appendLine("姓名：${name.orEmpty().ifBlank { emptyValue }}")
            appendLine("年龄：$ageText")
            appendLine("电话：${phone.orEmpty().ifBlank { emptyValue }}")
            appendLine("邮箱：${email.orEmpty().ifBlank { emptyValue }}")
            appendLine("地址：${address.orEmpty().ifBlank { emptyValue }}")
            appendLine("职业：${occupation.orEmpty().ifBlank { emptyValue }}")
            appendLine("公司：${company.orEmpty().ifBlank { emptyValue }}")
            appendLine("兴趣爱好：${hobbies.orEmpty().ifBlank { emptyValue }}")
            append("紧急联系人：${emergencyContact.orEmpty().ifBlank { emptyValue }}")
        }
    }
}
