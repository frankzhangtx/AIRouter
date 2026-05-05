package com.example.cctest.feature.personalinfo.data

import com.example.cctest.routing.parser.PersonalInfoFields

data class PersonalInfoRecord(
    val recordId: String,
    val name: String,
    val age: Int,
    val occupation: String,
    val city: String,
    val phone: String,
    val email: String,
    val address: String,
    val hobbies: String,
    val company: String,
    val emergencyContact: String
) {
    fun toDisplayText(index: Int): String {
        return "${index + 1}. 姓名：$name | 年龄：$age 岁 | 职业：$occupation | 城市：$city | 电话：$phone"
    }

    fun toFormFields(): PersonalInfoFields {
        return PersonalInfoFields(
            name = name,
            age = age,
            phone = phone,
            email = email,
            address = address,
            occupation = occupation,
            company = company,
            hobbies = hobbies,
            emergencyContact = emergencyContact
        )
    }
}
