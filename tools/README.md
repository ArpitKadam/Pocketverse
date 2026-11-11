# tools

Dev scripts. Each one is stdlib-only and documented here.

| Script | Purpose |
| --- | --- |
| `axml_dump.py` | Decode a binary `AndroidManifest.xml` (pulled out of an APK with `unzip`) into indented text for component, permission and intent-filter inventory. Exists because the REA Android providers are not configured on the dev machine (see `docs/research/re/TOOLING.md`). Usage: `python tools/axml_dump.py AndroidManifest.xml`. |
