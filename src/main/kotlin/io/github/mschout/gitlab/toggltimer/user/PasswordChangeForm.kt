package io.github.mschout.gitlab.toggltimer.user

data class PasswordChangeForm(
    val currentPassword: String? = null,
    val newPassword: String = "",
    val confirmPassword: String = "",
)
