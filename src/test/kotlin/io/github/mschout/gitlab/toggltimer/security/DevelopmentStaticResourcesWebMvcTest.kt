/*
 * Copyright 2026 Michael Schout
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.mschout.gitlab.toggltimer.security

import io.github.mschout.gitlab.toggltimer.home.HomeController
import io.github.mschout.gitlab.toggltimer.user.UserProfileService
import io.kotest.matchers.shouldNotBe
import java.nio.file.Files
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.servlet.resource.ResourceUrlProvider

@WebMvcTest(
    controllers = [HomeController::class],
    // Test application.yaml replaces the production file; retain its content-version strategy.
    properties =
        [
            "spring.web.resources.chain.strategy.content.enabled=true",
            "spring.web.resources.chain.strategy.content.paths=/css/**,/js/**",
        ],
)
@ActiveProfiles("dev")
@Import(
    SecurityConfig::class,
    AuthConfiguration::class,
    SecurityConfigWebMvcTest.MockBeans::class,
    UserProfileService::class,
)
class DevelopmentStaticResourcesWebMvcTest(
    @Autowired val mvc: MockMvc,
    @Autowired val resourceUrlProvider: ResourceUrlProvider,
) {
  companion object {
    private val resourceDirectory = Files.createTempDirectory("typescript-dev-resources")
    private val script =
        Files.createDirectories(resourceDirectory.resolve("js")).resolve("dev-cache-probe.js")

    @JvmStatic
    @DynamicPropertySource
    fun resourceLocations(registry: DynamicPropertyRegistry) {
      registry.add("spring.web.resources.static-locations") {
        "${resourceDirectory.toUri()},classpath:/static/"
      }
    }

    @JvmStatic
    @AfterAll
    fun removeResources() {
      Files.deleteIfExists(script)
      Files.deleteIfExists(script.parent)
      Files.deleteIfExists(resourceDirectory)
    }
  }

  @Test
  fun `dev profile recomputes script hashes after compilation and disables browser caching`() {
    Files.writeString(script, "window.devCacheProbe = 1;")
    val originalUrl = requireNotNull(resourceUrlProvider.getForLookupPath("/js/dev-cache-probe.js"))
    mvc.perform(get(originalUrl))
        .andExpect(status().isOk)
        .andExpect(header().string("Cache-Control", containsString("no-store")))
        .andExpect(content().string("window.devCacheProbe = 1;"))

    Files.writeString(script, "window.devCacheProbe = 2;")
    val updatedUrl = requireNotNull(resourceUrlProvider.getForLookupPath("/js/dev-cache-probe.js"))
    updatedUrl shouldNotBe originalUrl
    mvc.perform(get(updatedUrl))
        .andExpect(status().isOk)
        .andExpect(header().string("Cache-Control", containsString("no-store")))
        .andExpect(content().string("window.devCacheProbe = 2;"))
  }
}
