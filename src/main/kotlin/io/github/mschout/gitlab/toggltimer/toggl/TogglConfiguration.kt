package io.github.mschout.gitlab.toggltimer.toggl

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.support.DefaultConversionService
import org.springframework.http.HttpHeaders
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.web.client.RestClient
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory
import org.springframework.web.service.invoker.createClient
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.json.JsonMapper

@Configuration
@EnableConfigurationProperties(TogglClientProperties::class)
class TogglClientConfiguration {
  @Bean
  fun togglClient(
      clientProperties: TogglClientProperties,
      restClientBuilder: RestClient.Builder,
  ): TogglClient {
    val objectMapper =
        JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build()

    val restClient =
        restClientBuilder
            .baseUrl(clientProperties.baseUrl)
            .defaultHeaders { headers ->
              headers.set(HttpHeaders.ACCEPT, "application/json")
              headers.setBasicAuth(clientProperties.apiKey, "api_token")
            }
            .configureMessageConverters { converters ->
              converters.withJsonConverter(JacksonJsonHttpMessageConverter(objectMapper))
            }
            .build()

    val adapter = RestClientAdapter.create(restClient)
    val factory =
        HttpServiceProxyFactory.builderFor(adapter)
            .conversionService(DefaultConversionService())
            .build()

    return factory.createClient<TogglClient>()
  }
}

@ConfigurationProperties(prefix = "toggl")
data class TogglClientProperties(
    val apiKey: String,
    @DefaultValue("https://api.track.toggl.com/api/v9") val baseUrl: String,
)
