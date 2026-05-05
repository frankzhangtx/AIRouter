package com.example.cctest.navigation

enum class EntryActionKey {
    GO_TO_SECOND,
    RETURN_TO_SECOND,
    OPEN_PERSONAL_INFO_LIST,
    ENTER_PERSONAL_INFO_WORKFLOW,
    OPEN_HOUSE_DASHBOARD,
    OPEN_FOCUSED_DETAIL
}

data class EntryAction(
    val key: EntryActionKey,
    val from: AppScreen,
    val to: AppScreen,
    val displayLabel: String
)

class EntryActionRegistry {
    private val actions = listOf(
        EntryAction(
            key = EntryActionKey.GO_TO_SECOND,
            from = AppScreen.FIRST,
            to = AppScreen.SECOND,
            displayLabel = "前往第二页"
        ),
        EntryAction(
            key = EntryActionKey.RETURN_TO_SECOND,
            from = AppScreen.INTENT_ENTRY,
            to = AppScreen.SECOND,
            displayLabel = "返回第二页"
        ),
        EntryAction(
            key = EntryActionKey.RETURN_TO_SECOND,
            from = AppScreen.PERSONAL_INFO_LIST,
            to = AppScreen.SECOND,
            displayLabel = "返回第二页"
        ),
        EntryAction(
            key = EntryActionKey.RETURN_TO_SECOND,
            from = AppScreen.PERSONAL_INFO_FORM,
            to = AppScreen.SECOND,
            displayLabel = "返回第二页"
        ),
        EntryAction(
            key = EntryActionKey.RETURN_TO_SECOND,
            from = AppScreen.REVIEW_SUBMIT,
            to = AppScreen.SECOND,
            displayLabel = "返回第二页"
        ),
        EntryAction(
            key = EntryActionKey.RETURN_TO_SECOND,
            from = AppScreen.RESULT,
            to = AppScreen.SECOND,
            displayLabel = "返回第二页"
        ),
        EntryAction(
            key = EntryActionKey.OPEN_PERSONAL_INFO_LIST,
            from = AppScreen.SECOND,
            to = AppScreen.PERSONAL_INFO_LIST,
            displayLabel = "打开个人信息列表"
        ),
        EntryAction(
            key = EntryActionKey.ENTER_PERSONAL_INFO_WORKFLOW,
            from = AppScreen.SECOND,
            to = AppScreen.PERSONAL_INFO_FORM,
            displayLabel = "进入个人信息工作流"
        ),
        EntryAction(
            key = EntryActionKey.OPEN_HOUSE_DASHBOARD,
            from = AppScreen.SECOND,
            to = AppScreen.HOUSE_DASHBOARD,
            displayLabel = "打开家居看板"
        ),
        EntryAction(
            key = EntryActionKey.OPEN_FOCUSED_DETAIL,
            from = AppScreen.PERSONAL_INFO_LIST,
            to = AppScreen.PERSONAL_INFO_DETAIL,
            displayLabel = "打开已定位详情"
        )
    )

    private val actionsBySource = actions.groupBy { it.from }

    fun actionsFrom(screen: AppScreen): List<EntryAction> {
        return actionsBySource[screen].orEmpty()
    }

    fun resolvePath(from: AppScreen, to: AppScreen): List<EntryAction> {
        if (from == to) {
            return emptyList()
        }

        val visited = mutableSetOf(from)
        val queue = ArrayDeque<Pair<AppScreen, List<EntryAction>>>()
        queue.add(from to emptyList())

        while (queue.isNotEmpty()) {
            val (currentScreen, path) = queue.removeFirst()
            for (action in actionsFrom(currentScreen)) {
                if (!visited.add(action.to)) {
                    continue
                }
                val nextPath = path + action
                if (action.to == to) {
                    return nextPath
                }
                queue.add(action.to to nextPath)
            }
        }

        return emptyList()
    }
}
