# ViaFabricPlus Sync Notes

This document records the ViaFabricPlus source currently embedded in Epsilon and the
procedure for synchronizing later upstream changes.

## Current baseline

- Upstream repository: <https://github.com/ViaVersion/ViaFabricPlus>
- Upstream branch: `ver/26.2`
- Imported commit: `a53db9f064021ee2e830173117dc095966a5cd49`
- Imported commit title: `Add jump part to classic walking animation (#1261)`
- Epsilon import commit: `1d93f4f750ee262a6fed9d15b28421224e15de26`
- Epsilon import date: `2026-08-19`

The imported source was adapted for this repository after the upstream commit. The
upstream hash identifies the source baseline; it does not imply that the Epsilon tree
is byte-for-byte identical. In particular, the loader boundary, NeoForge integration,
Epsilon-specific mixins, and configuration ownership are local changes.

## Repository layout

- Shared implementation: `common/src/main/java/me/sofurry/via`
- Shared resources and mixin configuration: `common/src/main/resources`
- Fabric-only bridge and mixins: `fabric/src/main/java` and `fabric/src/main/resources`
- NeoForge-only bridge, mixins, and access transformer: `neoforge/src/main/java` and
  `neoforge/src/main/resources`

The Java symbols were renamed to ViaVersion. The Java package namespace is
`me.sofurry.via`; the upstream package `com.viaversion.viafabricplus` is not used in
the embedded source. The resource namespace `viafabricplus`, config directory
`config/viafabricplus`, translation keys, and upstream URL are intentionally retained.
The local Mixin configuration files use the `viaversion*.mixins.json` names. The
resource and config identifiers remain useful when comparing the embedded source with
upstream. The config directory is retained only for protocol-engine-owned runtime files
(including YAML files) and optional overriding jars. ViaFabricPlus's save/settings
subsystem is removed: user settings live in Epsilon's `ClientSetting` module, and the
selected protocol version is stored by Epsilon's `ConfigManager` as module custom state.

## Synchronizing an upstream update

### 1. Preserve the local state

Start from a clean synchronization point and record local work before touching the
embedded source. Do not reset or discard unrelated Epsilon changes.

```powershell
git status --short
git diff -- common fabric neoforge > epsilon-before-vfp-sync.patch
git remote add viafabricplus https://github.com/ViaVersion/ViaFabricPlus.git
git fetch viafabricplus ver/26.2
```

If the remote already exists, skip `git remote add` and run `git fetch` only.

### 2. Select and record the upstream commit

Inspect the commits after the recorded baseline and choose the commit that matches the
Minecraft version being maintained. Record the full 40-character hash before applying
anything.

```powershell
git log --date=short --format='%H %ad %s' `
  a53db9f064021ee2e830173117dc095966a5cd49..viafabricplus/ver/26.2
git show --stat --oneline <new-upstream-hash>
```

Do not automatically use the moving upstream `HEAD`. Check whether the update changes
the Minecraft mappings, ViaVersion API, ViaBedrock/ViaLegacy APIs, or the upstream module
layout first.

### 3. Compare the source by responsibility

Compare upstream common code with the corresponding Epsilon common code. Upstream API
and visuals modules are merged into the Epsilon `common` source tree, so map them into
the matching package rather than copying the upstream project root wholesale.

```powershell
git diff --find-renames `
  a53db9f064021ee2e830173117dc095966a5cd49..<new-upstream-hash> -- `
  src/main/java viafabricplus-api/src/main/java viafabricplus-visuals/src/main/java
```

Apply only the relevant implementation and resource changes. Keep Epsilon build files,
loader entrypoints, Epsilon mixins, and local NeoForge fixes under separate review.

### 4. Reapply the local naming and layout rules

For newly copied or renamed upstream files, apply the same source-symbol mapping used by
this port:

```text
ViaFabricPlus  -> ViaVersion
viaFabricPlus  -> viaVersion
VFP            -> ViaVersion
vfp            -> viaVersion
```

The Java package mapping is:

```text
com.viaversion.viafabricplus -> me.sofurry.via
```

Keep these runtime/resource identifiers unchanged unless a deliberate migration is
planned:

```text
viafabricplus
config/viafabricplus
viaversion*.mixins.json
viafabricplus*.accesswidener
```

Update filenames and all Java/Mixin configuration references together. Preserve the
upstream GitHub URL in license headers and provenance comments.

### 5. Reapply loader-specific adaptations

- Keep the common platform interface in `common` free of Fabric and NeoForge classes.
- Keep loader installation in `ViaVersionFabricPlatform` and
  `ViaVersionNeoForgePlatform`.
- Do not restore reflection or hard-coded loader class paths to the common layer.
- Merge NeoForge access changes into `META-INF/accesstransformer.cfg` deliberately;
  do not replace the file with the upstream Fabric access widener.
- Merge common access wideners and NeoForge access transformers entry by entry. Check
  every changed Minecraft member against the matching `reference/fabric-26.2` and
  `reference/neoforge-26.2` source.
- Preserve Epsilon-only mixins and the custom sync-payload hooks unless the upstream
  change makes them obsolete.
- Do not restore ViaFabricPlus's `save`, `settings`, or `api.settings` packages. New
  persistent user state must be integrated with Epsilon's `ConfigManager`.

### 6. Update this record

After the source is merged, replace the baseline hash and commit title in this document
with the selected full hash. Also note any local compatibility patch that was needed for
the update. Keep the old hash in the synchronization commit message or review notes so
the next update has an unambiguous range.

### 7. Build and manually test

Run both loader compilations and package builds before testing in-game:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.3'
.\gradlew.bat :fabric:compileJava :neoforge:compileJava --no-daemon
.\gradlew.bat :fabric:build :neoforge:jar --no-daemon
git diff --check
```

Then launch the loader manually and test joining representative protocol versions,
opening the ViaVersion settings screens, changing the per-server version, and loading a
NeoForge client. Treat Mixin application errors, missing resources, and access errors as
sync blockers rather than working around them with reflection.
