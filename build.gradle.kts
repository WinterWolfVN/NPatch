import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.internal.storage.file.FileRepository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.gradle.kotlin.dsl.extra

plugins {
    alias(libs.plugins.agp.lib) apply false
    alias(libs.plugins.agp.app) apply false
    alias(npatch.plugins.compose.compiler) apply false
    alias(npatch.plugins.kotlin.android) apply false
}

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.eclipse.jgit:org.eclipse.jgit:7.3.0.202506031305-r")
    }
}

val commitCount = run {
    val repo = FileRepository(rootProject.file(".git"))
    val refId = repo.refDatabase.exactRef("refs/remotes/origin/master").objectId!!
    Git(repo).log().add(refId).call().count()
}

val (coreCommitCount, coreLatestTag) = FileRepositoryBuilder().setGitDir(rootProject.file(".git/modules/core"))
    .runCatching {
        build().use { repo ->
            val git = Git(repo)
            val coreCommitCount = git.log().add(repo.refDatabase.exactRef("HEAD").objectId).call().count()
            val ver = git.describe().setTags(true).setAbbrev(0).call().removePrefix("v")
            coreCommitCount to ver
        }
    }.getOrNull() ?: (3047 to "2.0")

val defaultManagerPackageName by extra("org.lsposed.npatch")
val apiCode by extra(100)
val verCode by extra(commitCount)
val verName by extra("0.8.0")
val coreVerCode by extra(coreCommitCount)
val coreVerName by extra(coreLatestTag)
val androidMinSdkVersion by extra(24)
val androidTargetSdkVersion by extra(36)
val androidCompileSdkVersion by extra(36)
val androidCompileNdkVersion by extra("29.0.13599879")
val androidBuildToolsVersion by extra("36.1.0")
val androidSourceCompatibility by extra(JavaVersion.VERSION_17)
val androidTargetCompatibility by extra(JavaVersion.VERSION_17)

tasks.register<Delete>("clean") {
    delete(layout.buildDirectory)
}

listOf("Debug", "Release").forEach { variant ->
    tasks.register("build$variant") {
        dependsOn(tasks.findByPath(":jar:build$variant") ?: "jar:build$variant")
        dependsOn(tasks.findByPath(":manager:build$variant") ?: "manager:build$variant")
    }
}

tasks.register("buildAll") {
    dependsOn("buildDebug", "buildRelease")
}

