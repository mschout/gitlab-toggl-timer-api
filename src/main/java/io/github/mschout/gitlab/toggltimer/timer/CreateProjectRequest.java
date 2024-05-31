package io.github.mschout.gitlab.toggltimer.timer;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateProjectRequest {

	@NotNull
	private String issueUrl;

	@NotNull
	private Long workspaceId;

	@NotNull
	private Long clientId;

	GitLabIssue getIssue() {
		return GitLabIssue.fromUrl(issueUrl);
	}

}
