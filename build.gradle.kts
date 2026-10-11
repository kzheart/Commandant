import java.util.zip.ZipFile

plugins {
    id("me.kzheart.klib") version "0.7.0"
}

klib {
    libraryVersion.set("0.18.0")
    name("Commandant")
    main("me.kzheart.commandant.Commandant")
    version(project.version.toString())
    targetPackage("me.kzheart.commandant")
    noApiVersion()
    ketherInterop(true)
    softdepend("PlaceholderAPI")
    modules { command(); script() }
    relocate("org.apache.commons.jexl3", "jexl3")
    relocate("org.apache.commons.logging", "commonslogging")
}

val javascriptRuntime = configurations.create("javascriptRuntime")

dependencies {
    add(javascriptRuntime.name, "org.openjdk.nashorn:nashorn-core:15.6")
    compileOnly("org.spigotmc:spigot-api:1.12.2-R0.1-20180712.012057-156") { isTransitive = false }
    testImplementation("org.spigotmc:spigot-api:1.12.2-R0.1-20180712.012057-156") { isTransitive = false }
    testImplementation("com.google.guava:guava:21.0")
    testImplementation(platform("org.junit:junit-bom:5.14.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)); withSourcesJar() }
tasks.withType<JavaCompile>().configureEach {
    options.release.set(8)
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:-options")
}
tasks.test {
    useJUnitPlatform()
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(providers.gradleProperty("testJava").getOrElse("21")))
    })
}
tasks.build { dependsOn(tasks.named("shadowJar")) }

// Keep the Java 11+ engine nested: the plugin and Klib stay Java 8 compatible.
tasks.processResources {
    from(javascriptRuntime) { into("META-INF/commandant-js") }
}

tasks.register("verifyPluginJar") {
    dependsOn(tasks.named("shadowJar"))
    doLast {
        val jar = layout.buildDirectory.file("libs/${project.name}-${project.version}-all.jar").get().asFile
        ZipFile(jar).use { archive ->
            val entries = archive.entries().asSequence().toList()
            check(archive.getEntry("plugin.yml") != null) { "Missing plugin.yml" }
            check(entries.none { it.name.startsWith("taboolib/") || it.name.startsWith("kotlin/") }) {
                "Unexpected TabooLib/Kotlin runtime"
            }
            check(entries.none { it.name.startsWith("me/kzheart/klib/") }) { "Unrelocated Klib classes" }
            entries.filter { it.name.endsWith(".class") && !it.name.startsWith("META-INF/versions/") }
                .forEach { entry ->
                    archive.getInputStream(entry).use { input ->
                        val header = ByteArray(8)
                        check(input.read(header) == 8) { "Truncated class ${entry.name}" }
                        val major = ((header[6].toInt() and 255) shl 8) or (header[7].toInt() and 255)
                        check(major <= 52) { "${entry.name} requires Java class version $major" }
                    }
                }
        }
    }
}
tasks.check { dependsOn("verifyPluginJar") }

tasks.named("sourcesJar") {
    dependsOn("generateRemoteAccess", "generatePluginYaml", "generateGuardDataDirectory", "generateGuardEntrypoint", "generateGuardKetherInterop")
}
