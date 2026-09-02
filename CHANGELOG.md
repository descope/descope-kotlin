# Changelog

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
