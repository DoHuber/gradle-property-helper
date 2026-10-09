# Gradle Property Helper

A small Kotlin/Swing desktop app for switching grouped `gradle.properties` settings. Uses Java 21; no browser runtime or server.

## Run

Install a Java 21 JDK (including `javac`) and set `JAVA_HOME` if needed.

```sh
./gradlew run
```

Open **⚙ Settings**, select the project’s existing `gradle.properties` file directly, then load `features.example.json` or your own feature JSON. Each feature shows **enabled**, **disabled**, or **mixed** according to the current file. Each group has a separate label and one toggle showing its current state: green **Enabled**, red **Disabled**, or orange **Mixed**. Feature rows stay compact and aligned at the top of the window, with labels and state buttons in shared columns. Mixed groups are labeled **Mixed**; clicking them enables the whole group. Clicking an enabled group disables it. Each change applies every property in that group in one atomic file replacement. Failed writes keep the toggle in its previously saved state. Hover over a feature state to see which keys match each state, are missing, or match neither state. Refresh reloads external edits. The picker validates the exact file path before saving it. Previously saved project directories remain supported; a missing properties file in an existing saved directory is created on the first change.

## Saved settings

Project and feature JSON selections are saved immediately and restored on launch.
On Linux, the file is `$XDG_CONFIG_HOME/gradle-property-helper/settings.json`,
or `~/.config/gradle-property-helper/settings.json` when `XDG_CONFIG_HOME` is unset,
empty, or relative (following the XDG directory specification).
Only absolute paths are saved; feature definitions and toggle states are read
from the selected files each launch. Settings are written atomically.
Missing or invalid saved files produce a warning; the app still opens and
restores any valid selection. Choose replacement paths in Settings.
Delete `settings.json` while the app is closed to reset its selections.

## Configuration

Each feature has a unique `id`, a `label`, and `enabled` / `disabled` objects with the same property keys. Values are strings; `null` removes a property. Keys may contain letters, digits, dots, underscores and hyphens. A property may belong to only one feature; conflicting configurations are rejected. See the example JSON.

Comments, unrelated entries and existing line endings are retained. Matching duplicate entries are collapsed, continued entries and escaped keys are recognized, and new values use Java properties escaping. Files are interpreted using the traditional Java properties ISO-8859-1 encoding; use Unicode escapes for non-Latin-1 characters in existing files.

Before replacing an existing file, the app saves its previous contents to a uniquely named `gradle-property-helper-*.gradle.properties.bak` file in Java’s system temp directory (`java.io.tmpdir`, normally `/tmp` on Linux). The save status shows the exact backup path; hover over it to read the path in a tooltip. Each save retains a separate backup, with no permanent backup files created in the project. Temp backups may be removed by system cleanup; copy one elsewhere if you need to keep it. The short-lived staging file remains beside `gradle.properties` so replacement can be atomic. External edits detected since loading block the save until Refresh. There is no cross-process file lock, so avoid simultaneous writes by other tools. Symbolic-link property files are rejected. Atomic replacement must be supported by the filesystem. The app edits project-local properties only; it does not launch Gradle or edit user-wide properties.

## Standalone JAR

Build a single executable JAR with its runtime dependencies included:

```sh
./gradlew standaloneJar
java -jar build/libs/gradle-property-helper-standalone.jar
```

On Windows, use `gradlew.bat standaloneJar`. You can copy this JAR anywhere;
only Java 21 and a graphical desktop are needed to run it. The feature JSON
is selected separately in Settings (the example JSON is not bundled).
`./gradlew build` also produces the standalone JAR.

To build and copy the JAR to `/home/dominik/Tools` in one step:

```sh
./gradlew installStandaloneJar
```

The task creates the destination directory if needed and replaces the previous
`gradle-property-helper-standalone.jar` there. Override the destination with
`./gradlew installStandaloneJar -PtoolsDir=/another/path`.

## Build and test

```sh
./gradlew test installDist
./build/install/gradle-property-helper/bin/gradle-property-helper
```

The distribution includes runtime libraries and requires Java 21. A graphical desktop is required for the UI; core tests run headlessly. No credentials are required. The Gradle wrapper downloads Gradle and Maven Central dependencies on first use.
