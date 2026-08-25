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
        if (patch.mode()!=SemanticEditMode.PRESENTATIONAL_PATCH && patch.mode()!=SemanticEditMode.PARAMETRIC_PATCH
                && patch.mode()!=SemanticEditMode.CHOICE_REORDER) throw new IllegalArgumentException("적용할 수 없는 patch mode입니다.");
        try {
            JsonNode root=mapper.valueToTree(model);
            String beforePlaceholders = placeholders(root);
            var beforeMaterialized = materializer.materialize(model);
            for (var op: patch.operations()) applyOne(root, op, model);
            ProblemSemanticModelV1 result=mapper.treeToValue(root, ProblemSemanticModelV1.class);
            if (patch.mode()==SemanticEditMode.CHOICE_REORDER) requireReorderOnly(model, result);
            if (patch.mode()==SemanticEditMode.PRESENTATIONAL_PATCH && !beforePlaceholders.equals(placeholders(root)))
                throw new IllegalArgumentException("presentational patch가 placeholder를 변경했습니다.");
            var afterMaterialized = materializer.materialize(result);
            if ((patch.mode()==SemanticEditMode.PRESENTATIONAL_PATCH || patch.mode()==SemanticEditMode.CHOICE_REORDER)
                    && !Objects.equals(beforeMaterialized.report().resolvedValues(), afterMaterialized.report().resolvedValues()))
                throw new IllegalArgumentException("계산된 semantic value를 바꾸지 않아야 하는 patch가 값을 변경했습니다.");
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
        var parent=(com.fasterxml.jackson.databind.node.ObjectNode) parent(root, op.path());
        if (op.type()==SemanticPatchOperationType.SET_CHOICE_ORDER) parent.put(last(op.path()), parseOrder(op));
        else parent.put(last(op.path()), op.newValue());
    }
    /**
     * 보기 순서 변경이 정말 순서만 바꿨는지 확인한다.
     *
     * <p>path 검증만으로는 부족하다. 순서 변경 자체는 정답이 가리키는 보기 키를 바꾸는 수정이라
     * 표현 수정의 "정답 단위 불변" 검사를 적용할 수 없고, 그 대신 여기서 보기 집합이 그대로이고
     * displayOrder가 올바른 순열인지를 불변식으로 삼는다.
     */
    private void requireReorderOnly(ProblemSemanticModelV1 before, ProblemSemanticModelV1 after) {
        if (before.intent()==null || before.intent().questionType()!=com.cenedu.backend.global.common.enums.QuestionType.MULTIPLE_CHOICE)
            throw new IllegalArgumentException("보기 순서는 객관식에서만 바꿀 수 있습니다.");
        var beforeChoices=before.presentation().choices();
        var afterChoices=after.presentation().choices();
        if (beforeChoices.size()!=afterChoices.size() || afterChoices.isEmpty())
            throw new IllegalArgumentException("보기 순서 변경은 보기 개수를 바꿀 수 없습니다.");
        var beforeByKey=new java.util.HashMap<String, com.cenedu.backend.domain.problem.authoring.semantic.model.SemanticChoiceTemplate>();
        beforeChoices.forEach(choice -> beforeByKey.put(choice.choiceKey(), choice));
        var orders=new java.util.TreeSet<Integer>();
        boolean moved=false;
        for (var choice : afterChoices) {
            var original=beforeByKey.get(choice.choiceKey());
            if (original==null || !Objects.equals(original.contentTemplate(), choice.contentTemplate())
                    || !Objects.equals(original.valueKey(), choice.valueKey()))
                throw new IllegalArgumentException("보기 순서 변경은 보기 내용을 바꿀 수 없습니다.");
            if (original.displayOrder()!=choice.displayOrder()) moved=true;
            orders.add(choice.displayOrder());
        }
        if (orders.size()!=afterChoices.size() || orders.first()!=0 || orders.last()!=afterChoices.size()-1)
            throw new IllegalArgumentException("보기 displayOrder는 0부터 중복 없이 연속이어야 합니다.");
        // 순서가 그대로면 아무것도 바뀌지 않은 새 버전이 생긴다. 교사에게는 수정이 된 것처럼
        // 보이지만 문항은 그대로이므로, 조용히 통과시키지 않고 요청을 되돌린다.
        if (!moved) throw new IllegalArgumentException("보기 순서가 현재와 같습니다.");
    }

    private int parseOrder(SemanticPatchOperation op) {
        try { return Integer.parseInt(op.newValue().trim()); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("보기 displayOrder는 정수여야 합니다: "+op.newValue()); }
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
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("\\{\\{[A-Z][A-Z0-9_]*(?:_UNIT)?\\}\\}").matcher(node.toString());
        while(m.find()) values.add(m.group()); return String.join("|", values);
    }
    private java.util.Set<String> placeholderSet(String value){
        java.util.Set<String> result=new java.util.TreeSet<>(); if(value==null)return result;
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("\\{\\{[A-Z][A-Z0-9_]*(?:_UNIT)?\\}\\}").matcher(value); while(m.find())result.add(m.group()); return result;
    }
}
