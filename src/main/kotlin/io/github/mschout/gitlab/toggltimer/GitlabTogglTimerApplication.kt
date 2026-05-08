package io.github.mschout.gitlab.toggltimer

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.restclient.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.web.client.RestTemplate

@SpringBootApplication
class GitlabTogglTimerApplication {
  @Bean fun restTemplate(builder: RestTemplateBuilder): RestTemplate = builder.build()
}

fun main(args: Array<String>) {
  SpringApplication.run(GitlabTogglTimerApplication::class.java, *args)
}
