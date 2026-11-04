"""Reproduce the AMD extension without rewriting the original performance-v1 bundle."""
import argparse
import gzip
import importlib.util
import json
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location("performance_evidence", ROOT / "scripts/performance-data.py")
base = importlib.util.module_from_spec(spec)
spec.loader.exec_module(base)
DATA = ROOT / "data/performance-amd-v1"
MIGRATION = ROOT / "backend/src/main/resources/db/migration/V16__amd_performance_references.sql"
PROFILES = {
    "AMD Ryzen 7 7800X3D 8-Core Processor": ("CPU", "AMD-R7-7800X3D"),
    "AMD Radeon RX 7800 XT": ("HIP", "AMD-RX7800XT"),
}
MATCH_NOTES = {
    "AMD-R7-7800X3D": "商品AMD Ryzen 7 7800X3D对应原始设备名AMD Ryzen 7 7800X3D 8-Core Processor；8-Core后缀与AMD官方8核16线程一致，不混用7800X或其他X3D。120W为TDP，未补未知峰值功耗。",
    "AMD-RX7800XT": "AMD Radeon RX 7800 XT型号级HIP渲染参考；商品为16GB AMD reference；设备名不区分板卡厂商/变体，不是AMD公版板卡单卡实测，不代表游戏FPS。",
}


def refs(lines):
    return base.references(lines, PROFILES, 16, MATCH_NOTES)


def migration(references):
    result = "-- AMD extension from the same frozen CC0 snapshot; existing administrator references are preserved.\n"
    for sku, reference in references.items():
        identity = {"category": "CPU" if sku == "AMD-R7-7800X3D" else "GPU", "brand": "AMD",
                    "model": "Ryzen 7 7800X3D" if sku == "AMD-R7-7800X3D" else "Radeon RX 7800 XT (AMD reference)"}
        if sku == "AMD-RX7800XT":
            identity.update(boardVariant="AMD reference", vram=16)
        result += "INSERT INTO product_performance(product_id,reference_data,identity_data) SELECT p.id," + base.sql_text(json.dumps(reference, ensure_ascii=False)) + "," + base.sql_text(json.dumps(identity, ensure_ascii=False))
        result += " FROM products p WHERE p.sku=" + base.sql_text(sku) + " AND NOT EXISTS(SELECT 1 FROM product_performance r WHERE r.product_id=p.id);\n"
        result += "UPDATE products SET revision=revision+1 WHERE sku=" + base.sql_text(sku) + ";\n"
    return result


def verify():
    manifest = json.loads((DATA / "manifest.json").read_text(encoding="utf-8"))
    for path, checksum in manifest["checksums"].items():
        assert base.sha((ROOT / path).read_bytes()) == checksum, path
    raw = gzip.decompress((DATA / "submissions.jsonl.gz").read_bytes())
    assert base.sha(raw) == manifest["rawSubsetSha256"]
    references = refs(raw.splitlines())
    assert len(raw.splitlines()) == manifest["submissions"]
    assert references == json.loads((DATA / "references.json").read_text(encoding="utf-8"))
    assert MIGRATION.read_text(encoding="utf-8") == migration(references)
    for sku, reference in references.items():
        print(f"{sku}: {reference['sampleCount']} complete submissions, median={reference['rawScore']}")
    print("AMD extension raw records, conditions, UUIDs, hashes and migration verified.")


def write(snapshot):
    assert base.sha(snapshot.read_bytes()) == base.SNAPSHOT_SHA, "Unexpected snapshot; do not silently change versions"
    lines = []
    with zipfile.ZipFile(snapshot) as archive:
        names = [name for name in archive.namelist() if name.endswith(".jsonl")]
        assert len(names) == 1
        with archive.open(names[0]) as stream:
            for line in stream:
                if b'4.3.0' in line and (b'7800X3D' in line or b'RX 7800 XT' in line):
                    if base.accepted(json.loads(line), PROFILES, 16):
                        lines.append(line.rstrip(b"\r\n") + b"\n")
        DATA.mkdir(parents=True, exist_ok=True)
        (DATA / "LICENSE.txt").write_bytes(archive.read("LICENSE.txt"))
    lines.sort(key=lambda line: json.loads(line)["id"])
    raw = b"".join(lines)
    (DATA / "submissions.jsonl.gz").write_bytes(gzip.compress(raw, mtime=0))
    references = refs(lines)
    (DATA / "references.json").write_text(base.dump(references), encoding="utf-8", newline="\n")
    MIGRATION.write_text(migration(references), encoding="utf-8", newline="\n")
    artifacts = [DATA / "LICENSE.txt", DATA / "submissions.jsonl.gz", DATA / "references.json", MIGRATION]
    manifest = {"dataVersion": "performance-amd-v1", "mappingVersion": "performance-v1", "license": "CC0-1.0",
                "snapshot": {"sourceUrl": base.SOURCE, "capturedDate": "2026-11-03", "sha256": base.SNAPSHOT_SHA, "bytes": 102160171},
                "cpuThreads": 16, "sceneChecksums": base.SCENES, "submissions": len(lines), "rawSubsetSha256": base.sha(raw),
                "checksums": {str(path.relative_to(ROOT)).replace("\\", "/"): base.sha(path.read_bytes()) for path in artifacts}}
    (DATA / "manifest.json").write_text(base.dump(manifest), encoding="utf-8", newline="\n")
    verify()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write", action="store_true")
    parser.add_argument("--verify", action="store_true")
    parser.add_argument("--snapshot", type=Path)
    args = parser.parse_args()
    if args.write:
        assert args.snapshot, "--snapshot is required"
        write(args.snapshot)
    else:
        verify()
