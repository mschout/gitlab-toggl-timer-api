package io.github.mschout.gitlab.toggltimer.timer

import io.github.mschout.gitlab.toggltimer.toggl.CreateProjectRequest as CreateTogglProjectRequest
import io.github.mschout.gitlab.toggltimer.toggl.TogglClient
import io.github.mschout.gitlab.toggltimer.toggl.TogglProject
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class TogglService(private val togglClient: TogglClient) {

  fun findOrCreateProject(
      workspaceId: Long,
      clientId: Long,
      issueNumber: Long,
      issueTitle: String,
  ): TogglProject {
    val projects = togglClient.getProjects(workspaceId, name = issueNumber.toString())

    logger.info { "Found projects: $projects" }

    return projects
        .firstOrNull { it.name?.startsWith("$issueNumber -") == true }
        ?.also { logger.info { "Found project: $it" } }
        ?: run {
          logger.info { "Project not found in Toggl, creating project" }

          val createProjectRequest =
              CreateTogglProjectRequest(
                  name = "$issueNumber - $issueTitle",
                  clientId = clientId.toString(),
              )

          togglClient.createProject(workspaceId, createProjectRequest)
        }
  }
}
