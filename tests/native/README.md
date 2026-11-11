# Native fleet rule tests

These tests execute the production Kotlin state engine and JSON codec, not a JS
reimplementation. Android SDK and React Native are not needed for this test run.
Android adapter integration is checked separately by compilation and device tests.

Requirements:

- Java 21+ runtime, for example Eclipse Temurin.
- Kotlin compiler 2.2.20, the `kotlinc` directory from the official release archive.
- `org.json:json:20250517` JAR for JVM tests. Android supplies org.json in production;
  this JAR is not added to the APK.

From PowerShell in the repository:

```powershell
./scripts/test-native-fleet.ps1 -JavaPath 'C:\path\to\java.exe' -KotlinHome 'C:\path\to\kotlinc' -JsonJar 'C:\path\to\json.jar'
```

Sources: [Kotlin compiler](https://github.com/JetBrains/kotlin/releases/tag/v2.2.20),
[Temurin](https://adoptium.net/temurin/releases/),
[Maven Central JSON](https://repo.maven.apache.org/maven2/org/json/json/20250517/).

The script writes a uniquely named test JAR in the system temp directory and
exits nonzero on compiler/assertion failure. It does not install tools or modify
the Android installation. `npm run test:vehicles` tests the JS/native facade.
