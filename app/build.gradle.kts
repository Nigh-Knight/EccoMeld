import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}
plugins {
    id("com.android.application")
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    // protobuf plugin incompatible with AGP 9 — protoc runs as Exec task instead
}

val buildEccoPath by tasks.registering(Exec::class) {
    description = "Build EccoPath Next.js static export for Android"
    group = "eccopath"
    workingDir = file("${rootProject.projectDir}/eccopath")
    // Next.js only reads next.config.ts by name — temporarily swap to Android config
    doFirst {
        val eccoDir = file("${rootProject.projectDir}/eccopath")
        val original = file("$eccoDir/next.config.ts")
        val backup = file("$eccoDir/next.config.ts.bak")
        val android = file("$eccoDir/next.config.android.ts")
        original.copyTo(backup, overwrite = true)
        android.copyTo(original, overwrite = true)
    }
    commandLine("node", "${rootProject.projectDir}/eccopath/node_modules/.bin/next", "build")
    doLast {
        // Restore original config
        val eccoDir = file("${rootProject.projectDir}/eccopath")
        val original = file("$eccoDir/next.config.ts")
        val backup = file("$eccoDir/next.config.ts.bak")
        if (backup.exists()) {
            backup.copyTo(original, overwrite = true)
            backup.delete()
        }
        
        ProcessBuilder("node", "scripts/strip-crossorigin.mjs")
            .directory(file("${rootProject.projectDir}/eccopath"))
            .inheritIO()
            .start()
            .waitFor()
    }
    inputs.dir("${rootProject.projectDir}/eccopath/lib")
    inputs.dir("${rootProject.projectDir}/eccopath/app")
    inputs.file("${rootProject.projectDir}/eccopath/next.config.android.ts")
    outputs.dir("${rootProject.projectDir}/eccopath/out")
}

val copyEccoPathAssets by tasks.registering(Copy::class) {
    dependsOn(buildEccoPath)
    from("${rootProject.projectDir}/eccopath/out")
    into("${projectDir}/src/main/assets/eccopath")
}

