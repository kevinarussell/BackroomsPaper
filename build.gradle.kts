plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("com.modrinth.minotaur") version "2.+"
}

group = "io.bluewiz.backrooms"
version = (findProperty("version") as String?)?.takeIf { it != "unspecified" && it.isNotEmpty() } ?: "1.0.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
}

tasks.shadowJar {
    archiveClassifier.set("")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

modrinth {
    token.set(System.getenv("MODRINTH_TOKEN") ?: "")
    // Replace with your Modrinth project ID or slug after creating the project.
    projectId.set("the-backrooms-plugin")
    versionNumber.set(version.toString())
    versionType.set("release")
    uploadFile.set(tasks.shadowJar)
    gameVersions.addAll("1.21.11")
    loaders.addAll("paper", "purpur")
    changelog.set(System.getenv("CHANGELOG") ?: "")
    val readme = rootProject.file("README.md")
    if (readme.exists()) syncBodyFrom.set(readme.readText())
}
