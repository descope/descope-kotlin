import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("kotlin-android")
    id("maven-publish")
    id("signing")
}

android {
    namespace = "com.descope"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    testOptions {
        targetSdk = 36
    }

    lint {
        targetSdk = 36
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

kotlin {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_1_8)
}

dependencies {
    implementation(descopeLibs.kotlinx.coroutines.android)
    implementation(descopeLibs.androidx.lifecycle.common)
    implementation(descopeLibs.lifecycle.process)
    implementation(descopeLibs.browser)
    implementation(descopeLibs.androidx.security.crypto)
    implementation(descopeLibs.androidx.credentials)
    implementation(descopeLibs.androidx.credentials.play.services.auth)
    implementation(descopeLibs.googleid)

    testImplementation(descopeLibs.kotlinx.coroutines.test)
    testImplementation(descopeLibs.junit)
    testImplementation(descopeLibs.json)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.descope"
            artifactId = "descope-kotlin"
            version = System.getenv("DESCOPESDK_VERSION")
            pom {
                name = project.name
                description = project.name
                url = "https://github.com/descope/descope-kotlin"
                inceptionYear = "2023"
                licenses {
                    license {
                        name = "MIT License"
                        url = "http://www.opensource.org/licenses/mit-license.php"
                    }
                }
                developers {
                    developer {
                        id = "descope"
                        name = "Descope Inc"
                    }
                }
                scm {
                    connection = "scm:https://github.com/descope/descope-kotlin.git"
                    developerConnection = "scm:git@github.com:descope/descope-kotlin.git"
                    url = "https://github.com/descope/descope-kotlin"
                }
            }
            afterEvaluate {
                from(components["release"])
            }
        }
    }
}

signing {
    val signingKey = System.getenv("PGP_KEY")
    val signingPassword = System.getenv("PGP_PASSWORD")
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications["release"]) 
}
