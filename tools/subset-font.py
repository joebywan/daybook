#!/usr/bin/env python3
"""Cuts the web build's bundled fonts down to the characters in use.

A minimal TrueType (glyf) subsetter with no dependencies: it keeps glyph ids, so GPOS kerning and
GDEF stay valid; empties every glyph not reachable from the kept characters (composite components
are followed); rewrites cmap to just those characters; and drops GSUB (a ligature could land on an
emptied glyph), DSIG and glyph names. The name table, which carries Noto's OFL notice, is kept.

    python3 tools/subset-font.py web/src/wasmJsMain/composeResources/font

reads the system's Noto fonts (Debian/Ubuntu fonts-noto-core paths). Re-run it if a serif string
gains a character outside Latin-1 and the punctuation listed below.
"""
import struct
import sys


def checksum(b):
    b = b + b'\0' * (-len(b) % 4)
    return sum(struct.unpack('>%dI' % (len(b) // 4), b)) & 0xFFFFFFFF


def read_cmap(d):
    _, n = struct.unpack('>HH', d[:4])
    best = None
    for i in range(n):
        pid, eid, off = struct.unpack('>HHI', d[4 + 8 * i:12 + 8 * i])
        fmt = struct.unpack('>H', d[off:off + 2])[0]
        if (pid, eid, fmt) in ((3, 10, 12), (0, 4, 12)):
            best = (fmt, off)
        elif (pid, eid, fmt) in ((3, 1, 4), (0, 3, 4)) and best is None:
            best = (fmt, off)
    fmt, off = best
    m = {}
    if fmt == 12:
        ng = struct.unpack('>I', d[off + 12:off + 16])[0]
        for g in range(ng):
            s, e, sg = struct.unpack('>III', d[off + 16 + 12 * g:off + 28 + 12 * g])
            for c in range(s, e + 1):
                m[c] = sg + c - s
    else:
        segx2 = struct.unpack('>H', d[off + 6:off + 8])[0]
        seg = segx2 // 2
        ends = struct.unpack('>%dH' % seg, d[off + 14:off + 14 + segx2])
        starts = struct.unpack('>%dH' % seg, d[off + 16 + segx2:off + 16 + 2 * segx2])
        deltas = struct.unpack('>%dh' % seg, d[off + 16 + 2 * segx2:off + 16 + 3 * segx2])
        ro_off = off + 16 + 3 * segx2
        ros = struct.unpack('>%dH' % seg, d[ro_off:ro_off + segx2])
        for i in range(seg):
            for c in range(starts[i], ends[i] + 1):
                if c == 0xFFFF:
                    continue
                if ros[i] == 0:
                    g = (c + deltas[i]) & 0xFFFF
                else:
                    a = ro_off + 2 * i + ros[i] + 2 * (c - starts[i])
                    g = struct.unpack('>H', d[a:a + 2])[0]
                    if g:
                        g = (g + deltas[i]) & 0xFFFF
                if g:
                    m[c] = g
    return m


def write_cmap(m):
    chars = sorted(c for c in m if c < 0x10000)
    segs = []
    for c in chars:
        if segs and c == segs[-1][1] + 1 and m[c] == m[segs[-1][1]] + 1:
            segs[-1][1] = c
        else:
            segs.append([c, c])
    segs.append([0xFFFF, 0xFFFF])
    n = len(segs)
    ends = [e for s, e in segs]
    starts = [s for s, e in segs]
    deltas = [((m[s] - s) & 0xFFFF) if s != 0xFFFF else 1 for s, e in segs]
    sr = 2 * (1 << (n.bit_length() - 1))
    body = struct.pack('>HHHH', 2 * n, sr, (n.bit_length() - 1), 2 * n - sr)
    body += struct.pack('>%dH' % n, *ends) + b'\0\0' + struct.pack('>%dH' % n, *starts)
    body += struct.pack('>%dH' % n, *deltas) + struct.pack('>%dH' % n, *([0] * n))
    sub = struct.pack('>HHH', 4, 6 + len(body), 0) + body
    return struct.pack('>HH', 0, 2) + struct.pack('>HHI', 0, 3, 20) + struct.pack('>HHI', 3, 1, 20) + sub


def subset(src, dst, chars):
    d = open(src, 'rb').read()
    n = struct.unpack('>H', d[4:6])[0]
    tables = {}
    for i in range(n):
        tag, _, off, ln = struct.unpack('>4sIII', d[12 + 16 * i:28 + 16 * i])
        tables[tag.decode()] = d[off:off + ln]
    head = bytearray(tables['head'])
    long_loca = struct.unpack('>h', head[50:52])[0] == 1
    num = struct.unpack('>H', tables['maxp'][4:6])[0]
    loca = tables['loca']
    if long_loca:
        offs = struct.unpack('>%dI' % (num + 1), loca[:4 * (num + 1)])
    else:
        offs = [2 * x for x in struct.unpack('>%dH' % (num + 1), loca[:2 * (num + 1)])]
    glyf = tables['glyf']
    cmap = read_cmap(tables['cmap'])
    keep_map = {c: cmap[c] for c in chars if c in cmap}
    missing = [hex(c) for c in chars if c not in cmap]
    keep = {0} | set(keep_map.values())
    todo = list(keep)
    while todo:
        g = todo.pop()
        data = glyf[offs[g]:offs[g + 1]]
        if len(data) >= 10 and struct.unpack('>h', data[:2])[0] < 0:
            p = 10
            while True:
                flags, comp = struct.unpack('>HH', data[p:p + 4])
                if comp not in keep:
                    keep.add(comp)
                    todo.append(comp)
                p += 4 + (4 if flags & 1 else 2)
                if flags & 8:
                    p += 2
                elif flags & 0x40:
                    p += 4
                elif flags & 0x80:
                    p += 8
                if not flags & 0x20:
                    break
    new_glyf = bytearray()
    new_offs = []
    for g in range(num):
        new_offs.append(len(new_glyf))
        if g in keep:
            data = glyf[offs[g]:offs[g + 1]]
            new_glyf += data + b'\0' * (-len(data) % 4)
    new_offs.append(len(new_glyf))
    tables['glyf'] = bytes(new_glyf)
    tables['loca'] = struct.pack('>%dI' % (num + 1), *new_offs)
    head[50:52] = struct.pack('>h', 1)
    head[8:12] = b'\0\0\0\0'
    tables['head'] = bytes(head)
    tables['post'] = struct.pack('>I', 0x00030000) + tables['post'][4:32]
    tables['cmap'] = write_cmap(keep_map)
    for t in ('GSUB', 'DSIG', 'hdmx', 'LTSH', 'VDMX'):
        tables.pop(t, None)
    tags = sorted(tables)
    nt = len(tags)
    es = nt.bit_length() - 1
    out = bytearray(struct.pack('>IHHHH', 0x00010000, nt, 16 * (1 << es), es, 16 * nt - 16 * (1 << es)))
    offset = 12 + 16 * nt
    body = bytearray()
    for t in tags:
        b = tables[t]
        out += struct.pack('>4sIII', t.encode(), checksum(b), offset + len(body), len(b))
        body += b + b'\0' * (-len(b) % 4)
    font = out + body
    adj = (0xB1B0AFBA - checksum(bytes(font))) & 0xFFFFFFFF
    hoff = offset + sum(len(tables[t]) + (-len(tables[t]) % 4) for t in tags[:tags.index('head')])
    font[hoff + 8:hoff + 12] = struct.pack('>I', adj)
    open(dst, 'wb').write(font)
    print(dst, len(font), 'bytes,', len(keep), 'glyphs kept, missing', missing)


if __name__ == '__main__':
    latin = (list(range(0x20, 0x7F)) + list(range(0xA0, 0x100)) +
             [0x2013, 0x2014, 0x2018, 0x2019, 0x201C, 0x201D, 0x2022, 0x2026, 0x2032, 0x2033])
    out = sys.argv[1]
    noto = '/usr/share/fonts/truetype/noto/'
    subset(noto + 'NotoSerif-Bold.ttf', out + '/noto_serif_bold.ttf', latin)
    subset(noto + 'NotoSansSymbols-Regular.ttf', out + '/noto_sans_symbols_arrows.ttf', [0x2190, 0x2191, 0x2192, 0x2193])
