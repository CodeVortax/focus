# Distribution

Where Focus can be published and what each place needs. The store texts live in `fastlane/metadata/android/` (English and Arabic).

## Before any store

1. Add the images to `fastlane/metadata/android/en-US/images/`: `icon.png` (512x512) and 4 to 8 phone screenshots in `phoneScreenshots/`. See the README in that folder.
2. Tag every release (`v0.23.1-beta`) and attach the release-signed APK to the GitHub release. The tag must match `versionName`.
3. Add a changelog file for each new version code: `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`, 500 characters at most.

## GitHub and Obtainium

Releases already work with Obtainium (add the repository URL). Add topics to the repository: `android`, `digital-wellbeing`, `focus`, `accessibility-service`, `kotlin`, `jetpack-compose`.

## IzzyOnDroid

Uses your own signed APK from GitHub Releases, so the signature stays yours. Requirements: an OSI licence, public source, no trackers, a release-signed non-debuggable APK of about 30 MB or less, and fastlane metadata. Their policy rejects apps whose code contains LLM output, so read the current policy before applying: <https://izzyondroid.org/docs/general/AppInclusionPolicy/>. Request inclusion through the "New app inclusions" page linked from there.

## F-Droid

1. Fork <https://gitlab.com/fdroid/fdroiddata>, create a branch named `com.barq.focus`.
2. Copy `docs/distribution/fdroid-com.barq.focus.yml` to `metadata/com.barq.focus.yml`.
3. Run `fdroid lint`, `fdroid rewritemeta` and `fdroid build com.barq.focus` (or use the CI on your fork).
4. Open a merge request titled "New App: com.barq.focus" and answer the reviewers.

F-Droid signs with its own key unless the build is reproducible, so the F-Droid APK and your GitHub APK cannot update each other. Guide: <https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/>.

## Google Play

Skipped on purpose: it needs a paid developer account, and apps that use an accessibility service get extra review.

## Developer verification

Google is rolling out developer registration for apps installed on certified devices, starting with Brazil, Indonesia, Singapore and Thailand on 30 September 2026 and planned worldwide in 2027. It needs a fee, an identity document and proof that you hold the signing key. Keep `focus-release.jks` safe: see [Signing](signing.md).
