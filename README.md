# Gradle Property Helper

A small Kotlin/Swing desktop app for switching grouped `gradle.properties` settings. Uses Java 21; no browser runtime or server.

## Run

Install a Java 21 JDK (including `javac`) and set `JAVA_HOME` if needed.

```sh
./gradlew run
```

Open **⚙ Settings**, choose a local project directory, then load `features.example.json` or your own feature JSON. Each feature shows **enabled**, **disabled**, or **mixed** according to the current file. Enable/Disable applies every property in that group in one atomic file replacement. Refresh reloads external edits. A missing `gradle.properties` is created on the first change.

## Configuration

Each feature has a unique `id`, a `label`, and `enabled` / `disabled` objects with the same property keys. Values are strings; `null` removes a property. Keys may contain letters, digits, dots, underscores and hyphens. A property may belong to only one feature; conflicting configurations are rejected. See the example JSON.

Comments, unrelated entries and existing line endings are retained. Matching duplicate entries are collapsed, continued entries and escaped keys are recognized, and new values use Java properties escaping. Files are interpreted using the traditional Java properties ISO-8859-1 encoding; use Unicode escapes for non-Latin-1 characters in existing files.

Before replacing an existing file, the app copies it to `gradle.properties.bak` (overwriting the previous backup). External edits detected since loading block the save until Refresh. There is no cross-process file lock, so avoid simultaneous writes by other tools. Symbolic-link property files are rejected. Atomic replacement must be supported by the filesystem. The app edits project-local properties only; it does not launch Gradle or edit user-wide properties.

## Build and test

```sh
./gradlew test installDist
./build/install/gradle-property-helper/bin/gradle-property-helper
```

The distribution includes runtime libraries and requires Java 21. A graphical desktop is required for the UI; core tests run headlessly. No credentials are required. The Gradle wrapper downloads Gradle and Maven Central dependencies on first use.
