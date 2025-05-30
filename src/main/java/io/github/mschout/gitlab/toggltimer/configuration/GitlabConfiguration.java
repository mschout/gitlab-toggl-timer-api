package io.github.mschout.gitlab.toggltimer.configuration;

import static java.util.Objects.requireNonNull;

import org.gitlab4j.api.GitLabApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GitlabConfiguration {

  @Value("${gitlab.accessToken}")
  private String accessToken;

  @Value("${gitlab.url:https://gitlab.com}")
  private String gitlabUrl;

  @Bean
  public GitLabApi gitlabApi() {
    requireNonNull(accessToken, "accessToken is required");
    return new GitLabApi(gitlabUrl, accessToken);
  }
}
