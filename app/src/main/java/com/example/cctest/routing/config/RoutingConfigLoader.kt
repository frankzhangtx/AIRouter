package com.example.cctest.routing.config

import android.content.Context
import com.example.cctest.navigation.AppScreen
import com.example.cctest.navigation.DestinationContractRegistry
import com.example.cctest.navigation.DestinationKey
import com.example.cctest.navigation.DestinationPageType
import com.example.cctest.navigation.EntryActionRegistry
import com.example.cctest.routing.parser.UserGoal
import org.json.JSONArray
import org.json.JSONObject

data class RoutingRegistryBundle(
    val destinationContractRegistry: DestinationContractRegistry,
    val entryActionRegistry: EntryActionRegistry,
    val executorDefinitions: Map<String, RoutingExecutorDefinition>
)

data class RoutingExecutorDefinition(
    val key: String,
    val type: String,
    val params: Map<String, String> = emptyMap()
)

interface RoutingTextSource {
    fun readText(path: String): String
}

class AssetRoutingTextSource(
    private val context: Context
) : RoutingTextSource {
    override fun readText(path: String): String {
        return context.assets.open(path).bufferedReader().use { it.readText() }
    }
}

class ClasspathRoutingTextSource(
    private val classLoader: ClassLoader
) : RoutingTextSource {
    override fun readText(path: String): String {
        val stream = requireNotNull(classLoader.getResourceAsStream(path)) {
            "Missing classpath resource: $path"
        }
        return stream.bufferedReader().use { it.readText() }
    }
}

interface ResourceIdResolver {
    fun resolveId(name: String): Int
}

class ReflectionResourceIdResolver(
    private val resourceIdsClassName: String
) : ResourceIdResolver {
    private val idFields by lazy {
        Class.forName(resourceIdsClassName)
            .declaredFields
            .associateBy { it.name }
    }

    override fun resolveId(name: String): Int {
        val field = requireNotNull(idFields[name]) {
            "Missing R.id entry for $name in $resourceIdsClassName"
        }
        return field.getInt(null)
    }
}

