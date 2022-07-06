rootProject.name = "idea-plpgdebugger"
include("java")

pluginManagement {

    repositories {
        maven {
            url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
        }
        gradlePluginPortal()
    }

}