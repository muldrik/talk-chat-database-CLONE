group = "ru.senin.kotlin.net.registry"
application.mainClassName = "ru.senin.kotlin.net.registry.ApplicationKt"



dependencies {

    val ktor_version: String by project
    val logback_version: String by project
    val exposedVersion: String by project

    implementation(project(":shared"))
    implementation("io.ktor:ktor-server-netty:$ktor_version")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation("io.ktor:ktor-server-host-common:$ktor_version")
    implementation("io.ktor:ktor-jackson:$ktor_version")
    implementation("io.ktor:ktor-network:$ktor_version")
    implementation("io.ktor:ktor-client-core:$ktor_version")
    implementation("io.ktor:ktor-client-json:$ktor_version")
    implementation("io.ktor:ktor-client-cio:$ktor_version")
    implementation("io.ktor:ktor-websockets:$ktor_version")
    implementation("io.ktor:ktor-client-websockets:$ktor_version")
    implementation("io.ktor:ktor-client-logging:$ktor_version")
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("com.apurebase:arkenv:3.1.0")
    implementation(group = "io.github.rybalkinsd", name = "kohttp", version = "0.12.0")
    implementation("com.h2database:h2:1.4.199")

    testImplementation("io.ktor:ktor-server-tests:$ktor_version")
}
