import groovy.xml.XmlSlurper
import org.codehaus.groovy.runtime.ResourceGroovyMethods
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.kohsuke.github.GHReleaseBuilder
import org.kohsuke.github.GitHub
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.net.URL
import java.nio.file.Files
import java.util.*
import kotlin.collections.HashMap

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

val supportedMcVersions: List<String> = listOf(
    "1.20.2", "1.20.1", "1.20",
    "1.19.4", "1.19.3", "1.19.2", "1.19.1", "1.19",
    "1.18.2", "1.18.1", "1.18",
    "1.17.1", "1.17",
    "1.16.5", "1.16.4", "1.16.3", "1.16.2", "1.16.1", "1.16",
    "1.15.2", "1.15.1", "1.15",
    "1.14.4", "1.14.3", "1.14.2", "1.14.1", "1.14",
)

val includeApi: Configuration by configurations.creating

configurations {
    api {
        extendsFrom(includeApi)
    }
    include {
        extendsFrom(includeApi)
    }
}

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
    includeApi("net.frozenblock:kotlinlibraryextensions:1.0") // fulfilled by includeBuild
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

    register("javadocJar", Jar::class) {
        dependsOn(javadoc)
        archiveClassifier = "javadoc"
        from(javadoc.get().destinationDir)
    }

    register("sourcesJar", Jar::class) {
        dependsOn(classes)
        archiveClassifier = "sources"
        from(sourceSets.main.get().allSource)
    }

    withType(JavaCompile::class) {
        options.encoding = "UTF-8"
        // Minecraft 1.18 (1.18-pre2) upwards uses Java 17.
        options.release.set(17)
        options.isFork = true
        options.isIncremental = true
    }

    withType(KotlinCompile::class) {
        kotlinOptions {
            // Minecraft 1.18 (1.18-pre2) upwards uses Java 17.
            jvmTarget = "17"
        }
    }

    withType(Test::class) {
        maxParallelForks = Runtime.getRuntime().availableProcessors().div(2)
    }
}

val remapJar: Task by tasks
val sourcesJar: Task by tasks
val javadocJar: Task by tasks

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17

    // Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
    // if it is present.
    // If you remove this line, sources will not be generated.
    withSourcesJar()
    withJavadocJar()
}

tasks {
    jar {
        from("LICENSE") {
            rename { "${it}_${base.archivesName}"}
        }
    }
}

artifacts {
    archives(sourcesJar)
    archives(javadocJar)
}

fun getVersion(): String {
    return modVersion
    //return "$modVersion-$modLoader+$minecraftVersion"
}

val env = System.getenv()

publishing {
    val mavenUrl = env["MAVEN_URL"]
    val mavenUsername = env["MAVEN_USERNAME"]
    val mavenPassword = env["MAVEN_PASSWORD"]

    val release = mavenUrl?.contains("release")
    val snapshot = mavenUrl?.contains("snapshot")

    val publishingValid = rootProject == project && !mavenUrl.isNullOrEmpty() && !mavenUsername.isNullOrEmpty() && !mavenPassword.isNullOrEmpty()

    val publishVersion = makeModrinthVersion(modVersion)
    val snapshotPublishVersion = publishVersion + if (snapshot == true) "-SNAPSHOT" else ""

    val publishGroup = rootProject.group.toString().trim(' ')

    val hash = if (grgit.branch != null && grgit.branch.current() != null) grgit.branch.current().fullName else ""

    publications {
        var publish = true
        if (publishingValid) {
            try {
                val xml = ResourceGroovyMethods.getText(URL("$mavenUrl/${publishGroup.replace('.', '/')}/$snapshotPublishVersion/$publishVersion.pom"))
                val metadata = XmlSlurper().parseText(xml)

                if (metadata.getProperty("hash").equals(hash)) {
                    publish = false
                }
            } catch (ignored: FileNotFoundException) {
                // No existing version was published, so we can publish
            }
        } else {
            publish = false
        }

        if (publish) {
            create<MavenPublication>("mavenJava") {
                from(components["java"])

                artifact(javadocJar)

                pom {
                    groupId = publishGroup
                    artifactId = rootProject.base.archivesName.get().lowercase()
                    version = publishVersion
                    withXml {
                        asNode().appendNode("properties").appendNode("hash", hash)
                    }
                }
            }
        }
    }
    repositories {

        if (publishingValid) {
            maven {
                url = uri(mavenUrl!!)

                credentials {
                    username = mavenUsername
                    password = mavenPassword
                }
            }
        } else {
            mavenLocal()
        }
    }
}

extra {
    val properties = Properties()
    properties.load(FileInputStream(file("gradle/publishing.properties")))
    properties.forEach { (a, b) ->
        project.extra[a as String] = b as String
    }
}

val modrinth_id: String by extra
val release_type: String by extra
val changelog_file: String by extra

val modrinthVersion = makeModrinthVersion(modVersion)
val displayName = makeName(modVersion)
val changelogText = getChangelog(file(changelog_file))

fun makeName(version: String): String {
    return version
    //return "$version (${minecraftVersion})"
}

fun makeModrinthVersion(version: String): String {
    return version
    //return "$version-mc${minecraftVersion}"
}

fun getChangelog(changelogFile: File): String {
    val text = Files.readString(changelogFile.toPath())
    val split = text.split("-----------------")
    if (split.size != 2)
        throw IllegalStateException("Malformed changelog")
    return split[1].trim()
}

fun getBranch(): String {
    val env = System.getenv()
    var branch = env["GITHUB_REF"]
    if (branch != null && branch != "") {
        return branch.substring(branch.lastIndexOf("/") + 1)
    }

    if (grgit == null) {
        return "unknown"
    }

    branch = grgit.branch.current().name
    return branch.substring(branch.lastIndexOf("/") + 1)
}

modrinth {
    token.set(System.getenv("MODRINTH_TOKEN"))
    projectId.set(modrinth_id)
    versionNumber.set(modrinthVersion)
    versionName.set(displayName)
    versionType.set(release_type)
    changelog.set(changelogText)
    uploadFile.set(file("build/libs/${tasks.remapJar.get().archiveBaseName.get()}-${version}.jar"))
    gameVersions.set(supportedMcVersions)
    loaders.set(listOf("fabric", "quilt"))
}


val github by tasks.register("github") {
    dependsOn(remapJar)
    val env = System.getenv()
    val token = env["GITHUB_TOKEN"]
    val repoVar = env["GITHUB_REPOSITORY"]
    onlyIf {
        token != null && token != ""
    }

    doLast {
        val github = GitHub.connectUsingOAuth(token)
        val repository = github.getRepository(repoVar)

        val releaseBuilder = GHReleaseBuilder(repository, makeModrinthVersion(modVersion))
        releaseBuilder.name(makeName(modVersion))
        releaseBuilder.body(changelogText)
        releaseBuilder.commitish(getBranch())
        releaseBuilder.prerelease(release_type != "release")

        val ghRelease = releaseBuilder.create()
        ghRelease.uploadAsset(tasks.remapJar.get().archiveFile.get().asFile, "application/java-archive")
        ghRelease.uploadAsset(tasks.remapSourcesJar.get().archiveFile.get().asFile, "application/java-archive")
        ghRelease.uploadAsset(javadocJar.outputs.files.singleFile, "application/java-archive")
    }
}

val publishMod by tasks.register("publishMod") {
    dependsOn(tasks.publish)
    dependsOn(github)
    dependsOn(tasks.modrinth)
}
