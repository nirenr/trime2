/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.osfans.trime.app-convention")
    id("com.osfans.trime.native-app-convention")
    id("com.osfans.trime.data-checksums")
    id("com.osfans.trime.native-cache-hash")
    id("com.osfans.trime.opencc-data")
    alias(libs.plugins.aboutlibraries)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.osfans.trime"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
// 1. 定义签名配置
    signingConfigs {
        // 使用 findProperty 避免属性缺失时直接崩溃
        val keyPath = project.findProperty("signKeyFile") as? String

        if (!keyPath.isNullOrBlank()) {
            create("myCustomConfig") {
                storeFile = file(keyPath) // 关键：必须用 file() 包裹路径
                storePassword = project.findProperty("signKeyStorePwd") as? String
                keyAlias = project.findProperty("signKeyAlias") as? String
                keyPassword = project.findProperty("signKeyPwd") as? String
            }
        }
    }
    defaultConfig {
        applicationId = "com.nirenr.trime"
        minSdk = 21
        targetSdk = 35
        versionCode = 77
        versionName = "0.7.7"

        multiDexEnabled = true
        buildConfigField("String", "BUILDER", "\"${project.builder}\"")
        buildConfigField("long", "BUILD_TIMESTAMP", project.buildTimestamp)
        buildConfigField("String", "BUILD_COMMIT_HASH", "\"${project.buildCommitHash}\"")
        buildConfigField("String", "BUILD_GIT_REPO", "\"${project.buildGitRepo}\"")
        buildConfigField("String", "BUILD_VERSION_NAME", "\"${project.buildVersionName}\"")

        // --- 新增：注入 APP_KEY ---
        // 从文件中获取，如果文件或 Key 不存在，提供一个默认空字符串，防止编译报错
        var appKey = project.findProperty("API_KEY") as String
        buildConfigField("String", "API_KEY", appKey)
        var appId = project.findProperty("API_ID") as String
        buildConfigField("String", "API_ID", appId)
        // -------------------------
    }

    base {
        // https://www.norio.be/blog/archivesBaseName-removed-from-gradle9.html
        archivesName = "${defaultConfig.applicationId}-${defaultConfig.versionName}"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 2. 引用它
            signingConfig = signingConfigs.getByName("myCustomConfig")
            resValue("string", "trime_app_name", "@string/app_name_release")
        }
        debug {
            // 2. 引用它
            signingConfig = signingConfigs.getByName("myCustomConfig")
            resValue("string", "trime_app_name", "@string/app_name_debug")
        }
        all {
            // remove META-INF/version-control-info.textproto
            @Suppress("UnstableApiUsage")
            vcsInfo.include = false
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        compilerOptions {
            // https://youtrack.jetbrains.com/issue/KT-55947
            jvmTarget.set(JvmTarget.JVM_11)
            // https://youtrack.jetbrains.com/issue/KT-73255/Change-defaulting-rule-for-annotations
            freeCompilerArgs.add("-Xannotation-default-target=param-property")
        }
    }

    // hack workaround lint gradle 8.0.2
    lint {
        checkReleaseBuilds = false
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources {
            excludes +=
                setOf(
                    "/META-INF/*.version",
                    "/META-INF/*.kotlin_module", // cannot be excluded actually
                    "/META-INF/androidx/**",
                    "/DebugProbesKt.bin",
                    "/kotlin-tooling-metadata.json",
                )
        }
    }
}

kotlin {
    sourceSets.configureEach {
        kotlin.srcDir(layout.buildDirectory.dir("generated/ksp/$name/kotlin"))
    }
}

aboutLibraries {
    collect {
        configPath.set(file("licenses").takeIf { it.exists() })
        fetchRemoteLicense.set(false)
        fetchRemoteFunding.set(false)
        includePlatform.set(false)
    }
    export {
        excludeFields.set(
            setOf("generated", "developers", "organization", "scm", "funding", "content"),
        )
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android.applicationVariants.all {
    val variantName = name.replaceFirstChar { it.uppercase() }
    tasks.findByName("generateDataChecksums")?.also {
        tasks.getByName("merge${variantName}Assets").dependsOn(it)
    }
}

dependencies {
    // Markwon 核心库（必须引入）
    implementation("io.noties.markwon:core:4.6.2")
    // 如果你后续需要渲染像常用的：删除线、表格、任务列表等扩展语法（可选）
    // implementation "io.noties.markwon:ext-strikethrough:4.6.2"
    // implementation "io.noties.markwon:ext-tables:4.6.2"
    implementation(files("libs/com.vivo.speechsdk.asr_tts_5.2.4.00_external.aar"))
    ksp(project(":codegen"))
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))
    implementation(libs.kotlinx.coroutines)
    //implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.autofill)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)
    implementation(libs.androidx.paging.runtime.ktx)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.recyclerview)
    //ksp(libs.androidx.room.compiler)
    //implementation(libs.androidx.room.runtime)
    //implementation(libs.androidx.room.ktx)
    //implementation(libs.androidx.room.paging)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.flexbox)
    //implementation(libs.bravh)
    //implementation(libs.kaml)
    //implementation(libs.timber)
    //implementation(libs.xxpermissions)
    //ksp(libs.kotlin.inject.compiler)
    //implementation(libs.kotlin.inject.runtime)
    //implementation(libs.splitties.bitflags)
    //implementation(libs.splitties.systemservices)
    //implementation(libs.splitties.views.dsl)
    //implementation(libs.splitties.views.dsl.constraintlayout)
    //implementation(libs.splitties.views.dsl.coordinatorlayout)
    //implementation(libs.splitties.views.dsl.recyclerview)
    //implementation(libs.splitties.views.recyclerview)
    implementation(libs.aboutlibraries.core)
    //implementation(libs.iconics.core)
    //implementation(libs.community.material.typeface) {
    //    artifact { type = "aar" }
    //}
    implementation("com.squareup.okhttp3:okhttp:4.9.1")
    // Testing
    //testImplementation(libs.junit)
    //testImplementation(libs.kotest.runner.junit5)
    //testImplementation(libs.kotest.assertions.core)
    //androidTestImplementation(libs.junit)
}

configurations {
    all {
        // remove Baseline Profile Installer or whatever it is...
        exclude(group = "androidx.profileinstaller", module = "profileinstaller")
    }
}
