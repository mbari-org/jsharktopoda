plugins {
    application
    id("com.adarshr.test-logger") version "4.0.0"
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("org.beryx.jlink") version "3.2.1"
    id("com.github.ben-manes.versions") version "0.53.0"
}

version = "2.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenLocal()
    mavenCentral()
}

javafx {
    version = "25.0.1"
    modules = listOf(
        "javafx.base",
        "javafx.controls",
        "javafx.fxml",
        "javafx.graphics",
        "javafx.media",
        "javafx.swing"
    )
}

val runtimeJvmArgs = listOf("-Xms1g")

val vcr4j = "5.3.1"
dependencies {
    implementation("io.reactivex.rxjava3:rxjava:3.1.8")
    implementation("org.kordamp.ikonli:ikonli-javafx:12.3.1")
    implementation("org.kordamp.ikonli:ikonli-material-pack:12.3.1")
    implementation("org.mbari.commons:jcommons:0.0.7")
    implementation("org.mbari.vcr4j:vcr4j-core:$vcr4j")
    implementation("org.mbari.vcr4j:vcr4j-remote:$vcr4j")
    runtimeOnly("javax.servlet:javax.servlet-api:4.0.1")
    runtimeOnly("org.fusesource.jansi:jansi:2.4.2")
    runtimeOnly("org.slf4j:slf4j-jdk-platform-logging:2.0.17")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.29")
    testImplementation("junit:junit:4.13.2")
}

application {
    mainModule.set("jsharktopoda")
    mainClass.set("org.mbari.jsharktopoda.JSharktopoda")
    applicationDefaultJvmArgs = runtimeJvmArgs
}

tasks.jar {
    manifest {
        attributes("Main-Class" to "org.mbari.jsharktopoda.JSharktopoda")
    }
}

jlink {
    options.set(listOf("--strip-debug", "--compress", "zip-6", "--no-header-files", "--no-man-pages"))

    launcher {
        name = "jsharktopoda"
    }

    addExtraDependencies("javafx")

    jpackage {
        val customInstallerOptions = mutableListOf(
            "--app-version", project.version.toString(),
            "--copyright", "Monterey Bay Aquarium Research Institute 2020",
            "--name", project.name,
            "--vendor", "org.mbari"
        )

        val currentOs = System.getProperty("os.name").lowercase()
        when {
            "linux" in currentOs -> {
                installerType = "deb"
                imageOptions = listOf("--icon", "src/jpackage/linux/jsharktopoda.png")
            }
            "windows" in currentOs -> {
                installerType = "msi"
                customInstallerOptions.addAll(listOf(
                    "--win-upgrade-uuid", "bd89f21a-a2c3-49c1-9502-345199c3ed8e",
                    "--win-menu-group", "VARS",
                    "--win-menu"
                ))
                imageOptions = listOf("--icon", "src/jpackage/windows/jsharktopoda.ico")
            }
            else -> {
                installerType = "dmg"
                customInstallerOptions.addAll(listOf(
                    "--mac-package-name", "jsharktopoda",
                    "--mac-package-identifier", "org.mbari.jsharktopoda"
                ))
                imageOptions = listOf("--icon", "src/jpackage/macos/jsharktopoda.icns")
            }
        }
        installerOptions = customInstallerOptions
    }
}

tasks.named("jpackageImage") {
    doLast {
        val currentOs = System.getProperty("os.name").lowercase()
        if ("mac" in currentOs) {
            val signer = System.getenv("MAC_CODE_SIGNER")
            if (signer != null) {
                val entitlements = "${projectDir}/src/jpackage/macos/java.entitlements"
                val jpackageDir = file("${projectDir}/build/jpackage")

                fun codesign(vararg targets: String) {
                    val cmd = listOf(
                        "codesign",
                        "--entitlements", entitlements,
                        "--options", "runtime",
                        "--timestamp", "-vvv", "-f",
                        "--sign", signer
                    ) + targets.toList()
                    val process = ProcessBuilder(cmd)
                        .directory(jpackageDir)
                        .inheritIO()
                        .start()
                    val exitCode = process.waitFor()
                    if (exitCode != 0) {
                        throw GradleException("codesign failed with exit code $exitCode")
                    }
                }

                val dirsToBeSigned = listOf(
                    file("${projectDir}/build/jpackage/jsharktopoda.app/Contents/runtime/Contents/Home/bin"),
                    file("${projectDir}/build/jpackage/jsharktopoda.app/Contents/runtime/Contents/Home/lib"),
                    file("${projectDir}/build/jpackage/jsharktopoda.app/Contents/runtime/Contents/Home/lib/server"),
                    file("${projectDir}/build/jpackage/jsharktopoda.app/Contents/runtime/Contents/MacOS")
                )

                dirsToBeSigned.forEach { dir ->
                    println("Signing $dir")
                    dir.listFiles()
                        ?.filter { it.isFile }
                        ?.forEach { file ->
                            println("MACOSX: Signing $file")
                            codesign(file.absolutePath)
                        }
                }

                println("MACOSX: Signing application")
                codesign("jsharktopoda.app")
            }
        }
    }
}
