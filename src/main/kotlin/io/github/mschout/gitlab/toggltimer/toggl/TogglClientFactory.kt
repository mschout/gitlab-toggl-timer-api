package io.github.mschout.gitlab.toggltimer.toggl

import org.springframework.core.convert.support.DefaultConversionService
import org.springframework.http.HttpHeaders
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory
import org.springframework.web.service.invoker.createClient
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.json.JsonMapper

@Component
class TogglClientFactory(
    private val props: TogglClientProperties,
    private val restClientBuilder: RestClient.Builder,
) {
  private val objectMapper =
      JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build()

  fun forApiKey(apiKey: String): TogglClient {
    val restClient =
        restClientBuilder
            .baseUrl(props.baseUrl)
            .defaultHeaders { headers ->
              headers.set(HttpHeaders.ACCEPT, "application/json")
              headers.setBasicAuth(apiKey, "api_token")
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
