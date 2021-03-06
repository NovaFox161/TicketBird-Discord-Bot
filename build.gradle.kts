
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.21"
    java

    kotlin("plugin.spring") version "1.5.21"

    id("com.google.cloud.tools.jib") version("3.0.0")
    id ("org.springframework.boot") version ("2.5.0")

    id("com.gorylenko.gradle-git-properties") version "2.3.1"
}

buildscript {
    dependencies {
        classpath("com.squareup:kotlinpoet:1.7.2")
    }
}

repositories {
    mavenLocal()
    maven {
        url = uri("https://jitpack.io")
    }

    maven {
        url = uri("https://emily.dreamexposure.org/artifactory/dreamexposure-public/")
    }

    maven {
        url = uri("https://repo.maven.apache.org/maven2/")
    }
}
//versions
val d4jVersion = "3.2.0-M3"
val d4jStoresVersion = "3.2.0"
val springVersion = "2.5.1"

val springSecVersion = "5.5.1"
val nettyForcedVersion = "4.1.56.Final"
val reactorCoreVersion = "3.4.7"
val reactorNettyVersion = "1.0.8"
val r2dbcMysqlVersion = "0.8.1.RELEASE"
val r2dbcPoolVersion = "0.8.3.RELEASE"

val kotlinSrcDir: File = buildDir.resolve("src/main/kotlin")

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.5.21")

    implementation("org.dreamexposure:NovaUtils:1.0.0-SNAPSHOT")

    implementation("com.discord4j:discord4j-core:$d4jVersion")
    implementation("com.discord4j:stores-redis:$d4jStoresVersion") {
        exclude("io.netty", "*")
    }

    implementation("mysql:mysql-connector-java:8.0.26")
    implementation("org.json:json:20210307")

    implementation("dev.miku:r2dbc-mysql:$r2dbcMysqlVersion") {
        exclude("io.netty", "*")
        exclude("io.projectreactor", "*")
    }
    implementation("io.r2dbc:r2dbc-pool:$r2dbcPoolVersion")

    //Forced version nonsense
    implementation("io.netty:netty-all:$nettyForcedVersion")
    implementation("io.projectreactor:reactor-core:$reactorCoreVersion")
    implementation("io.projectreactor.netty:reactor-netty:$reactorNettyVersion")

    implementation("org.thymeleaf:thymeleaf:3.0.12.RELEASE")
    implementation("org.thymeleaf:thymeleaf-spring5:3.0.12.RELEASE")
    implementation("nz.net.ultraq.thymeleaf:thymeleaf-layout-dialect:2.5.3")

    implementation("org.springframework.boot:spring-boot-starter-thymeleaf:$springVersion")
    implementation("org.springframework.boot:spring-boot-starter-webflux:$springVersion")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc:$springVersion")
    implementation("org.springframework.session:spring-session-data-redis:$springVersion")
    implementation("org.springframework.security:spring-security-core:$springSecVersion")
    implementation("org.springframework.security:spring-security-web:$springSecVersion")

    implementation("com.squareup.okhttp3:okhttp:4.9.1")
    implementation("com.github.DiscordBotList:Java-Wrapper:v1.0")
    implementation("club.minnced:discord-webhooks:0.5.7")
    implementation("org.flywaydb:flyway-core:7.9.2")
}

group = "org.dreamexposure"
version = "1.0.3-SNAPSHOT"
description = "TicketBird"
java.sourceCompatibility = JavaVersion.VERSION_16

jib {
    var imageVersion = version.toString()
    if (imageVersion.contains("SNAPSHOT")) imageVersion = "latest"

    to.image = "rg.nl-ams.scw.cloud/dreamexposure/ticketbird:$imageVersion"
    from.image = "adoptopenjdk/openjdk16:alpine-jre"
    container.creationTime = "USE_CURRENT_TIMESTAMP"
}

gitProperties {
    extProperty = "gitPropertiesExt"

    val versionName = if (System.getenv("BUILD_NUMBER") != null) {
        "$version.b${System.getenv("BUILD_NUMBER")}"
    } else {
        "$version.d${System.currentTimeMillis().div(1000)}" //Seconds since epoch
    }

    customProperty("ticketbird.version", versionName)
    customProperty("ticketbird.version.d4j", d4jVersion)
}

kotlin {
    sourceSets {
        all {
            kotlin.srcDir(kotlinSrcDir)
        }
    }
}

tasks {
    generateGitProperties {
        doLast {
            @Suppress("UNCHECKED_CAST")
            val gitProperties = ext[gitProperties.extProperty] as Map<String, String>
            val enumPairs = gitProperties.mapKeys { it.key.replace('.', '_').toUpperCase() }

            val enumBuilder = TypeSpec.enumBuilder("GitProperty")
                    .primaryConstructor(
                            com.squareup.kotlinpoet.FunSpec.constructorBuilder()
                                    .addParameter("value", String::class)
                                    .build()
                    )

            val enums = enumPairs.entries.fold(enumBuilder) { accumulator, (key, value) ->
                accumulator.addEnumConstant(
                        key, TypeSpec.anonymousClassBuilder()
                        .addSuperclassConstructorParameter("%S", value)
                        .build()
                )
            }

            val enumFile = FileSpec.builder("org.dreamexposure.ticketbird", "GitProperty")
                    .addType(
                            enums // https://github.com/square/kotlinpoet#enums
                                    .addProperty(
                                           PropertySpec.builder("value", String::class)
                                                    .initializer("value")
                                                    .build()
                                    )
                                    .build()
                    )
                    .build()

            enumFile.writeTo(kotlinSrcDir)
        }
    }

    withType<KotlinCompile> {
        dependsOn(generateGitProperties)

        kotlinOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict")
            jvmTarget = targetCompatibility
        }
    }

    bootJar {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }

}
