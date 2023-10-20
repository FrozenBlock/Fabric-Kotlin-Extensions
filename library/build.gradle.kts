plugins {
    `maven-publish`
    eclipse
    idea
    `java-library`
    java
    id("com.github.johnrengelman.shadow") version("+")
    kotlin("jvm") version("1.9.10")
}

val modId: String by project
val projectVersion: String by project
val mavenGroup: String by project
val baseName: String by project

base {
    archivesName = baseName
}

version = projectVersion
group = mavenGroup

val shadowInclude: Configuration by configurations.creating

repositories {
    mavenCentral()
}

dependencies {
    shadowInclude(kotlin("scripting-jvm"))
    shadowInclude(kotlin("scripting-jvm-host"))
    shadowInclude(kotlin("scripting-jsr223"))
    shadowInclude(kotlin("scripting-dependencies"))
    shadowInclude(kotlin("scripting-dependencies-maven"))
}

kotlin {
    explicitApi()
}

tasks {
    shadowJar {
        isZip64 = true
        configurations = listOf(shadowInclude)
        archiveClassifier = ""
        dependencies {
            // exclude libraries in Fabric Language Kotlin
            exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib:.*"))
            exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib-jdk8:.*"))
            exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib-jdk7:.*"))
            exclude(dependency("org.jetbrains.kotlin:kotlin-reflect:.*"))

            exclude(dependency("org.jetbrains.kotlinx:kotlinx-coroutines-core:.*"))
            exclude(dependency("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:.*"))
            exclude(dependency("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:.*"))
            exclude(dependency("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:.*"))
            exclude(dependency("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:.*"))
            exclude(dependency("org.jetbrains.kotlinx:kotlinx-serialization-cbor-jvm:.*"))
            exclude(dependency("org.jetbrains.kotlinx:atomicfu-jvm:.*"))
            exclude(dependency("org.jetbrains.kotlinx:kotlinx-datetime-jvm:.*"))
        }
    }

    jar {
        dependsOn(shadowJar)
        onlyIf { false }
    }

    processResources {
        val properties = HashMap<String, Any>()
        properties["modId"] = modId
        properties["version"] = version

        properties.forEach { (a, b) -> inputs.property(a, b) }

        filesNotMatching(
            listOf(
                "**/*.java",
                "**/*.kt",
                "**/sounds.json",
                "**/lang/*.json",
                "**/.cache/*",
                "**/*.accesswidener",
                "**/*.nbt",
                "**/*.png",
                "**/*.ogg",
                "**/*.mixins.json"
            )
        ) {
            expand(properties)
        }
    }
}