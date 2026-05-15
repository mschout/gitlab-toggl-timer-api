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
