import argparse
import csv
import html
import json
from collections import Counter, defaultdict
from pathlib import Path


def _read_csv(path: Path):
    with path.open(encoding="utf-8-sig") as handle:
        return list(csv.DictReader(handle))


def _read_jsonl(path: Path):
    with path.open(encoding="utf-8") as handle:
        return [json.loads(line) for line in handle if line.strip()]


def build_html_report(run_dir: Path, output_path: Path):
    import plotly.graph_objects as go

    run_dir = Path(run_dir)
    catalog = _read_csv(run_dir / "reference" / "curriculum_units.csv")
    questions = _read_jsonl(run_dir / "03_learning_guided_questions.jsonl")
    manifest = json.loads((run_dir / "pipeline_manifest.json").read_text(encoding="utf-8"))
    unit_lookup = {row["curriculum_unit_id"]: row for row in catalog}
    datasets = sorted({row["sourceRef"].partition(":")[0] for row in questions})

    dataset_counts = Counter(row["sourceRef"].partition(":")[0] for row in questions)
    fig_dataset = go.Figure(go.Bar(x=datasets, y=[dataset_counts[x] for x in datasets],
                                   text=[dataset_counts[x] for x in datasets], textposition="auto"))
    fig_dataset.update_layout(title="원천 데이터셋별 중1 문항 수", template="plotly_white")

    ordered_units = [row["curriculum_unit_id"] for row in sorted(catalog, key=lambda x: int(x["display_order"]))]
    fig_small = go.Figure()
    for dataset in datasets:
        counts = Counter(row.get("sourceMetadata", {}).get("curriculumUnitId") for row in questions
                         if row["sourceRef"].startswith(dataset + ":"))
        fig_small.add_bar(name=dataset, x=[unit_lookup[x]["small_unit_name"] for x in ordered_units],
                          y=[counts[x] for x in ordered_units])
    fig_small.update_layout(title="데이터셋별 EBS 소단원 분배", barmode="stack", template="plotly_white",
                            xaxis_tickangle=-35, height=650)

    stages = Counter(row.get("pipelineStage", "UNKNOWN") for row in questions)
    fig_stage = go.Figure(go.Pie(labels=list(stages), values=list(stages.values()), hole=0.45))
    fig_stage.update_layout(title="파이프라인 처리 상태", template="plotly_white")

    guide_status = Counter("LearningGuide 있음" if row.get("learningGuide") else "LearningGuide 없음" for row in questions)
    fig_guide = go.Figure(go.Bar(x=list(guide_status), y=list(guide_status.values()), text=list(guide_status.values()), textposition="auto"))
    fig_guide.update_layout(title="LearningGuide 생성 현황", template="plotly_white")

    major_counts = defaultdict(int)
    for row in questions:
        target = unit_lookup.get(row.get("sourceMetadata", {}).get("curriculumUnitId"), {})
        major_counts[target.get("major_unit_name", "UNMAPPED")] += 1
    cards = "".join(f'<div class="card"><strong>{html.escape(name)}</strong><span>{count:,}</span></div>'
                    for name, count in major_counts.items())
    issues = [
        "30번 단원 조인은 2022 성취기준 기반 sourceTopicKey 한 컬럼을 사용합니다.",
        "110·111은 데이터셋 접두사가 포함된 sourceTopicKey를 사용해 토픽 코드 충돌을 방지합니다.",
        "정답이 없는 110·111 원천의 answerSpec은 이번 단계에서 임의 생성하지 않습니다.",
        "STEP_FILL과 answerType 보강은 LearningGuide 이후 별도 단계에서 수행합니다.",
    ]
    figures = [fig_dataset, fig_small, fig_stage, fig_guide]
    figure_html = "".join(fig.to_html(full_html=False, include_plotlyjs=True if i == 0 else False)
                          for i, fig in enumerate(figures))
    document = f"""<!doctype html><html lang="ko"><head><meta charset="utf-8">
<title>중1 수학 문제 파이프라인 보고서</title><style>
body{{font-family:Arial,'Apple SD Gothic Neo',sans-serif;max-width:1500px;margin:30px auto;padding:0 24px;color:#172033}}
.cards{{display:flex;gap:12px;flex-wrap:wrap;margin:24px 0}} .card{{border:1px solid #d0d5dd;border-radius:10px;padding:14px 18px;min-width:190px;display:flex;flex-direction:column}}
.card span{{font-size:26px;margin-top:5px}} .note{{background:#f8fafc;border-left:4px solid #64748b;padding:12px 20px}}
</style></head><body><h1>중1 수학 문제 파이프라인</h1>
<p>버전 {html.escape(manifest['pipelineVersion'])} · 총 {len(questions):,}문항 · 단원 매핑 및 LearningGuide 생성 현황</p>
<div class="cards">{cards}</div><div class="note"><h2>해석 시 주의사항</h2><ul>{''.join(f'<li>{html.escape(x)}</li>' for x in issues)}</ul></div>
{figure_html}</body></html>"""
    output_path = Path(output_path); output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(document, encoding="utf-8")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--run", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args(); build_html_report(args.run, args.output); print(args.output)


if __name__ == "__main__":
    main()
