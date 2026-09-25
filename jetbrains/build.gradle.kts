import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.2.21"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Only the common platform is used, so IDEA Community builds a plugin that runs in PyCharm too
        intellijIdeaCommunity(providers.gradleProperty("platformVersion"))
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
    // Required by the IntelliJ test framework on 2024.x platforms
    testImplementation("org.opentest4j:opentest4j:1.3.0")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        // 2024.3 bundles the Kotlin 2.0 standard library
        apiVersion.set(KotlinVersion.KOTLIN_2_0)
        languageVersion.set(KotlinVersion.KOTLIN_2_0)
    }
}

intellijPlatform {
    pluginConfiguration {
        version = providers.gradleProperty("pluginVersion")
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }
    pluginVerification {
        ides {
            recommended()
        }
    }
}

// The documentation pages and TextMate grammar are shared with the VS Code
// extension, so they are copied from the repository root at build time.
val docsDir = rootProject.layout.projectDirectory.dir("../utils/docs")
val grammarFile = rootProject.layout.projectDirectory.file("../syntaxes/nastran.json")

val generateDocsIndex by tasks.registering {
    description = "Writes docs/index.txt listing every documented Nastran entry"
    val input = docsDir
    val output = layout.buildDirectory.file("generated/docsIndex/index.txt")
    inputs.dir(input)
    outputs.file(output)
    doLast {
        val root = input.asFile
        val entries = root.walkTopDown()
            .filter { it.isFile && it.extension == "md" }
            .map { it.relativeTo(root).invariantSeparatorsPath.removeSuffix(".md") }
            .sorted()
            .toList()
        output.get().asFile.apply {
            parentFile.mkdirs()
            writeText(entries.joinToString("\n", postfix = "\n"))
        }
    }
}

tasks.processResources {
    from(docsDir) { into("docs") }
    from(grammarFile)
    from(generateDocsIndex) { into("docs") }
}

tasks.test {
    // Lets core tests locate the shared docs when run from the IDE or Gradle
    systemProperty("nastran.repoRoot", rootProject.layout.projectDirectory.dir("..").asFile.absolutePath)
}
