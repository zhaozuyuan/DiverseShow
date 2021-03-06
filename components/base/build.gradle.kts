import com.four.buildsrc.*

plugins {
    id("com.android.library")
    id("kotlin-android")
}

android  {
    compileSdkVersion(Env.COMPILE_VERSION)
    buildToolsVersion("30.0.2")

    defaultConfig {
        minSdkVersion(Env.MIN_SDK_VERSION)
        targetSdkVersion(Env.TARGET_SDK_VERSION)
        versionCode(Env.VERSION_CODE)
        versionName(Env.VERSION)

        testInstrumentationRunner("androidx.test.runner.AndroidJUnitRunner")
    }

    buildTypes {
        getAt("release").apply {
            minifyEnabled(false)
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {

    implRepo(Dep.kotlinStdlib)
    implRepo(Dep.coreKTX)
    implRepo(Dep.appcompat)
    implRepo(Dep.googleMaterial)
    implRepo(Dep.constraintLayout)
    testImpl(Dep.junit)
    androidTestImpl(Dep.junitExt)
    androidTestImpl(Dep.espressoCore)

    implRepo(Dep.lifecycleLiveDataKTX)
    implRepo(Dep.lifecycleViewModelKTX)
    implRepo(Dep.lifecycleExtensions)
    implRepo(Dep.eventLine)

    apiProject(Dep.commonUtilProject)
    implProject(Dep.commonNetProject)
}