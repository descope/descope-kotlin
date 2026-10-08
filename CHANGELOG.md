# Changelog

## [0.22.0](https://github.com/descope/descope-kotlin/compare/0.21.0...0.22.0) (2026-10-08)


### ⚠ BREAKING CHANGES

* fold session lifecycle into the session manager ([#370](https://github.com/descope/descope-kotlin/issues/370))

### Features

* **enchantedlink:** support enchanted link over SMS ([#360](https://github.com/descope/descope-kotlin/issues/360)) ([1cc8222](https://github.com/descope/descope-kotlin/commit/1cc82229f434c14b9ac90c1556550c70f94778de))


### Bug Fixes

* **deps:** update credentials to v1.6.0 ([#317](https://github.com/descope/descope-kotlin/issues/317)) ([f7dd657](https://github.com/descope/descope-kotlin/commit/f7dd6571b85b7d2c269054b5678f0afacfa4e35c))
* **deps:** update dependency androidx.browser:browser to v1.10.0 ([#313](https://github.com/descope/descope-kotlin/issues/313)) ([8e93f85](https://github.com/descope/descope-kotlin/commit/8e93f85f33b725a31a2a9dd85dc0a9ac50ae1b1e))
* **deps:** update dependency com.google.android.libraries.identity.googleid:googleid to v1.2.1 ([#375](https://github.com/descope/descope-kotlin/issues/375)) ([c44f0c2](https://github.com/descope/descope-kotlin/commit/c44f0c2edf49d438ed8345c9724049b627c237d6))
* **deps:** update kotlinx-coroutines monorepo to v1.11.0 ([#321](https://github.com/descope/descope-kotlin/issues/321)) ([307c329](https://github.com/descope/descope-kotlin/commit/307c3295d58d2ee0931a2ad0a0fbb382305db2b1))
* isolate session listener failures and add a network timeout ([#373](https://github.com/descope/descope-kotlin/issues/373)) ([3dac14e](https://github.com/descope/descope-kotlin/commit/3dac14ee4cbcc103abff558f69eb14d761fd20a0))
* session lifecycle leak and duplicate concurrent refreshes ([#366](https://github.com/descope/descope-kotlin/issues/366)) ([bcff2a7](https://github.com/descope/descope-kotlin/commit/bcff2a7a332727ed35d82f5e36a9ce8d2e61456c))
* stop periodic session refresh while app is in background ([#374](https://github.com/descope/descope-kotlin/issues/374)) ([371d9b4](https://github.com/descope/descope-kotlin/commit/371d9b4edc10ebcf7270081ff74fd5a168df7a9b))


### Code Refactoring

* fold session lifecycle into the session manager ([#370](https://github.com/descope/descope-kotlin/issues/370)) ([2583a2a](https://github.com/descope/descope-kotlin/commit/2583a2ab933d3b0ee5acc1cd632ede3d4a8af9b3))

## [0.21.0](https://github.com/descope/descope-kotlin/compare/0.20.0...0.21.0) (2026-09-02)


### Features

* **flows:** opt-in sendSessionToken to expose session JWT claims to flows ([#356](https://github.com/descope/descope-kotlin/issues/356)) RELEASE ([dfcf9ed](https://github.com/descope/descope-kotlin/commit/dfcf9ed4052b39d358afb0686cb879798233cef4))

## [0.20.0](https://github.com/descope/descope-kotlin/compare/0.19.2...0.20.0) (2026-08-12)


### Features

* expose flowOutput on AuthenticationResponse ([#338](https://github.com/descope/descope-kotlin/issues/338)) ([0742859](https://github.com/descope/descope-kotlin/commit/07428594f8e0ccbf270ff018f0a27a09f172e25d))
* Log and return CF-Ray response header in API failures ([#322](https://github.com/descope/descope-kotlin/issues/322)) ([996acf0](https://github.com/descope/descope-kotlin/commit/996acf06f095e9f5512730c7758e40fc5bb106a9))

## [0.19.2](https://github.com/descope/descope-kotlin/compare/0.19.1...0.19.2) (2026-08-04)


### Bug Fixes

* fall back to generic browser check when url resolution fails ([#344](https://github.com/descope/descope-kotlin/issues/344)) ([976588f](https://github.com/descope/descope-kotlin/commit/976588fc17b8f70c6f3ecb798bc0b08caa21339b))

## 0.19.1

### Features

- Add support for push authentication (#308)
- Expose `externalToken` on `AuthenticationResponse` (#336)
- Flow: native cancellation signals and typed bridge errors (#329)

---

Entries below `0.19.1` were tracked manually. From the next release onward this
file is generated automatically by [release-please](https://github.com/googleapis/release-please)
from Conventional Commit messages.
