package com.cenedu.backend.domain.problem.authoring.semantic.materialization;
import java.util.*;
public record SemanticMaterializationReport(int semanticSchemaVersion,List<String> topologicalOrder,Map<String,String> resolvedValues,Set<String> placeholderKeys,Set<String> diagramAssetKeys){}
