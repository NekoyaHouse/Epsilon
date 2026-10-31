// buildSrc 与主构建共用同一份版本目录，避免在预编译脚本插件里硬编码 Kotlin 插件版本。
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
