"""Rebuild expanded CPU/GPU render references from the frozen Blender CC0 snapshot."""

import argparse
from collections import defaultdict
from decimal import Decimal
import gzip
import json
from pathlib import Path
import re
import runpy
import statistics
import uuid
import zipfile


ROOT = Path(__file__).resolve().parent.parent
CATALOG = ROOT / "backend/src/main/resources/db/catalog"
BUNDLE = ROOT / "data/catalog-rendering-v1"
MIGRATION = CATALOG / "V33__expanded_catalog_rendering.sql"
BASE = runpy.run_path(str(ROOT / "scripts/performance-data.py"))
SHA = BASE["SNAPSHOT_SHA"]
CHIPSET = re.compile(r"(GeForce RTX [0-9]{4}(?: Ti)?(?: SUPER)?|Radeon RX [0-9]{4}(?: XT| XTX| GRE)?|Arc [AB][0-9]{3})")


def catalog():
    """Read the immutable V32 input so references match its exact SKU identities."""
    source = (CATALOG / "V32__expand_demo_catalog.sql").read_text(encoding="utf8")
    rows = json.loads(source.split("SET @catalog_rows = '", 1)[1].split("]';", 1)[0].replace("''", "'").replace("\\\\", "\\") + "]")
    return {row[0]: row for row in rows}


def devices(products):
    identities = {}
    for sku, row in products.items():
        _, _, category, brand, model, summary, *_ = row
        fields = {field["code"]: field for field in row[10]}
        identity = dict(category=category, brand=brand, model=model)
        if category == "CPU":
            name = f"{brand} {model}"
            names = [name]
            if brand == "AMD":
                names.append(f"{name} {fields['cores']['value']}-Core Processor")
                graphics = fields.get("igpuModel", {}).get("value", "")
                if re.fullmatch(r"AMD Ryzen [57] 8[0-9]{3}G", name) and re.fullmatch(r"Radeon 7[0-9]{2}M", graphics):
                    names.append(f"{name} w/ {graphics} Graphics")
            elif re.fullmatch(r"Intel Core i[3579]-13[0-9]{3}[A-Z]*", name):
                names.append(f"13th Gen {name}")
            identities[sku] = ("CPU", names, identity, None)
        elif category == "GPU":
            chip, separator, _ = summary.partition(" · ")
            assert separator and CHIPSET.fullmatch(chip), sku
            if chip.startswith("GeForce"):
                backend, name = "OPTIX", "NVIDIA " + chip
            elif chip.startswith("Radeon"):
                backend, name = "HIP", "AMD " + chip
            else:
                backend, name = "ONEAPI", f"Intel(R) Arc(TM) {chip[4:]} Graphics"
            identity.update(boardVariant=fields["boardVariant"]["value"], vram=fields["vram"]["value"], gpuChipset=chip)
            source = fields["boardVariant"]["sourceUrl"]
            chipset = dict(code="gpuChipset", label="GPU 芯片型号", unit="", value=chip,
                           sourceUrl=source, note="按商品记录的芯片型号关联 Blender 设备")
            identities[sku] = (backend, [name], identity, chipset)
    return identities


def samples(lines, identities):
    profiles = {name: (kind, sku) for sku, (kind, names, _, _) in identities.items() for name in names}
    groups = defaultdict(dict)
    raw = defaultdict(list)
    for line in lines:
        if b"2b18cad88b13" not in line or b"Windows" not in line:
            continue
        sample = json.loads(line)
        if len(sample.get("data", [])) != 3:
            continue
        device = sample["data"][0]["device_info"]
        values = device["compute_devices"]
        if len(values) != 1:
            continue
        name = values[0]["name"]
        if name not in profiles:
            continue
        kind = profiles[name][0]
        threads = device.get("num_cpu_threads") if kind == "CPU" else None
        if kind == "CPU" and (not isinstance(threads, int) or threads < 1):
            continue
        if not BASE["accepted"](sample, profiles, threads):
            continue
        key = (kind, name, threads)
        identifier = sample["id"]
        assert str(uuid.UUID(identifier)) == identifier and identifier not in groups[key]
        total = sum(Decimal(str(row["stats"]["samples_per_minute"])) for row in sample["data"])
        groups[key][identifier] = total
        raw[key].append(line.rstrip(b"\r\n") + b"\n")
    return groups, raw


def references(groups, identities):
    selected = {}
    for sku, (kind, names, identity, _) in identities.items():
        candidates = [key for key in groups if key[0] == kind and key[1] in names and len(groups[key]) >= 3]
        if not candidates:
            continue
        # Pick the most represented thread profile; never combine CPU thread counts or device names.
        key = max(candidates, key=lambda group: (len(groups[group]), group[2] or 0, group[1]))
        selected[sku] = key
    output = {}
    for key in sorted(set(selected.values())):
        kind, name, threads = key
        scores = groups[key]
        condition = f"单插槽 CPU，固定 {threads} 线程（该设备完整提交最多的线程配置）。" if kind == "CPU" else f"{kind} 单设备；宿主 CPU 不限。"
        note = "按 CPU 精确型号匹配。" if kind == "CPU" else "按商品记录中的 GPU 芯片型号匹配。公开设备名称未区分板卡、出厂频率和同芯片显存版本。"
        output[key] = {
            "valid": True,
            "rawScore": format(statistics.median(scores.values()).quantize(Decimal("0.000000000001")), "f"),
            "sourceUrl": BASE["SOURCE"], "snapshotDate": "2026-11-03", "snapshotSha256": SHA,
            "benchmarkVersion": "4.3.0", "mappingVersion": "performance-v1", "deviceName": name,
            "computeType": kind, "operatingSystem": "Windows", "scenes": sorted(BASE["SCENES"]),
            "sampleCount": len(scores), "sampleIds": sorted(scores),
            "sampleConditions": "Windows 64 位，固定 Blender 4.3.0 构建及启动器、脚本和场景校验值；每场景 30 秒；完整三场景总分的中位数。" + condition,
            "matchNote": note, "reason": "使用固定 Blender 官方 CC0 快照为新增目录初始化性能参考",
        }
    return selected, output


