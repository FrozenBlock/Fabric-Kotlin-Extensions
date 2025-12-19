import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `maven-publish`
    eclipse
    idea
    `java-library`
    java
    id("com.gradleup.shadow") version("+")
    kotlin("jvm") version("2.3.0")
}

val modId: String by project
val projectVersion: String by project
val mavenGroup: String by project
val baseName: String by project

val fabricKotlinVersion: String by project

base {
    archivesName = baseName
}

version = projectVersion
group = mavenGroup

val shadowInclude: Configuration by configurations.creating

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net")
}

dependencies {
    shadowInclude(kotlin("scripting-jvm"))
    shadowInclude(kotlin("scripting-jvm-host"))
    shadowInclude(kotlin("scripting-jsr223"))
    shadowInclude(kotlin("scripting-dependencies"))
    shadowInclude(kotlin("scripting-dependencies-maven"))
    shadowInclude(kotlin("metadata-jvm"))

    shadowInclude("net.fabricmc:mapping-io:0.8.0")
    shadowInclude("net.fabricmc:tiny-remapper:0.12.0")
}

kotlin {
    explicitApi()
}

tasks {
    withType(JavaCompile::class) {
        options.encoding = "UTF-8"
        // Minecraft 1.20.5 (24w14a) upwards uses Java 21.
        options.release.set(21)
        options.isFork = true
        options.isIncremental = true
    }

    withType(KotlinCompile::class) {
        compilerOptions {
            // Minecraft 1.20.5 (24w14a) upwards uses Java 21.
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
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

            exclude(dependency("com.google.guava:guava:.*"))

            exclude(dependency("org.apache.commons:commons-lang3:.*"))
            exclude(dependency("org.apache.httpcomponents:httpclient:.*"))
            exclude(dependency("org.apache.httpcomponents:httpcore:.*"))
            exclude(dependency("org.slf4j:slf4j-api:.*"))
            exclude(dependency("org.slf4j:slf4j-simple:.*"))
            exclude(dependency("org.ow2.asm:.*:.*"))
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
        properties["fabricKotlinVersion"] = ">=$fabricKotlinVersion"

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