package com.cenedu.backend.ai.verification.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.cenedu.backend.domain.problem.authoring.edit.ProblemEditInstruction;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemEditTargetRef;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.verification.EditVerificationContext;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationCheckType;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationFinding;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationIssueCode;

import org.springframework.stereotype.Component;

/**
 * 교사 수정 결과가 요청대로 반영됐는지, 건드리면 안 되는 곳을 건드렸는지 본다.
 *
 * <p>{@code EditVerificationContext} 가 있을 때만 수행한다. {@code CREATE} 요청에는 원본이 없어
 * 대조 대상이 없으므로 두 항목 모두 {@code NOT_APPLICABLE} 이다.
 *
 * <p>{@code baseSnapshot} 에도 정답이 들어 있다. 코드 판정에는 쓰되 <b>Solver 프롬프트에는 넣지
 * 않는다.</b> 이 클래스는 LLM 을 부르지 않으므로 그 경계를 넘지 않는다.
 */
@Component
public class EditScopeChecks {

    /**
     * 확정된 수정 지시가 실제로 스냅샷에 반영됐는지 본다.
     *
     * <p>판정 기준은 "그 위치의 값이 원본과 달라졌는가"다. 지시 내용대로 <b>옳게</b> 고쳤는지까지는
     * 보지 않는다 — 자연어 지시의 충족 여부는 값 대조로 판정할 수 없고, LLM 으로 보려면 원본과
     * 수정본을 한 컨텍스트에 넣어야 해서 별개의 설계가 필요하다. 여기서 잡는 것은
     * <b>지시를 무시하고 아무것도 고치지 않은 경우</b>다. 이게 재생성 판단에 가장 필요한 신호다.
     */
    public VerificationFinding editRequirement(
            QuestionSnapshotV1 candidate, EditVerificationContext context
    ) {
        if (context == null) {
            return Findings.notApplicable(VerificationCheckType.EDIT_REQUIREMENT,
                    "생성 요청에는 대조할 원본이 없습니다.");
        }
        List<ProblemEditInstruction> instructions = context.confirmedInstructions();
        if (instructions == null || instructions.isEmpty()) {
            return Findings.notApplicable(VerificationCheckType.EDIT_REQUIREMENT,
                    "확정된 수정 지시가 없습니다.");
        }

        List<String> unchanged = new ArrayList<>();
        for (ProblemEditInstruction instruction : instructions) {
            if (instruction == null || instruction.targetType() == null) {
                continue;
            }
            String before = SnapshotTargets.valueAt(
                    context.baseSnapshot(), instruction.targetType(), instruction.targetKey());
            String after = SnapshotTargets.valueAt(
                    candidate, instruction.targetType(), instruction.targetKey());
            if (Objects.equals(before, after)) {
                unchanged.add(describe(instruction.targetType().name(), instruction.targetKey()));
            }
        }

        if (unchanged.isEmpty()) {
            return Findings.pass(VerificationCheckType.EDIT_REQUIREMENT,
                    "확정된 수정 지시의 대상이 모두 원본과 달라졌습니다.");
        }
        return Findings.fail(
                VerificationCheckType.EDIT_REQUIREMENT,
                VerificationIssueCode.EDIT_REQUIREMENT_MISSING,
                "수정 지시가 반영되지 않은 대상이 " + unchanged.size() + "건 있습니다.",
                String.join(", ", unchanged));
    }

    /** 보호 범위는 값이 그대로여야 한다. 달라졌으면 허용되지 않은 수정이다. */
    public VerificationFinding protectedScope(
            QuestionSnapshotV1 candidate, EditVerificationContext context
    ) {
        if (context == null) {
            return Findings.notApplicable(VerificationCheckType.PROTECTED_SCOPE,
                    "생성 요청에는 보호 범위가 없습니다.");
        }
        List<ProblemEditTargetRef> targets = context.protectedTargets();
        if (targets == null || targets.isEmpty()) {
            return Findings.notApplicable(VerificationCheckType.PROTECTED_SCOPE,
                    "보호 대상이 지정되지 않았습니다.");
        }

        List<String> changed = new ArrayList<>();
        for (ProblemEditTargetRef target : targets) {
            if (target == null || target.targetType() == null) {
                continue;
            }
            String before = SnapshotTargets.valueAt(
                    context.baseSnapshot(), target.targetType(), target.targetKey());
            String after = SnapshotTargets.valueAt(
                    candidate, target.targetType(), target.targetKey());
            if (!Objects.equals(before, after)) {
                changed.add(describe(target.targetType().name(), target.targetKey()));
            }
        }

        if (changed.isEmpty()) {
            return Findings.pass(VerificationCheckType.PROTECTED_SCOPE,
                    "보호 대상이 원본과 동일합니다.");
        }
        return Findings.fail(
                VerificationCheckType.PROTECTED_SCOPE,
                VerificationIssueCode.PROTECTED_SCOPE_CHANGED,
                "보호 대상이 " + changed.size() + "건 변경되었습니다.",
                String.join(", ", changed));
    }

    private static String describe(String targetType, String targetKey) {
        return targetKey == null ? targetType : targetType + "(" + targetKey + ")";
    }
}
