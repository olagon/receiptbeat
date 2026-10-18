# Contributing

Thanks for helping. Receipt Beat is built for everyone, so plain code and plain words win.

1. Read `docs/SPEC.md` (the source of truth) and `CLAUDE.md` (the code rules).
2. Open an issue before big changes.
3. Run `./gradlew ktlintCheck detekt test assembleDebug` before a pull request.
4. Never add analytics, ads, crash reporting, Firebase or any telemetry library.
5. Never commit secrets. Keys go in `local.properties` or come from the user at runtime.
6. Money is always `Long` cents. Never `Float` or `Double`.

Tax presets for other countries are welcome as JSON files. See `app/src/main/assets/presets/`.
