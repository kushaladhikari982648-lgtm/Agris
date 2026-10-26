plugins {
    `java-library`
    checkstyle
    alias(libs.plugins.fabric.loom)
}

base {
    archivesName = "${project.property("archives_base_name")}-common"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(project.property("java_version").toString().toInt())
    }
    withSourcesJar()
}

repositories {
    mavenLocal()
}

loom {
    mixin {
        useLegacyMixinAp = false
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings(loom.layered { officialMojangMappings() })

    compileOnly("net.fabricmc:sponge-mixin:${project.property("sponge_mixin_version")}")
    compileOnly("io.github.llamalad7:mixinextras-common:${project.property("mixin_extras_version")}")
    annotationProcessor("io.github.llamalad7:mixinextras-common:${project.property("mixin_extras_version")}")

    compileOnly("org.lwjgl:lwjgl:${project.property("lwjgl_version")}")
    compileOnly("org.lwjgl:lwjgl-opengl:${project.property("lwjgl_version")}")
    compileOnly("org.lwjgl:lwjgl-glfw:${project.property("lwjgl_version")}")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.processResources {
    val props = mapOf(
        "version" to project.property("mod_version"),
        "minecraft_version" to project.property("minecraft_version"),
        "mod_id" to project.property("mod_id"),
        "mod_name" to project.property("mod_name"),
    )
    filesMatching(listOf("**/*.json", "**/*.toml", "META-INF/*.services")) {
        expand(props)
    }
}
