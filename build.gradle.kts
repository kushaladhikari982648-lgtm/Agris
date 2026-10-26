import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    java
    checkstyle
}

allprojects {
    group = property("maven_group").toString()
    version = property("mod_version").toString()

    defaultTasks("build")
}

subprojects {
    apply(plugin = "checkstyle")

    extensions.configure<CheckstyleExtension> {
        toolVersion = "10.20.1"
        configFile = rootProject.file("checkstyle.xml")
        maxWarnings = 0
        isIgnoreFailures = false
    }

    tasks.withType<Checkstyle>().configureEach {
        reports {
            xml.required = true
            html.required = false
        }
        enabled = false
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = project.property("java_version").toString().toInt()
        options.compilerArgs.addAll(listOf("-Xlint:all,-serial,-processing", "-parameters"))
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events(TestLogEvent.FAILED, TestLogEvent.SKIPPED)
            exceptionFormat = TestExceptionFormat.FULL
            showStandardStreams = false
        }
    }
}

tasks.register("buildAll") {
    group = "agris"
    description = "Builds common and fabric artifacts for Minecraft 1.20.1."
    dependsOn(":common:build", ":fabric:build")
}

tasks.register("verifyAll") {
    group = "agris"
    description = "Runs every verification: compile, unit tests, static tree checks, resource validation."
    dependsOn("buildAll", ":common:test", "checkTree")
}

tasks.register("checkTree") {
    group = "agris"
    description = "Runs the offline structural verifier (tools/check.py) — no JDK or maven needed."
    val script = rootProject.file("tools/check.py")
    val output = layout.buildDirectory.file("reports/agris/tree-check.txt")
    outputs.file(output)
    inputs.file(script)
    doLast {
        val target = output.get().asFile
        target.parentFile.mkdirs()
        val proc = ProcessBuilder("python3", script.absolutePath, rootProject.projectDir.absolutePath)
            .redirectErrorStream(true)
            .start()
        val text = proc.inputStream.bufferedReader().readText()
        target.writeText(text)
        print(text)
        val code = proc.waitFor()
        if (code != 0) {
            throw GradleException("tools/check.py reported structural errors (exit $code); see ${target.absolutePath}")
        }
    }
}
