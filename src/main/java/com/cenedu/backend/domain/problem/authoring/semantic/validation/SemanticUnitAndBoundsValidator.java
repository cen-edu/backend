package com.cenedu.backend.domain.problem.authoring.semantic.validation;
import com.cenedu.backend.domain.problem.authoring.semantic.model.*; import java.math.BigDecimal; import java.util.*;
public final class SemanticUnitAndBoundsValidator {
 public void appendDefinitionViolations(ProblemSemanticModelV1 m,List<String> v){ for(int i=0;i<m.parameters().size();i++){var p=m.parameters().get(i); if(p.key()==null||!p.key().matches("[A-Z][A-Z0-9_]{0,63}"))v.add("parameters["+i+"]: 논리 키 형식이 잘못되었습니다."); if(p.bounds()!=null){try{var min=new BigDecimal(p.bounds().minInclusive());var max=new BigDecimal(p.bounds().maxInclusive());var cur=new BigDecimal(p.value());if(min.compareTo(max)>0||cur.compareTo(min)<0||cur.compareTo(max)>0)v.add("parameters["+i+"].bounds: 값이 범위를 벗어났습니다.");}catch(Exception e){v.add("parameters["+i+"].bounds: 숫자 범위가 잘못되었습니다.");}}} }
}
