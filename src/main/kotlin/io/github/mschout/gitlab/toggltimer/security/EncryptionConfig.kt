package io.github.mschout.gitlab.toggltimer.security

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.encrypt.Encryptors
import org.springframework.security.crypto.encrypt.TextEncryptor

@Configuration
@EnableConfigurationProperties(EncryptionProperties::class)
class EncryptionConfig {
  @Bean
  @Suppress("DEPRECATION")
  fun textEncryptor(props: EncryptionProperties): TextEncryptor =
      Encryptors.delux(props.password, props.salt)
}

@ConfigurationProperties(prefix = "app.encryption")
data class EncryptionProperties(val password: String, val salt: String)
