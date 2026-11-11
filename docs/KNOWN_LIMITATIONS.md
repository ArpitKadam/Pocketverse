# Known limitations

Honest list of what does not work or cannot be verified, and why. Updated as research proceeds.

| Area | Limitation | Reason | Source |
| --- | --- | --- | --- |
| RE of installed apps | Private data dirs (`/data/data/<pkg>`) unreadable | Device is unrooted, `ro.debuggable=0` | `docs/research/re/TOOLING.md` |
| Generic Windows `.exe` | Not supported | No Wine/Box64 layer in scope (CLAUDE.md §7.2) | project spec |