fun Project.configureBaseExtension() {
    extensions.findByType(BaseExtension::class)?.run {
        compileSdkVersion(androidCompileSdkVersion)
        buildToolsVersion = androidBuildToolsVersion
        ndkVersion = androidCompileNdkVersion

        externalNativeBuild.cmake {
            version = "3.29.8+"
            buildStagingDirectory = layout.buildDirectory.get().asFile
        }

        defaultConfig {
            minSdk = androidMinSdkVersion
            targetSdk = androidTargetSdkVersion
            versionCode = verCode
            versionName = verName
            multiDexEnabled = true

            signingConfigs.create("config") {
                val androidStoreFile = (
                    System.getenv("ANDROID_STORE_FILE")
                        ?: project.findProperty("androidStoreFile")?.toString()
                    )?.takeIf { it.isNotBlank() }
                val androidStorePassword = System.getenv("ANDROID_STORE_PASSWORD")
                    ?: project.findProperty("androidStorePassword")?.toString()
                val androidKeyAlias = System.getenv("ANDROID_KEY_ALIAS")
                    ?: project.findProperty("androidKeyAlias")?.toString()
                val androidKeyPassword = System.getenv("ANDROID_KEY_PASSWORD")
                    ?: project.findProperty("androidKeyPassword")?.toString()
                if (androidStoreFile != null && androidStorePassword != null && androidKeyAlias != null && androidKeyPassword != null) {
                    storeFile = rootProject.file(androidStoreFile)
                    storePassword = androidStorePassword
                    keyAlias = androidKeyAlias
                    keyPassword = androidKeyPassword
                }
                if (this is com.android.build.api.dsl.ApkSigningConfig) {
                    enableV2Signing = true
                    enableV3Signing = true
                }
            }

            externalNativeBuild {
                cmake {
                    arguments += "-DEXTERNAL_ROOT=${File(rootDir.absolutePath, "core/external")}"
                    arguments += "-DCORE_ROOT=${File(rootDir.absolutePath, "core/core/src/main/jni")}"
                    abiFilters("arm64-v8a", "x86_64")
                    val flags = arrayOf(
                        "-Wall",
                        "-Qunused-arguments",
                        "-Wno-gnu-string-literal-operator-template",
                        "-fno-rtti",
                        "-fvisibility=hidden",
                        "-fvisibility-inlines-hidden",
                        "-fno-exceptions",
                        "-fno-stack-protector",
                        "-fomit-frame-pointer",
                        "-Wno-builtin-macro-redefined",
                        "-Wno-unused-value",
                        "-D__FILE__=__FILE_NAME__",
                    )
                    cppFlags("-std=c++20", *flags)
                    cFlags("-std=c18", *flags)
                    arguments(
                        "-DCMAKE_EXPORT_COMPILE_COMMANDS=ON",
                        "-DVERSION_CODE=$verCode",
                        "-DVERSION_NAME=$verName",
                    )
                }
            }
        }

        compileOptions {
            sourceCompatibility = androidSourceCompatibility
            targetCompatibility = androidTargetCompatibility
            isCoreLibraryDesugaringEnabled = true
        }

        buildTypes {                
            named("debug") {
                externalNativeBuild {
                    cmake {
                        arguments.addAll(
                            arrayOf(
                                "-DCMAKE_CXX_FLAGS_DEBUG=-Og",
                                "-DCMAKE_C_FLAGS_DEBUG=-Og",
                            )
                        )
                    }
                }
            }
            named("release") {
                signingConfig = null
                externalNativeBuild {
                    cmake {
                        val flags = arrayOf(
                            "-Wl,--exclude-libs,ALL",
                            "-ffunction-sections",
                            "-fdata-sections",
                            "-Wl,--gc-sections",
                            "-fno-unwind-tables",
                            "-fno-asynchronous-unwind-tables",
                            "-flto=thin",
                            "-Wl,--thinlto-cache-policy,cache_size_bytes=300m",
                            "-Wl,--thinlto-cache-dir=${layout.buildDirectory.get().asFile.absolutePath}/.lto-cache", 
                        )
                        cppFlags.addAll(flags)
                        cFlags.addAll(flags)
                        val configFlags = arrayOf(
                            "-Oz",
                            "-DNDEBUG"
                        ).joinToString(" ")
                        arguments.addAll(
                            arrayOf(
                                "-DCMAKE_CXX_FLAGS_RELEASE=$configFlags",
                                "-DCMAKE_CXX_FLAGS_RELWITHDEBINFO=$configFlags",
                                "-DCMAKE_C_FLAGS_RELEASE=$configFlags",
                                "-DCMAKE_C_FLAGS_RELWITHDEBINFO=$configFlags",
                                "-DDEBUG_SYMBOLS_PATH=${layout.buildDirectory.get().asFile.absolutePath}/symbols", 
                            )
                        )
                    }
                }
            }
        }           
    }
}

subprojects {
    plugins.withId("com.android.application") { configureBaseExtension() }
    plugins.withId("com.android.library") { configureBaseExtension() }

    afterEvaluate {
        if (plugins.hasPlugin("com.android.application") || plugins.hasPlugin("com.android.library")) {
            dependencies {
                add("coreLibraryDesugaring", "com.android.tools:desugar_jdk_libs_nio:2.1.5")
            }
        }
    }
}

allprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    tasks.withType<JavaCompile>().configureEach {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
}

project(":core") {
    afterEvaluate {
        if (property("android") is LibraryExtension) {
            val android = property("android") as LibraryExtension
            android.run {
                buildTypes {
                    getByName("release") {
                        proguardFiles(rootProject.file("share/lspatch-rules.pro"))
                    }
                }
            }
        }
    }
}

gradle.taskGraph.whenReady {
    allTasks.forEach { task ->
        if (task.name.contains("lint", ignoreCase = true)) {
            task.enabled = false
        }
    }
} 
 
