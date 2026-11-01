plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    api(project(":core:dsp"))
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }

// Tests read the same database the app ships.
sourceSets["test"].resources.srcDir(rootProject.file("app/src/main/assets/autoeq"))
