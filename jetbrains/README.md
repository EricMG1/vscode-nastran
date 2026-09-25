# MSC Nastran plugin for PyCharm and other JetBrains IDEs

A native IntelliJ Platform plugin that ports the features of the
[vscode-nastran](https://github.com/dmarc3/vscode-nastran) extension to PyCharm (free and Professional),
IntelliJ IDEA, CLion and every other IntelliJ-based IDE, version 2024.3 or newer.

It is written in Kotlin and needs no Python interpreter at runtime.
The documentation pages (`../utils/docs`) and the keyword regexes (`../syntaxes/nastran.json`)
are shared with the VS Code extension and bundled into the plugin at build time,
so improvements to either apply to both.

## Features

| Feature | VS Code extension | This plugin |
| --- | --- | --- |
| Syntax highlighting | TextMate grammar | Lexer driven by the same grammar regexes. Colors are set under *Settings, Editor, Color Scheme, Nastran* |
| Alternate field shading | Semantic tokens | Annotator shading small, large (`*`), free field (`,`) and tab-separated cards. MON\* labels are handled too |
| Hover documentation | pygls hover | Quick Documentation (hover or <kbd>Ctrl</kbd>+<kbd>Q</kbd> / <kbd>F1</kbd>) for over 1,200 FMS, Executive, Case Control, Bulk Data and PARAM entries |
| Completion | Bulk data cards | Entry names for the section of the current line. Bulk data cards insert their field template, and the second field of `PARAM` completes parameter names |
| Include hierarchy | Explorer view, F7 | *Nastran Includes* tool window with a *Rebuild* action. The root deck follows the active editor |
| Open include file | Context menu | <kbd>Ctrl</kbd>+Click (Go to Declaration) on the path. Missing files are flagged |
| Find in includes | Alt+F, writes `find_*.dat` | *Nastran, Find in Includes...* shows the results in the Find tool window. `file:///path#line` links in comments are also clickable |
| Execute model | F6, terminal | Run configurations (*Run 'deck.bdf'* from the context menu), or *Nastran, Run Nastran* |
| Field rulers | F9 / F10 | *Nastran, Insert Short/Long Field Ruler* |
| Line comments | `$` | <kbd>Ctrl</kbd>+<kbd>/</kbd> toggles `$` comments |
| Folding | BEGIN BULK to ENDDATA | Case Control (CEND to BEGIN BULK) and Bulk Data (BEGIN BULK to ENDDATA) |

Case Control, Executive Control and Bulk Data are told apart across include files the same way the VS Code extension does it.
The root deck's include tree is flattened, and each line is placed relative to `CEND` and `BEGIN BULK`.
A file outside the current tree uses its own `CEND` and `BEGIN BULK`, and a file with neither is treated as Bulk Data.

### Settings

*Settings, Tools, Nastran* has these options:

- The Nastran executable (default `nastran`)
- Default keywords for new run configurations (for example `mem=8gb scr=yes`)
- The short and long field ruler text

A run configuration can override the executable, keywords and working directory.

### Shortcuts

The VS Code extension's shortcuts (Alt+F, F6, F7, F9, F10) clash with JetBrains defaults, so the plugin registers no shortcuts.
To add your own, go to *Settings, Keymap* and search for "Nastran".

### File types

`.bdf`, `.dat` and `.nas` files open as Nastran.
To remove `.dat` or add other extensions, go to *Settings, Editor, File Types, Nastran*.

## Building

You need JDK 21. Gradle downloads the IntelliJ Platform on first use.

```sh
cd jetbrains
./gradlew buildPlugin      # plugin zip in build/distributions/
./gradlew test             # unit and platform tests
./gradlew runIde           # sandbox PyCharm with the plugin installed
./gradlew verifyPlugin     # binary compatibility with recent IDEs
```

To install the plugin, go to *Settings, Plugins*, click ⚙, choose *Install Plugin from Disk...*, and pick the zip from `build/distributions/`.
The `jetbrains-plugin` GitHub Actions workflow also uploads the zip as a build artifact.

## Layout

```
src/main/kotlin/com/nastran/jetbrains/
  core/        Plain Kotlin ports of the VS Code logic: grammar, docs lookup, card
               detection, field shading, include parsing, include tree and sections
  lang/        Language, file type, lexer, highlighter, color settings
  psi/         Parser, PSI and the INCLUDE reference
  editor/      Commenter, folding, annotator, file:// links
  docs/        Quick documentation
  completion/  Completion
  includes/    Include tree service, tool window and editor listeners
  actions/     Find in Includes, rulers, rebuild include tree
  run/         Run configuration type, producer and Run Nastran action
  settings/    Application settings
```
