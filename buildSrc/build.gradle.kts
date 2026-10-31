plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    // Kotlin Gradle plugin 的插件标记与部分依赖托管在插件门户，和 kotlin-dsl 自带的版本保持一致。
    gradlePluginPortal()
}

dependencies {
    // multiloader-common 预编译脚本要 apply Kotlin 插件并配置 Kotlin DSL，
    // 因此 KGP 必须出现在 buildSrc 的编译与运行类路径上；版本统一来自版本目录。
    implementation(libs.kotlin.gradle.plugin)
}
