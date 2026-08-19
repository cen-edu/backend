package com.cenedu.backend.ai.problem.render;

import com.cenedu.backend.domain.problem.authoring.diagram.*;
import com.cenedu.backend.domain.problem.authoring.semantic.evaluation.SemanticResolvedValue;
import java.util.*;

/** Renders plane geometry using resolved semantic coordinates and measurements. */
public final class PlaneGeometrySvgRenderer {
    public String render(PlaneGeometryDiagramSpecV1 s) { return render(s, Map.of()); }
    public String render(PlaneGeometryDiagramSpecV1 s, Map<String, SemanticResolvedValue> v) {
        int p=s.viewport().padding(); StringBuilder b=new StringBuilder("<rect x=\"").append(p).append("\" y=\"").append(p).append("\" width=\"").append(s.viewport().width()-2*p).append("\" height=\"").append(s.viewport().height()-2*p).append("\" fill=\"none\" stroke=\"#000000\"/>");
        Map<String,int[]> points=new LinkedHashMap<>();
        for(var q:s.points()){int x=coord(p,v,q.xKey()),y=coord(p,v,q.yKey());points.put(q.pointKey(),new int[]{x,y});b.append("<circle cx=\"").append(x).append("\" cy=\"").append(y).append("\" r=\"3\" fill=\"#000000\"/>");label(b,x+4,y-4,q.labelTemplate());}
        for(var q:s.segments()) line(b,points.get(q.startPointKey()),points.get(q.endPointKey()));
        for(var q:s.polygons()){StringBuilder d=new StringBuilder();for(String k:q.pointKeys()){int[] z=points.get(k);if(z!=null)d.append(d.isEmpty()?"M":" L").append(z[0]).append(' ').append(z[1]);}if(!d.isEmpty())b.append("<path d=\"").append(d).append(" Z\" fill=\"").append(q.filled()?"#EEEEEE":"none").append("\" stroke=\"#000000\"/>");}
        for(var q:s.circles()){int[] c=points.get(q.centerPointKey());if(c!=null)b.append("<circle cx=\"").append(c[0]).append("\" cy=\"").append(c[1]).append("\" r=\"").append(radius(v,q.radiusKey())).append("\" fill=\"none\" stroke=\"#000000\"/>");}
        for(var q:s.arcs()){int[] c=points.get(q.centerPointKey());if(c==null)continue;double r=radius(v,q.radiusKey()),a=num(v,q.startAngleKey(),0)*Math.PI/180,e=num(v,q.endAngleKey(),90)*Math.PI/180;int x1=(int)Math.round(c[0]+r*Math.cos(a)),y1=(int)Math.round(c[1]-r*Math.sin(a)),x2=(int)Math.round(c[0]+r*Math.cos(e)),y2=(int)Math.round(c[1]-r*Math.sin(e));b.append("<path d=\"M ").append(x1).append(' ').append(y1).append(" A ").append(r).append(' ').append(r).append(" 0 0 0 ").append(x2).append(' ').append(y2).append("\" fill=\"none\" stroke=\"#000000\"/>");}
        for(var q:s.angles()){int[] c=points.get(q.vertexPointKey()),a=points.get(q.startPointKey()),e=points.get(q.endPointKey());if(c!=null&&a!=null&&e!=null){b.append("<path d=\"M ").append(c[0]+(int)Math.signum(a[0]-c[0])*12).append(' ').append(c[1]+(int)Math.signum(a[1]-c[1])*12).append(" A 12 12 0 0 0 ").append(c[0]+(int)Math.signum(e[0]-c[0])*12).append(' ').append(c[1]+(int)Math.signum(e[1]-c[1])*12).append("\" fill=\"none\" stroke=\"#000000\"/>");label(b,c[0]+8,c[1]+16,text(v,q.angleValueKey(),q.labelTemplate()));}}
        for(var q:s.measurements())label(b,p+8,s.viewport().height()-p-8,text(v,q.valueKey(),q.labelTemplate())); return b.toString();
    }
    private static void line(StringBuilder b,int[] a,int[] c){if(a!=null&&c!=null)b.append("<line x1=\"").append(a[0]).append("\" y1=\"").append(a[1]).append("\" x2=\"").append(c[0]).append("\" y2=\"").append(c[1]).append("\" stroke=\"#000000\"/>");}
    private static void label(StringBuilder b,int x,int y,String t){if(t!=null&&!t.isBlank())b.append("<text x=\"").append(x).append("\" y=\"").append(y).append("\">").append(t).append("</text>");}
    private static String text(Map<String,SemanticResolvedValue> v,String k,String d){return k!=null&&v.containsKey(k)?v.get(k).canonicalValue():d;}
    private static int coord(int p,Map<String,SemanticResolvedValue> v,String k){return p+20+(int)(num(v,k,0)*10);}
    private static int radius(Map<String,SemanticResolvedValue> v,String k){return Math.max(4,(int)(num(v,k,2)*10));}
    private static double num(Map<String,SemanticResolvedValue> v,String k,double d){try{return Double.parseDouble(v.get(k).canonicalValue());}catch(Exception e){return d;}}
}
