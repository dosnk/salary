pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SalaryManager"

include(":app")
include(":core:common")
include(":core:network")
include(":core:data")
include(":core:design")
include(":core:ui")
include(":feature:auth")
include(":feature:home")
include(":feature:statistics")
include(":feature:ai")
include(":feature:profile")
include(":feature:messages")
