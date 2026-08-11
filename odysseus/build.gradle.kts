import java.util.Properties
import org.gradle.api.tasks.bundling.Jar

plugins {
    alias(libs.plugins.android.library)
    id("maven-publish")
    id("signing")
}

val codetitansProperties = Properties().apply {
    val propsFile = rootProject.file("codetitans.properties")
    if (propsFile.exists()) {
        propsFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "pl.codetitans.odyssesus"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 23

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildTypes {
        release {
            // Library modules should not minify themselves: shrinking is the consuming app's job,
            // using the consumer rules bundled from src/main/keepRules. Enabling it here has no
            // benefit and, without a matching proguardFiles entry point, previously produced an
            // AAR with an empty classes.jar.
            isMinifyEnabled = false
            isShrinkResources = false
            enableUnitTestCoverage = false
            enableAndroidTestCoverage = false
        }
        debug {
            isMinifyEnabled = false
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true
        }
    }

    publishing {
        singleVariant("release")
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}

val libraryVersion: String = codetitansProperties.getProperty("odysseusVersion")
    ?: project.findProperty("odysseusVersion") as String?
    ?: "0.0.1"

// Maven Central mandates sources + javadoc artifacts. They're only attached to the "mavenCentral"
// publication below (on top of the "release" component), so the GitHub Packages publication stays
// AAR-only.
val sourcesJar = tasks.register<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    from("src/main/java")
}

val javadocJar = tasks.register<Jar>("javadocJar") {
    archiveClassifier.set("javadoc")
    // Placeholder: Central only validates that this artifact exists. Wire up a real Javadoc task
    // here if generated API docs are wanted later.
}

afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                from(components["release"])

                groupId = "pl.codetitans"
                artifactId = "odysseus"
                version = libraryVersion
            }

            register<MavenPublication>("mavenCentral") {
                from(components["release"])
                artifact(sourcesJar)
                artifact(javadocJar)

                groupId = "pl.codetitans"
                artifactId = "odysseus"
                version = libraryVersion

                pom {
                    name.set("Odysseus Android Client")
                    description.set("Android client library for uploading log entries and events to the Odysseus Logging Platform.")
                    url.set("https://github.com/codetitans/odysseus-android")

                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }

                    developers {
                        developer {
                            id.set("codetitans")
                            name.set("CodeTitans Sp. z o.o.")
                            email.set("opensource@codetitans.pl")
                        }
                    }

                    scm {
                        connection.set("scm:git:https://github.com/codetitans/odysseus-android.git")
                        developerConnection.set("scm:git:ssh://git@github.com/codetitans/odysseus-android.git")
                        url.set("https://github.com/codetitans/odysseus-android")
                    }
                }
            }
        }

        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/codetitans/odysseus-android")

                credentials {
                    username = System.getenv("GITHUB_USERNAME")
                        ?: codetitansProperties.getProperty("github.username")
                        ?: project.findProperty("github.username") as String?
                    password = System.getenv("GITHUB_TOKEN")
                        ?: codetitansProperties.getProperty("github.token")
                        ?: project.findProperty("github.token") as String?
                }
            }

            // Sonatype's Central Publisher Portal, reached through its Nexus-compatible staging
            // bridge so the plain maven-publish plugin can deploy to it directly. See
            // https://central.sonatype.org/publish/publish-portal-ossrh-staging-api/
            // Requires a verified "pl.codetitans" namespace on central.sonatype.com and a user
            // token (not your account password) generated there.
            maven {
                name = "SonatypeCentral"
                url = uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")

                credentials {
                    username = System.getenv("SONATYPE_USERNAME")
                        ?: codetitansProperties.getProperty("sonatype.username")
                        ?: project.findProperty("sonatype.username") as String?
                    password = System.getenv("SONATYPE_PASSWORD")
                        ?: codetitansProperties.getProperty("sonatype.password")
                        ?: project.findProperty("sonatype.password") as String?
                }
            }
        }
    }

    // GPG-signs the Central publication (Central rejects unsigned artifacts). Skipped entirely -
    // including for GitHub Packages, which doesn't require signatures - when no key is configured,
    // so a plain build or a GitHub Packages publish keeps working without a GPG setup.
    signing {
        val signingKey = System.getenv("GPG_SIGNING_KEY")
            ?: codetitansProperties.getProperty("gpg.signingKey")
            ?: project.findProperty("gpg.signingKey") as String?
        val signingPassword = System.getenv("GPG_SIGNING_PASSWORD")
            ?: codetitansProperties.getProperty("gpg.signingPassword")
            ?: project.findProperty("gpg.signingPassword") as String?

        isRequired = signingKey != null && signingPassword != null
        if (isRequired) {
            useInMemoryPgpKeys(signingKey, signingPassword)
            sign(publishing.publications["mavenCentral"])
        }
    }
}