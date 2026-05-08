package io.github.mschout.gitlab.toggltimer.configuration

import com.github.benmanes.caffeine.cache.Caffeine
import java.util.concurrent.TimeUnit
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCache
import org.springframework.cache.support.SimpleCacheManager
import org.springframework.context.annotation.AdviceMode
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableCaching(mode = AdviceMode.ASPECTJ)
class CacheManagerConfiguration {
  @Bean
  fun cacheManager(): CacheManager =
      SimpleCacheManager().apply {
        setCaches(
            listOf(
                CaffeineCache(
                    GITLAB_PROJECT_CACHE,
                    Caffeine.newBuilder()
                        .maximumSize(100)
                        .expireAfterWrite(15, TimeUnit.MINUTES)
                        .build(),
                ),
                CaffeineCache(
                    GITLAB_ISSUE_CACHE,
                    Caffeine.newBuilder()
                        .maximumSize(100)
                        .expireAfterWrite(15, TimeUnit.MINUTES)
                        .build(),
                ),
            )
        )
      }

  companion object {
    const val GITLAB_PROJECT_CACHE = "gitlabProjectCache"
    const val GITLAB_ISSUE_CACHE = "gitlabIssueCache"
  }
}
