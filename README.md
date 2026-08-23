# Office Days Tracker — GitHub-ready Android project

Features planned/implemented in this package:
- Quarterly target defaults to 24 days and is intended to be editable per quarter.
- Minimum stay default: 2 hours.
- Office radius default: 250 m.
- GPS/geofence entry and exit detection.
- Local attendance storage.
- Manual correction/calendar module can be expanded in the app UI.
- GitHub Actions workflow builds `app-debug.apk` and uploads it as an artifact.

## Build online with GitHub Actions

1. Create a new GitHub repository.
2. Upload all files/folders from this project.
3. Make sure the repository contains the Gradle wrapper (`gradlew`, `gradlew.bat`, and `gradle/wrapper/*`).
4. Open **Actions** → **Build Android APK**.
5. Run the workflow.
6. Open the completed run and download **OfficeDaysTracker-debug** under Artifacts.

Android's build system uses Gradle/AGP to package APKs, and debug builds are signed with the default debug key for testing.
