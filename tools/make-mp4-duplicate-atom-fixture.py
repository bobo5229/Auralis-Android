#!/usr/bin/env python3
"""Test-fixture generator for the Auralis metadata layer.

This is test tooling only. It is NOT part of the application, and it is NOT a
metadata parser: it never interprets tag payloads. It only appends four
hand-built `moov/udta/meta/ilst` child atoms to a base MP4 file and fixes up the
size fields of the enclosing boxes, so that a regression test can exercise files
where several values of one field are stored as several atoms sharing one name.

Base file (regenerate with the same command if it ever needs to change):

    ffmpeg -y -f lavfi -i "anullsrc=r=44100:cl=stereo" -t 1 -c:a aac -b:a 32k \
        -metadata "title=Auralis Fixture" -metadata "artist=Fixture Artist" \
        -metadata "album=Fixture Album" \
        -metadata "album_artist=Fixture Album Artist" \
        -metadata "track=5/12" -metadata "disc=1/2" base.m4a

Atoms appended by this script:

    ---- (mean=com.apple.iTunes, name=GENRE)   = "Hyperpop"
    ---- (mean=com.apple.iTunes, name=GENRE)   = "Electropop"
    ---- (mean=com.apple.iTunes, name=MOOD)    = "hyperpop; electropop"
    ---- (mean=com.apple.iTunes, name=CHOICE)  = "Same"
    ---- (mean=com.apple.iTunes, name=CHOICE)  = "Same"
    <copyright>wrt                             = "Composer One"
    <copyright>wrt                             = "Composer Two"
    <verbatim copy of the base file's existing trkn atom>

so that one fixture covers the StringList merge (freeform and standard atoms),
the absence of splitting / de-duplication / case folding in that merge, and the
untouched non-StringList duplicate behaviour.

Usage:
    python tools/make-mp4-duplicate-atom-fixture.py <base.m4a> <out.m4a>
"""

import struct
import sys

CONTAINERS = {
    b"moov", b"udta", b"trak", b"mdia", b"minf", b"stbl", b"ilst",
    b"edts", b"dinf", b"mvex",
}

AUDIO_TYPE_UTF8 = 1


def boxes(data, start, end):
    """Yield (name, offset, size, header_len) for the boxes in [start, end)."""
    pos = start
    while pos + 8 <= end:
        size = struct.unpack(">I", data[pos:pos + 4])[0]
        name = data[pos + 4:pos + 8]
        header = 8
        if size == 1:
            size = struct.unpack(">Q", data[pos + 8:pos + 16])[0]
            header = 16
        elif size == 0:
            size = end - pos
        if size < header or pos + size > end:
            return
        yield name, pos, size, header
        pos += size


def find_path(data, names):
    """Return the chain of boxes matching `names`, outermost first."""
    chain = []
    start, end = 0, len(data)
    for want in names:
        hit = None
        for name, pos, size, header in boxes(data, start, end):
            if name == want:
                hit = (pos, size, header, name)
                break
        if hit is None:
            return None
        chain.append(hit)
        start, end = hit[0] + hit[2], hit[0] + hit[1]
        if want == b"meta":
            start += 4  # 'meta' is a FullBox: 4 bytes of version/flags
    return chain


def atom(name, payload):
    return struct.pack(">I", len(payload) + 8) + name + payload


def data_atom(value):
    payload = struct.pack(">I", AUDIO_TYPE_UTF8) + b"\0\0\0\0" + value.encode("utf-8")
    return atom(b"data", payload)


def text_atom(name, value):
    return atom(name, data_atom(value))


def freeform_atom(mean, name, value):
    body = (
        atom(b"mean", b"\0\0\0\0" + mean.encode("utf-8"))
        + atom(b"name", b"\0\0\0\0" + name.encode("utf-8"))
        + data_atom(value)
    )
    return atom(b"----", body)


def build(base_path, out_path):
    data = bytearray(open(base_path, "rb").read())

    chain = find_path(data, [b"moov", b"udta", b"meta", b"ilst"])
    if chain is None:
        raise SystemExit("base file has no moov/udta/meta/ilst")
    ilst_pos, ilst_size, _, _ = chain[-1]

    # Copy the base file's existing trkn atom verbatim so the fixture also covers
    # a duplicated non-StringList item.
    trkn = next(((pos, size) for name, pos, size, _ in
                 boxes(data, ilst_pos + 8, ilst_pos + ilst_size) if name == b"trkn"), None)
    if trkn is None:
        raise SystemExit("base file has no trkn atom")
    trkn_bytes = bytes(data[trkn[0]:trkn[0] + trkn[1]])

    appended = (
        freeform_atom("com.apple.iTunes", "GENRE", "Hyperpop")
        + freeform_atom("com.apple.iTunes", "GENRE", "Electropop")
        + freeform_atom("com.apple.iTunes", "MOOD", "hyperpop; electropop")
        + freeform_atom("com.apple.iTunes", "CHOICE", "Same")
        + freeform_atom("com.apple.iTunes", "CHOICE", "Same")
        + text_atom(b"\xa9wrt", "Composer One")
        + text_atom(b"\xa9wrt", "Composer Two")
        + trkn_bytes
    )

    insert_at = ilst_pos + ilst_size
    data[insert_at:insert_at] = appended

    for pos, size, _, name in chain:
        current = struct.unpack(">I", data[pos:pos + 4])[0]
        if current != size:
            raise SystemExit("unexpected %s size %d" % (name, current))
        data[pos:pos + 4] = struct.pack(">I", size + len(appended))

    open(out_path, "wb").write(bytes(data))
    print("wrote %s (%d bytes, +%d)" % (out_path, len(data), len(appended)))

    for name, pos, size, header in boxes(data, ilst_pos, ilst_pos + ilst_size + len(appended)):
        print("  ilst child: %s size=%d" % (name.decode("latin1"), size))


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    build(sys.argv[1], sys.argv[2])
