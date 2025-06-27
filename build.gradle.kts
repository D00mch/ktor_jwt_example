val kotlin_version: String by project
val logback_version: String by project
val ktor_version: String by project

plugins {
    kotlin("jvm") version "2.1.10"
    kotlin("plugin.serialization") version "2.1.21"
}

repositories {
    mavenCentral()
}

group = "dumch.com"
version = "0.0.1"

sourceSets {
    main {
        kotlin.srcDir("src")
        resources.srcDir("resources")
    }
    test {
        kotlin.srcDirs("test")
        resources.srcDirs("resources")
    }
}

tasks.test {
    enableAssertions = true
    useJUnitPlatform()
    reports {
        html.required = false
    }
}

dependencies {
    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation("io.ktor:ktor-server-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-netty:$ktor_version")
    implementation("io.ktor:ktor-server-config-yaml:$ktor_version")
    implementation("io.ktor:ktor-server-auth-jwt:$ktor_version")

    // you can get rid on the ktor-server-auth-jwt and import only the necessary dependencies
    // implementation("io.ktor:ktor-server-auth:$ktor_version")
    // implementation("com.auth0:java-jwt:4.5.0")
    // implementation("com.auth0:jwks-rsa:0.22.2")

    implementation("io.ktor:ktor-server-status-pages:$ktor_version")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("ch.qos.logback:logback-classic:$logback_version")

    testImplementation("io.ktor:ktor-server-test-host:$ktor_version")
    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktor_version")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:$kotlin_version")
}
