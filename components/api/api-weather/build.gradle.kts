import com.four.buildsrc.Dep
import com.four.buildsrc.Env
import com.four.buildsrc.implProject
import com.four.buildsrc.implRepo

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

    implProject(Dep.featureWeatherProject)
}