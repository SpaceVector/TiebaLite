# Repository Guidelines

## Project Structure & Module Organization
This repository is a single-module Android app. Root-level Gradle files (`build.gradle`, `settings.gradle`, `gradle.properties`) configure the build; the application module lives in `app/`.

Primary code is under `app/src/main/java/com/huanchengfly/tieba/post`. Keep new code in the existing package layout when possible:
- `activities/` for screens
- `api/` for network models, Retrofit interfaces, and interceptors
- `adapters/`, `services/`, `widgets/`, and `interfaces/` for UI and support code

Resources belong in `app/src/main/res`, static assets in `app/src/main/assets`, local JVM tests in `app/src/test/java`, and device tests in `app/src/androidTest/java`.

Most subdirectories also include a local `README.md`. Read the nearest one before editing in that area, and update it when the directory's responsibility changes.

## Build, Test, and Development Commands
Use the Gradle wrapper from the repository root:

- `./gradlew assembleDebug` builds the debug APK
- `./gradlew testDebugUnitTest` runs JVM unit tests in `app/src/test`
- `./gradlew connectedDebugAndroidTest` runs instrumentation tests on a device or emulator
- `./gradlew lint` runs Android Lint checks
- `./gradlew clean` removes build outputs

Release builds also read `application.properties` and `keystore.properties`; keep secrets local and out of commits.

## Coding Style & Naming Conventions
This codebase mixes Java and Kotlin. Follow the existing Android Studio defaults: 4-space indentation, no tabs, and organized imports. Match current naming patterns:

- `PascalCase` for classes, activities, adapters, and services
- `camelCase` for methods and fields
- `snake_case` for layout, drawable, menu, and value resource names
- test classes ending in `Test`

No dedicated formatter is checked in, so keep diffs small and consistent with nearby files.

## Testing Guidelines
Add or update JVM tests for logic that can run off-device, and instrumentation tests for Android framework behavior. Place tests in the mirrored package under `app/src/test/java` or `app/src/androidTest/java`.

Prefer descriptive test names such as `loginFailsWithoutCookie()` or `useAppContext()`. Run `./gradlew testDebugUnitTest` before opening a PR; run `connectedDebugAndroidTest` for UI, service, or platform-sensitive changes.

## Commit & Pull Request Guidelines
Commit history follows Conventional Commits, with optional scopes, for example `fix: 修复楼中楼无法显示更多` or `docs(README): 修改 README`. Use concise, imperative subjects.

Pull requests should include a short summary, linked issue when applicable, test evidence, and screenshots or recordings for UI changes. Keep PRs focused; avoid mixing refactors with behavior changes unless they are tightly related.
