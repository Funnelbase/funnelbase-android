# Releasing

Both artifacts — `com.funnel-base:funnelbase-android` and `com.funnel-base:funnelbase-android-fcm` —
are released together, under one version, to Maven Central through the
[Central Portal](https://central.sonatype.com). **A version published there can never be changed or
deleted**, so the last step is a person's, not the workflow's.

## Once, before the first release

1. **A Central Portal account** at <https://central.sonatype.com>, and the namespace
   `com.funnel-base` verified on it: the portal gives a code to publish as a DNS TXT record on
   `funnel-base.com`.
2. **A user token** from the portal (Account → Generate User Token): a username and a password.
3. **A GPG key** to sign with, its public half on a keyserver Central reads:

   ```bash
   gpg --quick-gen-key "Funnelbase <dev@funnel-base.com>" rsa4096 sign 0
   gpg --keyserver keyserver.ubuntu.com --send-keys <KEY ID>
   gpg --armor --export-secret-keys <KEY ID>      # the value of SIGNING_IN_MEMORY_KEY
   ```

4. **Four repository secrets** (Settings → Secrets and variables → Actions):

   | Secret | Value |
   |---|---|
   | `MAVEN_CENTRAL_USERNAME` | the user token's username |
   | `MAVEN_CENTRAL_PASSWORD` | the user token's password |
   | `SIGNING_IN_MEMORY_KEY` | the ASCII-armoured secret key, whole |
   | `SIGNING_IN_MEMORY_KEY_PASSWORD` | its passphrase |

   The release job runs in the `release` environment; add required reviewers there if a tag alone
   should not be enough to start an upload.

## Every release

1. **One version, three places.** `VERSION_NAME` in `gradle.properties`, and `SDK_VERSION` in
   `sdk-android/src/main/kotlin/com/funnelbase/sdk/Wire.kt` — the version every event batch reports
   on the wire. The release workflow refuses a tag that differs from either. Add the version to
   `CHANGELOG.md`, and update the version in `README.md`'s install snippet.
2. **Merge that to `main`**, with checks green.
3. **Tag and push:** `git tag v0.4.0 && git push origin v0.4.0`. The `release` workflow checks the
   versions, runs the tests, uploads both artifacts signed to the Central Portal, and creates a
   GitHub release.
4. **Publish in the portal.** Deployments → the new deployment: check it validated and lists both
   artifacts (`.aar`, `.pom`, `.module`, sources, javadoc, each with its `.asc`), then **Publish**.
   This is the irreversible step. It reaches Maven Central within about half an hour; search may
   take longer to show it.

Dropping a deployment in the portal instead of publishing it is how a bad upload is abandoned.
Fix, bump nothing, delete the tag (`git push --delete origin v0.4.0`) and the GitHub release, and
tag again.

## Before the first real release

`./gradlew publishToMavenLocal` puts both artifacts, unsigned, into `~/.m2`, where another build
can depend on them with `mavenLocal()` — how the Flutter plugin is built against a version that is
not on Maven Central yet.

## After a release

The Flutter plugin (`funnelbase-flutter`) depends on this version by its Maven coordinates; raise
it there when the Flutter packages should take it.
