// Top-level build file
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.10")
    }
}

plugins {
    id("com.android.application") version "9.0.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.10" apply false
    id("com.google.dagger.hilt.android") version "2.59" apply false
    id("com.google.devtools.ksp") version "2.3.5" apply false
}
