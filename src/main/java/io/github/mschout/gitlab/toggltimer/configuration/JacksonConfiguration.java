package io.github.mschout.gitlab.toggltimer.configuration;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfiguration {

	@Bean
	Module jacksonJdk8Module() {
		return new Jdk8Module();
	}

	// serialize Instant as ISO-8601 string
	@Bean
	Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
		return builder -> builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
	}

}
