# Gradle Property Helper

A small Kotlin tool for switching grouped `gradle.properties` settings, available as a Swing desktop app and an IntelliJ IDEA plugin. Both use the same feature JSON and editing logic. The desktop app uses Java 21; no browser runtime or server.

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
./gradlew :test :installDist
./build/install/gradle-property-helper/bin/gradle-property-helper
```

The distribution includes runtime libraries and requires Java 21. A graphical desktop is required for the UI; core tests run headlessly. No credentials are required. The Gradle wrapper downloads Gradle and Maven Central dependencies on first use.

## IntelliJ IDEA plugin

The plugin targets **IntelliJ IDEA 2026.2.x**, including 2026.2.3 (build 262).
It uses standard platform APIs and does not need Ultimate-only features.

Build an installable ZIP from the repository root:

```sh
./gradlew buildPlugin
```

The result is `intellij-plugin/build/distributions/gradle-property-helper-0.2.1.zip`.
The first build downloads the IDEA 2026.2.3 SDK from JetBrains; allow extra time and
several GB of disk space. You can use your existing IDE installation instead:

```sh
./gradlew buildPlugin -PideaHome=/absolute/path/to/idea
```

Use the installation root containing `product-info.json`, not its `bin` directory.
This avoids downloading another IDE. It must be IDEA 2026.2.x.

In IDEA, open **Settings → Plugins → ⚙ → Install Plugin from Disk…**, select the
ZIP without extracting it, then restart if prompted. Open **View → Tool Windows
→ Gradle Properties** (the right sidebar).

The plugin defaults to `gradle.properties` in the open project's root. Use its
Settings toolbar action to select a different properties file and the same
feature JSON you use in the desktop app. Paths are saved per project in the
IDE's workspace settings; files inside the project use relative paths.
Desktop selections in `~/.config` remain independent.

Each compact row has a label, a theme-aware state indicator, and an IDE-styled
action button. Green means enabled with a **Disable** button; red means disabled
with an **Enable** button. Orange means mixed, with an **Enable** button to enable
the whole group. Editor changes, external file changes and Undo/Redo refresh
the displayed states automatically. Refresh also reloads the feature JSON.

Changes use the current IDE document, preserve unrelated **unsaved edits**, are
recorded as one undoable command, and save the selected properties document.
A UTF-8 backup of the previous document text is kept in the system temp directory;
the status shows its path. A properties/configuration change during a toggle is
rejected rather than applying an outdated snapshot. Read-only files are handled
through IDEA's writable-file check. The plugin does not trigger a Gradle sync or
build automatically.

Run plugin integration tests or launch a sandbox IDE:

```sh
./gradlew :intellij-plugin:test
./gradlew :intellij-plugin:runIde
```

## Shared code and build targets

- `core`: feature JSON parsing/validation, state matching and pure text editing.
- `desktop`: Swing UI, Linux settings, atomic filesystem writes and temp backups.
- `intellij-plugin`: project tool window, IDE settings, document commands and listeners.

The existing desktop targets `standaloneJar`, `installStandaloneJar`, `run` and
`installDist` retain their behavior and output locations. For only the core and
desktop tests, use `./gradlew :test` (the leading colon selects the root task).
The unqualified `test`/`build` commands may also select plugin tasks in this
multi-project build and therefore need the IDEA SDK.
