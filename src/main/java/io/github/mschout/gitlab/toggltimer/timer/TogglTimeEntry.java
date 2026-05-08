package io.github.mschout.gitlab.toggltimer.timer;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@RequiredArgsConstructor
public class TogglTimeEntry {

  private final Long workspaceId;

  private final Long projectId;

  private final Instant start;

  private String description;

  private Long duration = -1L;

  private String createdWith = "Gitlab Toggl Timer";
}
