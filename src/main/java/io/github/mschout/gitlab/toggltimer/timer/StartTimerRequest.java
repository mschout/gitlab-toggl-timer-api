package io.github.mschout.gitlab.toggltimer.timer;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Data;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Data
public class StartTimerRequest {

  @NotNull private String issueUrl;

  @NotNull private Long workspaceId;

  @NotNull private Long clientId;

  @Nullable private Instant start;

  @Nullable private String description;

  GitLabIssue getIssue() {
    return GitLabIssue.fromUrl(issueUrl);
  }
}
