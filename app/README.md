# app

The thin shell: navigation, dependency injection, theme host, and the activity and process declarations for each engine family (see `docs/architecture/MODULE_MAP.md`, change 5).

- **Application id:** `com.pocketverse.app` (placeholder until the name check in `CLAUDE.md` is done).
- **ABI:** arm64-v8a only for now (spec section 3).
- **Depends on:** every module (it is the composition root).
- **Status:** P1-5: `PocketverseApp` wires the library (app-specific external directory, `Pocketverse/`) and the index by hand; `MainActivity` shows the library screen; `DebugImportStub` adds a fake test game in debuggable builds only, through the same transactional path the real import will use. P2-4: `MainActivity` registers the system file and folder pickers and hosts `ImportDialogs`; the import runs in `ImportViewModel` so it survives rotation. One instrumented test (`androidTest`) covers the real storage location; it compiles but has not been run on a device.
