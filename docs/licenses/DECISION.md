# Licence decision

## Pocketverse is licensed under GPLv3

- **Decided:** 2026-11-12, by the owner.
- **Choice:** GNU General Public License v3, open source.
- **Alternatives considered:** a permissive or closed-source app.
- **Why:** the strongest verified cores for each format are copyleft (melonDS GPL-3.0, mkxp-z GPLv2+ with OpenSSL making binaries GPLv3, EasyRPG GPL-3.0, LibretroDroid GPL-3.0, mGBA MPL-2.0). A GPLv3 app links all of them. Evidence: `COMPATIBILITY_MATRIX.md`. A permissive or closed app would force process-separated cores or weaker alternatives (gpSP, NooDS).
- **Consequences:** every release must publish full source and keep third-party notices. GPLv2-only dependencies are forbidden. Closed-source apps stay behaviour-only references. Revisit if a required dependency turns out to be GPLv2-only or unlicensed.
- **"GPL-3.0-only" vs "or later":** to be settled by the owner before the first public release. Default in the meantime: GPL-3.0-only headers on new files.
- **Open items:** legal review before public release; trademark and name search (CLAUDE.md header).
