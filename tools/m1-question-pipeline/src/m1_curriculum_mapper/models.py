from dataclasses import asdict, dataclass
from pathlib import Path


@dataclass(frozen=True)
class InputLayout:
    data_root: Path
    source30: Path
    source110: Path
    source111: Path
    step_fill_accepted: Path | None = None
    step_fill_rejected: Path | None = None
    essay_accepted: Path | None = None


@dataclass(frozen=True)
class SourceQuestion:
    dataset_id: str
    source_file: str
    question_id: str
    question_filename: str
    grade: str
    term: str
    unit: str
    sector2: str
    topic_code: str
    topic_name: str
    question_text: str

    def to_dict(self) -> dict[str, str]:
        return asdict(self)


@dataclass(frozen=True)
class Classification:
    major_unit: str
    middle_unit: str
    small_unit: str
    mapping_status: str
    mapping_confidence: float
    matched_rule: str
    mapping_reason: str
    curriculum_unit_id: str = ""

    def to_dict(self) -> dict[str, str | float]:
        return asdict(self)


@dataclass(frozen=True)
class AnswerSlotPlan:
    unit_key: str
    display_prefix: str
    display_suffix: str
    answer_raw: str
    answer_normalized: str
    value_kind: str
    compare_method: str
    source_evidence: str
