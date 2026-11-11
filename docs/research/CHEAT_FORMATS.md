# Cheat formats: GBA, NDS and container files

Task 8 research note, 2026-11-12. Purpose: a precise reference for implementing pure-Kotlin cheat decoders and their unit tests (`cheats-gba`, `cheats-nds`, `cheats-core`; see CLAUDE.md section 8). Everything here was read from the cloned upstream sources. Where a claim could not be verified from a source file it is marked `UNVERIFIED` with the reason. Hand-computed values are marked `HAND-COMPUTED, VERIFY AGAINST mGBA`.

Scope reminder: GameShark, Action Replay and Code Breaker are cartridge-hardware formats. They exist for GBA and NDS only. `.exe`-style games (RPG Maker, Ren'Py) get engine-native cheats; that is covered by `docs/research/re/joiplay-rpgm-plugin.md:71-84` and is not repeated here.

---

## 1. Scope and method

### 1.1 What was read

Seven cloned repositories under `research/sources/` (gitignored, read-only) plus four prior notes. Commit hashes from `git -C research/sources/<repo> rev-parse --short=10 HEAD`:

| Repo | Commit | Licence (from `docs/licenses/COMPATIBILITY_MATRIX.md:9-17`) |
| --- | --- | --- |
| mgba | `662dada8b7` | MPL-2.0 |
| vbam | `aad5da47e4` | GPL-2.0, "or later" unverified (`docs/licenses/COMPATIBILITY_MATRIX.md:11`) |
| gpsp | `5819380c2f` | GPL-2.0-or-later |
| melonds | `497aa6cdd2` | GPL-3.0 |
| desmume | `559bf216bd` | GPL-2.0-or-later |
| noods | `4cb1439c33` | GPL-3.0 (`research/sources/noods/LICENSE:1-2`) |
| melonds-android | `aaf263d233` (submodule `melonDS-android-lib` `431ab4bd00`) | GPL-3.0 |
| retroarch | `5ab2152e6a` | GPL-3.0 (`research/sources/retroarch/COPYING:1-2`) |

The `melonDS-android-lib` submodule is present in the clone and its `AREngine.cpp` and `ARCodeFile.cpp` differ from upstream melonDS only in the copyright-year line (`diff` run during this task; first differing line is line 2 of each file). So the installed melonDS-Android app executes the same Action Replay engine as `research/sources/melonds/src/AREngine.cpp`. (The note at `docs/research/OPEN_SOURCE_ALTERNATIVES.md:13` says the lib was not fetched; in the current clone it is.)

Not read, by instruction: `DemoGames/**/*.pdf` (copyrighted; the owner has not approved). `docs/research/re/melonds.md:97` records that such a PDF exists. No test vectors were taken from it.

### 1.2 Citation convention

To keep 900 lines readable, long paths are given a short alias once. A citation `[GS:104-249]` means `research/sources/mgba/src/gba/cheats/gameshark.c:104-249`. The full path of every alias is in the table in section 9. Line numbers are 1-based and refer to the commit hashes above.

### 1.3 Method and limits

- Source reading only. No reference decoder was compiled or run in this task (no C toolchain run was attempted). Therefore no encrypted-code test vector has been produced. Section 7 says how to produce them.
- Behaviour is described as implemented, not as it was on real hardware. Where two emulators disagree, both are recorded and the disagreement is listed in section 8. We must not pick a winner without hardware evidence or a published spec.
- Names: the sources use "GameShark v1/v2/v3" (VBA-M) and "GameShark v1" / "Pro Action Replay v3" (mGBA) for what turn out to be the same two cipher families (section 2.3).

---

## 2. GBA code families

### 2.1 Overview and how the cores name things

| Family | Text line | Encrypted? | mGBA name | VBA-M name | gpSP |
| --- | --- | --- | --- | --- | --- |
| Raw memory write (VBA style) | `AAAAAAAA:VV`, `AAAAAAAA:VVVV`, `AAAAAAAA:VVVVVVVV` | no | `GBA_CHEAT_VBA` [GCH:19-25], [GC:157-203] | `cheatsAddCheatCode` [VB:1435-1518] | not supported |
| GameShark v1 (original; also what Pro Action Replay v1/v2 hardware uses, per VBA-M comment "based on AR v1.0") | `XXXXXXXX YYYYYYYY` | TEA-style, seeds+tables "v1" | `GBA_GS_GSAV1` / `_RAW` [GSH:15-21] | `cheatsAddGSACode(..., v3=false)` code id 256 [VB:1572, 1922-2060] | not supported |
| GameShark Advance "v3" = Pro Action Replay v3 | `XXXXXXXX YYYYYYYY` | same cipher, seeds+tables "v3" | `GBA_GS_PARV3` / `_RAW` | `cheatsAddGSACode(..., v3=true)` code id 257 [VB:1631-1921] | not supported |
| Code Breaker | `XXXXXXXX YYYY` | optional, triggered by a `9xxxxxxx yyyy` line | `GBA_CHEAT_CODEBREAKER` | `cheatsAddCBACode` code id 512 [VB:2450-2611] | unencrypted only |

Equivalence of "GameShark v3" and "Pro Action Replay v3": VBA-M's `v3_deadtable1/2` contain exactly the same 256 bytes as mGBA's `_par3T1/_par3T2`, and `v1_deadtable1/2` equal mGBA's `_gsa1T1/_gsa1T2`. This was checked by hashing the extracted byte lists of the four table pairs: [VB:212-227] vs [GS:16-31]; [VB:230-245] vs [GS:35-50]; [VB:250-265] vs [PAR:15-30]; [VB:268-283] vs [PAR:34-49]; all four pairs gave identical MD5 prefixes. The decrypt rounds are also the same (compare [GS:65-73] with [VB:1558-1570]). So both projects implement one cipher with two seed sets.

Initial seeds, mGBA: v1 `{0x09F4FBBD, 0x9681884A, 0x352027E9, 0xF3DEE5A7}` [GS:13]; v3 `{0x7AA9648F, 0x7FAE6994, 0xC0EFAAD5, 0x42712C57}` [PAR:12]. VBA-M has no constant seeds; it derives them from the most recent `DEADFACE` line, or from parameter 0 if there is none [VB:1520-1545, 1608]. HAND-COMPUTED, VERIFY AGAINST mGBA: reseed with parameter 0 using the v1 tables gives for seed 0 the bytes `t1[0]+t2[0]=0x31+0xD8=0x09`, `t1[1]+t2[0]=0x1C+0xD8=0xF4`, `t1[2]+t2[0]=0x23+0xD8=0xFB`, `t1[3]+t2[0]=0xE5+0xD8=0xBD` (mod 256), i.e. `0x09F4FBBD`, which equals the first constant; with v3 tables the same exercise gives `0xD0+0xAA=0x7A`, `0xFF+0xAA=0xA9`, `0xBA+0xAA=0x64`, `0xE5+0xAA=0x8F` = `0x7AA9648F`. Only seed 0 of each set was hand-checked; seeds 1-3 follow the same formula and should be confirmed by the conformance test (section 7).

### 2.2 Common helpers

#### Hex parsing

mGBA's `hex32/hex16/hex8` read exactly 8/4/2 hex digits and fail otherwise (`research/sources/mgba/src/util/string.c:506-585`; hex32 at 506, hex16 at 538, hex8 at 572). Letters are accepted in both cases (not re-verified for `hexDigit`; `UNVERIFIED`, function body at `string.c:~490` was not read). VBA-M requires upper case: `CHEAT_IS_HEX` accepts only `A-F` and `0-9` [VB:304].

#### Line shapes accepted by mGBA's auto-detecting `addLine`

[GC:205-250]:

1. Read 8 hex digits (`op1`). If the next char is `:` the line is VBA style [GC:229-231].
2. Skip any whitespace (zero or more), read 4 hex digits (`op2`) [GC:232-238].
3. If the next char is end-of-line or whitespace, treat as Code Breaker (`op1`, 16-bit `op2`) [GC:239-241].
4. Otherwise read 4 more hex digits (`op3`), form `op2 = (op2<<16)|op3`, and run version auto-detection [GC:242-249].

So `AAAAAAAA BBBBBBBB` and `AAAAAAAABBBBBBBB` (no separator) are both accepted as a 16-digit line. The `type` argument can force a family: Code Breaker, GameShark, Pro Action Replay, VBA, or autodetect [GCH:19-25], [GC:205-220].

#### Version auto-detection (16-digit lines, mGBA)

Only runs while the set's `gsaVersion == 0`, i.e. on the first 16-digit line of a set [GC:110-146]. It scores four candidates and keeps the highest score (strict `>`, so earlier candidates win ties):

| Order | Candidate | How the line is prepared | Score function |
| --- | --- | --- | --- |
| 1 | `GSAV1` (encrypted, v1 seeds) | decrypt with v1 seeds | `GBACheatGameSharkProbability` [GS:287-345] |
| 2 | `PARV3` (encrypted, v3 seeds) | decrypt with v3 seeds | `GBACheatProActionReplayProbability` [PAR:372-442] |
| 3 | `GSAV1_RAW` | line as typed | GameShark score |
| 4 | `PARV3_RAW` | line as typed | PAR score |

[GC:112-139]. Quirk: step 2 decrypts `o1,o2` that were restored to the originals only after step 1 (`o1 = op1; o2 = op2` at [GC:115-116]), so this is correct, but step 3 and 4 use `op1/op2` directly. If the winning index is below 3 the line goes to the GameShark adder, else to the PAR adder [GC:141-145]. Later lines in the same set reuse the chosen version [GC:147-153]. The version can be saved in the cheat file as the directive `!GSAv1`, `!GSAv1 raw`, `!PARv3`, `!PARv3 raw` [GC:280-333].

Score rules (exact numbers matter if we reimplement the same detector):

- Both functions: second word `0x001DC0DE` scores `0x100` (an ID code) [GS:289-291], [PAR:374-376]. PAR also scores `0x100` for `op1 == 0xDEADFACE` when `op2`'s high half is zero [PAR:377-379].
- GameShark: per top nibble of `op1`: nibbles 0,1,2 and 6 start at `+0x20`, minus `0x10` if the value operand has unused high bits, plus the address plausibility `GBACheatAddressIsReal` for 0-2 [GS:293-317]; nibble 8 `+0x10` [GS:318-320]; nibble D `+0x20`, `-0x10` if `op2 & 0xFFCF0000`, plus address score [GS:321-327]; nibble E `+0x20` plus score of `op2` as address, `-0x10` if `op1 & 0x0F000000` [GS:328-334]; nibble F `+0x20`, `-0x10` if `op2 & 0xFFFF0000` [GS:335-340]; any other nibble (3,4,5,7,9,A,B,C) `-0x40` [GS:341-343]. Note nibble 3 (the list/group write) therefore scores negative in auto-detect.
- `GBACheatAddressIsReal` returns `-0x80` for BIOS, `+0x20` for in-range EWRAM/IWRAM, `+0x10` for IO, `-0x8` for OAM/VRAM/palette/ROM/SRAM in range, `-0x40` or `-0x80` when out of range, `-0xC0` for any other region [GC:335-386].
- PAR scoring is in [PAR:380-441]: `op1 == 0` special lines get `+0x40` and further adjustments by `op2 & 0xFE000000`; conditionals `+0x20`; plain writes `+0x20` plus the address score and `0` if bit 24 of `op1` is set for assign/indirect/add.

Cross-check: John GBA Lite's UI offers "Unspecified, Raw code, GameShark, GameShark v3, CodeBreaker" [`docs/research/re/john-gba-lite.md:112-113`] and its native library contains the VBA function names `cheatsAddGSACode` and `cheatsAddCBACode` [`docs/research/re/john-gba-lite.md:61`]. That is behavioural evidence only; no code from that app was used.

### 2.3 GameShark v1 and GameShark Advance v3 / PAR v3: the cipher (shared)

Both families are encrypted with one 64-bit block cipher over the two 32-bit words `(op1, op2)` of a line. The source comment in mGBA says it is TEA ("Tiny Encryption Algorithm") [GS:53]. All arithmetic is modulo 2^32.

Decrypt, exactly as implemented [GS:65-73]; VBA-M agrees [VB:1558-1570]:

```
sum = 0xC6EF3720
repeat 32 times:
    op2 -= ((op1 << 4) + seeds[2]) ^ (op1 + sum) ^ ((op1 >> 5) + seeds[3])
    op1 -= ((op2 << 4) + seeds[0]) ^ (op2 + sum) ^ ((op2 >> 5) + seeds[1])
    sum -= 0x9E3779B9
```

`>>` is a logical shift on unsigned 32-bit values. Encrypt is the inverse [GS:54-63]: `sum += 0x9E3779B9; op1 += F(op2, seeds[0], seeds[1], sum); op2 += F(op1, seeds[2], seeds[3], sum)`, 32 rounds, where `F(v,a,b,s) = ((v<<4)+a) ^ (v+s) ^ ((v>>5)+b)`. (32 times 0x9E3779B9 mod 2^32 is 0xC6EF3720, consistent with the decrypt start value.) Encrypt exists in mGBA only; use it to build round-trip tests.

Reseed (the `DEADFACE` code), mGBA [GS:75-86]:

```
params = low 16 bits of op2 of the "DEADFACE XXXXXXXX" line (after decryption)
s0 = params >> 8 ; s1 = params & 0xFF
for y in 0..3:
    for x in 0..3:
        z = (t1[(s0 + x) & 0xFF] + t2[(s1 + y) & 0xFF]) & 0xFF
        seeds[y] = (seeds[y] << 8) | z
```

Four shifts overwrite all of the old seed, so reseeding is independent of the previous seeds. VBA-M computes the same thing as `seed_gen(upper=params>>8, seed=(params&0xFF)+i)` with `newseed = (newseed<<8) | ((t1[(i+upper)&0xFF] + t2[seed]) & 0xFF)` for `i=0..3` [VB:1547-1556], and applies it as the seed for every GSA line, using the latest `DEADFACE` code found anywhere earlier in the list [VB:1520-1545].

Which tables: `t1,t2` = `_gsa1T1/_gsa1T2` for GameShark v1 [GS:15-51], `_par3T1/_par3T2` for PAR/GSA v3 [PAR:14-50]. The tables are 256 bytes each (2048 bytes of constants in total); do not retype them, import them programmatically from the C source into a Kotlin resource and test the import by hash.

Order of operations in mGBA for an encrypted line: decrypt first, then interpret [GS:251-268], [PAR:336-353]. A decrypted `DEADFACE` line reseeds for all following lines [GS:197-201], [PAR:275-278]. In "raw" mode the decrypt step is skipped (the `_RAW` fall-through), so `DEADFACE` is parsed but pointless [GS:261-265].

### 2.4 Raw RAM writes (8/16/32 bit)

Three places produce a plain write:

1. VBA style line `AAAAAAAA:VV..`. mGBA: after 8 digits and `:`, up to four bytes are read as hex8 pairs and joined big-endian into one value (`value = value<<8 | byte`); lengths 1, 2 or 4 bytes are valid, 0 and 3 are rejected [GC:157-182]. The value is therefore written as typed (`:AABB` writes 0xAABB as a 16-bit value). If `address < 0x08000000 || address >= 0x0E000000` it becomes a RAM `CHEAT_ASSIGN` with that width, repeat 1; otherwise it becomes a ROM patch (`mCheatPatch`) of that width [GC:183-201] (`GBA_BASE_ROM0`, `GBA_BASE_SRAM` constants, not read). VBA-M: lengths 11, 13, 17 characters = 8, 16, 32-bit; the first byte of the address must be 0x02-0x0D; stored as code ids 114/115 and a 8-bit variant with type 0 [VB:1435-1513]. VBA-M writes into the ROM image for addresses with top byte >= 0x08 (apply of `CHEATS_16_BIT_WRITE`/`CHEATS_32_BIT_WRITE`) [VB:1265-1280].
2. GameShark v1 nibbles 0, 1, 2 (section 2.5).
3. PAR v3 non-conditional, base `00` (section 2.6).

Width semantics at apply time: mGBA writes through `core->busWrite8/16/32`, which truncates the operand to the width [MC:73-85].

### 2.5 GameShark v1 (and original Pro Action Replay v1/v2) opcode table

Decode after decrypt (or as typed in raw mode). `type = op1 >> 28` [GS:105]. Addresses are `op1 & 0x0FFFFFFF`.

| Top nibble | VBA-M comment | mGBA behaviour | Citations |
| --- | --- | --- | --- |
| `0` | `0AAAAAAA 000000YY` 8-bit write | `CHEAT_ASSIGN` width 1, operand = whole `op2` (truncated on write) | [VB:22-23], [GS:136-141, 245-247] |
| `1` | `1AAAAAAA 0000YYYY` 16-bit write | width 2 | [VB:23], [GS:142-147] |
| `2` | `2AAAAAAA YYYYYYYY` 32-bit write | width 4 | [VB:24], [GS:148-153] |
| `3` | `30XXAAAA YYYYYYYY` "32bit Group Write, 8/16/32bit Sub/Add (depending on the XX value)" | mGBA: list write. First line sets `remaining = (op1 & 0xFFFF) - 1` and creates an entry at address `op2`, operand `op2`; each following line supplies two more target addresses (`op1`, then `op2`) written with the same operand, until `remaining` hits 0 [GS:154-161, 109-133]. VBA-M: sub-type by `(op1>>16)&0xFF`: `0x00` group write, `0x10/0x20` add/sub byte (`address & 0xFF`), `0x30/0x40` add/sub 16-bit, `0x50/0x60` 32-bit add/sub with data on the next line [VB:1930-1958]. | |
| `6` | `6AAAAAAA Z000YYYY` 16-bit ROM patch, `address >> 1`, `Z` selects the patch slot | mGBA: ROM patch at `0x08000000 | ((op1 & 0xFFFFFF) << 1)`, width 2, value `op2`; slot `Z` ignored [GS:162-169]. VBA-M: patches only when `op2 >> 24 == 0`; `address <<= 1` [VB:1960-1971] | [VB:27-28] |
| `8` | `8A1AAAAA 000000YY` 8-bit button write, `8A2AAAAA 0000YYYY` 16-bit, `8A4AAAAA` 32-bit "BUGGY, only writes 00000000", `80F00000 0000YYYY` slow motion | mGBA: bits 20-23 of `op1`: `1` = IF_BUTTON then 8-bit write at `op1 & 0x0F0FFFFF`; `2` = same with 16-bit; anything else (including 4 and F) returns false "unimplemented" [GS:170-196]. VBA-M implements 1, 2, 4 (writes constant 0 for 4) and F (slow motion) [VB:1972-1996] | [VB:29-32] |
| `D` | `DAAAAAAA 00Z0YYYY`, `Z` = 0: skip next if `!=`; 1: skip next if `==`; 2: skip next if `>` unsigned; 3: skip next if `<` unsigned | mGBA: `op1 == 0xDEADFACE` is the reseed code; otherwise 16-bit conditional, selector `op2 >> 20`: 0 EQ, 1 NE, 2 LE, 3 GE, operand `op2 & 0xFFFF`, skip count 1 [GS:197-222]. In mGBA the selector is not masked, so a value above 3 leaves `cheat->type` unset (undefined in C) [GS:203-216]; a Kotlin decoder must reject it. VBA-M: cases 0-3 as `CBA_IF_TRUE`, `CBA_IF_FALSE`, `<=`, `>=` [VB:1997-2025] | [VB:33-36] |
| `E` | `E0CCYYYY ZAAAAAAA`, `Z` as for `D`, skips `CC` lines | mGBA: always compares EQ (the `Z` nibble in `op2` is ignored), operand `op1 & 0xFFFF`, address `op2 & 0x0FFFFFFF`, skip count `(op1>>16) & 0xFF` [GS:223-231]. VBA-M honours `Z` 0-3 and skips `(op1>>16)&0xFF` entries [VB:2026-2050, apply 1245-1262] | [VB:37-40] |
| `F` | `FAAAAAAA 0000YYYY` master code | mGBA: hook at `0x08000000 | (op1 & 0x01FFFFFF)`, execution mode THUMB, only one hook per set (second returns false) [GS:232-241]. VBA-M: stores the master code address [VB:2051-2054] | [VB:41] |
| other | | mGBA returns false (unsupported) [GS:242-243] | |

ID code: `NNNNNNNN 001DC0DE`, where `NNNNNNNN` is the four-character game code. VBA-M reads the code from ROM offset 0xAC and only warns on mismatch, then stores the line as a no-op [VB:20, 1611-1626]. mGBA's `GBACheatAddGameSharkRaw` has no special case for `001DC0DE` (only the detection scorer knows it, [GS:289-291]), so an ID line is run through the normal type switch. Whether that creates a stray write depends on the game-code letters; `UNVERIFIED` because no line was executed.

Hardware-versus-emulator differences are real: the VBA-M header comment warns that `8A4AAAAA` is "BUGGY" on real AR v1.0 hardware [VB:31], mGBA skips it entirely.

Execution model in mGBA: each decoded entry is applied by `mCheatRefresh`; a conditional entry skips the next `repeat` entries when false [MC:696-794]. For GameShark conditionals the skip count is counted in decoded entries, not in text lines; button codes create two entries from one line [GS:171-181].

### 2.6 Pro Action Replay v3 / GameShark Advance v3 opcode table

Decode after decrypt with the v3 seeds. mGBA's parser is [PAR:245-334]. Ordering of checks:

1. If a ROM-patch value is pending, `op1` is the patch value [PAR:246-250].
2. If an "incomplete" entry (fill/slide/button/indirect continuation) is pending, `op1` supplies its operand and `op2` its slide parameters (see below) [PAR:251-266].
3. `op2 == 0x001DC0DE` is an ID code and is ignored [PAR:268-270].
4. `op1 == 0x00000000` goes to the "special" table (`op2` selects the function) [PAR:272-274]. `op1 == 0xDEADFACE` reseeds with v3 tables [PAR:275-278].
5. `op1 >> 24 == 0xC4` is the master code / hook [PAR:280-290].
6. Any of the condition bits `0x38000000` set means conditional [PAR:292-294]; otherwise a write family [PAR:296-334].

#### Bit layout of `op1`

| Bits | Meaning | Source |
| --- | --- | --- |
| 31-30 | action (for conditionals) or base (for writes): `00` / `40` / `80` / `C0` | [GCH:83-108, 127-132] |
| 29-27 | condition: `0` none; `1` EQ, `2` NE, `3` signed LT, `4` signed GT, `5` unsigned LT, `6` unsigned GT, `7` AND (mask `0x38000000`) | [GCH:65-74] |
| 26-25 | width: `0` 1 byte, `1` 2 bytes, `2` 4 bytes, `3` "false" (never) | [GCH:76-81], width computed at [PAR:74, 296] |
| 24 | must be 0 for assign/indirect/add; used as width bit for the `C6/C7` IO base | [PAR:326-328] |
| 23-0 | address: `_parAddr(x) = (x & 0xFFFFF) | ((x << 4) & 0x0F000000)`; i.e. low 20 bits stay, bits 23-20 move up to bits 27-24 | [PAR:51-53] |

VBA-M decodes the address as `((address & 0x00F00000) << 4) | (address & 0x0003FFFF)`, keeping only 18 low bits instead of 20 [VB:1633]. Difference noted in section 8.

Evidence from the mGBA tests that this layout is right: `00300000 00000078` writes byte 0x78 to `0x03000000`; `02300002 00005678` writes 16-bit 0x5678 to `0x03000002`; `04300004 12345678` writes 32-bit to `0x03000004` [MT:56-78].

#### Writes (no condition bits, `op1 != 0`)

| `op1 & 0xC0000000` | Entry created | Detail | Source |
| --- | --- | --- | --- |
| `0x00000000` assign | `CHEAT_ASSIGN`, width from bits 26-25 | `addressOffset = width`; for width < 4, `repeat = (op2 >> (width*8)) + 1` (high bits of `op2` are a repeat count minus one); operand = `op2` masked to width | [PAR:304-310] |
| `0x40000000` indirect | `CHEAT_ASSIGN_INDIRECT` | at apply time `address = read32(address) + addressOffset`; for width < 4, `addressOffset = (op2 >> (width*8)) * width`; operand masked | [PAR:311-316], apply [MC:675-679] |
| `0x80000000` add | `CHEAT_ADD` | read-modify-write add of the masked operand | [PAR:317-319], apply [MC:684-687] |
| `0xC0000000` other | `CHEAT_ASSIGN` to IO | `width = ((op1 >> 24) & 1) + 1`, address `GBA_BASE_IO | (op1 & OFFSET_MASK)`; so `C6xxxxxx` is a 1-byte IO write and `C7xxxxxx` a 2-byte IO write in mGBA | [PAR:320-324] |

After the switch, if bit 24 is set and the code is not in the `C6/C7` range the function returns false [PAR:326-328]; note the entry was already appended to the list at [PAR:297], so a rejected line can leave a half-initialised entry behind. `UNVERIFIED` whether `mCheatListAppend` zero-fills; treat any `false` return as "discard the whole set" in our design.

Slide / fill continuation lines ("special" `op1 == 0`, `op2` high byte `0x80/0x82/0x84`, see below): the next line is `VVVVVVVV SSTTNNNN` in the form (`op1` = value, `op2` = `OO RR NNNN`): `operandOffset = op2 >> 24`, `repeat = (op2 >> 16) & 0xFF`, `addressOffset = (op2 & 0xFFFF) * width` [PAR:251-264]. Test: lines `00000000 80300000` then `00000001 01020002` write byte 1 at `0x03000000` and byte 2 at `0x03000002` (value step 1, two repeats, stride 2 bytes) [MT:80-107]. The 16-bit and 32-bit variants are tested at [MT:109-165]. The 32-bit test asserts only the low 16 bits of each location [MT:157-162].

#### Conditionals (`op1 & 0x38000000 != 0`)

- Action bits `40000000`/`80000000`: `00` runs the next 1 entry only if true; `40` the next 2 entries; `80` starts a block that ends at an `ENDIF` special line and optionally has an `ELSE`; `C0` ("disable") is not implemented and returns false [PAR:75-105], [GCH:83-88].
- Compare type from the table above. `width == 3` (the "false" width) forces `CHEAT_NEVER` (never true) [PAR:137-140], tests [MT:1054-1179].
- Operand is `op2` masked to the width [PAR:84]. Address is `_parAddr(op1)` [PAR:82].
- Signed vs unsigned: mGBA's `LT/GT` read through `_readMem` as int32 of a zero-extended value, so for 1/2-byte widths the comparison behaves unsigned; the signed/unsigned distinction is real only for 4-byte reads. `UNVERIFIED`: code at [MC:708-737] was read, but the hardware meaning of "signed" for narrow widths was not checked.
- A new block conditional while another block is open ends the previous block first [PAR:97-104]. Block bookkeeping: `ENDIF` sets the open conditional's `repeat` to the count of entries since it (or `negativeRepeat` if an `ELSE` was seen); `ELSE` records the true-branch length [PAR:55-70]. At apply time, a true block with `negativeRepeat > 0` jumps over the else part when it reaches the else marker position [MC:785-793].

Tests that pin this behaviour (nested ifs, else, mixed one/two/block forms): [MT:167-1052] and `Never` forms [MT:1054-1179]. The listing is in section 7.

#### Special lines (`op1 == 0`): function selected by the top byte of `op2`

| `op2 & 0xFF000000` | Name | mGBA behaviour | Source |
| --- | --- | --- | --- |
| `0x00` | END | no-op, returns true | [GCH:110-111], falls through the switch at [PAR:147-243] |
| `0x08` | SLOWDOWN | unsupported, returns false | [PAR:148-151] |
| `0x10 / 0x12 / 0x14` | button write 1/2/4 bytes | appends `IF_BUTTON` then an `ASSIGN` of that width at `_parAddr(op2)`; the next line's `op1` is the value; no slide parameters follow (the pending entry is completed when the previous entry is `IF_BUTTON`) | [PAR:152-187], completion [PAR:254-259] |
| `0x18 / 0x1A / 0x1C / 0x1E` | ROM patch slot 1..4 | ROM patch address `0x08000000 | ((op2 & 0xFFFFFF) << 1)`, width 2; next line's `op1` is the 16-bit value; slot is not tracked (all patches use the same list) | [PAR:188-199, 234-241, 246-250] |
| `0x40` | ENDIF | closes the open block; returns false if no block is open | [PAR:200-205] |
| `0x60` | ELSE | marks the else boundary; false if no block | [PAR:206-211] |
| `0x80 / 0x82 / 0x84` | fill / slide 1/2/4 bytes | creates an `ASSIGN` at `_parAddr(op2)`; next line gives value and slide parameters (see above) | [PAR:212-232] |
| others | | silently ignored (switch has no default) and the function returns true | [PAR:147-243] |

The button used by "button" codes is whatever the frontend passes to `mCheatPressButton` [MC:798-800]; mGBA itself does not say which GBA key it is. `UNVERIFIED` how the app picks it.

#### Master code / hook

`op1 >> 24 == 0xC4`: hook address `0x08000000 | (op1 & (GBA_SIZE_ROM0 - 2))` (so bit 0 is cleared), THUMB mode [PAR:280-289]. Only one hook per set. VBA-M: any `(op1 >> 24) & 0xFE == 0xC4` becomes `MASTER_CODE` with address `(op1 & 0x1FFFFFF) | 0x08000000` [VB:1634-1640].

### 2.7 GameShark v3 as VBA-M decodes it (cross-check of the PAR v3 table)

VBA-M's `type = ((address >> 25) & 127) | ((address >> 17) & 0x80)` and `addr = ((address & 0x00F00000) << 4) | (address & 0x0003FFFF)` [VB:1632-1633]. The two views line up with mGBA's bit fields like this (derived by shifting: bits 31-25 of `op1` give the 7-bit group; bit 24 supplies bit 7):

| VBA-M `type` | VBA-M meaning [VB:1642-1920] | Equivalent mGBA field |
| --- | --- | --- |
| `0x00,0x01,0x02` | 8/16/32-bit fill or write (`GSA_8_BIT_FILL`, `GSA_16_BIT_FILL`, `INT_32_BIT_WRITE`) | base `00`, width 0/1/2 |
| `0x04,0x05,0x06` | if equal (8/16/32-bit), run next 1 | cond EQ, action next-1 |
| `0x07` | `GSA_ALWAYS`: unconditionally skip the next entry | cond EQ with width bits `3`, which mGBA turns into `CHEAT_NEVER`; action next-1 (derived by bit arithmetic: type 7 = cond `001`, width `11`) |
| `0x08,0x09,0x0A` | if not equal | cond NE |
| `0x0C..0x0E` / `0x10..0x12` | signed lower / signed higher | cond LT / GT |
| `0x14..0x16` / `0x18..0x1A` | unsigned lower / higher | cond ULT / UGT |
| `0x1C..0x1E` | if AND | cond AND |
| `0x20..0x22` | pointer write 8/16/32 (writes only if the pointer is inside EWRAM `0x02000000-0x0203FFFF` or IWRAM `0x03000000-0x03007FFF`) | base `40` |
| `0x24..0x27`, `0x28..0x2A`, `0x2C..0x3E` | the same conditionals with action "next 2" | action `40` |
| `0x40..0x42` | add 8/16/32 | base `80` |
| `0x44..0x5E` | the same conditionals with action "block" (VBA-M clears an `onoff` flag until a "codes on" line) | action `80` |
| `0x63` / `0xE3` | 16-bit / 32-bit IO register write | base `C0` with `C6` / `C7` |

The VBA-M `0x63/0xE3` mapping means `C6` = 16-bit IO write and `C7` = 32-bit IO write, while mGBA treats `C6` as 1 byte and `C7` as 2 bytes [PAR:321]. This is a disagreement; see section 8. VBA-M's IO write also stores `(uint8_t)` of the value into a byte index [VB:1087-1098], which looks lossy; `UNVERIFIED`.

VBA-M special lines (`address == 0`, type taken from `value`): `0x04` slowdown, `0x08/0x09/0x0A` button write 8/16/32, `0x0C..0x0F` ROM patch 1..4, `0x20` "codes on", `0x40/0x41/0x42` slide 8/16/32 [VB:1643-1686]. Their apply code for slides, including value and address strides, is at [VB:605-651]: 8-bit slide uses `count = value >> 16 & 0xFF`, value step `value >> 24`, address step `value & 0xFFFF` (times 2 or 4 for wider) which matches mGBA's `(op2 & 0xFFFF) * width`.

### 2.8 Code Breaker

#### Line format

`AAAAAAAA YYYY`: 8 hex digits, optional space(s), 4 hex digits [CB:353-368]. mGBA's `addLine` also routes 12-digit lines there in autodetect mode [GC:239-241]. VBA-M requires the exact form `XXXXXXXX YYYY` with a single space, 13 characters [VB:2450-2482]. gpSP scans for `%08x %04hx` with every non-hex char turned into a space, stride 13 characters per code [GP:254-274].

#### Encryption (mGBA)

State: `cbMaster` (the 32-bit `op1` of the seed line), `cbTable[0x30]` (a permutation of 0..0x2F), `cbSeeds[4]` and `cbRngState` [GCH:146-161].

Trigger: a line with top nibble `9` reseeds the engine [CB:302-304, enum CB_ENCRYPT = 9 at GCH:37]. Once `cbMaster` is non-zero every later line is decrypted before it is interpreted [CB:193-195]. The seed line itself is processed while `cbMaster` is still zero, so it is plaintext. VBA-M additionally requires the seed line to be the first Code Breaker line of the set: `cheatsCBAGetCount() == 0 && (address >> 28) == 9` [VB:2513] and decrypts later lines only if the first CB line began with `9` [VB:2440-2447, 2519-2520].

Reseed `_cbReseed(op1, op2)` [CB:153-187]:

```
rng = (op2 & 0xFF) ^ 0x1111
table[i] = i for i in 0..0x2F
repeat 0x50 times:
    x = swapIndex(); y = swapIndex()      # each call consumes one rand()
    swap table[x], table[y]
rng = 0x4EFAD1C3
repeat ((op1 >> 24) & 0xF) times: rng = rand()
seeds[2] = rand(); seeds[3] = rand()
rng = (op2 >> 8) ^ 0xF254
repeat (op2 >> 8) times: rng = rand()
seeds[0] = rand(); seeds[1] = rand()
master = op1
```

`rand()` [CB:74-84]: three LCG steps `s' = s * 0x41C64E6D + 0x3039` (mod 2^32): `roll`, `roll2`, `roll3`; result `((roll << 14) & 0xC0000000) | ((roll2 >> 1) & 0x3FFF8000) | ((roll3 >> 16) & 0x7FFF)`; the new state is `roll3`. VBA-M's `cheatsCBAEncWorker` is the same computation written differently [VB:2195-2205].

`swapIndex()` [CB:86-151]: draws `roll = rand()`; if `roll == 0x30` then 0; if `roll < 0x30` returns `roll`; otherwise runs a shift-and-subtract reduction ("count" starts at 0x30 and is scaled up by 16 then by 2 until it reaches `roll`, then subtracts `count`, `count>>1`, `count>>2`, `count>>3` per round, building `mask` bits) and a final correction `mask &= 0xE0000000; if (!mask || !(bit & 7)) return roll; roll += ...`. VBA-M implements the same algorithm as `cheatsCBACalcIndex` [VB:2210-2285]. It looks like a hand-rolled modulo; whether it always equals `roll % 0x30` is `UNVERIFIED` (not proven). Port it literally, never replace it with `%`.

Decrypt `_cbDecrypt(op1, op2)` [CB:26-72], with `buf[0..5]` = the 6 bytes of the line most-significant first (`op1` byte 3 down to 0, then `op2` high byte, low byte) [CB:12-24]:

```
1. for i from 0x2F down to 0:
       j = table[i]
       swap bit i of the 48-bit buffer with bit j     # bit n = (buf[n>>3] >> (n&7)) & 1
2. op1 ^= seeds[0]; op2 ^= (seeds[1] & 0xFFFF)
3. reload buf from op1/op2
   for i in 0..4: buf[i] ^= (master >> 8) ^ buf[i+1]      # uses the not-yet-modified buf[i+1]
   buf[5] ^= (master >> 8)
   for i in 5 down to 1: buf[i] ^= master ^ buf[i-1]       # uses the already-updated buf[i-1] from the ascending loop
   buf[0] ^= master
   (master is truncated to 8 bits whenever XORed into a byte)
4. op1 ^= seeds[2]; op2 ^= (seeds[3] & 0xFFFF)
```

VBA-M's `cheatsCBADecrypt` is the same sequence: bit scramble loop `count = 0x2F..0` using `cheatsCBASeedBuffer[count]` [VB:2394-2428, 2146-2161], XOR with `cheatsCBASeed[0..1]`, the rolling byte XOR with `cs` = the seed line's `op1` [VB:2410-2419], XOR with `cheatsCBASeed[2..3]`. VBA-M derives the seed array from the seed line as `array[1] = value & 0xFF`, `array[3] = (value >> 8) & 0xFF`, `array[4] = (address >> 24) & 0xF` [VB:2183-2193], consistent with mGBA's reseed.

Order of steps 3's two loops matters; take them exactly as written. The bit swap in step 1 is a plain exchange of two bits; if `i == table[i]` it is a no-op.

#### Opcode table (after decryption), `type = op1 >> 28`

| Nibble | Name [GCH:27-44] | Meaning (mGBA [CB:250-344]) | VBA-M comment [VB:45-65] and apply | gpSP [GP:82-181] |
| --- | --- | --- | --- | --- |
| `0` | `GAME_ID` | `0000AAAA 000Y` game CRC / flags. mGBA ignores it ("TODO: Run checksum") | VBA-M computes a CRC-16 over the first `0x10000` ROM bytes and warns on mismatch [VB:2535-2545]; flags `8` = CRC, `2` = DI | ignored |
| `1` | `HOOK` | master code: hook at `0x08000000 | (op1 & 0x01FFFFFF)`, THUMB; second hook rejected | master code; hardware comment "store address at ((YYYY << 0x16) + 0x08000100)" [VB:48-49] | sets `cheat_master_hook = 0x08000000 | (addr & 0x1FFFFFF)`, first one only [GP:49-69] |
| `2` | `OR_2` | `2AAAAAAA YYYY` 16-bit OR; address `op1 & 0x0FFFFFFF` | VBA-M masks the address with `0x0FFFFFFE` | 16-bit OR |
| `3` | `ASSIGN_1` | 8-bit write; operand is the 16-bit value truncated | same | 8-bit write |
| `4` | `FILL` | slide: this line `4AAAAAAA YYYY` (start address, start value), next line `XXXXCCCC IIII`: `repeat = op1 & 0xFFFF` (count), `operandOffset = op1 >> 16` (value step), `addressOffset = op2` (address step in bytes) [CB:208-246 area, 242-244] | VBA-M `count-1` + `x <= count` loop equals `count` iterations for count >= 1 [VB:770-784] | slide, aincr in bytes |
| `5` | `FILL_LIST` ("super code") | `5AAAAAAA CCCC` then lines of 3 half-words each; `remaining = CCCC` half-words; each half-word is byte-swapped so bytes land in the order typed [CB:280-286, 200-248] | `2*CCCC` bytes written; data laid out MSB first from `op1` then `op2` [VB:885-899, 2562-2566] | `value*2` bytes [GP:107-125] |
| `6` | `AND_2` | 16-bit AND | same | same |
| `7` | `IF_EQ` | if `[AAAAAAA] == YYYY` run the next entry, else skip 1 | VBA-M `CBA_IF_TRUE` | `if (read != value) i++` |
| `8` | `ASSIGN_2` | 16-bit write | same | same |
| `9` | `ENCRYPT` | reseed (above) | same; seed line must be the first | `has_encrypted_codebreaker`: any line with nibble 9 makes the whole cheat rejected [GP:36-47, 279-280] |
| `A` | `IF_NE` | execute next if `!=` | `CBA_IF_FALSE` | same |
| `B` | `IF_GT` | execute next if value `>` operand (unsigned effective) | "skip if `<=`" | skip if `<=` |
| `C` | `IF_LT` | execute next if value `<` operand | "skip if `>=`" in comment, code skips unless `<` | skip if `>=` |
| `D` | `IF_SPECIAL` | only `D0000020 YYYY` is implemented: `IF_NAND` against `KEYINPUT` (0x04000130), skip count 1 [CB:320-333]; any other `op1 & 0x0FFFFFFF` returns false | button forms `0x00`, `0x10`, `0x20` in `address & 0xF0` [VB:748-764] | forms `(address >> 4) & 0xF` = 0,1,2 [GP:148-163] |
| `E` | `ADD_2` | mGBA: 16-bit add of the operand zero-extended; address unmasked low bit | VBA-M: if address bit 0 is clear 16-bit add, else 32-bit add with the operand sign-extended `value & 0x8000 ? value | 0xFFFF0000 : value` [VB:862-870, 2597] | same split as VBA-M [GP:164-175] |
| `F` | `IF_AND` | execute next if `(value & operand) != 0` | VBA-M `GSA_16_BIT_IF_AND` | skip if `(value & operand) == 0` |

Disagreements are listed in section 8: key-mask semantics for `D`, `E` 32-bit form, `4`'s count edge cases.

Code Breaker versions: none of the three sources names version numbers (v1, v7 ...). VBA-M says it follows "the CBA clone Cheatcode S v1.1" [VB:45]. The only variation the sources model is plain versus encrypted via the `9` line. Whether real Code Breaker v1-v6 vs v7 differ in anything else is `UNVERIFIED` (no source documents it).

### 2.9 Master codes and hooks: how they behave in each core

- mGBA: a hook is a software breakpoint on the hook address; when the CPU reaches it, every set whose hook matches is refreshed, then the original opcode is run [MGBA:938-956]. Sets without a hook run once per frame at frame end [MGBA:970-982]. A hook is installed when its set is added or refreshed (`_addBreakpoint` with a reference count) [GC:16-36, 92-100, 252-257]. Hooks are THUMB mode only in the decoders (`hook->mode = MODE_THUMB`) [GS:238], [CB:260], [PAR:286].
- VBA-M: if no master code is active, `cheatsCheckKeys` runs on every LCD line; otherwise the master code address gates it [`research/sources/vbam/src/core/gba/gba.cpp:6302-6304`, `VB:570-575, 718-720`].
- gpSP: a single hook PC `cheat_master_hook`; `process_cheats` is called when the CPU PC equals it [GP:34, 184-195], callers `research/sources/gpsp/cpu.cc:1545-1546, 3111-3112` and `research/sources/gpsp/cpu_threaded.c:3220-3222, 3366-3368`; otherwise `main.c:227` calls it (condition not read, `UNVERIFIED`).
- Consequence for Pocketverse: emulating the hook is optional. If we apply cheats once per frame (what mGBA does for hook-less sets) the master code can be recorded and ignored. Games that rely on the hook to avoid being overwritten are a risk we cannot assess from the sources. Record this in `docs/KNOWN_LIMITATIONS.md`.

### 2.10 Region differences

No source contains per-region logic for GBA cheat decoding. The only game-specific coupling is the ID code: GameShark `NNNNNNNN 001DC0DE` carries the 4-char game code (`ROM[0xAC..0xAF]`), compared and warned by VBA-M [VB:1611-1626]; Code Breaker `0000AAAA 000Y` carries a CRC-16 of the first 64 KiB, compared by VBA-M [VB:2535-2545]. Codes are tied to a specific ROM revision by their addresses; nothing in the decoders can detect that.

### 2.11 What each core exposes for applying codes

#### mGBA (embedded core; also the libretro core)

- C API, `include/mgba/core/cheats.h`: `mCheatDevice` owns `mCheatSets`; each `mCheatSet` has `name`, `enabled`, a list of decoded `mCheat` entries, a list of raw `lines`, ROM patches, and virtual `addLine/refresh/...` [MCH:68-101].
- Entry types `mCheatType`: `ASSIGN, ASSIGN_INDIRECT, AND, ADD, SUB, OR, IF_EQ, IF_NE, IF_LT, IF_GT, IF_LE, IF_GE, IF_ULT, IF_UGT, IF_AND, IF_LAND, IF_NAND, IF_BUTTON, NEVER` [MCH:18-38]. `mCheat` = `{type, width, address, operand, repeat, negativeRepeat, addressOffset, operandOffset}` [MCH:40-50].
- Apply loop `mCheatRefresh` [MC:642-796]: ROM patches first, then each entry repeated `repeat` times with `address += addressOffset`, `operand += operandOffset` [MC:669-782]. Conditionals set `condition` and skip `repeat` following entries when false [MC:789-790].
- libretro entry points: `retro_cheat_reset` clears the device [MLR:1048-1050]; `retro_cheat_set(index, enabled, code)` ignores `index` and `enabled`, appends to set 0, normalises whitespace and `+` into one-space separators, and splits into lines at 13 characters (when the 14th char is space or end) or 17 characters; each line goes to `mCheatAddLine(..., 0)` = autodetect [MLR:1052-1083]. Because `enabled` is ignored, a frontend that wants to disable one cheat must reset and re-add the others, which is what RetroArch does [RA:85-117].
- Native cheat files: lines starting `#` open a named set, `!disabled` marks the next set disabled, `!<directive>` sets per-set options such as `GSAv1`, other lines are code lines [MC:266-355, 581-623]. The same loader switches to the libretro `.cht` parser when the first line starts with `cheats = ` and to the EZ Flash `.cht` parser when it starts with `[` [MC:324-336].

#### VBA-M

C-style globals: `cheatsList[MAX_CHEATS]`, `cheatsNumber` with `cheatsAddGSACode(code, desc, v3)`, `cheatsAddCBACode`, `cheatsAddCheatCode`, `cheatsEnable/Disable/Delete` [`research/sources/vbam/src/core/gba/gbaCheats.h:21-52`]. Applied by `cheatsCheckKeys(keys, extended)`; the `extended` bit 2 acts as the GameShark button [VB:733-747]. The cheat list file is a binary dump of the struct (version 1, type 0 or 1), not text [VB:2717-2858]. libretro: 12 hex digits are treated as Code Breaker (a space is inserted after the 8th digit), 16 digits as GameShark v3 (`v3=true`); anything else is "Invalid cheat code" [VBL:1897-1946]. So the VBA-M libretro core cannot take a v1 GameShark code.

#### gpSP

`cheat_parse(index, code)` accepts only unencrypted Code Breaker, up to 20 cheats of 64 lines each [GP header `research/sources/gpsp/cheats.h:21-22`]. Errors map to on-screen messages in the libretro glue [GPL:895-909]. `retro_cheat_set` with `enabled=false` calls `cheat_disable(index)` [GPL:879-892]. Not suitable as a cheat reference beyond Code Breaker plain.

---

## 3. NDS Action Replay DS

### 3.1 Honest scope statement

No source in the clones documents GameShark DS or Code Breaker DS opcodes.

- DeSmuME has a `CHEAT_TYPE_CODEBREAKER` type and a `BS`/`CB` prefix in its `.dct` file but the loader prints "Codebreaker code no supported" and skips the line [DS:1058-1067], and the apply switch for that type does nothing [DS:1173-1174]. Its own TODO says "CB not supported" [DS:1062].
- melonDS, NooDS and melonDS-Android implement Action Replay DS only. `docs/research/re/melonds.md:96` records that the installed app offers neither.
- The R4 database format (usrcheat.dat) is Action Replay oriented; its fields are described in section 3.5.

What we can honestly support: Action Replay DS codes (including "AR codes used by R4/EDGE/Acekard databases"). GameShark DS and Code Breaker DS must be documented as unsupported until a primary source is found (candidates for a later task: the GBATEK section on "DS Cart Cheat Action Replay" that DeSmuME cites in comments [DS:171-173]; not fetched here, `UNVERIFIED`).

### 3.2 Text and binary shape

A code is a list of pairs of 32-bit words `(a, b)`, printed as `AAAAAAAA BBBBBBBB`, 16 hex digits per line. melonDS stores `std::vector<u32> Code` as flat words [melonds `ARCodeFile.h:34-41`, `research/sources/melonds/src/ARCodeFile.h:34-41`]. All parsers require the total digit count to be a multiple of 16 (melonDS-Android: [MA:ui/cheats/ui/cheatform/CheatFormState.kt:179-181]; DeSmuME: [DS:1280-1282]).

### 3.3 Execution engine, melonDS (reference) with DeSmuME and NooDS cross-check

Where it runs: melonDS runs the engine from the ARM7 VBlank interrupt entry, "normally, those work by hijacking the ARM7 VBlank handler" [`research/sources/melonds/src/ARM.cpp:542-547`], in `RunCheats`, one pass per enabled code [AR:420-429]. Accesses use `ARM7Read/Write` [AR:87]. DeSmuME defaults to the ARM7 bus and offers `DFFFFFFF 99999999` / `DFFFFFFF 77777777` to switch to ARM9/ARM7 [DS:594-604]. melonDS does not implement `DF`; it hits `default` and aborts the code [AR:413-416].

State per code run [AR:46-57]: `offset` (0), `datareg` (0), `cond` (1), `condstack` (0), loop registers, `c5count`.

Instruction fetch: two words `a, b` per step [AR:65-66]; `op = a >> 24`.

Skipping rule when `cond == 0`: for ops other than `D0, D1, D2` and `C5` the instruction is skipped; for `Ex` (parameter copy) the inline data words are skipped as well: one extra pair per started 8 bytes [AR:70-82].

Opcode table (melonDS). `addr(a) = a & 0x0FFFFFFF`. "IF" pushes `cond` on the stack (`condstack = (condstack << 1) | cond`) then sets `cond`.

| Op (hex range) | Meaning | Operands | Source |
| --- | --- | --- | --- |
| `00-0F` | write 32-bit `b` to `addr(a) + offset` | | [AR:86-88] |
| `10-1F` | write 16-bit `b & 0xFFFF` to `addr(a) + offset` | | [AR:90-92] |
| `20-2F` | write 8-bit `b & 0xFF` | | [AR:94-96] |
| `30-3F` | IF `b > u32[A]` (A = `addr(a)`; if 0, A = `offset`) | | [AR:98-109] |
| `40-4F` | IF `b < u32[A]` | | [AR:111-122] |
| `50-5F` | IF `b == u32[A]` | | [AR:124-135] |
| `60-6F` | IF `b != u32[A]` | | [AR:137-148] |
| `70-7F` | IF `b.low > ((~b.high) & u16[A])` | `b.high` is a bit mask | [AR:150-163] |
| `80-8F` | IF `b.low < ((~b.high) & u16[A])` | | [AR:165-178] |
| `90-9F` | IF `b.low == ((~b.high) & u16[A])` | | [AR:180-193] |
| `A0-AF` | IF `b.low != ((~b.high) & u16[A])` | | [AR:195-208] |
| `B0-BF` | `offset = u32[addr(a) + offset]` | | [AR:210-212] |
| `C0` | FOR: loop start after this instruction, `loopcount = b`, saves `cond/condstack` | runs `b + 1` times | [AR:214-219] |
| `C4` | "offset = pointer to this opcode" | not implemented; logs and aborts this code | [AR:221-227] |
| `C5` | `c5count++`; if `cond`, IF `(c5count & b.low) == b.high` (pushes the stack) | counter never reset within one pass | [AR:229-245] |
| `C6` | `u32[b] = offset` | | [AR:247-249] |
| `D0` | ENDIF: `cond = condstack & 1; condstack >>= 1` | | [AR:251-254] |
| `D1` | NEXT: if `loopcount > 0` decrement and jump to loop start; else `cond = loopcond; condstack = loopcondstack` | | [AR:256-267] |
| `D2` | NEXT+FLUSH: loop as `D1`; on exit `offset = datareg = condstack = 0; cond = 1` | | [AR:269-282] |
| `D3` | `offset = b` | | [AR:284-286] |
| `D4` | data op on `datareg` selected by `a & 0xFF`: `00` add, `01` or, `02` and, `03` xor, `04` shift left (`b & 0xFF`, >31 gives 0), `05` shift right logical, `06` rotate right by `b & 0x1F`, `07` arithmetic shift right (>31 gives sign), `08` multiply; others log "bad AR D4 opcode" | | [AR:288-321] |
| `D5` | `datareg = b` | | [AR:323-325] |
| `D6` | `u32[b + offset] = datareg; offset += 4` | | [AR:327-330] |
| `D7` | `u16[b + offset] = datareg & 0xFFFF; offset += 2` | | [AR:332-335] |
| `D8` | `u8[b + offset] = datareg & 0xFF; offset += 1` | | [AR:337-340] |
| `D9` | `datareg = u32[b + offset]` | | [AR:342-344] |
| `DA` | `datareg = u16[b + offset]` | | [AR:346-348] |
| `DB` | `datareg = u8[b + offset]` | | [AR:350-352] |
| `DC` | `offset += b` | | [AR:354-356] |
| `E0-EF` | copy `b` inline bytes (following words) to `addr(a) + offset`: whole 8-byte groups as two 32-bit writes, then a tail of 4 and 1..3 bytes | inline data consumed from the code stream | [AR:358-387] |
| `F0-FF` | copy `b` bytes from address `offset` to `addr(a)` (32-bit chunks then bytes) | | [AR:389-411] |
| other | logs "bad AR opcode" and aborts this code | | [AR:413-416] |

DeSmuME cross-check: same operations with different bookkeeping. DeSmuME classifies `type = hi >> 28` and for `C`/`D` uses `hi >> 24` [DS:229-232]. Conditionals are `0x3-0xA` [DS:239-243]. It treats the condition register as a bit stack where bit 0 means "skip": every IF shifts the stack and pushes a skip bit even while already skipping [DS:239-244], `D0` shifts it back [DS:451-462], so nesting inside a skipped block stays balanced. Its comparison tables for 3-6 and 7-A are the same operations as melonDS [DS:301-399]. `0xB` loads offset [DS:401-409]. `C0` `iterations = lo`, runs `iterations + 1` times and also forces any earlier loop to end [DS:411-423]; `D1/D2` restore the saved status and loop back [DS:464-495]. `C4`, `C5` are no-ops (`C5` logs "Unsupported") and `C6` stores the offset [DS:425-449]. `D3-DC` as melonDS [DS:497-592]; `DB` carries a legacy bug for pre-1.54 codes (sets `offset = addr`) but the engine hard-codes `v154 = true` [DS:185, 581-583]. `E` copy: skipped as `(lo + 7) / 8` pairs when not executing [DS:247-253]; executed with inline words [DS:606-646]. `F` memory copy [DS:648-676]. The zero-address rule `if (x == 0) x = offset` for IF ops is also present (under `v154`) [DS:308].

NooDS cross-check: a simpler interpreter. It keeps a single `condFlag` meaning "skip", with no stack; `D0` simply clears it [NR:225-228], `D1` with an exhausted loop count clears it, `D2` clears and also resets `offset` and `dataReg` [NR:230-250]. So NooDS handles one level of IF only. IF conditions use the same comparisons (inverted into skip logic) [NR:142-196]. It implements `C0` (loop address), `C5` (`condFlag = (++counter & mask) != chk`), `C6`, `D3-DC`, `E` and `F` [NR:203-322], but not `D4` sub-ops other than add (`D4` is add only [NR:257-260]), so shift/rotate/multiply variants are not available there. `F` copy uses byte copies [NR:316-322].

### 3.4 Differences between the three engines that matter for us

1. Nested IFs inside a false IF. melonDS skips the nested IF without pushing it but still pops on every `D0` [AR:70-82, 251-254]; by reading the code this unbalances the stack when blocks are nested and the outer condition is false. DeSmuME keeps the stack balanced [DS:239-244]. This is by code reading, not by execution; `UNVERIFIED`. Recommendation: implement DeSmuME's rule (push on every IF, even while skipping) in our engine and test it explicitly; note the divergence in `docs/cheats/CONFORMANCE.md`.
2. Single-level IF only in NooDS (above).
3. `C5` handling differs in all three (melonDS increments the counter on every `C5` and evaluates it only while `cond` is set [AR:229-245]; NooDS also increments it in its skip path [NR:117-118] and in its handler [NR:213]; DeSmuME treats `C5` as a no-op [DS:435-441]).
4. `DF` bus switch is DeSmuME-only; `C4` is unimplemented everywhere.
5. Which CPU's bus is used: melonDS ARM7 (code comment on hijacking the ARM7 VBlank handler [`ARM.cpp:542-545`]); DeSmuME ARM7 default; NooDS passes `1` as the first argument of `memory.read/write` [NR:129]; meaning not verified, `UNVERIFIED`.

### 3.5 Master codes and ID codes (NDS)

- Real AR DS uses a master code to hook the game; melonDS and DeSmuME do not: they run all enabled codes from a fixed hook (melonDS) or on a timer-driven frame (`CHEATS::process`, `UNVERIFIED`, not read). The "master code" and "game ID" concepts therefore only exist as data in the database files (`ARDatabaseDAT.cpp` skips the nine header words that hold them: `u32 flags[9]` where `flags[0]` is item count and the next eight are "master codes - use unclear" [ADB:43-48, 194-195]).
- melonDS's DAT reader names the 8 master-code words but ignores them. The Android reader likewise skips 32 bytes ("MASTER_CODE_SIZE") [MA:common/cheats/UsrCheatDatabaseParser.kt:26, 195].
- A database entry is keyed by the cartridge game code plus a checksum (below); that is the NDS equivalent of the GBA ID code.

### 3.6 Per-region differences (NDS)

Codes are address-specific to a ROM build. Databases separate builds by `(game code, checksum)` pairs where the game code embeds the region letter (the fourth ASCII character) and the checksum is a CRC of the ROM header [ADB:33-37]. There is no region logic in the engines.

### 3.7 File formats: melonDS `.mch` (ARCodeFile)

Per-game file `<rom>.mch` [`research/sources/melonds/src/frontend/qt_sdl/EmuInstance.cpp:840`]. Text, one directive per line, `#` comments and blank lines ignored [ACF:99-100]. Keywords are matched case-insensitively at the start of a (leading-whitespace-trimmed) line [ACF:102-227]:

```
ROOT                       # switch back to root-level (no category)
CAT <0|1> <name>           # category; 1 = only one code may be enabled; plain "CAT <name>" is accepted too
DESC <text>                # description of the preceding CAT or CODE
CODE <0|1> <name>          # code; 1 = enabled
XXXXXXXX XXXXXXXX          # one data line = two words, uppercase hex when written
```

Details: `CAT` regex accepts `CAT 0 name`/`CAT 1 name` and falls back to "backwards compatibility" `CAT name` [ACF:110-134]. A `CODE` line before any category/ROOT is an error [ACF:165-170]. A data line needs two hex words (`%08X %08X`) and a started `CODE` [ACF:206-226]. Parsing aborts the whole file on the first malformed line and then `Error` is set [ACF:37-39, 129-134, 211-216]. Categories hold only codes (no nested categories) [ACF:46-53, `ARCodeFile.h`]. `OnlyOneCodeEnabled` keeps the first enabled code and disables the rest [ACF:373-397]. Save writes the same grammar, emitting `ROOT` before the first root-level code [ACF:236-293].

### 3.8 File formats: R4 `usrcheat.dat` (ARDatabaseDAT)

High-level layout (author's own comment block, [ADB:23-67]), little-endian 32-bit words:

```
0x00  "R4 CheatCode" (12 bytes) then version word 0x00000100  -> 16-byte magic compared exactly [ADB:126-133]
0x10  database description (melonDS reads 0x3C bytes) [ADB:135-137]
0x4C  encoding word set by r4cce.exe (melonDS: "???")
0x50  database active flag
0x100 entry list, 16 bytes each: { gamecode, header checksum, absolute data offset, 0 }; terminated by an all-zero entry [ADB:139-162]
```

- Header checksum: "logical NOT of CRC32 over first 512 bytes" of the ROM [ADB:35].
- Cheat data at the offset: NUL-terminated game name, pad to 4 bytes, flags word (low bits = item count, top nibble game-active), 8 master-code words (ignored), then items [ADB:41-48, 189-204]. melonDS masks the count with `0xFFFFFF` [ADB:204] although its comment says bits 0-27 [ADB:46]. The Android parser reads the low 16 bits [MA:common/cheats/UsrCheatDatabaseParser.kt:203-205 for item bytes]. `UNVERIFIED` which is right for databases with more than 65535 items; both are far above any realistic game.
- Item header word: bits 0-23 length, bit 24 flag, bit 28 type (`0` cheat, `1` category). Then NUL-terminated name, NUL-terminated description, pad to 4 bytes [ADB:49-66, 210-217]. For a cheat, length = (name+desc size rounded up to words) + 1 + code words, which melonDS validates [ADB:253-264]. Then: `codelen` word (number of code words, must be even and `< 0x100000`) followed by the code words [ADB:250-296]. A category's length field is the number of cheats inside it (1..0xFFFF) [ADB:221-232, 244].
- Android parser: magic `R4 CheatCode`, header 0x100 bytes, description at 0x10 (60 bytes), **encoding word at 0x4C**: bytes decode as `0x53D5` GBK, `0x53F5` Big5, `0x5375` Shift_JIS, `0x7355` UTF-8, else UTF-8 [MA:common/cheats/UsrCheatDatabaseParser.kt:20-26, 128-139]. It treats flags `0x1000` and `0x1100` (high half-word) as folder and folder-one-hot [MA:...:28-29, 246]. Codes are converted from little-endian to the text form `XXXXXXXX XXXXXXXX ...` with space between each 4-byte group [MA:...:249-271].
- Encrypted variants: DeSmuME also reads databases that fail the magic test by decrypting 512-byte blocks with a bit-twiddling XOR stream, `R4Decrypt` [DS:2193-2220, 2239-2285]. melonDS does not (it rejects them [ADB:128-133]). If we want to import encrypted `usrcheat.dat` we must port `R4Decrypt` (the algorithm is in the cited lines; seeds `n ^ 0x484A`, key per 512-byte block).
- DeSmuME treats an item whose high nibble is `1` as a folder (`(entryValue & 0xF0000000) == 0x10000000`) and counts entry size as `(value & 0x00FFFFFF) + 1` words [DS:1941, 1988]. Same layout, different flag decoding from melonDS's (`1<<28`).

### 3.9 File formats: the XML `codelist` used by melonDS-Android

Element grammar as parsed by `XmlCheatDatabaseSAXHandler` [MA:impl/XmlCheatDatabaseSAXHandler.kt:45-195]:

```
<codelist>
  <name>Database name (optional version in parentheses; parentheses are removed)</name>
  <game>
    <name>Game name</name>
    <gameid>XXXX CCCCCCCC</gameid>        # game code, space, header checksum (hex text)
    <folder>
      <name>Folder name</name>
      <cheat>
        <name>Cheat name</name>
        <note>Optional description</note>
        <codes>XXXXXXXX XXXXXXXX ...</codes>
      </cheat>
    </folder>
  </game>
</codelist>
```

Rules visible in the handler: the root must be `codelist` [XML:46-51]; the first `name` is the database name [XML:53-56, 112-117]; per game, the first `name` is the game name and `gameid` is split on a single space into code and checksum [XML:65-73, 127-132]; a `cheat` only exists inside a `folder` [XML:85-88]; empty `note` is dropped [XML:144-150]; folders without cheats and games without folders are dropped [XML:173-193]. Nested folders are not supported (a `folder` flag is not re-entrant [XML:75-78]). Cheats come in disabled (`enabled = false`) [XML:166].

Runtime hand-off: each `Cheat.code` string is split on single spaces in JNI; every part must be exactly 8 hex characters or the whole cheat is discarded [`research/sources/melonds-android/app/src/main/cpp/MelonDSAndroidJNI.cpp:95-135`]. The editing UI inserts a newline after every 16 hex characters [MA:ui/cheats/ui/cheatform/CheatFormState.kt:125-146] and validates only that the digit count is a multiple of 16 [MA:...:179-181]. How `\n` becomes a space before the JNI call was not found; `UNVERIFIED`, check `CheatsViewModel` and the repository before copying the code string handling.

Android's model: `Cheat(id, cheatDatabaseId, name, description, code, enabled)`, `CheatFolder(id, name, cheats)`, `Game(id, name, gameCode, gameChecksum, cheats)` [MA:domain/model/Cheat.kt:3-10, CheatFolder.kt:3, Game.kt:3]. Import from files goes through `XmlCheatDatabaseParser` or `UsrCheatDatabaseParser` [MA:common/cheats/XmlCheatDatabaseParser.kt:8-31, UsrCheatDatabaseParser.kt:44]. No sample XML file exists in the clone, so the exact text layout of `<codes>` (spaces vs newlines) is `UNVERIFIED`; our importer must accept any run of whitespace between words.

### 3.10 File formats: others seen

- DeSmuME `.dct` (text): header comment, `Name=`, `Serial=`, then lines `AR 1 XXXXXXXXYYYYYYYY,XXXXXXXXYYYYYYYY ;description` where the type token is `DS` (internal), `AR` or `CB` (saved) / `BS` (read); `0/1` is the enabled flag; code pairs have no space inside and are comma separated; description after `;` [DS:958-1000, 1006-1130]. Load strips every non-hex character from the code part and requires `length % 16 == 0` [DS:1069-1078]; code count max `MAX_XX_CODE = 1024` pairs [`research/sources/desmume/desmume/src/cheatSystem.h:30`]. `DS` internal type: the top nibble of the address field holds the write size minus one (0..3) [DS:1119-1120, 983-989]. Typing leniency: the letter `O` is read as `0` because "100% of pokemon codes were typed with the letter O" [DS:1261-1270].
- NooDS `.cht` (text, **not** the libretro .cht): `[Name]+` or `[Name]-` then lines `XXXXXXXX XXXXXXXX`, blank line ends the cheat [NR:41-93]; extension chosen at `research/sources/noods/src/core/memory/cartridge.cpp:38`.

---

## 4. Container formats to import and export

| Format | Platform | Parse/write evidence | Notes |
| --- | --- | --- | --- |
| Plain text list | both | mGBA native `.cheats` [MC:266-355, 581-623]; DeSmuME-style stripped hex [DS:1259-1282] | See below |
| libretro `.cht` | both | mGBA reader [MC:357-461]; RetroArch writer/reader [RA:140-285, 455-540] | See below |
| EZ Flash `.cht` | GBA | mGBA reader [MC:463-579] | Chinese-encoded, byte-level pokes (below) |
| melonDS `.mch` | NDS | section 3.7 | |
| melonDS-Android XML `codelist` | NDS | section 3.9 | |
| R4 `usrcheat.dat` | NDS | sections 3.8 | |
| DeSmuME `.dct` | NDS | section 3.10 | |
| NooDS `.cht` | NDS | section 3.10 | name clash with libretro `.cht` |
| VBA-M binary cheat list | GBA | [VB:2717-2858] | binary struct dump, do not import |

### 4.1 libretro `.cht`

Key/value config file (RetroArch's `config_file` syntax: `key = "value"`). RetroArch writes [RA:140-285]:

```
cheats = N
cheat0_desc = "..."
cheat0_code = "..."           # code text handed to the core; falls back to code when desc is empty
cheat0_enable = false|true
cheat0_big_endian = false|true
cheat0_handler = 0|1                     # 0 = emulator (core handles code), 1 = RetroArch memory handler
cheat0_memory_search_size = 3
cheat0_cheat_type = 0..7                 # DISABLED, SET_TO_VALUE, INCREASE, DECREASE, RUN_NEXT_IF_EQ/NEQ/LT/GT
cheat0_value, cheat0_address, cheat0_address_bit_position
cheat0_rumble_* (type, value, port, primary/secondary strength/duration)
cheat0_repeat_count, cheat0_repeat_add_to_value, cheat0_repeat_add_to_address
```

Keys and their source [RA:151-172], load switch [RA:473-533], enums [`research/sources/retroarch/cheat_manager.h:26-45`]. The loader has two passes: first it reads `cheats` (the count), then every `cheat<i>_<name>` [RA:455-470, 474-537]. `cheat_manager_save` writes the `.cht` path (a `.cht` is appended when saving by database name) [RA:187-191]. At runtime RetroArch resets the core and then calls `retro_cheat_set(index++, true, code)` for each enabled cheat with handler EMU and non-empty code [RA:85-107]. Multi-line codes are joined in the `code` string with `+` between lines; mGBA's `.cht` reader splits on `+` [MC:443-453]; the libretro entry point of mGBA also accepts spaces and `+` [MLR:1069-1075].

mGBA's reader is stricter than RetroArch's: every line must start with `cheat`, otherwise parsing fails; `cheats` is read once; `desc`, `enable` and `code` are the only fields used [MC:372-458]. `enable` is recognised only if the value is exactly `true` followed by a newline [MC:441-442]. Quoted strings are unquoted through `parseQuotedString` [MC:438-445]. Max 1000 cheats [MC:13, 400-402]. A line longer than 512 bytes is truncated by the 512 `MAX_LINE_LENGTH` buffer [MC:12, 358].

Mapping of a libretro entry to our canonical model: `desc` -> name, `code` -> raw lines (split on `+`), `enable` -> enabled. Everything else (`handler`, address/value pairs, rumble) belongs to RetroArch's own memory-poking cheats and can be preserved verbatim in a side blob.

### 4.2 EZ Flash `.cht` (GBA)

mGBA detects it by a first line starting with `[` [MC:331-335]. Sections `[Cheat name]`, then lines `OnOff=addr,val,val,...;addr,val;...` with hex numbers, ending at a `[GameInfo]` section; names are GBK-encoded and converted [MC:463-579]. Addresses below 0x40000 are EWRAM (`+ 0x02000000`), otherwise IWRAM (`+ 0x03000000 - 0x40000`) [MC:535-539]. Each value is emitted as an internal VBA-style line `%08X:%02X` [MC:544-545, 554-555]. This is import-only for us (low value); document and skip unless asked.

### 4.3 Plain text lists

No standard; real-world files have one code per line grouped under a title line. Our importer should accept:

- mGBA native: `# title` lines, `!disabled`, `!GSAv1`-style directives [MC:288-322]. Round-trippable via `mCheatSaveFile` [MC:581-623].
- A block per cheat: any title line (anything that is not code-shaped), then code lines. Title detection by exclusion (section 6).
- melonDS `.mch` text [section 3.7] and NooDS `[Name]+` text [section 3.10].

### 4.4 `usrcheat.dat` at a glance

Fixed 0x100-byte header, a game table (gamecode, ROM-header checksum, absolute offset), and per-game blobs containing a name, flags, categories and cheats, each with 4-byte aligned strings; see 3.8 for the field list. Import-only for us: we have no use for writing it.

---

## 5. Canonical cheat model (recommendation)

Goal: one model that holds the user's raw text unchanged, the decoded operations when decoding succeeded, and enough metadata to re-encode or export. Kotlin sketch (names are ours):

```kotlin
data class CheatEntry(
    val id: String,                // stable uuid
    val name: String,
    val platform: Platform,        // GBA, NDS, RPGMAKER..., RENPY (engine-native lives elsewhere)
    val sourceFormat: CodeFormat,  // see enum below
    val rawLines: List<String>,    // exactly what the user pasted/imported, trimmed
    val ops: List<CheatOp>?,       // null when decode failed; see decodeError
    val decodeError: DecodeIssue?, // line index + plain-language reason
    val enabled: Boolean,
    val notes: String,
    val origin: Origin,            // USER_ENTERED | IMPORTED(file name, importer id)
    val folder: String?,           // grouping (NDS databases, melonDS categories)
    val onlyOneInFolder: Boolean,  // melonDS category flag, UI only
    val gameKey: GameKey?,         // GBA: header game code (+ CRC16-first-64KiB); NDS: game code + header checksum
    val formatDirectives: Map<String,String> // e.g. "cipher" -> "GSAv1" | "PARv3" | "raw"
)

enum class CodeFormat {
    GBA_RAW_VBA, GBA_GAMESHARK_V1, GBA_GAMESHARK_V1_RAW, GBA_PAR_V3, GBA_PAR_V3_RAW,
    GBA_CODEBREAKER, GBA_CODEBREAKER_ENCRYPTED, NDS_ACTION_REPLAY, UNKNOWN
}
```

Keep `rawLines` as the source of truth: CLAUDE.md requires "untouched originals" in the game's `cheats/` folder (section 8.2). Store the decoded `ops` as a cache, recomputable.

Decoded operation vocabulary. It must cover every opcode found above; GBA codes are an ordered entry list with skip counts, NDS codes a small register machine. Use one sealed hierarchy; the executor is per-platform.

Common types: `Width { B1, B2, B4 }`, `Addr = UInt`.

Memory writes
- `Write(width, addr, value)` (GS 0/1/2; PAR assign; CB 3/8; VBA-line; NDS 0x/1x/2x with `+offset` flag)
- `Fill(width, addr, value, count, addrStride, valueStride)` (PAR slide/fill; CB 4; GSA v3 slide 40/41/42)
- `WriteBytes(addr, bytes)` (CB 5 super; NDS Ex)
- `WriteList(width, value, addrs)` (GS 3)
- `PointerWrite(width, ptrAddr, offset, value, ptrRange)` (PAR 40; GSA v3 20-22; mGBA indirect)
- `Rmw(op: AND|OR|ADD|SUB|XOR, width, addr, operand)` (PAR add 80; CB 2/6/E; GS 3x add/sub)
- `RomPatch(slot, addr, value16)` (GS 6; PAR 18-1E; VBA-line in ROM range)
- `IoWrite(width, ioOffset, value)` (PAR C6/C7; GSA v3 63/E3). Width semantics disputed (section 8).
- `CopyMem(src, dst, len)` (NDS Fx)

Control flow, GBA style (entry-count)
- `If(cmp, width, addr, operand, mask?, scope)` where `cmp ∈ EQ NE LT GT LE GE (signed or unsigned) AND NAND LAND NEVER ALWAYS BUTTON` and `scope = SkipNext(n) | Block` (PAR block/else/endif; GS D/E; CB 7/A/B/C/F/D; GSA v3 conditions)
- `Else`, `EndIf`
- `IfRange(cmp, width, addr, operand, lines)` (GS E)
- `ButtonWrite(width, addr, value)` (GS 8; PAR 10-14; GSA v3)
- `Slowdown(value)` (GS 8F; PAR 08; GSA v3 04), decoded but flagged unsupported at apply time

Control flow, NDS style (stack machine)
- `IfNds(cmp, a, b, useMask, useOffsetWhenZero)` (ops 3x-Ax)
- `EndIf` (D0), `For(count)` (C0), `Next` (D1), `NextFlush` (D2)
- `SetOffset(value)` D3, `AddOffset(value)` DC, `LoadOffset(addr)` Bx, `StoreOffset(addr)` C6
- `SetData(value)` D5, `DataOp(kind, value)` D4 (add or xor shl shr ror asr mul), `LoadData(width, addr)` D9-DB, `StoreData(width, addr, autoInc)` D6-D8
- `IfCounter(mask, expected)` C5
- `SwitchBus(arm9)` DF (DeSmuME only)
- `Unsupported(rawA, rawB, reason)` C4 and unknown opcodes

Housekeeping ops (decoder output, not executed by the apply loop)
- `Hook(addr, mode)` (GS F; PAR C4; CB 1)
- `IdCode(gameCode: String?, crc16: Int?)` (GS ..001DC0DE; CB 0)
- `SetCipher(kind, params)` (GS/PAR DEADFACE; CB 9). Decoder consumes these; they need not reach the executor.

Executor contract (in `engine-api`): `applyFrame(ops, memoryAccess, buttons)`; a core can implement it itself (libretro cheat API: pass raw lines) or let our executor poke memory through `engine-api`. mGBA and gpSP take raw lines through `retro_cheat_set` [MLR:1052-1109], [GPL:879-910]; melonDS in libretro/standalone has its own engine. Because the libretro cores re-decode the raw text, the safest implementation order is: (1) pass raw lines to the core when the core accepts the format; (2) use our own decoder only for validation, UI display and cores without a cheat API.

Per-platform entry-list invariant: skip counts count decoded entries, not text lines. Mirror mGBA's entry model for GBA so the block/else tests can be reused as behaviour specs.

---

## 6. Auto-detection of pasted text

Normalisation first:

1. Split on newlines and on `+` (libretro joins lines with `+` [MC:447-452], [MLR:1070]). Trim. Drop empty lines.
2. Treat a line as a title if it is not code-shaped (below). Collect consecutive code lines under the previous title, or into an unnamed cheat.
3. Remove a trailing description after `;` when present (DeSmuME `.dct` style [DS:1069-1070]).
4. For lines without separators, strip all characters that are not `0-9A-Fa-f` and re-chunk; NDS and GBA 16-digit codes are fixed width. Warn when the letter `O` or `o` is found and offer to read it as zero (DeSmuME does this silently [DS:1261-1270]).

Per-line shapes (after normalisation, ignoring case):

| Regex (hex = `[0-9A-Fa-f]`) | Interpretation | Notes |
| --- | --- | --- |
| `^hex{8}:hex{2}$`, `:hex{4}$`, `:hex{8}$` | GBA raw VBA | mGBA also accepts 3-4 byte forms made of 1, 2 or 4 bytes; 3 bytes rejected [GC:179-181] |
| `^hex{8}[ \t]*hex{4}$` | GBA Code Breaker (12 digits) | mGBA decides on 4 digits then end/space [GC:239-241] |
| `^hex{8}[ \t]*hex{8}$` | 16-digit family | GBA: GSA v1, GSA v3/PAR v3, each encrypted or raw; NDS: Action Replay |
| `^hex{16}$` | same as above without separator | accepted by mGBA [GC:242-249] |
| `^hex{8} hex{4} hex{4}$` | 16-digit family split | mGBA's read path |
| anything else | title or error | |

Platform known (from the imported ROM) removes most ambiguity: NDS lines are always Action Replay, 16 digits; a 12-digit line on an NDS game is an error.

GBA 16-digit disambiguation, in order of confidence:

1. A first-nibble check is weak. After correct decryption, GameShark v1 raw and PAR v3 raw codes have different shapes: v1 uses nibbles `0,1,2,3,6,8,D,E,F` [GS:105, GCH:46-56]; v3 uses the layout in section 2.6 where bits 31-30 are `00/40/80/C0`. For example `0E300000 00000000` is valid only as PAR v3. Many lines are valid in both.
2. If the second word is `001DC0DE` the line is an ID code in either family; the first word is a 4-letter ASCII-like game code (strong signal, compare with the ROM header at `0xAC`).
3. If `op1 == DEADFACE` (raw) the set is raw encrypted-capable; it reseeds.
4. Otherwise run mGBA's four-candidate scorer (section 2.2) on the first line of the set, giving 100% weight to an ID/DEADFACE hit. mGBA does this on the first line only; we should score the first up to 3 non-ID lines and sum, since a single line is ambiguous. This is our design suggestion; it is not in any source.
5. Always allow the user to override (UI: format dropdown with "Auto-detect" first), and persist the choice as `formatDirectives["cipher"]`, which mirrors mGBA's `!GSAv1`/`!PARv3` directives [GC:280-333].

Genuine ambiguities that need manual override:

- Encrypted GSA v1 versus encrypted PAR v3: a wrong guess decrypts to noise. Plausibility scoring helps but is not proof.
- Raw GSA v1 versus raw PAR v3: some lines are legal in both with different meaning (e.g. a line with top nibble `0..2` in v1 is a write; in v3 the same first word means an assign of whatever the width bits say).
- 12-digit codes on GBA: Code Breaker plain or encrypted. Encrypted ones look like noise; the first line of an encrypted set starts with `9`. If a set contains a line beginning with `9`, treat the set as encrypted CB (mGBA/VBA-M) and refuse on gpSP.
- Any NDS line that starts with `9` etc is simply AR; but 16 digits can also be a GBA 16-digit code. Use the ROM platform.
- VBA-M versus mGBA: VBA-M's libretro layer decides GBA type purely by digit count (12 = CB, 16 = GSA v3) and cannot take GSA v1 [VBL:1897-1946]. If our core is VBA-M, forbid v1.

---

## 7. Test-vector policy

### 7.1 What exists upstream

| Repo | Test or vector files for cheats found | Citation |
| --- | --- | --- |
| mGBA | `src/gba/test/cheats.c`: 25 cmocka tests, all for **Pro Action Replay v3 raw** (`GBACheatSetGameSharkVersion(..., GBA_GS_PARV3_RAW)`), driving a real GBA core and asserting memory contents. Names: `createSet` [MT:35], `addRawPARv3` [MT:44], `doPARv3Assign` [MT:56], `doPARv3Slide1/2/4` [MT:80, 109, 138], `doPARv3If1` [MT:167], `If1x1` [MT:196], `If2` [MT:251], `If2x2` [MT:285], `If2Contain1` [MT:353], `IfX` [MT:400], `IfXxX` [MT:429], `IfXElse` [MT:492], `IfXElsexX` [MT:527], `IfXElsexXElse` [MT:598], `IfXContain1` [MT:676], `IfXContain1Else` [MT:738], `IfXElseContain1` [MT:808], `IfXContain1ElseContain1` [MT:878], `IfButton` [MT:1024], `Never1` [MT:1054], `Never2` [MT:1083], `NeverX` [MT:1117], `NeverXElse` [MT:1146]; suite list [MT:1181-1206]. No GameShark v1, no Code Breaker, no encrypted-code test. `addRawPARv3` also asserts that `43000000 00000000` is rejected [MT:51-52]. |
| mGBA | `src/core/test/*.c`, `src/gba/test/core.c` exist but do not test cheats (file listing during this task; contents not read). |
| VBA-M | No GBA cheat tests. The only cheat test is `src/core/test/gb_cheats_save_state_test.cpp` (Game Boy, not GBA). |
| gpSP | `tests/` holds dynarec generator sources, no cheat tests. |
| melonDS | no AR tests (test dirs are `gdb_test`, `libslirp/test`, `teakra`). |
| DeSmuME | no cheat tests; `tools/ds_tests` exists, contents not inspected. |
| NooDS | no tests. |
| melonDS-Android | `app/src/test/java/me/magnum/melonds/VersionTest.kt` only; no cheat tests. |
| RetroArch | `samples/cheats/cheat_regions/cheat_regions_test.c` (209 lines) and `samples/cheats/cheat_search/cheat_search_test.c` (328 lines): about RetroArch's own memory search and regions, not code decoding. No sample `.cht`. |

So there are **no published encrypted-code vectors** in any clone. Expected values for GameShark v1, PAR v3 encrypted and Code Breaker encrypted must be generated by us.

### 7.2 Licensing of what we may reuse

From `docs/licenses/COMPATIBILITY_MATRIX.md:9-17` and `:5`:

- mGBA tests are MPL-2.0 files (header at [MT:1-5]). The inputs and expected memory values are facts about a behaviour, but the file as a whole is MPL code. Policy: do not copy `cheats.c`. Re-express each scenario in Kotlin as our own test (our own data layout, our own assertions), citing mGBA in a comment as the origin of the scenario. If a verbatim copy ever becomes necessary, keep it in a separate file with the MPL header intact and list it in `docs/licenses/THIRD_PARTY.md` (MPL is file-level; compatible with our GPLv3 per the matrix, `COMPATIBILITY_MATRIX.md:9`).
- VBA-M tables and logic are GPL-2.0, "or later" not confirmed (`COMPATIBILITY_MATRIX.md:11`). Do not copy VBA-M code; we use it only to confirm behaviour.
- The two 256-byte table pairs (v1 and v3) are data inside mGBA files. They are needed by any decoder. Because they are identical in mGBA (MPL-2.0) and VBA-M (GPLv2), take them from mGBA under MPL, keep the attribution, and test by hash. Record this decision in `docs/licenses/THIRD_PARTY.md` before integration. `UNVERIFIED`: whether a table of constants is copyrightable; that is a legal question for a licence review.
- melonDS, DeSmuME, NooDS, RetroArch are GPL-3.0 / GPL-2.0-or-later; reading them is fine, copying code is allowed under our GPLv3 but not planned.
- No PDF vectors: the Action Replay guide under `DemoGames/` is not used without the owner's approval (`docs/research/re/melonds.md:97`).

### 7.3 What we must generate ourselves

1. **Cipher round trips (no external vector needed).** For random plaintext pairs and each seed set, `decrypt(encrypt(x)) == x`. mGBA exposes `GBACheatEncryptGameShark` [GS:54-63]; our Kotlin must match it. Run mGBA's reference functions on our own plaintext examples (write a tiny C driver outside the repo's tracked tree, compile against the cloned `gameshark.c`/`parv3.c`) and store `(plain, cipher)` pairs as our fixtures.
2. **Seed tables.** Test: SHA-256 of the 256-byte arrays equals a pinned hash; initial seeds equal `reseed(0)` [section 2.1 hand check].
3. **Reseed.** Pairs `(params, resulting seeds)` produced by running mGBA `GBACheatReseedGameShark`.
4. **Code Breaker.** For several seed lines, record `cbTable`, `cbSeeds`, then `(cipher, plain)` for each following line, all produced by running the mGBA reference `_cbReseed`/`_cbDecrypt`. Also test the `swapIndex` reduction against the reference over a large set of `roll` values (it may not equal `% 0x30`, section 2.8).
5. **Decoder tests (plain text to ops).** Hand-written tables per opcode from sections 2.5, 2.6, 2.8: one line in, one expected op out. Where the expected value comes from reading the source, label the test with the line citation.
6. **Behaviour tests.** The 25 PARv3 scenarios, re-expressed over a fake memory array. Add GameShark v1 and Code Breaker scenarios by running mGBA and recording memory results.
7. **NDS AR.** A fake bus with a few memory words; test each opcode in section 3.3 on small inputs, plus nested-IF and loop cases. Expected values to be produced by running melonDS's `RunCheat` on the same input (it is C++; a headless harness is possible) and, for nested IF, by DeSmuME as a second opinion; record disagreements in `CONFORMANCE.md`.
8. **Files.** Our own small `.mch`, `usrcheat.dat`, XML `codelist`, libretro `.cht` and NooDS `.cht` samples written by hand to exercise every grammar rule in sections 3.7-3.10 and 4.1. Binary `usrcheat.dat` fixtures are produced by a script in `tools/`, not by hand.

Hand-computed values anywhere in these tests must carry the label `HAND-COMPUTED, VERIFY AGAINST mGBA` (or `...melonDS`) until a reference run replaces them. The only hand-computed values in this document are the two first seeds in section 2.1.

---

## 8. Gaps and honest limitations (feeds `docs/KNOWN_LIMITATIONS.md`)

Verified by reading but disagreeing between implementations (we must choose and document):

1. IO register writes `C6/C7` (PAR v3 / GSA v3): mGBA 1 byte and 2 bytes [PAR:321], VBA-M 16 bit and 32 bit [VB:1912-1917]. `UNVERIFIED` which matches hardware.
2. Button conditions in Code Breaker `D` and GameShark button codes: mGBA tests `KEYINPUT & mask == 0` (all masked keys down) for `D0000020` [CB:320-329]; VBA-M and gpSP test `(keys & value) == 0` against active-high key bits and skip [VB:751-754], [GP:158-161]. The two read as "all pressed" versus "any pressed"; semantics differ and the sources do not say which is hardware-correct.
3. Group write (GS nibble 3): mGBA writes `op2` to a list of addresses where the first address is also `op2` [GS:154-161]; VBA-M's loop count is `(op1 & 0xFFFE) + 1` [VB:1210-1222] versus mGBA's `(op1 & 0xFFFF) - 1` remaining. For count N they produce different numbers of writes. `UNVERIFIED` which (if either) matches a real GameShark.
4. GS `E` range-if: mGBA only does equality and ignores the `Z` selector [GS:223-231]; VBA-M honours `Z` 0-3.
5. CB `E` (add): mGBA is always a 16-bit add of a zero-extended operand [CB:334-338]; VBA-M and gpSP switch to 32-bit with a sign-extended operand when the address is odd [VB:862-870], [GP:164-175].
6. PAR v3 address mask: 20 bits (mGBA) versus 18 bits (VBA-M) [PAR:52], [VB:1633].
7. mGBA GameShark ID line `001DC0DE` is not skipped in `GBACheatAddGameSharkRaw` [GS:135-244] but is ignored by PAR [PAR:268-270] and VBA-M [VB:1611-1626].
8. NDS nested IF inside a false IF: melonDS versus DeSmuME (section 3.4).
9. NDS `C5` counter and `C4` rewrite code.

Not verifiable from the sources read:

- Real hardware behaviour of any code. Sources are emulators; their own comments cite no primary hardware spec for GBA. VBA-M's header comment is the only prose description [VB:17-66].
- Code Breaker version differences (v1 to v7) and whether `D` forms other than `0x00/0x10/0x20` exist (both mGBA and VBA-M reject or ignore them).
- GBATEK's AR DS document, which DeSmuME cites [DS:171-173], was not fetched.
- GameShark DS and Code Breaker DS formats: no source.
- Slowdown codes (GS `80F00000`, PAR `08`, GSA v3 `04`): mGBA does not implement them [PAR:148-151], [GS:170-196]; VBA-M does [VB:593-606].
- PAR "disable-type" conditionals (`action C0`) are unimplemented in mGBA [PAR:75-79].
- `retro_cheat_set` from mGBA ignores `index` and `enabled` [MLR:1052-1054]; the libretro frontend must reset and re-add on every change.
- Behaviour of cheats at the very first frame, with or without master code, on real games.
- The XML `<codes>` text layout (spaces vs newlines) and the newline-to-space conversion before JNI in melonDS-Android.
- How `GBA_BASE_*`/`OFFSET_MASK` constants resolve (headers under `include/mgba/internal/gba/gba.h` were not read).
- The `mCheatListAppend` zero-fill assumption (section 2.6).
- `swapIndex`/`CalcIndex` equivalence with `%` (section 2.8).
- Seeds 1-3 of the initial v1 and v3 seed sets were not hand-checked (section 2.1).
- No reference binary was run; nothing in this note has been executed.
- Legal status of reusing the cipher tables (section 7.2).

Feature support we can honestly promise after implementing this note:

| Format | Parse | Decode | Apply (our executor) | Notes |
| --- | --- | --- | --- | --- |
| GBA raw VBA | yes | yes | yes | |
| GameShark v1 (plain + encrypted) | yes | yes | most opcodes; slowdown, 32-bit button write (bugged on hardware) unsupported | choose a policy for section 8 items 3, 4 |
| PAR v3 / GSA v3 (plain + encrypted) | yes | yes | all except slowdown and disable-action conditionals | choose a policy for item 1 |
| Code Breaker (plain + encrypted) | yes | yes | all except button forms other than `D0000020` pending item 2 | game-ID CRC check optional |
| NDS Action Replay | yes | yes | all except `C4` | `DF` if we want DeSmuME compatibility |
| NDS GameShark / Code Breaker | no | no | no | no source |

---

## 9. Source index (every file read)

Aliases used in citations and the full repo-relative paths.

| Alias | Path | Lines read | Purpose |
| --- | --- | --- | --- |
| GS | `research/sources/mgba/src/gba/cheats/gameshark.c` | 1-346 (all) | GameShark v1, TEA cipher, reseed |
| GSH | `research/sources/mgba/src/gba/cheats/gameshark.h` | 1-30 | version enum, prototypes |
| CB | `research/sources/mgba/src/gba/cheats/codebreaker.c` | 1-369 (all) | Code Breaker |
| PAR | `research/sources/mgba/src/gba/cheats/parv3.c` | 1-443 (all) | PAR v3 |
| (parv3.h) | `research/sources/mgba/src/gba/cheats/parv3.h` | 1-22 | prototypes, seeds extern |
| GC | `research/sources/mgba/src/gba/cheats.c` | 1-387 (all) | GBA set glue, auto-detect, VBA line |
| GCH | `research/sources/mgba/include/mgba/internal/gba/cheats.h` | 1-183 (all) | enums, struct |
| MC | `research/sources/mgba/src/core/cheats.c` | 1-824 (all) | generic engine and file readers |
| MCH | `research/sources/mgba/include/mgba/core/cheats.h` | 1-134 (all) | public cheat API |
| MLR | `research/sources/mgba/src/platform/libretro/libretro.c` | 1040-1119 | libretro cheat entry |
| MGBA | `research/sources/mgba/src/gba/gba.c` | 930-982 | hook and frame-end refresh |
| MT | `research/sources/mgba/src/gba/test/cheats.c` | 1-788, 1024-1207 | cheat tests |
| (string.c) | `research/sources/mgba/src/util/string.c` | 500-600 | hex parsing |
| VB | `research/sources/vbam/src/core/gba/gbaCheats.cpp` | 1-210, 286-700, 700-860, 885-960, 1021-1030, 1150-1290, 1288-1335, 1435-1514, 1515-2135, 2136-2616, 2717-2870 | VBA-M cheats |
| (gbaCheats.h) | `research/sources/vbam/src/core/gba/gbaCheats.h` | 1-52 | API |
| VBL | `research/sources/vbam/src/libretro/libretro.cpp` | 1876-1960 | libretro cheat entry |
| (gba.cpp) | `research/sources/vbam/src/core/gba/gba.cpp` | 6296-6308 | apply call site |
| GP | `research/sources/gpsp/cheats.c` | 19-291 | gpSP cheats |
| (cheats.h) | `research/sources/gpsp/cheats.h` | 1-42 | limits, errors |
| GPL | `research/sources/gpsp/libretro/libretro.c` | 874-915 | libretro cheat entry |
| AR | `research/sources/melonds/src/AREngine.cpp` | 18-431 (all) | NDS AR engine |
| (AREngine.h) | `research/sources/melonds/src/AREngine.h` | 20-43 | engine class |
| ACF | `research/sources/melonds/src/ARCodeFile.cpp` | 18-397 | `.mch` reader/writer |
| (ARCodeFile.h) | `research/sources/melonds/src/ARCodeFile.h` | 20-83 | model |
| ADB | `research/sources/melonds/src/ARDatabaseDAT.cpp` | 18-373 | usrcheat.dat reader |
| (ARDatabaseDAT.h) | `research/sources/melonds/src/ARDatabaseDAT.h` | 25-74 | model |
| (ARM.cpp) | `research/sources/melonds/src/ARM.cpp` | 535-552 | where cheats run |
| (EmuInstance.cpp) | `research/sources/melonds/src/frontend/qt_sdl/EmuInstance.cpp` | 825-852 | `.mch` loading |
| DS | `research/sources/desmume/desmume/src/cheatSystem.cpp` | 150-690, 692-900, 958-1130, 1160-1200, 1250-1312, 1840-1990, 2085-2290 | DeSmuME AR, `.dct`, R4 DB |
| (cheatSystem.h) | `research/sources/desmume/desmume/src/cheatSystem.h` | 30-62, 178-179 (grep) | limits, constants |
| NR | `research/sources/noods/src/core/hle/action_replay.cpp` | 18-332 (all) | NooDS AR |
| (cartridge.cpp) | `research/sources/noods/src/core/memory/cartridge.cpp` | 33-53 (grep + view) | `.cht` path |
| XML | `research/sources/melonds-android/app/src/main/java/me/magnum/melonds/impl/XmlCheatDatabaseSAXHandler.kt` | 1-218 (all) | XML parser |
| MA | `research/sources/melonds-android/app/src/main/java/me/magnum/melonds/` | `domain/model/Cheat.kt`, `CheatFolder.kt`, `Game.kt`, `common/cheats/XmlCheatDatabaseParser.kt`, `common/cheats/UsrCheatDatabaseParser.kt` (1-330), `ui/cheats/ui/cheatform/CheatFormState.kt` (125-181), `MelonEmulator.kt` (grep) | Android model, usr parser, form |
| (JNI) | `research/sources/melonds-android/app/src/main/cpp/MelonDSAndroidJNI.cpp` | 75-150 | code string hand-off |
| (lib AR) | `research/sources/melonds-android/melonDS-android-lib/src/AREngine.cpp`, `ARCodeFile.cpp` | diff vs upstream | identical apart from year |
| RA | `research/sources/retroarch/cheat_manager.c` | 85-285, 455-540 (and greps) | `.cht` read/write |
| (RA h) | `research/sources/retroarch/cheat_manager.h` | 26-60 | enums |
| (RA tests) | `research/sources/retroarch/samples/cheats/*` | headers only | tests of search/regions |
| prior | `docs/research/re/john-gba-lite.md` (61, 98, 108-116); `docs/research/re/melonds.md` (91-98); `docs/research/re/joiplay-rpgm-plugin.md` (71-84); `docs/research/OPEN_SOURCE_ALTERNATIVES.md` (9-17, 29-30, 59); `docs/licenses/COMPATIBILITY_MATRIX.md` (5-17); `CLAUDE.md` section 8 | | project requirements and licences |
