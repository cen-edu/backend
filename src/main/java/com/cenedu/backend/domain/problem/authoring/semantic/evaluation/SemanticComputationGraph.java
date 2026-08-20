package com.cenedu.backend.domain.problem.authoring.semantic.evaluation;

import com.cenedu.backend.domain.problem.authoring.semantic.model.*;

import java.util.*;

public final class SemanticComputationGraph {
    public List<SemanticComputation> topologicallySort(ProblemSemanticModelV1 m) {
        var by = new LinkedHashMap<String, SemanticComputation>();
        for (var c : m.computations()) by.put(c.key(), c);
        var d = new HashMap<String, Integer>();
        for (var c : m.computations()) d.put(c.key(), 0);
        for (var c : m.computations())
            for (var o : c.operands()) if (by.containsKey(o)) d.merge(c.key(), 1, Integer::sum);
        var q = new ArrayDeque<String>();
        for (var c : m.computations()) if (d.get(c.key()) == 0) q.add(c.key());
        var out = new ArrayList<SemanticComputation>();
        while (!q.isEmpty()) {
            var k = q.remove();
            out.add(by.get(k));
            for (var c : m.computations())
                if (c.operands().contains(k) && d.merge(c.key(), -1, Integer::sum) == 0) q.add(c.key());
        }
        if (out.size() != by.size()) {
            var remaining = by.keySet().stream().filter(k -> out.stream().noneMatch(c -> c.key().equals(k))).sorted().toList();
            throw new SemanticEvaluationException("cycle: " + String.join(", ", remaining));
        }
        return out;
    }
}
