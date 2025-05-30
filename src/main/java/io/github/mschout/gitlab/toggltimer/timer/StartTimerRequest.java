package io.github.mschout.gitlab.toggltimer.timer;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Data;
import org.springframework.lang.Nullable;

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
