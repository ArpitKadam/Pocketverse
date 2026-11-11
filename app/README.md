# app

The thin shell: navigation, dependency injection, theme host, and the activity and process declarations for each engine family (see `docs/architecture/MODULE_MAP.md`, change 5).

- **Application id:** `com.pocketverse.app` (placeholder until the name check in `CLAUDE.md` is done).
- **ABI:** arm64-v8a only for now (spec section 3).
- **Depends on:** every module (it is the composition root).
- **Status:** skeleton (P1-2): one activity that shows the app name.
