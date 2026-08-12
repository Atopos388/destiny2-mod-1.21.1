# Contributing

Thanks for helping improve the project. The repository is an experimental alpha, so focused, reviewable changes are more useful than large unannounced rewrites.

## Before you start

- Search existing issues before creating a new one.
- Open an issue before implementing a large gameplay, rendering, networking, dependency, or data-format change.
- Small fixes, tests, and documentation improvements may go directly to a pull request.
- Do not submit secrets, generated caches, run directories, extracted proprietary assets, or files you do not have permission to redistribute.

## Development setup

1. Install JDK 21 and clone the repository.
2. Create a branch from `main`.
3. Run `./gradlew build` (`.\gradlew.bat build` on Windows) once before editing.
4. Keep changes scoped and reuse existing gameplay/client boundaries.

## Verification

At minimum, run:

```powershell
.\gradlew.bat test
.\gradlew.bat build
```

Run `.\gradlew.bat codeGraph` after structural or cross-module changes. Visual or input changes must also include the in-game scenario, camera perspective, and settings used for manual verification. A green build alone is not acceptance for visible behavior.

## Pull requests

- Explain the problem, the chosen approach, and alternatives considered.
- List automated checks and manual in-game checks separately.
- Include screenshots or short recordings for visual changes.
- Call out save-data, networking, balance, performance, and compatibility risks.
- Use conventional commit prefixes such as `feat:`, `fix:`, `refactor:`, `test:`, and `docs:`.

## Asset and reference policy

Reference material may be used to study behavior, timing, composition, or style, but contributed files must be original or have clear redistribution permission. Do not add ripped game assets, leaked content, or trademarked material presented as official. Record third-party attribution and license requirements in `NOTICE.md`.

By contributing, you agree that your contribution may be distributed under the repository's GPL-3.0-only license.

