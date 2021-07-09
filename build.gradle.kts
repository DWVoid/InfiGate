import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack
val ktor_version: String by project
val kotlin_version: String by project
val logback_version: String by project

plugins {
    application
    kotlin("multiplatform") version "1.5.20"
}

group = "cn.cn.newinfinideas"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    jvm("server") {
        compilations.all {
            kotlinOptions.jvmTarget = "11"
        }
        withJava()
    }
    js("client", IR) {
        binaries.executable()
        browser {
            commonWebpackConfig {
                cssSupport.enabled = true
            }
        }
    }
    sourceSets {
        val commonMain by getting
        val serverMain by getting {
            dependencies {
                implementation("io.ktor:ktor-server-core:$ktor_version")
                implementation("io.ktor:ktor-auth:$ktor_version")
                implementation("io.ktor:ktor-locations:$ktor_version")
                implementation("io.ktor:ktor-websockets:$ktor_version")
                implementation("io.ktor:ktor-client-core:$ktor_version")
                implementation("io.ktor:ktor-client-core-jvm:$ktor_version")
                implementation("io.ktor:ktor-client-cio:$ktor_version")
                implementation("io.ktor:ktor-client-websockets:$ktor_version")
                implementation("io.ktor:ktor-server-host-common:$ktor_version")
                implementation("io.ktor:ktor-server-netty:$ktor_version")
                implementation("io.ktor:ktor-html-builder:$ktor_version")
                implementation("ch.qos.logback:logback-classic:$logback_version")
                implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:0.7.2")
            }
        }
        val clientMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlin-wrappers:kotlin-react:17.0.2-pre.214-kotlin-$kotlin_version")
                implementation("org.jetbrains.kotlin-wrappers:kotlin-react-dom:17.0.2-pre.214-kotlin-$kotlin_version")
                implementation("org.jetbrains.kotlin-wrappers:kotlin-styled:5.3.0-pre.214-kotlin-$kotlin_version")
                implementation("org.jetbrains.kotlin-wrappers:kotlin-react-router-dom:5.2.0-pre.214-kotlin-$kotlin_version")
            }
        }
    }
}

application {
    mainClass.set("ServerKt")
}

tasks.getByName<KotlinWebpack>("clientBrowserProductionWebpack") {
    outputFileName = "client.js"
}

tasks.getByName<Jar>("serverJar") {
    dependsOn(tasks.getByName("clientBrowserProductionWebpack"))
    val clientBrowserProductionWebpack = tasks.getByName<KotlinWebpack>("clientBrowserProductionWebpack")
    from(File(clientBrowserProductionWebpack.destinationDirectory, clientBrowserProductionWebpack.outputFileName))
}

tasks.getByName<JavaExec>("run") {
    dependsOn(tasks.getByName<Jar>("serverJar"))
    classpath(tasks.getByName<Jar>("serverJar"))
}