package com.cenedu.backend.domain.problem.authoring.edit.semantic;

import com.cenedu.backend.domain.problem.authoring.semantic.model.ProblemSemanticModelV1;
import com.cenedu.backend.domain.problem.authoring.port.ProblemSemanticMaterializer;
import com.cenedu.backend.domain.problem.authoring.semantic.materialization.DefaultProblemSemanticMaterializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Iterator;

/** 허용된 semantic path만 copy-on-write로 적용하고 기존 materializer로 재검증한다. */
public class ProblemSemanticPatchApplier {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ProblemSemanticPatchClassifier classifier;
    private final ProblemSemanticMaterializer materializer;
    public ProblemSemanticPatchApplier() { this(new ProblemSemanticPatchClassifier(), new DefaultProblemSemanticMaterializer()); }
    public ProblemSemanticPatchApplier(ProblemSemanticPatchClassifier classifier, ProblemSemanticMaterializer materializer) { this.classifier=classifier; this.materializer=materializer; }
    public ProblemSemanticModelV1 apply(ProblemSemanticModelV1 model, ProblemSemanticPatch patch) {
        if (model == null || patch == null || patch.schemaVersion()!=ProblemSemanticPatch.CURRENT_SCHEMA_VERSION || patch.requestId()==null || patch.baseVersionId()==null) throw new IllegalArgumentException("invalid semantic patch");
        if (classifier.classify(patch)!=patch.mode()) throw new IllegalArgumentException("patch mode와 operation이 일치하지 않습니다.");
        if (patch.mode()!=SemanticEditMode.PRESENTATIONAL_PATCH && patch.mode()!=SemanticEditMode.PARAMETRIC_PATCH) throw new IllegalArgumentException("적용할 수 없는 patch mode입니다.");
        try {
            JsonNode root=mapper.valueToTree(model);
            String beforePlaceholders = placeholders(root);
            for (var op: patch.operations()) applyOne(root, op, model);
            ProblemSemanticModelV1 result=mapper.treeToValue(root, ProblemSemanticModelV1.class);
            if (patch.mode()==SemanticEditMode.PRESENTATIONAL_PATCH && !beforePlaceholders.equals(placeholders(root)))
                throw new IllegalArgumentException("presentational patch가 placeholder를 변경했습니다.");
            materializer.materialize(result);
            return result;
        } catch (SemanticPatchConflictException e) { throw e; }
        catch (Exception e) { throw new IllegalArgumentException("semantic patch를 적용할 수 없습니다.", e); }
    }
    private void applyOne(JsonNode root, SemanticPatchOperation op, ProblemSemanticModelV1 model) {
        if (!ProblemSemanticPatchPath.isAllowed(op.path())) throw new IllegalArgumentException("허용되지 않은 path: "+op.path());
        JsonNode target=find(root, op.path()); String actual=target==null||target.isNull()?null:target.asText();
        if (op.expectedOldValue()!=null && !java.util.Objects.equals(op.expectedOldValue(), actual)) throw new SemanticPatchConflictException(op.path(), op.expectedOldValue(), actual);
        if (target==null || !target.isValueNode()) throw new IllegalArgumentException("scalar path가 아닙니다: "+op.path());
        if (ProblemSemanticPatchPath.isParameter(op.path()) && "value".equals(last(op.path()))) {
            for (JsonNode parameter : root.path("parameters")) if (op.path().contains("/" + parameter.path("key").asText() + "/")
                    && !parameter.path("editable").asBoolean()) throw new IllegalArgumentException("editable이 아닌 parameter입니다.");
        }
        ((com.fasterxml.jackson.databind.node.ObjectNode) parent(root, op.path())).put(last(op.path()), op.newValue());
    }
    private JsonNode find(JsonNode root,String path){
        String[] a=parts(path);
        if(a.length==3 && "parameters".equals(a[0])) { JsonNode list=root.path("parameters"); for(JsonNode item:list) if(a[1].equals(item.path("key").asText())) return item.path(a[2]); return null; }
        JsonNode p=root;for(String s:a){p=p.path(s);if(p.isMissingNode())return null;}return p;
    }
    private JsonNode parent(JsonNode root,String path){
        String[] a=parts(path);
        if(a.length==3 && "parameters".equals(a[0])) { for(JsonNode item:root.path("parameters")) if(a[1].equals(item.path("key").asText())) return item; }
        JsonNode p=root;for(int i=0;i<a.length-1;i++){JsonNode n=p.path(a[i]);if(n.isArray()){int idx=Integer.parseInt(a[++i]);n=n.get(idx);}p=n;}return p;
    }
    private String last(String p){String[] a=parts(p);return a[a.length-1];}
    private String[] parts(String p){return p.substring(1).split("/");}
    private String placeholders(JsonNode node){
        java.util.Set<String> values=new java.util.TreeSet<>();
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("\\$\\{[A-Z][A-Z0-9_]*}").matcher(node.toString());
        while(m.find()) values.add(m.group()); return String.join("|", values);
    }
}
