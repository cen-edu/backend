import argparse
import csv
import json
import uuid
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path

from .catalog import curriculum_units
from .full_classifier import classify_m1_question
from .full_source import iter_all_30, iter_all_110_111
from .topic_key import source_topic_key


MAPPING_VERSION = "3.0.0"
NAMESPACE = uuid.UUID("2574f521-e312-4e76-b17a-99f0c2026b86")


def _write_csv(path: Path, rows: list[dict], fields: list[str]):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields, extrasaction="ignore")
        writer.writeheader(); writer.writerows(rows)


def _write_jsonl(path: Path, rows: list[dict]):
    path.write_text("".join(json.dumps(row, ensure_ascii=False) + "\n" for row in rows), encoding="utf-8")


def _stable_id(kind: str, *parts: str) -> str:
    return str(uuid.uuid5(NAMESPACE, "|".join((kind, *map(str, parts)))))


def write_load_package(source_rows, output_dir: Path, *, include_question_mappings: bool = True) -> dict:
    output_dir = Path(output_dir); output_dir.mkdir(parents=True, exist_ok=True)
    catalog_rows = [row.to_dict() for row in curriculum_units()]
    catalog_by_id = {row["curriculum_unit_id"]: row for row in catalog_rows}
    question_rows = []
    topic_groups = defaultdict(list)
    for source in source_rows:
        result = classify_m1_question(source.unit, source.topic_code, source.topic_name, source.question_text)
        topic_key = (source_topic_key("30", standards_2022=[source.topic_code])
                     if source.dataset_id == "30" else source_topic_key(source.dataset_id, topic_code=source.topic_code))
        row = {
            "mapping_id": _stable_id("question", source.dataset_id, source.question_id),
            "dataset_id": source.dataset_id, "source_question_id": source.question_id,
            "source_grade": source.grade, "source_term": source.term, "source_unit": source.unit,
            "source_topic_key": topic_key, "source_topic_code": source.topic_code, "source_topic_name": source.topic_name,
            "curriculum_unit_id": result.curriculum_unit_id, "mapping_status": result.mapping_status,
            "mapping_confidence": result.mapping_confidence, "matched_rule": result.matched_rule,
            "mapping_reason": result.mapping_reason, "mapping_version": MAPPING_VERSION,
        }
        question_rows.append(row)
        topic_groups[topic_key].append(
            (source, result.curriculum_unit_id, result.mapping_status, result.matched_rule, result.mapping_confidence)
        )
    question_rows.sort(key=lambda x: (x["dataset_id"], x["source_question_id"]))

    topic_rows = []
    for key, results in sorted(topic_groups.items()):
        source = results[0][0]
        dataset, grade, unit, code, name = source.dataset_id, source.grade, source.unit, source.topic_code, source.topic_name
        choices = Counter((curriculum_id, status, rule) for _, curriculum_id, status, rule, _ in results)
        (curriculum_id, status, rule), dominant_count = choices.most_common(1)[0]
        dominant_confidences = [confidence for _, target, target_status, target_rule, confidence in results
                                if (target, target_status, target_rule) == (curriculum_id, status, rule)]
        alternatives = sorted({target for _, target, _, _, _ in results if target != curriculum_id})
        curriculum = catalog_by_id[curriculum_id]
        external_key = ("EBS_MATH:2022:MIDDLE:M1:MATH:SUB:"
                        f"{curriculum['major_unit_name']}>{curriculum['middle_unit_name']}>{curriculum['small_unit_name']}")
        topic_rows.append({
            "source_topic_mapping_id": _stable_id("topic", key), "source_topic_key": key,
            "dataset_id": dataset, "source_grade": grade, "source_unit": unit,
            "source_topic_code": code, "source_topic_name": name, "curriculum_unit_id": curriculum_id,
            "curriculum_external_key": external_key,
            "major_unit_name": curriculum["major_unit_name"], "middle_unit_name": curriculum["middle_unit_name"],
            "small_unit_name": curriculum["small_unit_name"],
            "mapping_status": status, "mapping_confidence": round(sum(dominant_confidences) / len(dominant_confidences), 4),
            "matched_rule": rule, "question_count": len(results), "dominant_question_count": dominant_count,
            "exception_question_count": len(results) - dominant_count, "is_ambiguous": len(choices) > 1,
            "alternative_curriculum_unit_ids": "|".join(alternatives), "mapping_version": MAPPING_VERSION,
        })

    catalog_fields = list(catalog_rows[0])
    question_fields = list(question_rows[0]) if question_rows else ["mapping_id"]
    topic_fields = list(topic_rows[0]) if topic_rows else ["source_topic_mapping_id"]
    outputs = [("curriculum_units", catalog_rows, catalog_fields),
               ("source_topic_curriculum_mappings", topic_rows, topic_fields)]
    if include_question_mappings:
        outputs.append(("source_question_curriculum_mappings", question_rows, question_fields))
    else:
        for suffix in ("csv", "jsonl"):
            stale = output_dir / f"source_question_curriculum_mappings.{suffix}"
            if stale.exists():
                stale.unlink()
    for name, rows, fields in outputs:
        _write_csv(output_dir / f"{name}.csv", rows, fields)
        _write_jsonl(output_dir / f"{name}.jsonl", rows)

    manifest = {
        "mapping_version": MAPPING_VERSION, "generated_at": datetime.now(timezone.utc).isoformat(),
        "curriculum_unit_count": len(catalog_rows), "topic_mapping_count": len(topic_rows),
        "question_mapping_count": len(question_rows),
        "dataset_counts": dict(Counter(row["dataset_id"] for row in question_rows)),
        "status_counts": dict(Counter(row["mapping_status"] for row in question_rows)),
        "curriculum_counts": dict(Counter(row["curriculum_unit_id"] or "UNMAPPED" for row in question_rows)),
        "join_key": (["dataset_id", "source_question_id"] if include_question_mappings else ["source_topic_key"]),
    }
    (output_dir / "load_manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    return manifest


def main():
    parser = argparse.ArgumentParser(description="Build PostgreSQL-ready EBS M1 curriculum mapping relations")
    parser.add_argument("--source-30", type=Path); parser.add_argument("--source-110", type=Path)
    parser.add_argument("--source-111", type=Path); parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args(); rows = []; seen = set()
    sources = (("30", args.source_30), ("110", args.source_110), ("111", args.source_111))
    if not any(path for _, path in sources): parser.error("하나 이상의 원천 경로가 필요합니다")
    for dataset, root in sources:
        if not root: continue
        iterator = iter_all_30(root) if dataset == "30" else iter_all_110_111(root, dataset)
        for row in iterator:
            key = (dataset, row.question_id)
            if key not in seen: seen.add(key); rows.append(row)
    manifest = write_load_package(rows, args.output)
    print(json.dumps(manifest, ensure_ascii=False, indent=2))


if __name__ == "__main__": main()
