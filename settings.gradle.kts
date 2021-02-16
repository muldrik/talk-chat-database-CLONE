pluginManagement {
    plugins {
        val kotlin_version: String by settings
        kotlin("jvm").version(kotlin_version)
    }
}

include("shared")
include("client")
include("registry")
