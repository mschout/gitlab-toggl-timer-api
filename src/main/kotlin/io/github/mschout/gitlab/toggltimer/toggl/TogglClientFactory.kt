/*
 * Copyright 2026 Michael Schout
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
