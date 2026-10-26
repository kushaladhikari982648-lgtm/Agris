plugins {
    java
    checkstyle
    alias(libs.plugins.fabric.loom)
}

version = "${project.property("mod_version")}+mc${project.property("minecraft_version")}"

base {
    archivesName = "${project.property("archives_base_name")}-fabric"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(project.property("java_version").toString().toInt())
    }
    withSourcesJar()
}

val commonJava = file("../common/src/main/java")
val commonResources = file("../common/src/main/resources")

sourceSets {
    main {
        java.srcDir(commonJava)
        resources.srcDir(commonResources)
    }
}

loom {
    mods {
        create("agris") {
            sourceSet(sourceSets["main"])
        }
    }
    if (project.property("agris.enableRunConfigs").toString().toBoolean()) {
        runs {
            create("client") {
                client()
            }
        }
    }
}

repositories {
    mavenLocal()
}

dependencies {
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings(loom.officialMojangMappings())

    modImplementation("net.fabricmc:fabric-loader:${project.property("fabric_loader_version")}")
    modCompileOnly("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_api_version")}")

    compileOnly("io.github.llamalad7:mixinextras-common:${project.property("mixin_extras_version")}")
    include("io.github.llamalad7:mixinextras-fabric:${project.property("mixin_extras_version")}")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.processResources {
    val props = mapOf(
        "version" to project.property("mod_version"),
        "minecraft_version" to project.property("minecraft_version"),
        "loader_version" to project.property("fabric_loader_version"),
        "fabric_api_version" to project.property("fabric_api_version"),
        "mod_id" to project.property("mod_id"),
        "mod_name" to project.property("mod_name"),
        "mod_description" to project.property("mod_description"),
        "mod_homepage" to project.property("mod_homepage"),
        "mod_sources" to project.property("mod_sources"),
        "mod_issue_tracker" to project.property("mod_issue_tracker"),
        "mod_license" to project.property("mod_license"),
    )
    filesMatching("fabric.mod.json") {
        expand(props)
    }
}

tasks.withType<Jar>().configureEach {
    exclude("com/terraformersmc/**")
}

tasks.withType<net.fabricmc.loom.task.RemapJarTask> {
    addNestedDependencies = true
}

tasks.named("build") {
    dependsOn("remapJar")
}
