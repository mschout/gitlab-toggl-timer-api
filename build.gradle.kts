import org.springframework.boot.gradle.tasks.bundling.BootBuildImage

plugins {
  java
  id("mschout.all-conventions")
  alias(libs.plugins.spring.boot)
  alias(libs.plugins.spring.dependency.management)
  alias(libs.plugins.freefair.aspectj)
  alias(libs.plugins.spotless)
}

group = "io.github.mschout"

version = "0.9.0"

description = "Personal Toggl Timer Integrations for Gitlab"

java { toolchain { languageVersion = JavaLanguageVersion.of(24) } }

configurations { compileOnly { extendsFrom(configurations.annotationProcessor.get()) } }

repositories {
  mavenCentral()
  maven { url = uri("https://repo.spring.io/snapshot") }
}

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
  implementation("org.springframework.boot:spring-boot-starter-validation")
  implementation("org.springframework.boot:spring-boot-starter-cache")
  implementation("org.springframework.boot:spring-boot-starter-aop")
  implementation("com.github.ben-manes.caffeine:caffeine")
  implementation("org.springframework:spring-tx")
  implementation(libs.gitlab4j.api)
  implementation(libs.springdoc.openapi.starter.webmvc.ui)
  implementation(libs.jackson.datatype.jdk8)
  implementation(libs.thymeleaf.layout.dialect)
  implementation(libs.aspectjrt)

  aspect("org.springframework:spring-aspects")

  compileOnly("org.projectlombok:lombok")
  annotationProcessor("org.projectlombok:lombok")

  developmentOnly("org.springframework.boot:spring-boot-devtools")

  testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.test { useJUnitPlatform() }

tasks.named<BootBuildImage>("bootBuildImage") { imageName = "mschout/gitlab-toggl-timer" }

spotless {
  java {
    googleJavaFormat().formatJavadoc(true)
    importOrder()
    removeUnusedImports()
    formatAnnotations()
  }
  format("thymeleaf") {
    target("src/main/resources/templates/**/*.html")
    prettier(mapOf("prettier" to libs.versions.prettier.get()))
        .config(
            mapOf(
                "parser" to "html",
                "printWidth" to 120,
                "tabWidth" to 2,
                "useTabs" to false,
                "singleQuote" to true,
            ),
        )
  }
}
