package com.cenedu.backend.ai.problem.adapter;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

/** 임시 SVG에 실행 코드나 외부 자원 참조가 들어가는 것을 저장 전에 차단한다. */
@Component
public class SafeSvgSanitizer {
    private static final Pattern FORBIDDEN = Pattern.compile(
            "<(?:script|foreignObject|iframe|object|embed|image|use)\\b|\\bon[a-z]+\\s*=|(?:href|src|style)\\s*=|javascript:|url\\s*\\(",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG = Pattern.compile("</?([A-Za-z][A-Za-z0-9]*)\\b");

    /** 제한된 SVG 요소만 포함된 문서를 반환한다. */
    public String sanitize(String svg) {
        if (svg == null || !svg.stripLeading().startsWith("<svg") || FORBIDDEN.matcher(svg).find()) {
            throw new IllegalArgumentException("안전하지 않은 SVG입니다.");
        }
        var tags = TAG.matcher(svg);
        var allowed = java.util.Set.of("svg","g","line","rect","circle","path","polyline","polygon","text","tspan","defs","marker");
        while (tags.find()) if (!allowed.contains(tags.group(1))) throw new IllegalArgumentException("허용되지 않은 SVG 요소입니다.");
        try {
            var f=DocumentBuilderFactory.newInstance(); f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true); f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true); f.setFeature("http://xml.org/sax/features/external-general-entities",false); f.setFeature("http://xml.org/sax/features/external-parameter-entities",false); f.setExpandEntityReferences(false); f.setNamespaceAware(true);
            var root=f.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(svg.getBytes(java.nio.charset.StandardCharsets.UTF_8))).getDocumentElement();
            if(!"svg".equals(root.getLocalName())&&!"svg".equals(root.getNodeName()))throw new IllegalArgumentException("SVG root가 아닙니다.");
            var allowedAttrs=java.util.Set.of("xmlns","viewBox","x","y","x1","y1","x2","y2","cx","cy","r","width","height","fill","stroke","stroke-width","stroke-dasharray","points","d","font-size","text-anchor","transform","id","marker-end","marker-start");
            var nodes=root.getElementsByTagName("*"); for(int i=0;i<nodes.getLength();i++){var e=(Element)nodes.item(i);var attrs=e.getAttributes();for(int j=0;j<attrs.getLength();j++)if(!allowedAttrs.contains(attrs.item(j).getNodeName()))throw new IllegalArgumentException("허용되지 않은 SVG 속성입니다.");}
        } catch (IllegalArgumentException e){throw e;} catch(Exception e){throw new IllegalArgumentException("SVG XML이 올바르지 않습니다.",e);}
        return svg;
    }
}
