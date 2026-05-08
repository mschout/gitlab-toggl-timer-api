package io.github.mschout.gitlab.toggltimer.configuration;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.DateTimeFeature;

@Configuration
public class JacksonConfiguration {

  // serialize Instant as ISO-8601 string
  @Bean
  JsonMapperBuilderCustomizer jacksonCustomizer() {
    return builder -> builder.disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS);
  }
}
