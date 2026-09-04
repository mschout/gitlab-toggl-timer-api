import org.springframework.boot.gradle.tasks.bundling.BootBuildImage

plugins {
  alias(libs.plugins.mschout.conventions)
  alias(libs.plugins.kotlin.jpa)
  alias(libs.plugins.kotlin.spring)
  alias(libs.plugins.spring.boot)
  alias(libs.plugins.spring.dependency.management)
}

group = "io.github.mschout"

version = "0.9.0"

description = "Personal Toggl Timer Integrations for Gitlab"

kotlin { compilerOptions { extraWarnings = true } }

repositories {
  mavenCentral()
  maven { url = uri("https://repo.spring.io/snapshot") }
}

dependencies {
  implementation(libs.bundles.spring)
  implementation(libs.bundles.webjars)
  implementation(libs.caffeine)
  implementation(libs.spring.boot.flyway)
  implementation(libs.flyway.database.postgresql)
  implementation(libs.gitlab4j.api)
  implementation(libs.springdoc.openapi.starter.webmvc.ui)
  implementation(libs.jackson.module.kotlin)
  implementation(libs.kotlin.logging)
  implementation(libs.kotlin.reflect)
  implementation(libs.thymeleaf.extras.springsecurity)
  implementation(libs.samstevens.totp)
  implementation(libs.spring.boot.starter.jdbc)

  runtimeOnly(libs.postgresql)

  developmentOnly("org.springframework.boot:spring-boot-devtools")
  developmentOnly("org.springframework.boot:spring-boot-docker-compose")

  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation(libs.bundles.kotest)
  testImplementation(libs.bundles.testcontainers)
  testImplementation(libs.mockk)
  testImplementation(libs.spring.boot.starter.webmvc.test)
  testImplementation(libs.spring.security.test)
}

tasks.test { useJUnitPlatform() }

tasks.named<BootBuildImage>("bootBuildImage") {
  imageName = providers.environmentVariable("IMAGE_NAME").orElse("mschout/gitlab-toggl-timer").get()
  environment.put(
      "TRAINING_RUN_JAVA_TOOL_OPTIONS",
      "-Dspring.profiles.active=aot-cache",
  )

  val registryUsername = providers.environmentVariable("REGISTRY_USERNAME").orNull
  val registryPassword = providers.environmentVariable("REGISTRY_PASSWORD").orNull
  if (!registryUsername.isNullOrBlank() && !registryPassword.isNullOrBlank()) {
    docker {
      publishRegistry {
        username = registryUsername
        password = registryPassword
      }
    }
  }
}
