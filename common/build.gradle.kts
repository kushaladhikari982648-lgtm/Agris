plugins {
    `java-library`
    checkstyle
    alias(libs.plugins.fabric.loom)
}

version = "${project.property("mod_version")}+mc${project.property("minecraft_version")}"

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

dependencies {
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings(loom.officialMojangMappings())

    modCompileOnly("net.fabricmc:fabric-loader:${project.property("fabric_loader_version")}")

    compileOnly("net.fabricmc:sponge-mixin:${project.property("sponge_mixin_version")}")
    compileOnly("io.github.llamalad7:mixinextras-common:${project.property("mixin_extras_version")}")

    compileOnly("org.lwjgl:lwjgl:${project.property("lwjgl_version")}")
    compileOnly("org.lwjgl:lwjgl-opengl:${project.property("lwjgl_version")}")
    compileOnly("org.lwjgl:lwjgl-glfw:${project.property("lwjgl_version")}")

    testImplementation("org.lwjgl:lwjgl:${project.property("lwjgl_version")}")
    testImplementation("org.lwjgl:lwjgl-opengl:${project.property("lwjgl_version")}")
    testImplementation("org.lwjgl:lwjgl-glfw:${project.property("lwjgl_version")}")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
