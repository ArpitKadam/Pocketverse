#!/usr/bin/env python3
"""Dump a binary AndroidManifest.xml (AXML) as indented text.

Usage: python tools/axml_dump.py path/to/AndroidManifest.xml

Stdlib only. Exists because the REA Android providers (JADX, apktool) are not
configured on the dev machine; see docs/research/re/TOOLING.md. Handles the
manifest subset needed for component/permission/intent-filter inventory.
"""
import struct
import sys

STRING_POOL, XML_START_NS, XML_END_NS, XML_START, XML_END = 0x0001, 0x0100, 0x0101, 0x0102, 0x0103


def read_pool(buf, off):
    _, hdr, size, count, _, flags, str_off, _ = struct.unpack_from("<HHIIIIII", buf, off)
    utf8 = bool(flags & 0x100)
    offsets = struct.unpack_from(f"<{count}I", buf, off + hdr)
    base = off + str_off
    out = []
    for o in offsets:
        p = base + o
        if utf8:
            n = buf[p]
            p += 2 if n & 0x80 else 1
            if n & 0x80:
                n = ((n & 0x7F) << 8) | buf[p - 1]
            blen = buf[p]
            p += 1
            if blen & 0x80:
                blen = ((blen & 0x7F) << 8) | buf[p]
                p += 1
            out.append(buf[p:p + blen].decode("utf-8", "replace"))
        else:
            n = struct.unpack_from("<H", buf, p)[0]
            p += 2
            if n & 0x8000:
                n = ((n & 0x7FFF) << 16) | struct.unpack_from("<H", buf, p)[0]
                p += 2
            out.append(buf[p:p + n * 2].decode("utf-16-le", "replace"))
    return out, off + size


def value(pool, raw, dtype, data):
    if raw != 0xFFFFFFFF:
        return pool[raw]
    if dtype == 0x03:
        return pool[data]
    if dtype == 0x12:
        return "true" if data else "false"
    if dtype == 0x10:
        return str(struct.unpack("<i", struct.pack("<I", data))[0])
    if dtype == 0x01:
        return f"@0x{data:08x}"
    return f"0x{data:08x}"


def dump(buf):
    pool, pos = read_pool(buf, 8)
    depth = 0
    while pos < len(buf):
        ctype, hdr, size = struct.unpack_from("<HHI", buf, pos)
        if ctype == XML_START:
            name_idx = struct.unpack_from("<I", buf, pos + 20)[0]
            attr_start, _, attr_count = struct.unpack_from("<HHH", buf, pos + 24)
            attrs = []
            for i in range(attr_count):
                a = pos + 16 + attr_start + i * 20
                _, aname, raw, _, _, dtype, data = struct.unpack_from("<IIIHBBI", buf, a)
                attrs.append(f'{pool[aname]}="{value(pool, raw, dtype, data)}"')
            print("  " * depth + f"<{pool[name_idx]} " + " ".join(attrs) + ">")
            depth += 1
        elif ctype == XML_END:
            depth -= 1
        pos += size


if __name__ == "__main__":
    dump(open(sys.argv[1], "rb").read())
