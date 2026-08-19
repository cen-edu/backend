package com.cenedu.backend.ai.problem.render;

import com.cenedu.backend.domain.problem.authoring.diagram.*;
import com.cenedu.backend.domain.problem.authoring.semantic.evaluation.SemanticResolvedValue;
import java.util.*;

/** Renders deterministic oblique projections for each supported solid family. */
public final class SolidGeometrySvgRenderer {
    public String render(SolidGeometryDiagramSpecV1 s){return render(s,Map.of());}
    public String render(SolidGeometryDiagramSpecV1 s,Map<String,SemanticResolvedValue> v){
        int p=s.viewport().padding(), w=scaled(v,s.widthKey(),s.viewport().width()/3), d=scaled(v,s.depthKey(),42), h=scaled(v,s.heightKey(),s.viewport().height()/2), slant=scaled(v,s.slantHeightKey(),h), r=scaled(v,s.radiusKey(),Math.min(w,h)/3), x=p+20, base=s.viewport().height()-p-10, top=base-(s.solidKind()==SolidGeometryKind.CONE||s.solidKind()==SolidGeometryKind.PYRAMID?Math.min(h,slant):h);
        StringBuilder b=new StringBuilder();
        switch(s.solidKind()){
            case CYLINDER -> { b.append("<ellipse cx=\"").append(x+w/2).append("\" cy=\"").append(top).append("\" rx=\"").append(r).append("\" ry=\"12\" fill=\"none\" stroke=\"#000000\"/>"); b.append("<line x1=\"").append(x+w/2-r).append("\" y1=\"").append(top).append("\" x2=\"").append(x+w/2-r).append("\" y2=\"").append(base).append("\" stroke=\"#000000\"/><line x1=\"").append(x+w/2+r).append("\" y1=\"").append(top).append("\" x2=\"").append(x+w/2+r).append("\" y2=\"").append(base).append("\" stroke=\"#000000\"/><ellipse cx=\"").append(x+w/2).append("\" cy=\"").append(base).append("\" rx=\"").append(r).append("\" ry=\"12\" fill=\"none\" stroke=\"#000000\"/>"); }
            case SPHERE -> b.append("<circle cx=\"").append(x+w/2).append("\" cy=\"").append(top+h/2).append("\" r=\"").append(r).append("\" fill=\"none\" stroke=\"#000000\"/>");
            case CONE, PYRAMID -> { int apex=x+w/2; b.append("<polygon points=\"").append(x).append(',').append(base).append(' ').append(x+w).append(',').append(base).append(' ').append(x+w-d).append(',').append(top+20).append(' ').append(x+d).append(',').append(top).append("\" fill=\"none\" stroke=\"#000000\"/>"); b.append("<line x1=\"").append(apex).append("\" y1=\"").append(top).append("\" x2=\"").append(x).append("\" y2=\"").append(base).append("\" stroke=\"#000000\" stroke-dasharray=\"4 3\"/>"); }
            default -> { b.append("<polygon points=\"").append(x).append(',').append(base).append(' ').append(x+w).append(',').append(base).append(' ').append(x+w-d).append(',').append(top+20).append(' ').append(x+d).append(',').append(top).append("\" fill=\"none\" stroke=\"#000000\"/>"); b.append("<line x1=\"").append(x).append("\" y1=\"").append(base).append("\" x2=\"").append(x+d).append("\" y2=\"").append(top).append("\" stroke=\"#000000\" stroke-dasharray=\"4 3\"/>"); }
        }
        int labelY=Math.max(p+14,top+18);for(var label:s.labels()){String t=label.labelTemplate();if(v.containsKey(label.valueKey()))t+=" "+v.get(label.valueKey()).canonicalValue();b.append("<text x=\"").append(x+8).append("\" y=\"").append(labelY).append("\">").append(escape(t)).append("</text>");labelY+=16;}
        return b.toString();
    }
    private int scaled(Map<String,SemanticResolvedValue> v,String k,int d){try{return Math.max(8,Integer.parseInt(v.get(k).canonicalValue())*10);}catch(Exception e){return Math.max(8,d);}}
    private String escape(String s){return s==null?"":s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
}
