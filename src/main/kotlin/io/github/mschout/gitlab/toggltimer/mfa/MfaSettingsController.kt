package io.github.mschout.gitlab.toggltimer.mfa

import io.github.mschout.gitlab.toggltimer.user.CurrentUserCredentialsService
import jakarta.servlet.http.HttpSession
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes

private const val PENDING_TOTP_KEY = "mfa.pendingTotpId"
private const val PENDING_TOTP_URI_KEY = "mfa.pendingTotpUri"
private const val PENDING_RECOVERY_KEY = "mfa.pendingRecoveryCodes"

@Controller
@RequestMapping("/settings/mfa")
class MfaSettingsController(
    private val credentialsService: CurrentUserCredentialsService,
    private val mfaService: MfaService,
    private val totpService: TotpService,
) {

  @GetMapping
  fun show(model: Model, session: HttpSession): String {
    val user = credentialsService.currentUser()
    model.addAttribute("totps", mfaService.confirmedTotps(user))
    model.addAttribute("passkeys", mfaService.passkeysFor(user))
    model.addAttribute("recoveryCodeCount", mfaService.unusedRecoveryCodeCount(user))
    val pendingId = session.getAttribute(PENDING_TOTP_KEY) as? Long
    val pendingUri = session.getAttribute(PENDING_TOTP_URI_KEY) as? String
    if (pendingId != null && pendingUri != null) {
      model.addAttribute("pendingTotpId", pendingId)
      model.addAttribute("pendingTotpQr", pendingUri)
    }
    val pendingRecoveryCodes = session.getAttribute(PENDING_RECOVERY_KEY) as? List<*>
    if (pendingRecoveryCodes != null) {
      model.addAttribute("freshRecoveryCodes", pendingRecoveryCodes)
      session.removeAttribute(PENDING_RECOVERY_KEY)
    }
    return "settings/mfa"
  }

  @PostMapping("/totp/start")
  fun startTotp(
      @RequestParam("label", required = false) labelInput: String?,
      session: HttpSession,
  ): String {
    val user = credentialsService.currentUser()
    val label = labelInput?.trim().orEmpty().ifBlank { "Authenticator" }
    val pending = mfaService.beginTotpEnrollment(user, label)
    val qrUri = totpService.qrDataUri(accountLabel = user.email, secret = pending.secret)
    session.setAttribute(PENDING_TOTP_KEY, pending.id)
    session.setAttribute(PENDING_TOTP_URI_KEY, qrUri)
    return "redirect:/settings/mfa"
  }

  @PostMapping("/totp/confirm")
  fun confirmTotp(
      @RequestParam("code") code: String,
      session: HttpSession,
      redirectAttrs: RedirectAttributes,
  ): String {
    val user = credentialsService.currentUser()
    val pendingId =
        session.getAttribute(PENDING_TOTP_KEY) as? Long
            ?: run {
              redirectAttrs.addFlashAttribute("error", "No TOTP enrollment in progress.")
              return "redirect:/settings/mfa"
            }
    val ok = mfaService.confirmTotp(user, pendingId, code)
    if (!ok) {
      redirectAttrs.addFlashAttribute("error", "Invalid code — please try again.")
      return "redirect:/settings/mfa"
    }
    session.removeAttribute(PENDING_TOTP_KEY)
    session.removeAttribute(PENDING_TOTP_URI_KEY)
    redirectAttrs.addFlashAttribute("success", "Authenticator added.")
    if (mfaService.unusedRecoveryCodeCount(user) == 0L) {
      val codes = mfaService.regenerateRecoveryCodes(user)
      session.setAttribute(PENDING_RECOVERY_KEY, codes)
      redirectAttrs.addFlashAttribute(
          "info",
          "Save these recovery codes — they will not be shown again.",
      )
    }
    return "redirect:/settings/mfa"
  }

  @PostMapping("/totp/cancel")
  fun cancelTotp(session: HttpSession): String {
    val user = credentialsService.currentUser()
    val pendingId = session.getAttribute(PENDING_TOTP_KEY) as? Long
    if (pendingId != null) mfaService.cancelPendingTotp(user, pendingId)
    session.removeAttribute(PENDING_TOTP_KEY)
    session.removeAttribute(PENDING_TOTP_URI_KEY)
    return "redirect:/settings/mfa"
  }

  @PostMapping("/totp/{id}/delete")
  fun deleteTotp(@PathVariable("id") id: Long, redirectAttrs: RedirectAttributes): String {
    val user = credentialsService.currentUser()
    if (mfaService.removeTotp(user, id)) {
      redirectAttrs.addFlashAttribute("success", "Authenticator removed.")
    }
    return "redirect:/settings/mfa"
  }

  @PostMapping("/passkey/{credentialId}/delete")
  fun deletePasskey(
      @PathVariable("credentialId") credentialId: String,
      redirectAttrs: RedirectAttributes,
  ): String {
    val user = credentialsService.currentUser()
    if (mfaService.removePasskey(user, credentialId)) {
      redirectAttrs.addFlashAttribute("success", "Passkey removed.")
    }
    return "redirect:/settings/mfa"
  }

  @PostMapping("/recovery-codes")
  fun regenerateRecoveryCodes(session: HttpSession, redirectAttrs: RedirectAttributes): String {
    val user = credentialsService.currentUser()
    if (!mfaService.hasAnyMfa(user)) {
      redirectAttrs.addFlashAttribute(
          "error",
          "Set up at least one MFA method before generating recovery codes.",
      )
      return "redirect:/settings/mfa"
    }
    val codes = mfaService.regenerateRecoveryCodes(user)
    session.setAttribute(PENDING_RECOVERY_KEY, codes)
    redirectAttrs.addFlashAttribute("success", "Recovery codes regenerated.")
    return "redirect:/settings/mfa"
  }
}
