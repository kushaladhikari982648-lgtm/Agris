pluginManagement {
    repositories {
        mavenLocal()
        maven {
            name = "FabricMC"
            url = uri("https://maven.fabricmc.net/")
        }
        maven {
            name = "SpongePowered"
            url = uri("https://repo.spongepowered.org/maven/")
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        maven {
            name = "FabricMC"
            url = uri("https://maven.fabricmc.net/")
        }
        maven {
            name = "SpongePowered"
            url = uri("https://repo.spongepowered.org/maven/")
        }
        mavenCentral()
    }
}

rootProject.name = "agris"

include("common")
include("fabric")