class JsonRoutingConfigLoader(
    private val textSource: RoutingTextSource,
    private val resourceIdResolver: ResourceIdResolver
) {
    fun load(): RoutingRegistryBundle {
        val destinationRegistry = DestinationContractRegistry()
        val entryActionRegistry = EntryActionRegistry()

        val destinationsRoot = JSONObject(textSource.readText(DESTINATIONS_PATH))
        validateDestinationsConfig(destinationsRoot, destinationRegistry)

        val graphRoot = JSONObject(textSource.readText(JOURNEY_GRAPH_PATH))
        val executorDefinitions = parseExecutors(graphRoot.getJSONArray("executors"))
        validateJourneyGraph(graphRoot)

        return RoutingRegistryBundle(
            destinationContractRegistry = destinationRegistry,
            entryActionRegistry = entryActionRegistry,
            executorDefinitions = executorDefinitions
        )
    }

    private fun validateDestinationsConfig(
        destinationsRoot: JSONObject,
        destinationRegistry: DestinationContractRegistry
    ) {
        val screens = parseScreens(destinationsRoot.getJSONArray("screens"))
        parseScreenKey(destinationsRoot.getString("defaultEntryScreenKey"))
        validateGoalMappings(destinationsRoot.getJSONArray("goalMappings"), destinationRegistry)
        validateDestinations(
            destinationsArray = destinationsRoot.getJSONArray("destinations"),
            screens = screens,
            destinationRegistry = destinationRegistry
        )
    }

    private fun validateGoalMappings(
        goalMappingsArray: JSONArray,
        destinationRegistry: DestinationContractRegistry
    ) {
        for (index in 0 until goalMappingsArray.length()) {
            val item = goalMappingsArray.getJSONObject(index)
            UserGoal.valueOf(item.getString("goal"))
            destinationRegistry.require(parseDestinationKey(item.getString("destinationKey")))
        }
    }

    private fun parseScreens(screensArray: JSONArray): Map<String, AppScreen> {
        return buildMap {
            for (index in 0 until screensArray.length()) {
                val item = screensArray.getJSONObject(index)
                val screenKey = item.getString("key")
                val screen = parseScreenKey(screenKey)
                val expectedDestinationId = item.optStringOrNull("destinationIdName")
                    ?.let(resourceIdResolver::resolveId)

                require(screen.destinationId == expectedDestinationId) {
                    "Screen $screenKey destinationId mismatch. " +
                        "Expected ${screen.destinationId}, config defines $expectedDestinationId."
                }

                put(screenKey, screen)
            }
        }
    }

    private fun validateDestinations(
        destinationsArray: JSONArray,
        screens: Map<String, AppScreen>,
        destinationRegistry: DestinationContractRegistry
    ) {
        for (index in 0 until destinationsArray.length()) {
            val item = destinationsArray.getJSONObject(index)
            val destinationKey = parseDestinationKey(item.getString("key"))
            val contract = destinationRegistry.require(destinationKey)
            val screen = item.optStringOrNull("screenKey")?.let { key ->
                requireNotNull(screens[key]) { "Missing screen for $key" }
            }

            require(contract.pageType == DestinationPageType.valueOf(item.getString("pageType"))) {
                "Destination ${item.getString("key")} pageType mismatch."
            }
            require(contract.screen == screen) {
                "Destination ${item.getString("key")} screen mismatch."
            }
            require(contract.supportsAutofill == item.optBoolean("supportsAutofill")) {
                "Destination ${item.getString("key")} supportsAutofill mismatch."
            }
            require(contract.supportsContinue == item.optBoolean("supportsContinue")) {
                "Destination ${item.getString("key")} supportsContinue mismatch."
            }
            require(contract.supportsSubmit == item.optBoolean("supportsSubmit")) {
                "Destination ${item.getString("key")} supportsSubmit mismatch."
            }
            require(contract.supportsListFocus == item.optBoolean("supportsListFocus")) {
                "Destination ${item.getString("key")} supportsListFocus mismatch."
            }
            require(contract.supportsDetailDisplay == item.optBoolean("supportsDetailDisplay")) {
                "Destination ${item.getString("key")} supportsDetailDisplay mismatch."
            }
        }
    }

    private fun validateJourneyGraph(graphRoot: JSONObject) {
        val actionsArray = graphRoot.getJSONArray("actions")
        for (index in 0 until actionsArray.length()) {
            val item = actionsArray.getJSONObject(index)
            parseScreenKey(item.getString("fromScreenKey"))
            parseScreenKey(item.getString("toScreenKey"))
            require(item.getString("executorKey").isNotBlank()) {
                "Action ${item.optString("key")} is missing executorKey."
            }
        }
    }

    private fun parseExecutors(executorsArray: JSONArray): Map<String, RoutingExecutorDefinition> {
        return buildMap {
            for (index in 0 until executorsArray.length()) {
                val item = executorsArray.getJSONObject(index)
                val key = item.getString("key")
                put(
                    key,
                    RoutingExecutorDefinition(
                        key = key,
                        type = item.getString("type"),
                        params = item.optJSONObject("params").toStringMap()
                    )
                )
            }
        }
    }

    private fun parseScreenKey(key: String): AppScreen {
        return when (key) {
            "first" -> AppScreen.FIRST
            "second" -> AppScreen.SECOND
            "intent_entry" -> AppScreen.INTENT_ENTRY
            "personal_info_form" -> AppScreen.PERSONAL_INFO_FORM
            "review_submit" -> AppScreen.REVIEW_SUBMIT
            "result" -> AppScreen.RESULT
            "personal_info_list" -> AppScreen.PERSONAL_INFO_LIST
            "personal_info_detail" -> AppScreen.PERSONAL_INFO_DETAIL
            "house_dashboard" -> AppScreen.HOUSE_DASHBOARD
            else -> error("Unsupported screen key: $key")
        }
    }

    private fun parseDestinationKey(key: String): DestinationKey {
        return when (key) {
            "intent_entry" -> DestinationKey.INTENT_ENTRY
            "personal_info_form" -> DestinationKey.PERSONAL_INFO_FORM
            "review_submit" -> DestinationKey.REVIEW_SUBMIT
            "result" -> DestinationKey.RESULT
            "personal_info_list" -> DestinationKey.PERSONAL_INFO_LIST
            "personal_info_detail" -> DestinationKey.PERSONAL_INFO_DETAIL
            "house_dashboard" -> DestinationKey.HOUSE_DASHBOARD
            else -> error("Unsupported destination key: $key")
        }
    }

    private fun JSONObject.optStringOrNull(name: String): String? {
        return optString(name).takeIf { it.isNotBlank() }
    }

    private fun JSONObject?.toStringMap(): Map<String, String> {
        if (this == null) {
            return emptyMap()
        }
        return keys().asSequence().associateWith { getString(it) }
    }

    companion object {
        const val DESTINATIONS_PATH = "routing/destinations.json"
        const val JOURNEY_GRAPH_PATH = "routing/journey_graph.json"
    }
}
