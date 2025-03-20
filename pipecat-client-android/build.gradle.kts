plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.jetbrains.kotlin.serialization)
    alias(libs.plugins.jetbrains.dokka)
    `maven-publish`
    signing
}

android {
    namespace = "ai.pipecat.client"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    lint {
        targetSdk = 35
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
}

publishing {
    repositories {
        maven {
            url = rootProject.layout.buildDirectory.dir("PipecatLocalRepo").get().asFile.toURI()
            name = "PipecatLocalRepo"
        }
    }

    publications {
        register<MavenPublication>("release") {
            groupId = "ai.pipecat"
            artifactId = "client"
            version = "0.3.3"

            pom {
                name.set("Pipecat Client")
                description.set("Core Pipecat client library for Android")
                url.set("https://github.com/pipecat-ai/pipecat-client-android")

                developers {
                    developer {
                        id.set("pipecat.ai")
                        name.set("pipecat.ai")
                    }
                }

                licenses {
                    license {
                        name.set("BSD 2-Clause License")
                        url.set("https://github.com/pipecat-ai/pipecat-client-android/blob/main/LICENSE")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/pipecat-ai/pipecat-client-android.git")
                    developerConnection.set("scm:git:ssh://github.com:pipecat-ai/pipecat-client-android.git")
                    url.set("https://github.com/pipecat-ai/pipecat-client-android")
                }
            }

            afterEvaluate {
                from(components["release"])
            }
        }
    }
}

signing {
    val signingKey = System.getenv("RTVI_GPG_SIGNING_KEY")
    val signingPassphrase = System.getenv("RTVI_GPG_SIGNING_PASSPHRASE")

    if (!signingKey.isNullOrEmpty() || !signingPassphrase.isNullOrEmpty()) {
        useInMemoryPgpKeys(signingKey, signingPassphrase)
        sign(publishing.publications)
    }
}
