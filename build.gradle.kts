buildscript {
    repositories {
        gradlePluginPortal()
    }
    dependencies {
        classpath("org.kohsuke:github-api:1.316")
    }
}

plugins {
    id("fabric-loom") version("+")
    id("org.ajoberstar.grgit") version("+")
    id("com.modrinth.minotaur") version("+")
    `maven-publish`
    eclipse
    idea
    `java-library`
    java
    kotlin("jvm") version("1.9.10")
}

val minecraftVersion: String by project
val quiltMappings: String by project
val parchmentMappings: String by project
val loaderVersion: String by project

val modId: String by project
val modVersion: String by project
val modLoader: String by project
val mavenGroup: String by project
val baseName: String by project

val fabricKotlinVersion: String by project

base {
    archivesName = baseName
}

version = getVersion()
group = mavenGroup

repositories {
    maven("https://jitpack.io")
    maven("https://maven.parchmentmc.org")
    maven("https://maven.quiltmc.org/repository/release")
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings(loom.layered {
        // please annoy treetrain if this doesnt work
        mappings("org.quiltmc:quilt-mappings:$quiltMappings:intermediary-v2")
        parchment("org.parchmentmc.data:parchment-$parchmentMappings@zip")
        officialMojangMappings {
            nameSyntheticMembers = false
        }
    })

    modImplementation("net.fabricmc:fabric-language-kotlin:$fabricKotlinVersion")

    // don't shadow the kotlin libraries because it errors on build time for some reason
    // instead use this module that isn't connected to fabric loom whatsoever
    api("net.frozenblock:kotlinlibraryextensions:1.0")?.let { include(it) } // fulfilled by includeBuild
}

kotlin {
    explicitApi()
}

tasks {

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

fun getVersion(): String {
    return modVersion
    //return "$modVersion-$modLoader+$minecraftVersion"
}