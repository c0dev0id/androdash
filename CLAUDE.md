# Project Notes

## Build Environment

AGP (Android Gradle Plugin) cannot be resolved from this development environment due to proxy/network restrictions. Local Gradle builds (`./gradlew assembleDebug`, `./gradlew lint`, etc.) will fail at plugin resolution. Do not attempt local builds — let CI handle build verification instead.
