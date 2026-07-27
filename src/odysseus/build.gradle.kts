import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    id("maven-publish")
}

val githubProperties = Properties().apply {
    val propsFile = rootProject.file("github.properties")
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
        minSdk = 28

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

afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                from(components["release"])

                groupId = "pl.codetitans"
                artifactId = "odysseus"
                version = githubProperties.getProperty("odysseusVersion")
                            ?: project.findProperty("odysseusVersion") as String?
                                ?: "0.0.1"
            }
        }

        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/codetitans/odysseus-android")

                credentials {
                    username = System.getenv("GITHUB_USERNAME")
                        ?: githubProperties.getProperty("github.username")
                        ?: project.findProperty("github.username") as String?
                    password = System.getenv("GITHUB_TOKEN")
                        ?: githubProperties.getProperty("github.token")
                        ?: project.findProperty("github.token") as String?
                }
            }
        }
    }
}