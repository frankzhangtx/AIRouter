package com.example.cctest.navigation

enum class DestinationPageType {
    ENTRY,
    FORM,
    REVIEW,
    RESULT,
    LOOKUP_LIST,
    DETAIL_READ_ONLY,
    TERMINAL_ACTIVITY
}

enum class DestinationKey {
    INTENT_ENTRY,
    PERSONAL_INFO_FORM,
    REVIEW_SUBMIT,
    RESULT,
    PERSONAL_INFO_LIST,
    PERSONAL_INFO_DETAIL,
    HOUSE_DASHBOARD
}

data class DestinationContract(
    val key: DestinationKey,
    val pageType: DestinationPageType,
    val screen: AppScreen? = null,
    val supportsAutofill: Boolean = false,
    val supportsContinue: Boolean = false,
    val supportsSubmit: Boolean = false,
    val supportsListFocus: Boolean = false,
    val supportsDetailDisplay: Boolean = false,
    val entryActions: Set<EntryActionKey> = emptySet()
)

class DestinationContractRegistry {
    private val contracts = mapOf(
        DestinationKey.INTENT_ENTRY to DestinationContract(
            key = DestinationKey.INTENT_ENTRY,
            pageType = DestinationPageType.ENTRY,
            screen = AppScreen.INTENT_ENTRY,
            supportsContinue = true
        ),
        DestinationKey.PERSONAL_INFO_FORM to DestinationContract(
            key = DestinationKey.PERSONAL_INFO_FORM,
            pageType = DestinationPageType.FORM,
            screen = AppScreen.PERSONAL_INFO_FORM,
            supportsAutofill = true,
            supportsContinue = true,
            entryActions = setOf(EntryActionKey.RETURN_TO_SECOND)
        ),
        DestinationKey.REVIEW_SUBMIT to DestinationContract(
            key = DestinationKey.REVIEW_SUBMIT,
            pageType = DestinationPageType.REVIEW,
            screen = AppScreen.REVIEW_SUBMIT,
            supportsContinue = true,
            supportsSubmit = true,
            entryActions = setOf(EntryActionKey.RETURN_TO_SECOND)
        ),
        DestinationKey.RESULT to DestinationContract(
            key = DestinationKey.RESULT,
            pageType = DestinationPageType.RESULT,
            screen = AppScreen.RESULT,
            entryActions = setOf(EntryActionKey.RETURN_TO_SECOND)
        ),
        DestinationKey.PERSONAL_INFO_LIST to DestinationContract(
            key = DestinationKey.PERSONAL_INFO_LIST,
            pageType = DestinationPageType.LOOKUP_LIST,
            screen = AppScreen.PERSONAL_INFO_LIST,
            supportsListFocus = true,
            supportsDetailDisplay = true,
            entryActions = setOf(
                EntryActionKey.RETURN_TO_SECOND,
                EntryActionKey.OPEN_FOCUSED_DETAIL
            )
        ),
        DestinationKey.PERSONAL_INFO_DETAIL to DestinationContract(
            key = DestinationKey.PERSONAL_INFO_DETAIL,
            pageType = DestinationPageType.DETAIL_READ_ONLY,
            screen = AppScreen.PERSONAL_INFO_DETAIL,
            supportsDetailDisplay = true
        ),
        DestinationKey.HOUSE_DASHBOARD to DestinationContract(
            key = DestinationKey.HOUSE_DASHBOARD,
            pageType = DestinationPageType.TERMINAL_ACTIVITY,
            screen = AppScreen.HOUSE_DASHBOARD
        )
    )

    fun require(key: DestinationKey): DestinationContract {
        return requireNotNull(contracts[key]) { "Missing destination contract for $key" }
    }
}
