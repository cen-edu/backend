package com.cenedu.backend.domain.problem.authoring.semantic.evaluation;

import com.cenedu.backend.domain.problem.authoring.semantic.model.*;
import com.cenedu.backend.domain.problem.authoring.semantic.validation.*;

import java.util.*;

public final class SemanticComputationEngine {
    public SemanticEvaluation evaluate(ProblemSemanticModelV1 m) {
        var order = new SemanticComputationGraph().topologicallySort(m);
        var values = new LinkedHashMap<String, SemanticResolvedValue>();
        for (var p : m.parameters()) values.put(p.key(), new SemanticResolvedValue(p.valueType(), p.value(), p.unit()));
        for (var c : order) {
            var n = calc(c, values);
            values.put(c.key(), new SemanticResolvedValue(SemanticValueType.RATIONAL, n.canonical(), c.unit()));
        }
        return new SemanticEvaluation(m, Map.copyOf(values), order.stream().map(SemanticComputation::key).toList());
    }

    private SemanticNumber calc(SemanticComputation c, Map<String, SemanticResolvedValue> v) {
        var operands = c.operands() == null ? List.<String>of() : c.operands();
        var a = operands.stream().map(k -> {
            var value = v.get(k);
            if (value == null) throw new SemanticEvaluationException(
                    "계산 key=" + c.key() + "의 operand=" + k + " resolved value가 없습니다.");
            return SemanticNumber.parse(value.canonicalValue());
        }).toList();
        int required = switch (c.operation()) {
            case IDENTITY, NEGATE, ABS -> 1;
            case ADD, SUBTRACT, MULTIPLY, DIRECT_PROPORTION, DIVIDE, INVERSE_PROPORTION -> 2;
            default -> 0;
        };
        if (a.size() < required) {
            throw new SemanticEvaluationException("계산 key=" + c.key() + " operation=" + c.operation()
                    + "에 필요한 operand 수=" + required + ", 실제=" + a.size());
        }
        return switch (c.operation()) {
            case IDENTITY -> a.get(0);
            case ADD -> a.get(0).add(a.get(1));
            case SUBTRACT -> a.get(0).sub(a.get(1));
            case MULTIPLY, DIRECT_PROPORTION -> a.get(0).mul(a.get(1));
            case DIVIDE, INVERSE_PROPORTION -> a.get(0).div(a.get(1));
            case NEGATE -> a.get(0).neg();
            case ABS -> new SemanticNumber(a.get(0).numerator().abs(), a.get(0).denominator());
            case SUM -> a.stream().reduce(SemanticNumber::add).orElseThrow();
            case PRODUCT -> a.stream().reduce(SemanticNumber::mul).orElseThrow();
            default -> throw new SemanticEvaluationException("unsupported operation: " + c.operation());
        };
    }
}
