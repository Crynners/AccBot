import java.lang.reflect.Field
import java.time.ZonedDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.TreeMap

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("com.android.compose.screenshot")
}

// Read version from gradle.properties
val versionMajor = property("VERSION_MAJOR").toString().toInt()
val versionMinor = property("VERSION_MINOR").toString().toInt()
val versionPatch = property("VERSION_PATCH").toString().toInt()

android {
    namespace = "com.accbot.dca"
    compileSdk = 36

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("KEY_ALIAS") ?: "accbot-upload"
                keyPassword = System.getenv("KEY_PASSWORD") ?: ""
            }
        }
    }

    defaultConfig {
        applicationId = "com.accbot.dca"
        minSdk = 26
        targetSdk = 36
        versionCode = versionMajor * 10000 + versionMinor * 1000 + versionPatch * 100
        versionName = "$versionMajor.$versionMinor.$versionPatch"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Build metadata - includes UTC time for precise build identification
        val buildTimestamp = ZonedDateTime.now(ZoneOffset.UTC)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'"))
        buildConfigField("String", "BUILD_DATE", "\"$buildTimestamp\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    experimentalProperties["android.experimental.enableScreenshotTest"] = true

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

}

dependencies {
    // Core Android
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.activity:activity-compose:1.12.3")

    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2026.01.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.9.7")

    // WorkManager - for reliable background execution
    implementation("androidx.work:work-runtime-ktx:2.11.0")

    // Room - local database
    implementation("androidx.room:room-runtime:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // Hilt - dependency injection
    implementation("com.google.dagger:hilt-android:2.59")
    ksp("com.google.dagger:hilt-compiler:2.59")
    implementation("androidx.hilt:hilt-work:1.3.0")
    ksp("androidx.hilt:hilt-compiler:1.3.0")
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")

    // Security
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.biometric:biometric:1.1.0")

    // Networking - OkHttp + JSON parsing
    implementation("com.squareup.okhttp3:okhttp:5.3.0")
    implementation("com.google.code.gson:gson:2.12.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Crypto for API signatures (HMAC-SHA256, etc.)
    implementation("commons-codec:commons-codec:1.21.0")

    // Vico - Compose charting library for portfolio performance
    implementation("com.patrykandpatrick.vico:compose-m3:2.4.3")

    // QR Code scanning and OCR with ML Kit and CameraX
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("androidx.camera:camera-camera2:1.5.1")
    implementation("androidx.camera:camera-lifecycle:1.5.1")
    implementation("androidx.camera:camera-view:1.5.1")

    // CRON expression parsing for custom DCA schedules
    implementation("com.cronutils:cron-utils:9.2.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.01.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Screenshot testing
    screenshotTestImplementation("androidx.compose.ui:ui-tooling")
    screenshotTestImplementation("com.android.tools.screenshot:screenshot-validation-api:0.0.1-alpha13")
}

// Workaround for Windows CreateProcess error=206 (command line too long)
// The screenshot plugin passes ~70K chars of classpath data via -D system properties,
// exceeding Windows' 32K CreateProcess limit.
// Solution: Use reflection to replace the forkOptions' internal systemProperties map with
// a custom map that diverts long values to a JDK @argfile.
afterEvaluate {
    tasks.matching { it.name.contains("ScreenshotTest") && it is Test }.configureEach {
        val test = this as Test
        test.maxHeapSize = "4g"
        val argfileDir = File(project.layout.buildDirectory.asFile.get(), "argfiles")
        argfileDir.mkdirs()
        val argfile = File(argfileDir, "${test.name}-sysprops.txt")
        val longProps = mutableMapOf<String, Any>()

        // Use reflection to replace the internal systemProperties map inside forkOptions.options (JvmOptions)
        val forkOptionsField = Test::class.java.getDeclaredField("forkOptions")
        forkOptionsField.isAccessible = true
        val forkOptions = forkOptionsField.get(test)

        // Access DefaultJavaForkOptions.options (JvmOptions)
        val optionsField = forkOptions::class.java.superclass!!.getDeclaredField("options")
        optionsField.isAccessible = true
        val jvmOptions = optionsField.get(forkOptions)

        // Find mutableSystemProperties field in JvmOptions
        val jvmOptionsClass = jvmOptions::class.java.let { c ->
            generateSequence(c) { it.superclass }.first { it.simpleName == "JvmOptions" }
        }
        val sysPropsField = jvmOptionsClass.getDeclaredField("mutableSystemProperties")
        sysPropsField.isAccessible = true

        // Use Unsafe to replace the final field with our intercepting map
        val unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null)
        val unsafeClass = unsafe::class.java
        val objectFieldOffset = unsafeClass.getMethod("objectFieldOffset", Field::class.java)
        val putObject = unsafeClass.getMethod("putObject", Any::class.java, Long::class.java, Any::class.java)
        val offset = objectFieldOffset.invoke(unsafe, sysPropsField) as Long
        @Suppress("UNCHECKED_CAST")
        val originalMap = sysPropsField.get(jvmOptions) as MutableMap<String, Any?>
        // Copy existing entries
        val originalEntries = originalMap.toMutableMap()

        // Create intercepting map that diverts long values
        val backingMap = TreeMap<String, Any?>()
        backingMap.putAll(originalEntries)
        val interceptingMap = object : MutableMap<String, Any?> by backingMap {
            override fun put(key: String, value: Any?): Any? {
                if (value != null && value.toString().length > 500) {
                    longProps[key] = value
                    return null
                }
                return backingMap.put(key, value)
            }

            override fun putAll(from: Map<out String, Any?>) {
                for ((k, v) in from) put(k, v)
            }
        }
        // Replace the final field using Unsafe
        putObject.invoke(unsafe, jvmOptions, offset, interceptingMap)

        // Add provider to write diverted props to @argfile
        test.jvmArgumentProviders.add(object : org.gradle.process.CommandLineArgumentProvider {
            override fun asArguments(): Iterable<String> {
                if (longProps.isEmpty()) return emptyList()
                val sb = StringBuilder()
                for ((key, value) in longProps) {
                    // In JDK @argfile with double quotes, backslash is escape char - double them
                    val escaped = value.toString().replace("\\", "\\\\")
                    sb.appendLine("\"-D${key}=${escaped}\"")
                }
                argfile.writeText(sb.toString())
                println("Diverted ${longProps.size} long system properties (${longProps.values.sumOf { it.toString().length }} chars) to @argfile")
                return listOf("@${argfile.absolutePath}")
            }
        })
    }
}
