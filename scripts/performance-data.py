"""Offline, reproducible Blender evidence extraction. Python standard library only.

Rebuild: python scripts/performance-data.py --write --snapshot .local/task-08-data/opendata-captured-2026-11-03.zip
Verify committed evidence: python scripts/performance-data.py --verify
"""
import argparse
import gzip
import hashlib
import json
import math
import statistics
import uuid
import zipfile
from decimal import Decimal
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / "data/performance-v1"
MIGRATION = ROOT / "backend/src/main/resources/db/migration/V15__trusted_performance_demo.sql"
SCENES = {
    "classroom": "936f0a528f03eba291c67ea87743a0ebde58938fb87471b262721b89b46b875d",
    "junkshop": "f5515a21a1337b908212d8d76733530df83014464a24c585ab7b96000da0cdce",
    "monster": "8ff812a02f720433de1403dab447be9541eecca71cc919224a7790d017c1b2ce",
}
DEVICES = {
    "Intel Core i5-14600K": ("CPU", "INTEL-I5-14600K"),
    "Intel Core i5-14600KF": ("CPU", "INTEL-I5-14600KF"),
    "NVIDIA GeForce RTX 4070 SUPER": ("OPTIX", "NVIDIA-RTX4070SUPER-FE"),
}
SNAPSHOT_SHA = "7e0875527aea34d96e99c933d1e82c62a20dac0a343720d252b12c21228812dd"
SOURCE = "https://opendata.blender.org/snapshots/opendata-latest.zip"
INTEL = "https://www.intel.com/content/www/us/en/products/sku/236799/intel-core-i5-processor-14600k-24m-cache-up-to-5-30-ghz/specifications.html"


def sha(content):
    return hashlib.sha256(content).hexdigest()


def dump(value):
    return json.dumps(value, ensure_ascii=False, indent=2) + "\n"


def accepted(document, profiles=DEVICES, cpu_threads=20):
    rows = document.get("data", [])
    if document.get("schema_version") != "v4" or len(rows) != 3:
        return False
    first = rows[0]
    devices = first.get("device_info", {}).get("compute_devices", [])
    if len(devices) != 1 or devices[0].get("name") not in profiles:
        return False
    name = devices[0]["name"]
    backend = profiles[name][0]
    if {row.get("scene", {}).get("label") for row in rows} != set(SCENES):
        return False
    for row in rows:
        version, system, info = row["blender_version"], row["system_info"], row["device_info"]
        if version.get("version") != "4.3.0" or version.get("label") != "4.3.0" or version.get("build_hash") != "2b18cad88b13":
            return False
        if row["benchmark_launcher"].get("label") != "3.1.0" or row["benchmark_script"].get("label") != "3.1.1":
            return False
        if row["benchmark_launcher"].get("checksum") != "5c65e95672826b04d3178ae02342fafcd2192987ab16ad54a5aee2346a254a40":
            return False
        if row["benchmark_script"].get("checksum") != "5a706e14279da13b2bed2dbb9e0a47c2588b9ea4f37cf8f9c7d79c8b7c634c1f":
            return False
        if system.get("system") != "Windows" or system.get("bitness") != "64bit" or info.get("device_type") != backend:
            return False
        row_devices = info.get("compute_devices", [])
        if len(row_devices) != 1 or row_devices[0].get("name") != name or row_devices[0].get("type") != backend:
            return False
        if backend == "CPU" and (info.get("num_cpu_threads") != cpu_threads or system.get("num_cpu_sockets") != 1):
            return False
        scene = row["scene"]
        if scene.get("checksum") != SCENES[scene["label"]]:
            return False
        stats = row["stats"]
        if stats.get("time_limit") != 30 or any(not math.isfinite(stats[k]) or stats[k] <= 0 for k in ("samples_per_minute", "number_of_samples", "time_for_samples")):
            return False
        calculated = Decimal(str(stats["number_of_samples"])) * 60 / Decimal(str(stats["time_for_samples"]))
        if abs(calculated - Decimal(str(stats["samples_per_minute"]))) > Decimal("0.00000001"):
            return False
    return True


