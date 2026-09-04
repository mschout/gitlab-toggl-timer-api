# TOTP library replacement

Research date: 2026-09-04

## Recommendation

Replace `dev.samstevens.totp:totp:1.7.1` with:

```kotlin
implementation("com.helger:ph-totp:2.0.0")
implementation("com.helger:ph-totp-qrcode:2.0.0")
```

`ph-totp` is the best fit for this application. It is a maintained Java 17+ fork and refactor of the exact library and version currently in use. It retains Base32 secret generation, TOTP generation and verification, `otpauth://` URI construction, and optional ZXing PNG/data-URI generation. Version 2.0.0 was released on 2026-05-14 and updates Commons Codec to 1.22.0, optional Commons Net to 3.13.0, and ZXing to 3.5.4. Its verifier also documents constant-time comparison and evaluation of the full discrepancy window. See the [official project README](https://github.com/phax/ph-totp#readme).

The migration is mostly package and type renaming: `dev.samstevens.totp` becomes `com.helger.totp`, interfaces gain an `I` prefix, `HashingAlgorithm` becomes `EHashingAlgorithm`, and `ZxingPngQrGenerator` becomes `ZxingPngQrCodeImageGenerator`. QR support is now a separate module. The fork's [migration notes](https://github.com/phax/ph-totp#news-and-noteworthy) list the complete API changes.

This app should preserve SHA-1, six digits, a 30-second period, and the current `±1` window. Those are the values in [`TotpService`](../../src/main/kotlin/io/github/mschout/gitlab/toggltimer/mfa/TotpService.kt), and they are part of each user's authenticator enrollment. Because both libraries use the same RFC 6238 algorithm and Base32 secret representation, existing encrypted secrets and authenticator registrations should remain usable; changing those parameters would require re-enrollment. RFC 6238 identifies HMAC-SHA-1 as the default interoperability profile ([RFC 6238, section 1.2](https://www.rfc-editor.org/rfc/rfc6238#section-1.2)).

The main caveat is maturity: `ph-totp` 2.0.0 is the fork's first release and has little visible adoption. It is nevertheless the narrowest migration with the smallest amount of new authentication and QR glue code for this service.

## What the reported vulnerability means here

The upstream artifact has not had a release or commit since 2020; its [commit history](https://github.com/samdjstevens/java-totp/commits/master/) ends with the 1.7.1 release on 2020-11-05. Its POM pins Commons Codec 1.13, optional Commons Net 3.6, and ZXing 3.4.0 ([upstream POM](https://github.com/samdjstevens/java-totp/blob/master/totp/pom.xml)). This stale dependency graph, rather than the TOTP algorithm itself, is the practical reason to replace it.

Two dependency findings are easily conflated:

- CVE-2021-37533 affects Commons Net's FTP client before 3.9.0: an FTP client could trust the host returned in a PASV response and connect elsewhere. The authoritative description is in the [Apache advisory](https://lists.apache.org/thread/o6yn9r9x6s94v97264hmgol1sf48mvx7). Commons Net 3.6 is optional in `java-totp`, and `./gradlew dependencyInsight --dependency commons-net --configuration runtimeClasspath` finds no Commons Net in this application's runtime graph. The application also uses `SystemTimeProvider`, not `NtpTimeProvider`. Therefore this CVE is not a runtime exposure in the resolved application as currently configured.
- The actual runtime graph does include `com.beust:jcommander:1.72`, through `java-totp` -> `com.google.zxing:javase:3.4.0`. The upstream project has an open [dependency vulnerability report](https://github.com/samdjstevens/java-totp/issues/66) about that version. This is a stale build/dependency-metadata finding, not evidence of a flaw in code generation or verification. ZXing 3.5.4 instead declares `org.jcommander:jcommander:1.85` in its [official release POM](https://github.com/zxing/zxing/blob/zxing-3.5.4/javase/pom.xml).

The security report should therefore be checked for its advisory ID and vulnerable path. If it only names CVE-2021-37533, the correct short-term response is to document/suppress the non-runtime optional dependency finding rather than claim exploitable FTP behavior. If it names JCommander or merely rejects the stale parent artifact, use one of the remediation paths below.

## Options

| Option | Fit for this service | Tradeoffs |
|---|---|---|
| `com.helger:ph-totp:2.0.0` + `ph-totp-qrcode:2.0.0` | Best overall; direct replacement for secret generation, verification, URI, PNG, and data URI | Small API rename migration; new fork with only one release so far; Java 17+ is fully compatible with JVM 25 |
| `dev.turingcomplete:kotlin-onetimepassword:3.0.0` | Good Kotlin-native core and maintained 3.0.0 release; supports TOTP/HOTP, random secrets, and key URIs | No QR image renderer; the app must own ZXing integration and implement/test the `±1` verification window around its timestamp-oriented API |
| Keep `java-totp` and override transitives | Smallest immediate diff | Leaves an unmaintained top-level library and may not satisfy scanners that flag the parent artifact; use only as a stopgap |

The Kotlin alternative's official documentation lists its current coordinate, algorithms, random-secret generator, and key-URI builder ([project README](https://github.com/marcelkliemannel/kotlin-onetimepassword#readme)). It is attractive if the goal is to make QR rendering an explicit application boundary, but that is more migration work than this vulnerability requires.

## Narrow stopgap

If a full replacement cannot be made immediately, constrain/override both ZXing artifacts from 3.4.0 to 3.5.4, then confirm that `com.beust:jcommander:1.72` is absent from `runtimeClasspath`. This keeps the current API while moving ZXing to its modern JCommander coordinate. If the scanner specifically identifies a different transitive, constrain that exact module to the vendor-fixed version instead. Do not add Commons Net merely to upgrade it: it is currently absent and is unnecessary with `SystemTimeProvider`.

This stopgap should be time-bounded because the top-level library remains unmaintained.

## Migration verification

Before merging a replacement:

1. Run RFC 6238 test vectors and tests covering the current bucket, `±1` accepted buckets, and `±2` rejected buckets.
2. Generate codes from representative existing encrypted Base32 secrets with both libraries at fixed timestamps and assert equality.
3. Assert the generated `otpauth://` URI retains issuer, label escaping, SHA-1, six digits, and the 30-second period; scan one generated QR code with a real authenticator.
4. Run Gradle dependency insight for `jcommander`, `commons-net`, `zxing-core`, and `zxing-javase` and archive the resolved result with the security finding.
5. Keep the service's existing six-digit input validation and do not re-enroll users unless a compatibility test disproves the expected equivalence.
