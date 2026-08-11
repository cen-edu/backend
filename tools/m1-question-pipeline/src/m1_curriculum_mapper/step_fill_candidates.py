from __future__ import annotations

from .derivations import is_probability_mapping, select_probability_step_fill


def select_candidates(rows: list[dict], baseline_110_refs: set[str] | list[dict]) -> list[dict]:
    """Select STEP_FILL candidates, using content deduplication when 110 rows are available."""
    selected, _ = select_candidates_with_audit(rows, baseline_110_refs)
    return selected


def select_candidates_with_audit(rows: list[dict], baseline_110_refs: set[str] | list[dict]) -> tuple[list[dict], list[dict]]:
    """Return candidates plus exact 111/110 duplicate audit rows for persisted rejection output."""
    baseline_rows = [row for row in baseline_110_refs if isinstance(row, dict)] if isinstance(baseline_110_refs, list) else []
    baseline_refs = {
        str(row.get("sourceRef") or "") if isinstance(row, dict) else str(row)
        for row in baseline_110_refs
    }
    probability_rows = [row for row in rows if str(row.get("sourceRef") or "").startswith("111:")]
    probability_selected = {}
    duplicate_audits = []
    if baseline_rows:
        selected_probability, duplicates = select_probability_step_fill(probability_rows, baseline_rows)
        probability_selected = {row["sourceRef"]: row for row in selected_probability}
        duplicate_audits = [{
            "sourceRef": row["sourceRef"], "status": "REJECTED",
            "issues": [{"code": "DUPLICATE_110_CONTENT", "message": "110 문항과 정확한 내용 fingerprint가 일치합니다.", "path": "contentFingerprint"}],
            "stepFillDuplicateEvidence": row["stepFillDuplicateEvidence"],
        } for row in duplicates]

    selected = []
    for row in rows:
        source_ref = str(row.get("sourceRef") or "")
        reason = None
        if source_ref.startswith("110:") and source_ref not in baseline_refs:
            reason = "NEW_110_NOT_IN_BASELINE"
        elif source_ref.startswith("111:") and is_probability_mapping(row):
            if baseline_rows:
                selected_row = probability_selected.get(source_ref)
                if selected_row is not None:
                    selected.append(selected_row)
                continue
            else:
                reason = "111_DATA_AND_PROBABILITY"
        if reason:
            selected.append({**row, "stepFillSelectionReason": reason})
    return sorted(selected, key=lambda row: row["sourceRef"]), sorted(duplicate_audits, key=lambda row: row["sourceRef"])