def references(raw_lines, profiles=DEVICES, cpu_threads=20, match_notes=None):
    groups = {name: [] for name in profiles}
    ids = set()
    for line in raw_lines:
        document = json.loads(line)
        assert accepted(document, profiles, cpu_threads), "Curated subset contains an inadmissible submission"
        identifier = document["id"]
        assert str(uuid.UUID(identifier)) == identifier and identifier not in ids
        ids.add(identifier)
        name = document["data"][0]["device_info"]["compute_devices"][0]["name"]
        total = sum(Decimal(str(row["stats"]["samples_per_minute"])) for row in document["data"])
        groups[name].append((identifier, total))
    result = {}
    for name, samples in groups.items():
        assert len(samples) >= 3, f"Insufficient complete submissions: {name}"
        backend, sku = profiles[name]
        result[sku] = {
            "valid": True,
            "rawScore": str(statistics.median([score for _, score in samples]).quantize(Decimal("0.000000000001"))),
            "sourceUrl": SOURCE, "snapshotDate": "2026-11-03", "snapshotSha256": SNAPSHOT_SHA,
            "benchmarkVersion": "4.3.0", "mappingVersion": "performance-v1", "deviceName": name,
            "computeType": backend, "operatingSystem": "Windows", "scenes": sorted(SCENES),
            "sampleCount": len(samples), "sampleIds": sorted(identifier for identifier, _ in samples),
            "sampleConditions": "Windows 64bit；Blender 4.3.0 build 2b18cad88b13；launcher 3.1.0/script 3.1.1（固定checksum）；单设备；每场景30秒；完整三场景总分的中位数；"
                + (f"单插槽CPU、{cpu_threads}线程。" if backend == "CPU" else f"{backend}；宿主CPU不筛选。")
                + "社区提交，不统一频率、驱动及散热条件；原始系统信息逐条保留。",
            "matchNote": match_notes[sku] if match_notes else ("精确CPU型号匹配，K和KF独立聚合。" if backend == "CPU" else "NVIDIA GeForce RTX 4070 SUPER型号级参考；商品为12GB Founders Edition，厂商型号/显存见NVIDIA规格；公开设备名不区分板卡，非FE单卡实测，不代表游戏FPS。"),
            "reason": "官方CC0快照受控样本初始化",
        }
    return result


def sql_text(value):
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def seed_sql(refs):
    parameters = []
    for code, label, value, unit in [
        ("socket", "CPU 插槽", "LGA1700", ""), ("cores", "核心数", 14, "核"), ("threads", "线程数", 20, "线程"),
        ("integratedGraphics", "可用核显", True, ""), ("basePower", "基础功耗", 125, "W"),
        ("peakPowerEstimate", "峰值功耗估算", 181, "W"), ("memoryGenerations", "支持内存代际", ["DDR4", "DDR5"], ""),
    ]:
        parameters.append(dict(code=code, label=label, value=value, unit=unit, sourceUrl=INTEL,
                               note="Intel ARK SKU236799；峰值使用Maximum Turbo Power；UHD Graphics 770核显。"))
    sql = """-- Generated by scripts/performance-data.py from the frozen CC0 evidence bundle.
-- Prices and stock are project demonstration values, not manufacturer quotations.
INSERT INTO products(sku,name,category,brand,model,summary,image_url,price,stock,status,source_url,price_note,data_version)
VALUES('INTEL-I5-14600K','Intel Core i5-14600K','CPU','Intel','i5-14600K','Intel · LGA1700 · UHD 770核显','/images/intel-cpu.svg',1599.00,10,'ON_SALE',
""" + sql_text(INTEL) + ", 'CNY演示价格与库存，非实时市场报价','performance-demo-v1');\n"
    sql += "INSERT INTO product_specifications(product_id,description,parameters,data_version,verified_on) SELECT id,'14核20线程；UHD Graphics 770核显；最大睿频功耗181W；仍使用独立散热器。'," + sql_text(json.dumps(parameters, ensure_ascii=False)) + ",'performance-demo-v1','2026-11-03' FROM products WHERE sku='INTEL-I5-14600K';\n"
    for sku, reference in refs.items():
        sql += "INSERT INTO product_performance(product_id,reference_data,identity_data) SELECT p.id," + sql_text(json.dumps(reference, ensure_ascii=False)) + ",JSON_OBJECT('category',p.category,'brand',p.brand,'model',p.model"
        if sku == "NVIDIA-RTX4070SUPER-FE":
            sql += ",'boardVariant','Founders Edition','vram',12"
        sql += ") FROM products p WHERE p.sku=" + sql_text(sku) + ";\n"
        sql += "UPDATE products SET revision=revision+1 WHERE sku=" + sql_text(sku) + ";\n"
    return sql


