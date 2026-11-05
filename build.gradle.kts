import com.github.gradle.node.npm.task.NpmTask
import org.springframework.boot.gradle.tasks.bundling.BootBuildImage

plugins {
  alias(libs.plugins.node)
  alias(libs.plugins.mschout.conventions)
  alias(libs.plugins.kotlin.jpa)
  alias(libs.plugins.kotlin.spring)
  alias(libs.plugins.spring.boot)
  alias(libs.plugins.spring.dependency.management)
}

group = "io.github.mschout"

val gitVersion = extra["gitVersion"] as groovy.lang.Closure<*>

version = gitVersion.call().toString()

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
  implementation(libs.ph.totp)
  implementation(libs.ph.totp.qrcode)
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

node {
  version = libs.versions.node.get()
  download = true
  npmInstallCommand = "ci"
}

val formatTypeScript =
    tasks.register<NpmTask>("formatTypeScript") {
      group = "formatting"
      description = "Format TypeScript sources with oxfmt."
      dependsOn(tasks.npmInstall)
      args = listOf("run", "fmt")
    }

val checkTypeScriptFormatting =
    tasks.register<NpmTask>("checkTypeScriptFormatting") {
      group = "verification"
      description = "Check TypeScript formatting with oxfmt without changing files."
      dependsOn(tasks.npmInstall)
      mustRunAfter(formatTypeScript)
      args = listOf("run", "fmt:check")
    }

tasks.named("spotlessApply") { dependsOn(formatTypeScript) }

tasks.named("spotlessCheck") { dependsOn(checkTypeScriptFormatting) }

val typeScriptResources = layout.buildDirectory.dir("generated-resources/typescript")
val compileTypeScript =
    tasks.register<NpmTask>("compileTypeScript") {
      group = "build"
      description = "Type-check and compile browser scripts (supports --continuous)."
      dependsOn(tasks.npmInstall)
      mustRunAfter(formatTypeScript)
      args = listOf("run", "build")
      inputs
          .files(fileTree("src/main/typescript") { include("**/*.ts") })
          .withPathSensitivity(PathSensitivity.RELATIVE)
      inputs
          .files("tsconfig.json", "package.json", "package-lock.json")
          .withPathSensitivity(PathSensitivity.RELATIVE)
      inputs.property("nodeVersion", libs.versions.node)
      outputs.dir(typeScriptResources)
      // tsc does not remove output for deleted or renamed sources. This task owns the directory.
      val outputDirectory = typeScriptResources
      doFirst { outputDirectory.get().asFile.deleteRecursively() }
    }

sourceSets.main { output.dir(mapOf("builtBy" to compileTypeScript), typeScriptResources) }

tasks.check { dependsOn(compileTypeScript) }

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
