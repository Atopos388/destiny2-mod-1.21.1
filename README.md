# Destiny 2 Inspired Mod for Fabric

[![Build](https://github.com/Atopos388/destiny2-mod-1.21.1/actions/workflows/build.yml/badge.svg)](https://github.com/Atopos388/destiny2-mod-1.21.1/actions/workflows/build.yml)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)
![Minecraft 1.21.1](https://img.shields.io/badge/Minecraft-1.21.1-62B47A)
![Fabric](https://img.shields.io/badge/Loader-Fabric-DBD0B4)
![Java 21](https://img.shields.io/badge/Java-21-E76F00)
![Status: Experimental Alpha](https://img.shields.io/badge/Status-Experimental%20Alpha-orange)

[简体中文](README.zh-CN.md)

An open-source Minecraft Fabric project exploring how Destiny-inspired combat systems can be expressed through Minecraft-native mechanics, rendering, animation, UI, and server-authoritative gameplay.

> [!WARNING]
> This repository is an experimental alpha. Systems, save data, balance, and APIs may change without migration support. There is no stable public release yet.

## What is in the project?

- Three evolving subclass foundations: Solar Warlock, Void Hunter, and Arc Titan.
- Active abilities, aspects, fragments, grenades, class abilities, supers, status effects, and energy/cooldown rules.
- Custom weapon behavior and roll data, Armor 3.0 experiments, loot, HUDs, and subclass configuration UI.
- Renderer-first VFX and supporting particle systems for abilities and world entities.
- World pickups including Orbs of Power and Firesprites, plus crafting/progression experiments such as Light Crystals.
- Minecraft-style industrial home-building machines: power, fabrication, storage, processing, and logistics prototypes.
- Automated contract and unit tests for gameplay rules, assets, data contracts, and integration boundaries.

The goal is not a one-to-one content port. The project investigates reusable, maintainable ways to build ability-driven combat in Fabric while keeping gameplay authority on the server and visual presentation on the client.

## Project status

| Area | Current state |
| --- | --- |
| Target | Minecraft 1.21.1, Fabric Loader, Java 21 |
| Gameplay | Playable foundations; content and balance are still being refined |
| Rendering | Custom world/entity render passes plus particles; live visual tuning is ongoing |
| Multiplayer | Core gameplay is designed around server-authoritative state; broader multiplayer testing is needed |
| Compatibility | Development setup only; no stable compatibility promise yet |
| Releases | Source builds only until the first reviewed alpha release |

## Architecture

The codebase is organized around explicit boundaries:

- common gameplay and registries under `src/main/kotlin`;
- client rendering, input, HUD, and visual runtime under client packages;
- data and assets under `src/main/resources`;
- generated dependency reports under `build/reports/code-graph`;
- JUnit contract tests under `src/test`.

For broad changes, the custom `codeGraph` Gradle task generates a source dependency overview to make review and maintenance easier.

## Build from source

### Requirements

- JDK 21
- Git
- Windows PowerShell or a POSIX-compatible shell

Clone the repository, then run:

```powershell
# Windows
.\gradlew.bat build
```

```bash
# Linux/macOS
./gradlew build
```

The distributable mod JAR is generated in `build/libs/`. Do not install the `-sources.jar` file.

### Development checks

```powershell
.\gradlew.bat test
.\gradlew.bat codeGraph
```

Automated checks verify code and resource contracts, but they do not replace in-game visual, input, multiplayer, and performance acceptance testing.

### Local dependency note

The current development branch includes project-specific local JARs in `libs/`, including a patched LDLib client build and compile-time layout bindings. Their provenance and redistribution terms must be audited before a public binary release. This is tracked as release-blocking maintenance work rather than hidden behind a successful build.

## Contributing

Bug reports, focused fixes, tests, documentation, and well-scoped feature proposals are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request. Larger gameplay or architecture changes should start with an issue so authority boundaries, asset rights, and test expectations can be agreed on first.

Please use the provided issue forms:

- [Report a bug](https://github.com/Atopos388/destiny2-mod-1.21.1/issues/new?template=bug_report.yml)
- [Propose a feature](https://github.com/Atopos388/destiny2-mod-1.21.1/issues/new?template=feature_request.yml)
- [Report a security issue](SECURITY.md)

See [ROADMAP.md](ROADMAP.md) for current priorities and [CHANGELOG.md](CHANGELOG.md) for notable changes.

## License and fan-project notice

Original project code is licensed under [GPL-3.0-only](LICENSE). Additional attribution and third-party notices are listed in [NOTICE.md](NOTICE.md).

This is an independent, non-commercial fan project. It is not affiliated with, endorsed, or sponsored by Bungie or Sony Interactive Entertainment. Destiny, Destiny 2, and related names and marks belong to their respective owners. Contributors must only submit code and assets they have the right to redistribute; do not submit ripped or proprietary game assets.