android {
    namespace = "com.metrolist.music"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.meld.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "0.6.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        // LastFM API keys from GitHub Secrets
        val lastFmKey = localProperties.getProperty("LASTFM_API_KEY") ?: System.getenv("LASTFM_API_KEY") ?: ""
        val lastFmSecret = localProperties.getProperty("LASTFM_SECRET") ?: System.getenv("LASTFM_SECRET") ?: ""

        buildConfigField("String", "LASTFM_API_KEY", "\"$lastFmKey\"")
        buildConfigField("String", "LASTFM_SECRET", "\"$lastFmSecret\"")

        
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        }
    }

    flavorDimensions += listOf("abi", "variant")
    productFlavors {
        // FOSS variant (default) - F-Droid compatible, no Google Play Services
        create("foss") {
            dimension = "variant"
            isDefault = true
            buildConfigField("Boolean", "CAST_AVAILABLE", "false")
        }
        
        // GMS variant - with Google Cast support (requires Google Play Services)
        create("gms") {
            dimension = "variant"
            buildConfigField("Boolean", "CAST_AVAILABLE", "true")
        }
        
        create("universal") {
            dimension = "abi"
            buildConfigField("String", "ARCHITECTURE", "\"universal\"")
        }
        create("arm64") {
            dimension = "abi"
            buildConfigField("String", "ARCHITECTURE", "\"arm64\"")
        }
        create("armeabi") {
            dimension = "abi"
            buildConfigField("String", "ARCHITECTURE", "\"armeabi\"")
        }
        create("x86") {
            dimension = "abi"
            buildConfigField("String", "ARCHITECTURE", "\"x86\"")
        }
        create("x86_64") {
            dimension = "abi"
            buildConfigField("String", "ARCHITECTURE", "\"x86_64\"")
        }
    }

    signingConfigs {
        create("persistentDebug") {
            storeFile = file("persistent-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            storeFile = file("keystore/release.keystore")
            storePassword = System.getenv("STORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
        getByName("debug") {
            keyAlias = "androiddebugkey"
            keyPassword = "android"
            storePassword = "android"
            storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isCrunchPngs = false
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk {
                debugSymbolLevel = "NONE"
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            signingConfig = if (System.getenv("GITHUB_EVENT_NAME") == "pull_request") {
                signingConfigs.getByName("debug")
            } else {
                signingConfigs.getByName("persistentDebug")
            }
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        jvmToolchain(21)
        compilerOptions {
            freeCompilerArgs.add("-Xannotation-default-target=param-property")
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        lintConfig = file("lint.xml")
        warningsAsErrors = false
        abortOnError = false
        checkDependencies = false
    }

    androidResources {
        generateLocaleConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
            keepDebugSymbols += listOf(
                "**/libandroidx.graphics.path.so",
                "**/libdatastore_shared_counter.so"
            )
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/CONTRIBUTORS.md"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(copyEccoPathAssets)
    dependsOn(generateProto)
}

val protocVersion = libs.versions.protobuf.get()

val downloadProtoc by tasks.registering {
    description = "Download protoc matching project protobuf version"
    val protocDir = layout.buildDirectory.dir("protoc")
    val protocBin = protocDir.map { it.file("bin/protoc") }
    outputs.dir(protocDir)
    doLast {
        val dir = protocDir.get().asFile
        if (protocBin.get().asFile.exists()) return@doLast
        val osName = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch")
        val platform = when {
            osName.contains("linux") && arch == "amd64" -> "linux-x86_64"
            osName.contains("linux") && arch == "aarch64" -> "linux-aarch_64"
            osName.contains("mac") && arch == "aarch64" -> "osx-aarch_64"
            osName.contains("mac") -> "osx-x86_64"
            else -> error("Unsupported platform: $osName/$arch")
        }
        val zipFile = File(dir, "protoc.zip")
        // Java protobuf runtime uses 4.x versioning; protoc releases use the base version without the leading "4."
        val releaseVersion = protocVersion.removePrefix("4.")
        val url = "https://github.com/protocolbuffers/protobuf/releases/download/v$releaseVersion/protoc-$releaseVersion-$platform.zip"
        ant.invokeMethod("get", mapOf("src" to url, "dest" to zipFile))
        ant.invokeMethod("unzip", mapOf("src" to zipFile, "dest" to dir))
        zipFile.delete()
        protocBin.get().asFile.setExecutable(true)
    }
}

abstract class GenerateProtoTask @javax.inject.Inject constructor() : DefaultTask() {
    @get:InputFile
    abstract val protoFile: RegularFileProperty

    @get:InputDirectory
    abstract val protocDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val javaOut = outputDir.get().asFile
        javaOut.mkdirs()
        val protocBin = protocDir.get().file("bin/protoc").asFile.absolutePath
        val protoPath = protoFile.get().asFile
        ProcessBuilder(
            protocBin,
            "--proto_path=${protoPath.parentFile.absolutePath}",
            "--java_out=lite:${javaOut.absolutePath}",
            protoPath.absolutePath
        ).inheritIO().start().waitFor().let { exitCode ->
            if (exitCode != 0) error("protoc failed with exit code $exitCode")
        }
    }
}

val generateProto by tasks.registering(GenerateProtoTask::class) {
    description = "Generate Java lite protobuf classes from metroproto"
    group = "protobuf"
    dependsOn(downloadProtoc)
    protoFile.set(file("${rootProject.projectDir}/metroproto/listentogether.proto"))
    protocDir.set(layout.buildDirectory.dir("protoc"))
    outputDir.set(layout.buildDirectory.dir("generated/source/proto/main/java"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.java?.addGeneratedSourceDirectory(generateProto) { it.outputDir }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn"
        )
        suppressWarnings.set(false)
    }
}

dependencies {
    implementation(libs.guava)
    implementation(libs.coroutines.guava)
    implementation(libs.concurrent.futures)

    implementation(libs.activity)
    implementation(libs.browser)
    implementation(libs.hilt.navigation)
    implementation(libs.datastore)
    implementation(libs.webkit)

    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.util)
    implementation(libs.compose.ui.tooling)
    implementation(libs.compose.animation)
    implementation(libs.compose.reorderable)

    implementation(libs.viewmodel)
    implementation(libs.viewmodel.compose)

    implementation(libs.material3)
    implementation(libs.palette)
    implementation(libs.materialKolor)

    implementation(libs.appcompat)

    implementation(libs.coil)
    implementation(libs.coil.network.okhttp)

    implementation(libs.ucrop)

    implementation(libs.shimmer)

    implementation(libs.media3)
    implementation(libs.media3.session)
    implementation(libs.media3.okhttp)

    // Google Cast - only included in GMS flavor (not available in F-Droid/FOSS builds)
    "gmsImplementation"(libs.media3.cast)
    "gmsImplementation"(libs.mediarouter)
    "gmsImplementation"(libs.cast.framework)

    implementation(libs.room.runtime)
    implementation(libs.kuromoji.ipadic)
    implementation(libs.tinypinyin)
    ksp(libs.room.compiler)
    implementation(libs.room.ktx)

    implementation(libs.apache.lang3)

    implementation(libs.hilt)
    implementation(libs.jsoup)
    ksp(libs.hilt.compiler)

    implementation(project(":innertube"))
    implementation(project(":kugou"))
    implementation(project(":lrclib"))
    implementation(project(":kizzy"))
    implementation(project(":lastfm"))
    implementation(project(":betterlyrics"))
    implementation(project(":simpmusic"))
    implementation(project(":shazamkit"))
    implementation(project(":spotify"))

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)

    // Protobuf for message serialization (lite version for Android)
    implementation(libs.protobuf.javalite)
    implementation(libs.protobuf.kotlin.lite)

    coreLibraryDesugaring(libs.desugaring)

    implementation(libs.timber)

    testImplementation(libs.junit)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.coroutines.test)
}
