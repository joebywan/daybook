#!/usr/bin/env python3
"""Print the SHA-256 of an APK's signing certificate.

Written rather than shelling out to `apksigner verify --print-certs` because that has been
observed to print nothing at all on a CI runner and still exit zero, which turns the release
guard into a no-op. This reads the certificate out of the APK Signing Block directly, so it
either produces a digest or fails loudly.
"""

import hashlib
import struct
import sys

APK_SIG_BLOCK_MAGIC = b"APK Sig Block 42"
SCHEME_V2_ID = 0x7109871A
SCHEME_V3_ID = 0xF05368C0


class ApkCertError(RuntimeError):
    pass


def find_eocd(data: bytes) -> int:
    """Locate the End of Central Directory record, scanning back over any trailing comment."""
    for start in range(len(data) - 22, max(len(data) - 22 - 65535, -1), -1):
        if data[start:start + 4] == b"PK\x05\x06":
            return start
    raise ApkCertError("no End of Central Directory record; not a zip/APK")


def central_directory_offset(data: bytes) -> int:
    eocd = find_eocd(data)
    return struct.unpack_from("<I", data, eocd + 16)[0]


def signing_block(data: bytes) -> bytes:
    cd_offset = central_directory_offset(data)
    if cd_offset < 32:
        raise ApkCertError("central directory too close to the start for a signing block")
    if data[cd_offset - 16:cd_offset] != APK_SIG_BLOCK_MAGIC:
        raise ApkCertError("APK Signing Block magic not found: the APK is not v2/v3 signed")
    size_at_end = struct.unpack_from("<Q", data, cd_offset - 24)[0]
    block_start = cd_offset - size_at_end - 8
    if block_start < 0:
        raise ApkCertError("APK Signing Block size is implausible")
    size_at_start = struct.unpack_from("<Q", data, block_start)[0]
    if size_at_start != size_at_end:
        raise ApkCertError("APK Signing Block size fields disagree")
    return data[block_start + 8:cd_offset - 24]


def scheme_value(block: bytes, wanted_id: int):
    """Walk the block's id-value pairs and return the value for wanted_id, if present."""
    offset = 0
    while offset + 12 <= len(block):
        pair_len = struct.unpack_from("<Q", block, offset)[0]
        if pair_len < 4 or offset + 8 + pair_len > len(block):
            break
        pair_id = struct.unpack_from("<I", block, offset + 8)[0]
        if pair_id == wanted_id:
            return block[offset + 12:offset + 8 + pair_len]
        offset += 8 + pair_len
    return None


def length_prefixed(buf: bytes):
    """Yield each uint32-length-prefixed element of a sequence."""
    offset = 0
    while offset + 4 <= len(buf):
        size = struct.unpack_from("<I", buf, offset)[0]
        if offset + 4 + size > len(buf):
            raise ApkCertError("length-prefixed element runs past the end of its sequence")
        yield buf[offset + 4:offset + 4 + size]
        offset += 4 + size


def first_certificate(scheme_block: bytes) -> bytes:
    # scheme block -> signers -> signer -> signed data -> [digests][certificates][attributes]
    for signers in length_prefixed(scheme_block):
        for signer in length_prefixed(signers):
            for signed_data in length_prefixed(signer):
                parts = list(length_prefixed(signed_data))
                if len(parts) < 2:
                    continue
                for certificate in length_prefixed(parts[1]):
                    return certificate
    raise ApkCertError("no certificate found inside the signing block")


def certificate_sha256(path: str) -> str:
    with open(path, "rb") as handle:
        data = handle.read()
    block = signing_block(data)
    for scheme_id in (SCHEME_V2_ID, SCHEME_V3_ID):
        value = scheme_value(block, scheme_id)
        if value is not None:
            return hashlib.sha256(first_certificate(value)).hexdigest()
    raise ApkCertError("signing block contains neither a v2 nor a v3 signature")


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: apk-cert.py <path-to-apk>", file=sys.stderr)
        return 2
    try:
        print(certificate_sha256(sys.argv[1]))
    except ApkCertError as error:
        print(f"apk-cert: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
