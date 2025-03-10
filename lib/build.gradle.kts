import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// library version is defined in gradle.properties
val libraryVersion: String by project

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.gradle.maven-publish")
    id("org.jetbrains.dokka")
    id("org.jetbrains.dokka-javadoc")
}

android {
    namespace = "com.chaintope.tapyrus.wallet"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(file("proguard-android-optimize.txt"), file("proguard-rules.pro"))
        }
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
    
    lint {
        // Disable lint errors that would prevent the build
        abortOnError = false
        // Ignore the NewApi issue with java.lang.ref.Cleaner
        disable += "NewApi"
    }
}

kotlin {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation("net.java.dev.jna:jna:5.14.0@aar")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7")
    implementation("androidx.appcompat:appcompat:1.4.0")
    implementation("androidx.core:core-ktx:1.7.0")
    api("org.slf4j:slf4j-api:1.7.30")

    androidTestImplementation("com.github.tony19:logback-android:2.0.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")
    androidTestImplementation("org.jetbrains.kotlin:kotlin-test:1.6.10")
    androidTestImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.6.10")
}

// Extract JNA native libraries
tasks.register<Copy>("extractJnaLibs") {
    doLast {
        // Use a resolvable configuration
        val jnaAar = configurations.getByName("releaseCompileClasspath")
            .filter { it.name.contains("jna") && it.name.endsWith("aar") }
            .singleOrNull()
        
        if (jnaAar != null) {
            from(zipTree(jnaAar)) {
                include("jni/**")
                into(layout.buildDirectory.dir("jniLibs").get().asFile)
            }
        } else {
            logger.warn("JNA AAR not found in releaseCompileClasspath")
        }
    }
}

// Make sure extractJnaLibs runs during the build process
//tasks.named("preBuild") {
//    finalizedBy("extractJnaLibs")
//}

android {
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs(layout.buildDirectory.dir("jniLibs").get().asFile)
        }
    }
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("maven") {
                groupId = "com.github.chaintope"
                artifactId = "tapyrus-wallet-android"
                version = libraryVersion

                from(components["release"])
                pom {
                    name.set("TapyrusWalletAndroid")
                    description.set("Kotlin bindings for Tapyrus Wallet")
                    url.set("https://github.com/chaintope/rust-tapyrus-wallet-ffi")
                    licenses {
                        license {
                            name.set("MIT")
                            url.set("https://github.com/chaintope/rust-tapyrus-wallet-ffi/blob/master/LICENSE")
                        }
                    }
                    developers {
                        developer {
                            id.set("chaintope")
                            name.set("Chaintope Inc.")
                            email.set("info@chaintope.com")
                        }
                    }
                    scm {
                        connection.set("scm:git:github.com/chaintope/rust-tapyrus-wallet-ffi.git")
                        developerConnection.set("scm:git:ssh://github.com/chaintope/rust-tapyrus-wallet-ffi.git")
                        url.set("https://github.com/chaintope/rust-tapyrus-wallet-ffi/tree/master")
                    }
                }
                
                // This is a workaround for an issue where JitPack removes the thirdPartyCompatibility metadata
                // from the .module file during the build process, even when @aar is specified in the dependency.
                // By explicitly setting the dependency metadata in the POM XML, we ensure proper AAR resolution.
                pom.withXml {
                    asNode().apply {
                        // Find existing dependencies node or create one if it doesn't exist
                        val dependencies = children().find { (it as groovy.util.Node).name() == "dependencies" } as groovy.util.Node? 
                            ?: appendNode("dependencies")
                        
                        // Explicitly add JNA dependency with AAR type
                        dependencies.appendNode("dependency").apply {
                            appendNode("groupId", "net.java.dev.jna")
                            appendNode("artifactId", "jna")
                            appendNode("version", "5.14.0")
                            appendNode("type", "aar")
                            
                            // Add properties equivalent to thirdPartyCompatibility metadata
                            appendNode("classifier", "")
                            val properties = appendNode("properties")
                            properties.appendNode("aar.type", "aar")
                            properties.appendNode("aar.extension", "aar")
                        }
                    }
                }
            }
        }
    }
}
