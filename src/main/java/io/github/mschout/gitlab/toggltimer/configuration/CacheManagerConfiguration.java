package io.github.mschout.gitlab.toggltimer.configuration;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.val;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.AdviceMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching(mode = AdviceMode.ASPECTJ)
public class CacheManagerConfiguration {

	public static final String GITLAB_PROJECT_CACHE = "gitlabProjectCache";

	public static final String GITLAB_ISSUE_CACHE = "gitlabIssueCache";

	@Bean
	CacheManager cacheManager() {
		val cacheManager = new SimpleCacheManager();

		cacheManager.setCaches(List.of(
				new CaffeineCache(GITLAB_PROJECT_CACHE,
						Caffeine.newBuilder().maximumSize(100).expireAfterWrite(15, TimeUnit.MINUTES).build()),
				new CaffeineCache(GITLAB_ISSUE_CACHE,
						Caffeine.newBuilder().maximumSize(100).expireAfterWrite(15, TimeUnit.MINUTES).build())));

		return cacheManager;
	}

}
