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
package io.github.mschout.gitlab.toggltimer.user

import io.github.mschout.gitlab.toggltimer.gitlab.GitLabApiFactory
import io.github.mschout.gitlab.toggltimer.toggl.TogglClient
import io.github.mschout.gitlab.toggltimer.toggl.TogglClientFactory
import io.github.mschout.gitlab.toggltimer.toggl.TogglWorkspace
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.gitlab4j.api.GitLabApi
import org.gitlab4j.api.GitLabApiException
import org.gitlab4j.api.UserApi
import org.gitlab4j.api.models.User
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClientException

class TokenValidationControllerTest {

  private lateinit var gitLabApiFactory: GitLabApiFactory
  private lateinit var togglClientFactory: TogglClientFactory
  private lateinit var gitLabApi: GitLabApi
  private lateinit var userApi: UserApi
  private lateinit var togglClient: TogglClient
  private lateinit var controller: TokenValidationController

  @BeforeEach
  fun setUp() {
    gitLabApiFactory = mockk()
    togglClientFactory = mockk()
    gitLabApi = mockk()
    userApi = mockk()
    togglClient = mockk()
    every { gitLabApi.userApi } returns userApi
    controller = TokenValidationController(gitLabApiFactory, togglClientFactory)
  }

  @Test
  fun `validateGitlab returns valid true when current user lookup succeeds`() {
    every { gitLabApiFactory.forToken("good-token") } returns gitLabApi
    every { userApi.currentUser } returns User().apply { id = 1L }

    controller.validateGitlab(ValidateGitlabRequest("good-token")).valid shouldBe true
  }

  @Test
  fun `validateGitlab returns valid false when GitLab API throws`() {
    every { gitLabApiFactory.forToken("bad-token") } returns gitLabApi
    every { userApi.currentUser } throws GitLabApiException("401 Unauthorized")

    controller.validateGitlab(ValidateGitlabRequest("bad-token")).valid shouldBe false
  }

  @Test
  fun `validateGitlab returns valid false when current user is null`() {
    every { gitLabApiFactory.forToken("weird-token") } returns gitLabApi
    every { userApi.currentUser } returns null

    controller.validateGitlab(ValidateGitlabRequest("weird-token")).valid shouldBe false
  }

  @Test
  fun `validateToggl returns valid true when workspaces fetch succeeds`() {
    every { togglClientFactory.forApiKey("good-key") } returns togglClient
    every { togglClient.getWorkspaces() } returns listOf(TogglWorkspace(id = 1L, name = "Acme"))

    controller.validateToggl(ValidateTogglRequest("good-key")).valid shouldBe true
  }

  @Test
  fun `validateToggl returns valid true even when no workspaces are returned`() {
    every { togglClientFactory.forApiKey("empty-key") } returns togglClient
    every { togglClient.getWorkspaces() } returns emptyList()

    controller.validateToggl(ValidateTogglRequest("empty-key")).valid shouldBe true
  }

  @Test
  fun `validateToggl returns valid false when client throws`() {
    every { togglClientFactory.forApiKey("bad-key") } returns togglClient
    every { togglClient.getWorkspaces() } throws RestClientException("403 Forbidden")

    controller.validateToggl(ValidateTogglRequest("bad-key")).valid shouldBe false
  }
}
