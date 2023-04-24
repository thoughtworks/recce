pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.ajoberstar.reckon.settings") version "0.18.0"
}

extensions.configure<org.ajoberstar.reckon.gradle.ReckonExtension> {
    setDefaultInferredScope("patch")
    stages("dev", "final")
    setScopeCalc(calcScopeFromProp())
    setStageCalc(calcStageFromProp())
}

rootProject.name = "recce-server"
