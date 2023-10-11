pluginManagement {
    repositories {
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
        maven {
            name = "Jitpack"
            setUrl("https://jitpack.io/")
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "Fabric Kotlin Extensions"

includeBuild("library") {
    dependencySubstitution {
        substitute(module("net.frozenblock:kotlinlibraryextensions")).using(project(":"))
    }
}
