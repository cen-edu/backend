from __future__ import annotations

import argparse
import copy
import csv
import json
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

from .enrichment import apply_curriculum_mapping, attach_learning_guide, normalize_30_record, normalize_110_111_record
from .full_source import iter_all_30, iter_all_110_111
from .full_classifier import classify_record
from .load_pipeline import write_load_package
from .raw_sources import is_m1_110_111, is_m1_math_30, iter_raw_json
from .source28 import build_source28_index


PIPELINE_VERSION = "m1-question-pipeline-v1"


def _write_jsonl(path: Path, rows: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("".join(json.dumps(row, ensure_ascii=False) + "\n" for row in rows), encoding="utf-8")


def _topic_index(path: Path) -> dict[str, dict]:
    with path.open(encoding="utf-8-sig", newline="") as stream:
        return {row["source_topic_key"]: row for row in csv.DictReader(stream)}


def _normalized_records(source30: Path, source110: Path, source111: Path) -> list[dict]:
    records_by_ref = {}
    for source_file, raw in iter_raw_json(source30):
        if not is_m1_math_30(raw):
            continue
        record = normalize_30_record(raw, source_file)
        records_by_ref.setdefault(record["sourceRef"], record)
    for dataset, root in (("110", source110), ("111", source111)):
        for source_file, raw in iter_raw_json(root):
            if not is_m1_110_111(raw):
                continue
            record = normalize_110_111_record(raw, dataset, source_file)
            source_ref = record["sourceRef"]
            existing = records_by_ref.get(source_ref)
            if existing is None:
                records_by_ref[source_ref] = record
                continue
            evidence = (record.get("sourceMetadata") or {}).get("sourceEvidence")
            if evidence:
                existing.setdefault("sourceMetadata", {})["sourceEvidence"] = copy.deepcopy(evidence)
    return sorted(records_by_ref.values(), key=lambda row: row["sourceRef"])


def _has_one_primary_mapping(record: dict) -> bool:
    mappings = record.get("curriculumMappings") or []
    return len(mappings) == 1 and mappings[0].get("isPrimary") is True and bool(mappings[0].get("curriculumUnitId"))


def run_pipeline(source30: Path, source110: Path, source111: Path, output_dir: Path, *, source28: Path | None = None) -> dict:
    output_dir = Path(output_dir)
    reference_dir = output_dir / "reference"
    mapping_sources = []
    seen_mapping_questions = set()
    for source in (*iter_all_30(source30), *iter_all_110_111(source110, "110"), *iter_all_110_111(source111, "111")):
        key = (source.dataset_id, source.question_id)
        if key not in seen_mapping_questions:
            seen_mapping_questions.add(key)
            mapping_sources.append(source)
    mapping_manifest = write_load_package(mapping_sources, reference_dir, include_question_mappings=False)
    normalized = _normalized_records(source30, source110, source111)
    _write_jsonl(output_dir / "01_normalized_questions.jsonl", normalized)
    topic_index = _topic_index(reference_dir / "source_topic_curriculum_mappings.csv")
    mapped = []
    for record in normalized:
        classification, audit = classify_record(record)
        mapped.append(apply_curriculum_mapping(record, topic_index, classification=classification, audit=audit))
    _write_jsonl(output_dir / "02_curriculum_mapped_questions.jsonl", mapped)
    source28_index = build_source28_index(source28) if source28 else {}
    enriched = [attach_learning_guide(record, source28_index) for record in mapped if _has_one_primary_mapping(record)]
    _write_jsonl(output_dir / "03_learning_guided_questions.jsonl", enriched)
    stages = Counter(row["pipelineStage"] for row in enriched)
    manifest = {
        "pipelineVersion": PIPELINE_VERSION, "generatedAt": datetime.now(timezone.utc).isoformat(),
        "counts": {"normalized": len(normalized), "curriculumMapped": sum(bool(row.get("curriculumMappings")) for row in mapped),
                   "learningGuideEnriched": stages.get("LEARNING_GUIDE_ENRICHED", 0),
                   "needsReview": sum(1 for row in mapped if not _has_one_primary_mapping(row))},
        "datasets": dict(Counter(row["sourceRef"].partition(":")[0] for row in normalized)),
        "stages": dict(stages), "mapping": mapping_manifest,
        "joinKey": "sourceTopicKey", "finalOutput": "03_learning_guided_questions.jsonl",
    }
    (output_dir / "pipeline_manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    return manifest


def main() -> None:
    parser = argparse.ArgumentParser(description="30·110·111 원천에 EBS 단원과 LearningGuide를 순차 결합합니다")
    parser.add_argument("--source-30", type=Path, required=True)
    parser.add_argument("--source-110", type=Path, required=True)
    parser.add_argument("--source-111", type=Path, required=True)
    parser.add_argument("--source-28", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(run_pipeline(args.source_30, args.source_110, args.source_111, args.output, source28=args.source_28), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
