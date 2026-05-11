package io.github.mschout.gitlab.toggltimer.security

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.springframework.security.crypto.encrypt.TextEncryptor
import org.springframework.stereotype.Component

@Component
@Converter(autoApply = false)
class EncryptedStringConverter(private val encryptor: TextEncryptor) :
    AttributeConverter<String?, String?> {

  override fun convertToDatabaseColumn(attribute: String?): String? =
      attribute?.takeIf { it.isNotEmpty() }?.let(encryptor::encrypt)

  override fun convertToEntityAttribute(dbData: String?): String? =
      dbData?.takeIf { it.isNotEmpty() }?.let(encryptor::decrypt)
}
