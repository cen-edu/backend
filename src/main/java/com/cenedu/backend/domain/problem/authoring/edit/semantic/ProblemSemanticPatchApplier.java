package com.cenedu.backend.domain.problem.authoring.edit.semantic;

import com.cenedu.backend.domain.problem.authoring.semantic.model.ProblemSemanticModelV1;
import com.cenedu.backend.domain.problem.authoring.port.ProblemSemanticMaterializer;
import com.cenedu.backend.domain.problem.authoring.semantic.materialization.DefaultProblemSemanticMaterializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;

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
            var beforeMaterialized = materializer.materialize(model);
            for (var op: patch.operations()) applyOne(root, op, model);
            ProblemSemanticModelV1 result=mapper.treeToValue(root, ProblemSemanticModelV1.class);
            if (patch.mode()==SemanticEditMode.PRESENTATIONAL_PATCH && !beforePlaceholders.equals(placeholders(root)))
                throw new IllegalArgumentException("presentational patch가 placeholder를 변경했습니다.");
            var afterMaterialized = materializer.materialize(result);
            if (patch.mode()==SemanticEditMode.PRESENTATIONAL_PATCH
                    && !Objects.equals(beforeMaterialized.report().resolvedValues(), afterMaterialized.report().resolvedValues()))
                throw new IllegalArgumentException("presentational patch가 normalized semantic value를 변경했습니다.");
            return result;
        } catch (SemanticPatchConflictException e) { throw e; }
        catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) { throw new IllegalArgumentException("semantic patch를 적용할 수 없습니다.", e); }
    }
    private void applyOne(JsonNode root, SemanticPatchOperation op, ProblemSemanticModelV1 model) {
        if (!ProblemSemanticPatchPath.isAllowed(op.path())) throw new IllegalArgumentException("허용되지 않은 path: "+op.path());
        JsonNode target=find(root, op.path()); String actual=target==null||target.isNull()?null:target.asText();
        if (!java.util.Objects.equals(op.expectedOldValue(), actual)) throw new SemanticPatchConflictException(op.path(), op.expectedOldValue(), actual);
        if (target==null || !target.isValueNode()) throw new IllegalArgumentException("scalar path가 아닙니다: "+op.path());
        if (ProblemSemanticPatchPath.isParameter(op.path())) {
            for (JsonNode parameter : root.path("parameters")) if (op.path().contains("/" + parameter.path("key").asText() + "/")
                    && !parameter.path("editable").asBoolean()) throw new IllegalArgumentException("editable이 아닌 parameter입니다.");
        }
        if ((op.type()==SemanticPatchOperationType.SET_TEMPLATE_TEXT || op.type()==SemanticPatchOperationType.SET_LABEL_TEXT)
                && !placeholderSet(actual).equals(placeholderSet(op.newValue())) )
            throw new IllegalArgumentException("template별 placeholder가 변경되었습니다: " + op.path());
        if (op.type()==SemanticPatchOperationType.SET_DIAGRAM_STYLE && !last(op.path()).matches("strokeColor|fillColor|accentColor|strokeWidth|fontSize"))
            throw new IllegalArgumentException("허용되지 않은 diagram style field입니다.");
        ((com.fasterxml.jackson.databind.node.ObjectNode) parent(root, op.path())).put(last(op.path()), op.newValue());
    }
    private JsonNode find(JsonNode root,String path){JsonNode p=resolve(root,parts(path),false);return p;}
    private JsonNode parent(JsonNode root,String path){String[] a=parts(path);return resolve(root,java.util.Arrays.copyOf(a,a.length-1),true);}
    private JsonNode resolve(JsonNode current,String[] tokens,boolean parentMode){
        JsonNode p=current;
        for(int i=0;i<tokens.length;i++){
            String token=tokens[i];
            if(p.isArray()){
                if(token.matches("[0-9]+")){p=p.get(Integer.parseInt(token));}
                else { JsonNode found=null; for(JsonNode item:p){ for(String key:new String[]{"key","choiceKey","stepKey","rubricKey","assetKey","labelKey"}) if(token.equals(item.path(key).asText())) {found=item;break;} if(found!=null)break;} p=found; }
            } else p=p==null?null:p.path(token);
            if(p==null||p.isMissingNode()) return null;
        }
        return p;
    }
    private String last(String p){String[] a=parts(p);return a[a.length-1];}
    private String[] parts(String p){return p.substring(1).split("/");}
    private String placeholders(JsonNode node){
        java.util.Set<String> values=new java.util.TreeSet<>();
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("\\$\\{[A-Z][A-Z0-9_]*}").matcher(node.toString());
        while(m.find()) values.add(m.group()); return String.join("|", values);
    }
    private java.util.Set<String> placeholderSet(String value){
        java.util.Set<String> result=new java.util.TreeSet<>(); if(value==null)return result;
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("\\$\\{[A-Z][A-Z0-9_]*}").matcher(value); while(m.find())result.add(m.group()); return result;
    }
}