def scenarios():
    shared = ["ASUS-TUF-B760M-PLUS-WIFI", "KINGSTON-KF560C36BBEK2-32", "SAMSUNG-MZ-V9P1T0BW", "CORSAIR-CP-9020295", "DEEPCOOL-CC560", "DEEPCOOL-AK400"]
    return {
        "note": "仅为后续规则/推荐的真实候选数据，不是已执行兼容引擎的通过结果。全部售价与库存均为商城演示值。",
        "office": {"skus": ["INTEL-I5-14600K"] + shared, "demoTotalCny": "5653.00", "graphics": "UHD 770 + 主板HDMI/DisplayPort"},
        "gaming": {"skus": ["INTEL-I5-14600KF", "NVIDIA-RTX4070SUPER-FE"] + shared, "demoTotalCny": "10552.00", "graphics": "RTX 4070 SUPER FE"},
        "creation": {"skus": ["INTEL-I5-14600K", "NVIDIA-RTX4070SUPER-FE"] + shared, "demoTotalCny": "10652.00", "graphics": "RTX 4070 SUPER FE"},
    }


def write(snapshot):
    assert sha(snapshot.read_bytes()) == SNAPSHOT_SHA, "Wrong frozen snapshot; never silently update the data version"
    lines = []
    with zipfile.ZipFile(snapshot) as archive:
        names = [name for name in archive.namelist() if name.endswith(".jsonl")]
        assert len(names) == 1
        with archive.open(names[0]) as stream:
            for line in stream:
                # Fast prefilter, followed by structural/exact matching.
                if b'4.3.0' not in line or not any(name.encode() in line for name in DEVICES):
                    continue
                if accepted(json.loads(line)):
                    lines.append(line.rstrip(b"\r\n") + b"\n")
        DATA.mkdir(parents=True, exist_ok=True)
        (DATA / "LICENSE.txt").write_bytes(archive.read("LICENSE.txt"))
    lines.sort(key=lambda line: json.loads(line)["id"])
    raw = b"".join(lines)
    (DATA / "submissions.jsonl.gz").write_bytes(gzip.compress(raw, mtime=0))
    refs = references(lines)
    (DATA / "references.json").write_text(dump(refs), encoding="utf-8", newline="\n")
    (DATA / "scenarios.json").write_text(dump(scenarios()), encoding="utf-8", newline="\n")
    MIGRATION.write_text(seed_sql(refs), encoding="utf-8", newline="\n")
    artifacts = [DATA / "LICENSE.txt", DATA / "submissions.jsonl.gz", DATA / "references.json", DATA / "scenarios.json", MIGRATION]
    manifest = {
        "dataVersion": "performance-demo-v1", "mappingVersion": "performance-v1", "license": "CC0-1.0",
        "snapshot": {"sourceUrl": SOURCE, "capturedDate": "2026-11-03", "lastModifiedUtc": "2026-11-03T00:01:23Z", "bytes": 102160171, "sha256": SNAPSHOT_SHA},
        "sceneChecksums": SCENES, "rawSubsetSha256": sha(raw), "submissions": len(lines),
        "checksums": {str(path.relative_to(ROOT)).replace("\\", "/"): sha(path.read_bytes()) for path in artifacts},
    }
    (DATA / "manifest.json").write_text(dump(manifest), encoding="utf-8", newline="\n")
    verify()


def verify():
    manifest = json.loads((DATA / "manifest.json").read_text(encoding="utf-8"))
    for path, checksum in manifest["checksums"].items():
        assert sha((ROOT / path).read_bytes()) == checksum, f"Checksum mismatch: {path}"
    raw = gzip.decompress((DATA / "submissions.jsonl.gz").read_bytes())
    assert sha(raw) == manifest["rawSubsetSha256"]
    refs = references(raw.splitlines())
    assert len(raw.splitlines()) == manifest["submissions"]
    assert refs == json.loads((DATA / "references.json").read_text(encoding="utf-8"))
    assert seed_sql(refs) == MIGRATION.read_text(encoding="utf-8")
    assert scenarios() == json.loads((DATA / "scenarios.json").read_text(encoding="utf-8"))
    for sku, reference in refs.items():
        print(f"{sku}: {reference['sampleCount']} complete submissions, median={reference['rawScore']}")
    print("Evidence, checksums, exact conditions, raw scene calculations and seed migration verified.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write", action="store_true")
    parser.add_argument("--verify", action="store_true")
    parser.add_argument("--snapshot", type=Path)
    args = parser.parse_args()
    if args.write:
        assert args.snapshot, "--snapshot is required for rebuilding"
        write(args.snapshot)
    else:
        verify()
