# Cinecred Agent Notes

## Environment Lessons

- This repository is a Gradle/Kotlin JVM project and the documented Windows entry point is `gradlew.bat runOnWindows`.
- The project requires JDK 21. The Gradle toolchain is pinned to Java 21 and auto-download is disabled.
- A newer JDK on `PATH` is not enough. Gradle will still fail if it cannot discover a local JDK 21 installation.
- `JAVA_HOME` must point to a valid JDK before invoking `gradlew.bat`. An invalid `JAVA_HOME` causes the wrapper to fail before Gradle starts.
- JDK 23 does not satisfy this build. The toolchain resolution fails with `languageVersion=21`.
- On this machine, a working JDK 21 was installed at `C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot`.

## Build And Run Lessons

- `runOnWindows` handles both compilation and native library collection for Windows, then launches the app.
- The first successful run may take several minutes because Gradle and dependencies need to be downloaded and compiled.
- Kotlin daemon instability can appear during compilation. In this repo, Gradle fell back to non-daemon Kotlin compilation and still completed successfully.
- If you need a repeatable local launch, set `JAVA_HOME` for the current PowerShell session and prepend its `bin` directory to `PATH` before calling `gradlew.bat`.

## Suggested Command

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat runOnWindows
```
