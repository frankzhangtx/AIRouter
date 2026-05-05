package com.example.cctest.routing.config

import android.content.Context
import com.example.cctest.navigation.AppScreen
import com.example.cctest.navigation.DestinationContract
import com.example.cctest.navigation.DestinationContractRegistry
import com.example.cctest.navigation.DestinationPageType
import com.example.cctest.navigation.DestinationPlannerProfile
import com.example.cctest.navigation.EntryAction
import com.example.cctest.navigation.EntryActionRegistry
import com.example.cctest.navigation.StepExecutorDefinition
import com.example.cctest.navigation.StepExecutorRegistry
import com.example.cctest.routing.parser.UserGoal
import org.json.JSONArray
import org.json.JSONObject

data class RoutingRegistryBundle(
    val destinationContractRegistry: DestinationContractRegistry,
    val entryActionRegistry: EntryActionRegistry,
    val stepExecutorRegistry: StepExecutorRegistry
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
        val destinationsRoot = JSONObject(textSource.readText(DESTINATIONS_PATH))
        val screens = parseScreens(destinationsRoot.getJSONArray("screens"))
        val destinationRegistry = DestinationContractRegistry(
            screens = screens.associateBy { it.key },
            destinations = parseDestinations(
                destinationsArray = destinationsRoot.getJSONArray("destinations"),
                screens = screens.associateBy { it.key }
            ),
            goalMappings = parseGoalMappings(destinationsRoot.getJSONArray("goalMappings")),
            defaultEntryScreenKey = destinationsRoot.getString("defaultEntryScreenKey")
        )
        val graphRoot = JSONObject(textSource.readText(JOURNEY_GRAPH_PATH))
        return RoutingRegistryBundle(
            destinationContractRegistry = destinationRegistry,
            entryActionRegistry = EntryActionRegistry(
                actions = parseEntryActions(
                    actionsArray = graphRoot.getJSONArray("actions"),
                    screens = screens.associateBy { it.key }
                )
            ),
            stepExecutorRegistry = StepExecutorRegistry(
                executors = parseExecutors(graphRoot.getJSONArray("executors"))
            )
        )
    }

    private fun parseGoalMappings(goalMappingsArray: JSONArray): Map<UserGoal, String> {
        return buildMap {
            for (index in 0 until goalMappingsArray.length()) {
                val item = goalMappingsArray.getJSONObject(index)
                put(
                    UserGoal.valueOf(item.getString("goal")),
                    item.getString("destinationKey")
                )
            }
        }
    }

    private fun parseScreens(screensArray: JSONArray): List<AppScreen> {
        return buildList {
            for (index in 0 until screensArray.length()) {
                val item = screensArray.getJSONObject(index)
                add(
                    AppScreen(
                        key = item.getString("key"),
                        destinationId = item.optStringOrNull("destinationIdName")
                            ?.let(resourceIdResolver::resolveId),
                        displayName = item.getString("displayName")
                    )
                )
            }
        }
    }

    private fun parseDestinations(
        destinationsArray: JSONArray,
        screens: Map<String, AppScreen>
    ): Map<String, DestinationContract> {
        return buildMap {
            for (index in 0 until destinationsArray.length()) {
                val item = destinationsArray.getJSONObject(index)
                val screenKey = item.optStringOrNull("screenKey")
                val key = item.getString("key")
                put(
                    key,
                    DestinationContract(
                        key = key,
                        pageType = DestinationPageType.valueOf(item.getString("pageType")),
                        plannerProfile = DestinationPlannerProfile.valueOf(item.getString("plannerProfile")),
                        screen = screenKey?.let { requireNotNull(screens[it]) { "Missing screen for $it" } },
                        displayName = item.getString("displayName"),
                        defaultExecutorKey = item.getString("defaultExecutorKey"),
                        workflowId = item.optStringOrNull("workflowId"),
                        focusExecutorKey = item.optStringOrNull("focusExecutorKey"),
                        messageExecutorKey = item.optStringOrNull("messageExecutorKey"),
                        stopExecutorKey = item.optStringOrNull("stopExecutorKey"),
                        supportsAutofill = item.optBoolean("supportsAutofill"),
                        supportsContinue = item.optBoolean("supportsContinue"),
                        supportsSubmit = item.optBoolean("supportsSubmit"),
                        supportsListFocus = item.optBoolean("supportsListFocus"),
                        supportsDetailDisplay = item.optBoolean("supportsDetailDisplay")
                    )
                )
            }
        }
    }

    private fun parseEntryActions(
        actionsArray: JSONArray,
        screens: Map<String, AppScreen>
    ): List<EntryAction> {
        return buildList {
            for (index in 0 until actionsArray.length()) {
                val item = actionsArray.getJSONObject(index)
                add(
                    EntryAction(
                        key = item.getString("key"),
                        from = requireNotNull(screens[item.getString("fromScreenKey")]) {
                            "Missing source screen for action ${item.getString("key")}"
                        },
                        to = requireNotNull(screens[item.getString("toScreenKey")]) {
                            "Missing target screen for action ${item.getString("key")}"
                        },
                        displayLabel = item.getString("displayLabel"),
                        executorKey = item.getString("executorKey")
                    )
                )
            }
        }
    }

    private fun parseExecutors(executorsArray: JSONArray): Map<String, StepExecutorDefinition> {
        return buildMap {
            for (index in 0 until executorsArray.length()) {
                val item = executorsArray.getJSONObject(index)
                val key = item.getString("key")
                put(
                    key,
                    StepExecutorDefinition(
                        key = key,
                        type = item.getString("type"),
                        params = item.optJSONObject("params").toStringMap()
                    )
                )
            }
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
