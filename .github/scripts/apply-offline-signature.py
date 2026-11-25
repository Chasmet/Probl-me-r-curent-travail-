"""Reconstruct an APK from the exact unsigned build and a detached public APK v2 block.
No private key or password is stored in GitHub. apksigner must verify the result separately."""
import base64, hashlib, json, pathlib, struct, sys
manifest_path, artifact_dir, out = sys.argv[1:]
m = json.loads(pathlib.Path(manifest_path).read_text())
apks = list(pathlib.Path(artifact_dir).glob("*.apk"))
if len(apks) != 1:
    raise SystemExit("Expected exactly one unsigned APK")
b = apks[0].read_bytes()
assert hashlib.sha256(b).hexdigest() == m["unsigned_sha256"]
e = b.rfind(b"PK\x05\x06")
assert e >= 0 and e + 22 == len(b), "Unexpected ZIP end record"
cd = struct.unpack_from("<I", b, e+16)[0]
assert cd < e and b[cd:cd+4] == b"PK\x01\x02"
assert b[cd-16:cd] != b"APK Sig Block 42", "Expected unsigned input"
block = base64.b64decode(m["public_signing_block"], validate=True)
assert 32 <= len(block) <= 65536 and block[-16:] == b"APK Sig Block 42"
assert struct.unpack_from("<Q",block,0)[0] == len(block)-8
assert struct.unpack_from("<Q",block,len(block)-24)[0] == len(block)-8
end = bytearray(b[e:])
struct.pack_into("<I", end, 16, cd+len(block))
signed = b[:cd]+block+b[cd:e]+end
assert hashlib.sha256(signed).hexdigest() == m["signed_sha256"]
pathlib.Path(out).write_bytes(signed)
