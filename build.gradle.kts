import groovy.xml.XmlSlurper
import org.codehaus.groovy.runtime.ResourceGroovyMethods
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.net.URL
import java.nio.file.Files
import java.util.*
import kotlin.collections.HashMap

plugins {
    id("net.fabricmc.fabric-loom") version("1.17-SNAPSHOT")
    id("org.ajoberstar.grgit") version("+")
    id("me.modmuss50.mod-publish-plugin") version("+")
    `maven-publish`
    eclipse
    idea
    `java-library`
    java
    kotlin("jvm") version("2.4.10")
}

val minecraftVersion: String by project
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
    "26.3-snapshot-5",
    "26.2",
    "26.1.2", "26.1.1", "26.1"
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
    maven {
        setUrl("https://cursemaven.com")

        content {
            includeGroup("curse.maven")
        }
    }
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")

    implementation("net.fabricmc:fabric-loader:$loaderVersion")
    api("net.fabricmc:fabric-language-kotlin:$fabricKotlinVersion")

    // don't shadow the kotlin libraries because it errors on build time for some reason
    // instead use this module that isn't connected to fabric loom whatsoever
    includeApi("net.frozenblock:kotlinlibraryextensions:2.0") // fulfilled by includeBuild
}

kotlin {
    explicitApi()
}

tasks {

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
        // Minecraft 26.1 (26.1-snapshot-1) upwards uses Java 25.
        options.release.set(25)
        options.isFork = true
        options.isIncremental = true
    }

    withType(KotlinCompile::class) {
        compilerOptions {
            // Minecraft 26.1 (26.1-snapshot-1) upwards uses Java 25.
            jvmTarget.set(JvmTarget.JVM_25)
        }
    }

    withType(Test::class) {
        maxParallelForks = Runtime.getRuntime().availableProcessors().div(2)
    }
}

val jar: Jar by tasks
val sourcesJar: Jar by tasks
val javadocJar: Jar by tasks

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25

    // Loom will automatically attach sourcesJar to a SourcesJar task and to the "build" task
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
        try {
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
        } catch (e: Exception) {
            publish = false
            println("Unable to publish to maven. The maven server may be offline.")
        }

        if (publish) {
            create<MavenPublication>("mavenJava") {
                from(components["java"])

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
val curseforge_id: String by extra
val release_type: String by extra
val curseforge_minecraft_version: String by extra
val changelog_file: String by extra

val modrinthVersion = makeModrinthVersion(modVersion)
val displayName = makeName(modVersion)
val changelogText = getChangelog(file(changelog_file))

// CURSEFORGE HOTFIX BECAUSE CURSEFORGE DOESN'T SUPPORT 26.2 SNAPSHOTS
val cfSupportedMcVersions = supportedMcVersions.map { ver ->
    if (ver == "26.3-snapshot-5") {
        return@map "26.1-snapshot"
    }
    ver
}

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

publishMods {
    version.set(modrinthVersion)
    file.set(jar.archiveFile)
    changelog.set(changelogText)
    type.set(STABLE)
    modLoaders.add("fabric")
    //additionalFiles.from(sourcesJar.archiveFile, javadocJar.archiveFile)

    curseforge {
        client = true
        server = true
        version.set(modrinthVersion)
        projectId.set(curseforge_id)
        projectSlug.set("fabric-kotlin-extensions")
        accessToken.set(providers.environmentVariable("CURSEFORGE_TOKEN"))
        minecraftVersions.addAll(cfSupportedMcVersions)
        requires("fabric-language-kotlin")
    }
    modrinth {
        version.set(modrinthVersion)
        projectId.set(modrinth_id)
        accessToken.set(providers.environmentVariable("MODRINTH_TOKEN"))
        minecraftVersions.addAll(supportedMcVersions)
        requires("fabric-language-kotlin")
    }
    github {
        version.set(modrinthVersion)
        repository.set("FrozenBlock/Fabric-Kotlin-Extensions")
        accessToken.set(providers.environmentVariable("GITHUB_TOKEN"))
        commitish.set(getBranch())
        additionalFiles.from(sourcesJar.archiveFile.get().asFile, javadocJar.archiveFile.get().asFile)
    }
}
tasks.named("publishGithub") {
    dependsOn(jar, sourcesJar, javadocJar)
}

val publishMod by tasks.register("publishMod") {
    dependsOn(tasks.publish)
    dependsOn(tasks.publishMods)
}
