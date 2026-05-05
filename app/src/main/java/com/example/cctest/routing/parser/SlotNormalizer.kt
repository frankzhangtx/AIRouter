package com.example.cctest.routing.parser

import com.example.cctest.navigation.HouseDashboardTab

class SlotNormalizer {
    fun normalize(result: ParseResult): ParseResult {
        val normalizedFields = result.slots.personalInfoFields.copy(
            name = result.slots.personalInfoFields.name?.trim(),
            phone = result.slots.personalInfoFields.phone?.filter(Char::isDigit),
            email = result.slots.personalInfoFields.email?.trim()?.lowercase(),
            address = result.slots.personalInfoFields.address?.trim(),
            occupation = result.slots.personalInfoFields.occupation?.trim(),
            company = result.slots.personalInfoFields.company?.trim(),
            hobbies = result.slots.personalInfoFields.hobbies?.trim(),
            emergencyContact = result.slots.personalInfoFields.emergencyContact?.trim()
        )
        val dashboardTab = when (result.slots.dashboardTab) {
            HouseDashboardTab.WORK -> HouseDashboardTab.WORK
            else -> HouseDashboardTab.HOME
        }
        return result.copy(
            slots = result.slots.copy(
                personalInfoFields = normalizedFields,
                listPosition = result.slots.listPosition?.takeIf { it > 0 },
                personName = result.slots.personName?.trim(),
                phone = result.slots.phone?.filter(Char::isDigit),
                city = result.slots.city?.trim(),
                dashboardTab = if (result.userGoal == UserGoal.OpenHouseDashboard) dashboardTab else result.slots.dashboardTab
            )
        )
    }
}