def sql(identities, selected, refs):
    def payload(rows):
        return BASE["sql_text"]("[\n" + ",\n".join(json.dumps(row, ensure_ascii=False, separators=(",", ":")) for row in rows) + "\n]")

    fields = [[sku, identity["model"], param] for sku, (_, _, identity, param) in identities.items() if param]
    matches = [[sku, key[1], identities[sku][2]] for sku, key in selected.items()]
    return """-- Reproducible with scripts/catalog-rendering.py and the Blender CC0 evidence bundle.
START TRANSACTION;

SET @catalog_gpu_chipsets = """ + payload(fields) + """;
SET @catalog_rendering_devices = """ + payload(list(refs.values())) + """;
SET @catalog_rendering_skus = """ + payload(matches) + """;

UPDATE product_specifications s
JOIN products p ON p.id = s.product_id AND p.data_version = 'catalog-expansion-2025-v1'
JOIN JSON_TABLE(@catalog_gpu_chipsets, '$[*]' COLUMNS (
    sku VARCHAR(80) PATH '$[0]', model VARCHAR(100) PATH '$[1]', parameter_data JSON PATH '$[2]'
)) input ON input.sku = p.sku AND BINARY input.model = BINARY p.model
LEFT JOIN product_performance saved ON saved.product_id = p.id
SET s.parameters = JSON_ARRAY_APPEND(s.parameters, '$', input.parameter_data), p.revision = p.revision + 1
WHERE saved.product_id IS NULL
  AND JSON_SEARCH(s.parameters, 'one', 'gpuChipset', NULL, '$[*].code') IS NULL;

UPDATE products p
JOIN JSON_TABLE(@catalog_rendering_skus, '$[*]' COLUMNS (sku VARCHAR(80) PATH '$[0]')) input ON input.sku = p.sku
LEFT JOIN product_performance saved ON saved.product_id = p.id
SET p.revision = p.revision + 1
WHERE p.data_version = 'catalog-expansion-2025-v1' AND saved.product_id IS NULL;

INSERT INTO product_performance (product_id, reference_data, identity_data)
SELECT p.id, r.reference_data, input.identity_data
FROM JSON_TABLE(@catalog_rendering_skus, '$[*]' COLUMNS (
    sku VARCHAR(80) PATH '$[0]', device VARCHAR(150) PATH '$[1]', identity_data JSON PATH '$[2]'
)) input
JOIN products p ON p.sku = input.sku AND p.data_version = 'catalog-expansion-2025-v1'
JOIN JSON_TABLE(@catalog_rendering_devices, '$[*]' COLUMNS (
    device VARCHAR(150) PATH '$.deviceName', reference_data JSON PATH '$'
)) r ON BINARY r.device = BINARY input.device
LEFT JOIN product_performance saved ON saved.product_id = p.id
WHERE saved.product_id IS NULL;

COMMIT;
"""


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--snapshot", type=Path, help="Rebuild from the exact 2026-11-03 official ZIP")
    args = parser.parse_args()
    identities = devices(catalog())
    if args.snapshot:
        assert BASE["sha"](args.snapshot.read_bytes()) == SHA, "Unexpected Blender snapshot checksum"
        with zipfile.ZipFile(args.snapshot) as archive:
            member = next(name for name in archive.namelist() if name.endswith(".jsonl"))
            with archive.open(member) as lines:
                groups, raw = samples(lines, identities)
            license_text = archive.read("LICENSE.txt")
    else:
        with gzip.open(BUNDLE / "submissions.jsonl.gz") as lines:
            groups, raw = samples(lines, identities)
    selected, refs = references(groups, identities)
    assert len({key[1] for key in refs}) == len(refs), "Multiple profiles selected for the same device"
    migration = sql(identities, selected, refs)
    evidence = b"".join(sorted(line for key in refs for line in raw[key]))
    if args.snapshot:
        BUNDLE.mkdir(exist_ok=True, parents=True)
        (BUNDLE / "LICENSE.txt").write_bytes(license_text)
        (BUNDLE / "submissions.jsonl.gz").write_bytes(gzip.compress(evidence, mtime=0))
        MIGRATION.write_text(migration, encoding="utf8", newline="\n")
    else:
        assert evidence == gzip.decompress((BUNDLE / "submissions.jsonl.gz").read_bytes())
        assert migration == MIGRATION.read_text(encoding="utf8")
    for kind in ("CPU", "GPU"):
        members = {sku for sku, (_, _, identity, _) in identities.items() if identity["category"] == kind}
        print(f"{kind}: {len(members & selected.keys())}/{len(members)} SKU scored")
        missing = sorted({identities[sku][1][0] for sku in members - selected.keys()})
        print("Insufficient samples:", ", ".join(missing))
    print(f"{len(refs)} exact devices, {len(evidence.splitlines())} accepted submissions")


if __name__ == "__main__":
    main()
