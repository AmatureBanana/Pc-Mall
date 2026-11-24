"""Verify the fixed task 24 Blender scores against the bundled open submissions."""

import argparse
from decimal import Decimal
import gzip
import hashlib
import json
from pathlib import Path
import runpy
from statistics import median
from zipfile import ZipFile


ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / "data/performance-acceptance-v1"
BASE = runpy.run_path(str(ROOT / "scripts/performance-data.py"))
PROFILES = {
    "Intel Core i5-14400F": ("CPU", 16),
    "Intel Core i7-14700K": ("CPU", 28),
    "Intel Core i9-14900K": ("CPU", 32),
    "NVIDIA GeForce RTX 4080 SUPER": ("OPTIX", None),
    "NVIDIA GeForce RTX 4090": ("OPTIX", None),
}


def verify(snapshot):
    catalog = json.loads((DATA / "catalog.json").read_text(encoding="utf-8"))
    references = json.loads((DATA / "rendering.json").read_text(encoding="utf-8"))
    rows = {}
    with gzip.open(DATA / "submissions.jsonl.gz") as archive:
        for line in archive:
            sample = json.loads(line)
            uid = sample["id"]
            assert uid not in rows, f"Duplicate sample {uid}"
            rows[uid] = (line.rstrip(), sample)

    used = set()
    assert len(references) == len(PROFILES)
    assert {ref["deviceName"] for ref in references} == set(PROFILES) == {row["reference"] for row in catalog if "reference" in row}
    for reference in references:
        name = reference["deviceName"]
        backend, threads = PROFILES[name]
        assert reference["computeType"] == backend
        assert reference["snapshotSha256"] == BASE["SNAPSHOT_SHA"]
        assert reference["sourceUrl"] == BASE["SOURCE"]
        assert reference["scenes"] == sorted(BASE["SCENES"])
        assert reference["benchmarkVersion"] == "4.3.0" and reference["mappingVersion"] == "performance-v1"
        ids = reference["sampleIds"]
        assert len(ids) == reference["sampleCount"] == 9 and ids == sorted(set(ids)) and not used.intersection(ids)
        used.update(ids)
        totals = []
        for uid in ids:
            sample = rows[uid][1]
            assert BASE["accepted"](sample, {name: (backend, None)}, threads), uid
            totals.append(sum(Decimal(str(row["stats"]["samples_per_minute"])) for row in sample["data"]))
        assert median(totals).quantize(Decimal("0.000000000001")) == Decimal(reference["rawScore"]), name
    assert used == rows.keys()

    if snapshot:
        verify_snapshot(snapshot, rows, references)
    print(f"{len(catalog)} products, {len(references)} Blender references and {len(rows)} submissions verified")


def verify_snapshot(snapshot, rows, references):
    with snapshot.open("rb") as stream:
        assert hashlib.file_digest(stream, "sha256").hexdigest() == BASE["SNAPSHOT_SHA"], "Unexpected Blender snapshot"
    expected = {raw for raw, _ in rows.values()}
    matches = {name: set() for name in PROFILES}
    names = [name.encode() for name in PROFILES]
    with ZipFile(snapshot) as archive:
        source, = (name for name in archive.namelist() if name.endswith(".jsonl"))
        with archive.open(source) as stream:
            for line in stream:
                if b"2b18cad88b13" not in line or b"Windows" not in line or not any(name in line for name in names):
                    continue
                sample = json.loads(line)
                for name, (backend, threads) in PROFILES.items():
                    if BASE["accepted"](sample, {name: (backend, None)}, threads):
                        matches[name].add(sample["id"])
                        expected.discard(line.rstrip())
                        break
    assert not expected, "At least one bundled submission is absent from the original snapshot"
    for reference in references:
        name = reference["deviceName"]
        assert reference["sampleIds"] == sorted(matches[name])[:9], f"Unexpected sample selection for {name}"


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--snapshot", type=Path, help="Also locate the submissions in the original frozen ZIP")
    verify(parser.parse_args().snapshot)
