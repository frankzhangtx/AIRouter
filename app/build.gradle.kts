import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}

fun resolveStringProperty(propertyKey: String, envKey: String, defaultValue: String = ""): String {
    return project.providers.gradleProperty(propertyKey).orNull
        ?: localProperties.getProperty(propertyKey)
        ?: System.getenv(envKey)
        ?: defaultValue
}

fun resolveBooleanProperty(propertyKey: String, envKey: String, defaultValue: Boolean): Boolean {
    return resolveStringProperty(propertyKey, envKey, defaultValue.toString())
        .toBooleanStrictOrNull()
        ?: defaultValue
}

fun resolveIntProperty(propertyKey: String, envKey: String, defaultValue: Int): Int {
    return resolveStringProperty(propertyKey, envKey, defaultValue.toString()).toIntOrNull() ?: defaultValue
}

fun resolveLongProperty(propertyKey: String, envKey: String, defaultValue: Long): Long {
    return resolveStringProperty(propertyKey, envKey, defaultValue.toString()).toLongOrNull() ?: defaultValue
}

fun resolveFloatProperty(propertyKey: String, envKey: String, defaultValue: Float): Float {
    return resolveStringProperty(propertyKey, envKey, defaultValue.toString()).toFloatOrNull() ?: defaultValue
}

fun escapeBuildConfigString(value: String): String {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
}

android {
    namespace = "com.example.cctest"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.cctest"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "boolean",
            "ROUTING_ENABLE_LLM_PARSING",
            resolveBooleanProperty(
                propertyKey = "routing.enableLlmParsing",
                envKey = "ROUTING_ENABLE_LLM_PARSING",
                defaultValue = false
            ).toString()
        )
        buildConfigField(
            "boolean",
            "ROUTING_FALLBACK_TO_RULE_ON_LLM_FAILURE",
            resolveBooleanProperty(
                propertyKey = "routing.fallbackToRuleOnLlmFailure",
                envKey = "ROUTING_FALLBACK_TO_RULE_ON_LLM_FAILURE",
                defaultValue = true
            ).toString()
        )
        buildConfigField(
            "float",
            "ROUTING_MIN_RULE_CONFIDENCE",
            "${resolveFloatProperty(
                propertyKey = "routing.minRuleConfidence",
                envKey = "ROUTING_MIN_RULE_CONFIDENCE",
                defaultValue = 0.82f
            )}f"
        )
        buildConfigField(
            "long",
            "ROUTING_LLM_REQUEST_TIMEOUT_MS",
            "${resolveLongProperty(
                propertyKey = "routing.llmRequestTimeoutMs",
                envKey = "ROUTING_LLM_REQUEST_TIMEOUT_MS",
                defaultValue = 12_000L
            )}L"
        )
        buildConfigField(
            "int",
            "ROUTING_LLM_CONNECT_TIMEOUT_MS",
            resolveIntProperty(
                propertyKey = "routing.llmConnectTimeoutMs",
                envKey = "ROUTING_LLM_CONNECT_TIMEOUT_MS",
                defaultValue = 4_000
            ).toString()
        )
        buildConfigField(
            "int",
            "ROUTING_LLM_READ_TIMEOUT_MS",
            resolveIntProperty(
                propertyKey = "routing.llmReadTimeoutMs",
                envKey = "ROUTING_LLM_READ_TIMEOUT_MS",
                defaultValue = 8_000
            ).toString()
        )
        buildConfigField(
            "String",
            "ROUTING_LLM_BASE_URL",
            "\"${escapeBuildConfigString(
                resolveStringProperty(
                    propertyKey = "routing.llmBaseUrl",
                    envKey = "ROUTING_LLM_BASE_URL",
                    defaultValue = "https://api.openai.com/v1/chat/completions"
                )
            )}\""
        )
        buildConfigField(
            "String",
            "ROUTING_LLM_API_KEY",
            "\"${escapeBuildConfigString(
                resolveStringProperty(
                    propertyKey = "routing.llmApiKey",
                    envKey = "ROUTING_LLM_API_KEY"
                )
            )}\""
        )
        buildConfigField(
            "String",
            "ROUTING_LLM_MODEL",
            "\"${escapeBuildConfigString(
                resolveStringProperty(
                    propertyKey = "routing.llmModel",
                    envKey = "ROUTING_LLM_MODEL",
                    defaultValue = "gpt-4o-mini"
                )
            )}\""
        )
        buildConfigField(
            "String",
            "ROUTING_LLM_PROVIDER_NAME",
            "\"${escapeBuildConfigString(
                resolveStringProperty(
                    propertyKey = "routing.llmProviderName",
                    envKey = "ROUTING_LLM_PROVIDER_NAME",
                    defaultValue = "openai-compatible"
                )
            )}\""
        )
        buildConfigField(
            "String",
            "ROUTING_LLM_PROMPT_VERSION",
            "\"${escapeBuildConfigString(
                resolveStringProperty(
                    propertyKey = "routing.llmPromptVersion",
                    envKey = "ROUTING_LLM_PROMPT_VERSION",
                    defaultValue = "intent-routing-v2"
                )
            )}\""
        )
        buildConfigField(
            "String",
            "ROUTING_LLM_SCHEMA_VERSION",
            "\"${escapeBuildConfigString(
                resolveStringProperty(
                    propertyKey = "routing.llmSchemaVersion",
                    envKey = "ROUTING_LLM_SCHEMA_VERSION",
                    defaultValue = "v1"
                )
            )}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.espresso.intents)
}
