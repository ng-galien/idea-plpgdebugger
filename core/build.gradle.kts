import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.tasks.RunPluginVerifierTask

fun properties(key: String) = providers.gradleProperty(key)
fun environment(key: String) = providers.environmentVariable(key)

plugins {
    id("java") // Java support
    alias(libs.plugins.kotlin) // Kotlin support
    alias(libs.plugins.gradleIntelliJPlugin) // Gradle IntelliJ Plugin
    alias(libs.plugins.changelog) // Gradle Changelog Plugin
    alias(libs.plugins.qodana) // Gradle Qodana Plugin
    alias(libs.plugins.kover) // Gradle Kover Plugin
}

group = properties("pluginGroup").get()
version = properties("pluginVersion").get()

// Configure project's dependencies
repositories {
    mavenCentral()
}

dependencies {
    // Postgres
    implementation(libs.postgres)
    // JDBI
    implementation(libs.jdbi3Core)
    implementation(libs.jdbi3Kotlin)
    implementation(libs.jdbi3Kotlin)
    implementation(libs.jdbi3KotlinSqlObject)
    implementation(libs.jdbi3Postgres)
    // Arrow
    implementation(libs.arrowCore)
    implementation(libs.arrowFxCoroutines)

    // Kotlin and logging
    testImplementation(kotlin("test"))
    testImplementation(kotlin("reflect"))
    testRuntimeOnly(libs.logbackClassic)
    testRuntimeOnly(libs.jansi)
    // Junit 5 for IntelliJ
    testImplementation(platform(libs.junitBom))
    testImplementation(libs.junitJupiterApi)
    testImplementation(libs.junitJupiter)
    testImplementation(libs.junitJupiterParams)
    testImplementation(libs.junitPlatformRunner)
    testRuntimeOnly(libs.junitJupiterEngine)
    testRuntimeOnly(libs.junitVintageEngine)
    // Container and driver
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainersJunitJupiter)
    // JDBI testing
    testImplementation(libs.jdbi3Testing)
    // Guava
    testImplementation(libs.guava)
}

configurations {
    all {
        exclude(group = "org.slf4j")
    }
}

kotlin {
    jvmToolchain(17)
}

// Configure Gradle IntelliJ Plugin - read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    pluginName = properties("pluginName")
    version = properties("platformVersion")
    type = properties("platformType")

    // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file.
    plugins = properties("platformPlugins").map { it.split(',').map(String::trim).filter(String::isNotEmpty) }
    downloadSources = true
}
//
//
//// Configure Gradle Changelog Plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
//changelog {
//    groups.empty()
//    repositoryUrl = properties("pluginRepositoryUrl")
//}
//
//// Configure Gradle Qodana Plugin - read more: https://github.com/JetBrains/gradle-qodana-plugin
//qodana {
//    cachePath = provider { file(".qodana").canonicalPath }
//    reportPath = provider { file("build/reports/inspections").canonicalPath }
//    saveReport = true
//    showReport = environment("QODANA_SHOW_REPORT").map { it.toBoolean() }.getOrElse(false)
//}
//
//// Configure Gradle Kover Plugin - read more: https://github.com/Kotlin/kotlinx-kover#configuration
//koverReport {
//    defaults {
//        xml {
//            onCheck = true
//        }
//    }
//}
//
//tasks {
//
//    wrapper {
//        gradleVersion = properties("gradleVersion").get()
//    }
//
//    patchPluginXml {
//        version = properties("pluginVersion")
//        sinceBuild = properties("pluginSinceBuild")
//        untilBuild = properties("pluginUntilBuild")
//
//        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
//        pluginDescription = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
//            val start = "<!-- Plugin description -->"
//            val end = "<!-- Plugin description end -->"
//
//            with (it.lines()) {
//                if (!containsAll(listOf(start, end))) {
//                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
//                }
//                subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
//            }
//        }
//
//        val changelog = project.changelog // local variable for configuration cache compatibility
//        // Get the latest available change notes from the changelog file
//        changeNotes = properties("pluginVersion").map { pluginVersion ->
//            with(changelog) {
//                renderItem(
//                    (getOrNull(pluginVersion) ?: getUnreleased())
//                        .withHeader(false)
//                        .withEmptySections(false),
//                    Changelog.OutputType.HTML,
//                )
//            }
//        }
//    }
//
//    // Configure UI tests plugin
//    // Read more: https://github.com/JetBrains/intellij-ui-test-robot
//    runIdeForUiTests {
//        systemProperty("robot-server.port", "8082")
//        systemProperty("ide.mac.message.dialogs.as.sheets", "false")
//        systemProperty("jb.privacy.policy.text", "<!--999.999-->")
//        systemProperty("jb.consents.confirmation.enabled", "false")
//    }
//
//    runPluginVerifier {
//        failureLevel = project.objects.listProperty(RunPluginVerifierTask.FailureLevel::class.java).apply {
//            add(RunPluginVerifierTask.FailureLevel.COMPATIBILITY_WARNINGS)
//            add(RunPluginVerifierTask.FailureLevel.COMPATIBILITY_PROBLEMS)
////            add(RunPluginVerifierTask.FailureLevel.DEPRECATED_API_USAGES)
////            add(RunPluginVerifierTask.FailureLevel.SCHEDULED_FOR_REMOVAL_API_USAGES)
//            add(RunPluginVerifierTask.FailureLevel.EXPERIMENTAL_API_USAGES)
//            add(RunPluginVerifierTask.FailureLevel.INTERNAL_API_USAGES)
//            add(RunPluginVerifierTask.FailureLevel.OVERRIDE_ONLY_API_USAGES)
//            add(RunPluginVerifierTask.FailureLevel.NON_EXTENDABLE_API_USAGES)
//            add(RunPluginVerifierTask.FailureLevel.PLUGIN_STRUCTURE_WARNINGS)
//            add(RunPluginVerifierTask.FailureLevel.MISSING_DEPENDENCIES)
//            add(RunPluginVerifierTask.FailureLevel.INVALID_PLUGIN)
//        }
//    }
//
//    signPlugin {
//        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
//        privateKey.set(System.getenv("PRIVATE_KEY"))
//        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
//    }
//
//    publishPlugin {
//        dependsOn("patchChangelog")
//        token.set(System.getenv("PUBLISH_TOKEN"))
//        // pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
//        // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
//        // https://plugins.jetbrains.com/docs/intellij/deployment.html#specifying-a-release-channel
//        //channels.set(listOf(properties("pluginVersion").split('-').getOrElse(1) { "default" }.split('.').first()))
//    }
//
//    test {
//        useJUnitPlatform {
//            includeEngines("junit-vintage", "junit-jupiter")
//        }
//    }
//}
