from __future__ import annotations

import argparse
import json
from datetime import datetime, timezone
from pathlib import Path

from .derivations import resolve_essay_cache, sample_essay_candidates
from .finalization import run_finalization
from .pipeline import run_pipeline


def _read_jsonl(path: Path | None) -> list[dict]:
    if path is None or not Path(path).is_file():
        return []
    return [json.loads(line) for line in Path(path).read_text(encoding="utf-8").splitlines() if line.strip()]


def _write_jsonl(path: Path, rows: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("".join(json.dumps(row, ensure_ascii=False) + "\n" for row in rows), encoding="utf-8")


def run_delivery(source30: Path, source110: Path, source111: Path,
                 step_fill_accepted: Path, step_fill_rejected: Path,
                 output_dir: Path, *, essay_accepted: Path | None = None,
                 essay_limit: int = 20, seed: int = 20260810) -> dict:
    output_dir = Path(output_dir)
    pipeline_dir = output_dir / "pipeline"
    final_dir = output_dir / "final"
    pipeline_manifest = run_pipeline(source30, source110, source111, pipeline_dir)
    guided_path = pipeline_dir / "03_learning_guided_questions.jsonl"
    guided_rows = _read_jsonl(guided_path)
    essay_candidates = sample_essay_candidates([row for row in guided_rows if str(row.get("sourceRef") or "").startswith("111:")],
                                                limit=essay_limit, seed=seed)
    essay_rows, essay_rejected = resolve_essay_cache(essay_candidates, _read_jsonl(essay_accepted))
    production_rows = [*guided_rows, *essay_rows]
    production_path = pipeline_dir / "04_questions_with_essays.jsonl"
    _write_jsonl(pipeline_dir / "04_essay_candidates.jsonl", essay_candidates)
    _write_jsonl(pipeline_dir / "04_essay_accepted.jsonl", essay_rows)
    _write_jsonl(pipeline_dir / "04_essay_rejected.jsonl", essay_rejected)
    _write_jsonl(production_path, production_rows)
    final_manifest = run_finalization(
        production_path,
        step_fill_accepted,
        step_fill_rejected,
        final_dir,
        source30=source30,
        source110=source110,
        source111=source111,
    )
    manifest = {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "pipeline": pipeline_manifest,
        "essay": {
            "sampled": len(essay_candidates), "accepted": len(essay_rows), "rejected": len(essay_rejected),
            "cacheReused": sum(row.get("essayCacheStatus") == "REUSED" for row in essay_rows),
            "limit": essay_limit, "seed": seed, "apiCalls": 0,
        },
        "finalization": final_manifest,
        "finalContract": "final/final_questions.jsonl",
        "rejected": "final/final_questions_rejected.jsonl",
        "localAssetRoot": "final/questions",
        "storageKeyRoot": "questions/",
    }
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / "delivery_manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    return manifest


def main() -> None:
    raw_parser = argparse.ArgumentParser(add_help=False)
    raw_parser.add_argument("--data-root", type=Path)
    raw_parser.add_argument("--output", type=Path)
    raw_parser.add_argument("--allow-api-generation", action="store_true")
    raw_parser.add_argument("--essay-limit", type=int, default=20)
    raw_parser.add_argument("--seed", type=int, default=20260810)
    raw_parser.add_argument("--reference-final-datashape", type=Path)
    raw_args, _ = raw_parser.parse_known_args()
    if raw_args.data_root is not None:
        if raw_args.output is None:
            raw_parser.error("--output is required with --data-root")
        from .build import run_build
        print(json.dumps(run_build(raw_args.data_root, raw_args.output,
                                   allow_api_generation=raw_args.allow_api_generation,
                                   essay_limit=raw_args.essay_limit, seed=raw_args.seed,
                                   reference_final_datashape=raw_args.reference_final_datashape),
                         ensure_ascii=False, indent=2))
        return
    parser = argparse.ArgumentParser(description="30·110·111 원천과 저장된 STEP_FILL 결과로 최종 적재 패키지를 생성합니다")
    parser.add_argument("--source-30", type=Path, required=True)
    parser.add_argument("--source-110", type=Path, required=True)
    parser.add_argument("--source-111", type=Path, required=True)
    parser.add_argument("--step-fill-accepted", type=Path, required=True)
    parser.add_argument("--step-fill-rejected", type=Path, required=True)
    parser.add_argument("--essay-accepted", type=Path)
    parser.add_argument("--essay-limit", type=int, default=20)
    parser.add_argument("--seed", type=int, default=20260810)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(run_delivery(args.source_30, args.source_110, args.source_111,
                                  args.step_fill_accepted, args.step_fill_rejected, args.output,
                                  essay_accepted=args.essay_accepted, essay_limit=args.essay_limit, seed=args.seed),
                     ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
