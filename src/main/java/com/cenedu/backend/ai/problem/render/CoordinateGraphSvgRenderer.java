package com.cenedu.backend.ai.problem.render;

import com.cenedu.backend.domain.problem.authoring.diagram.*;
import com.cenedu.backend.domain.problem.authoring.semantic.evaluation.SemanticResolvedValue;
import java.util.*;

/** Renders graph functions from the evaluated coordinate bounds. */
public final class CoordinateGraphSvgRenderer {
    public String render(CoordinateGraphDiagramSpecV1 s) { return render(s, Map.of()); }
    public String render(CoordinateGraphDiagramSpecV1 s, Map<String, SemanticResolvedValue> v) {
        int p=s.viewport().padding(), w=s.viewport().width(), h=s.viewport().height();
        double xmin=num(v,s.xMinKey(),-10), xmax=num(v,s.xMaxKey(),10), ymin=num(v,s.yMinKey(),-10), ymax=num(v,s.yMaxKey(),10);
        int axisY=py(0,ymin,ymax,p,h-p), axisX=px(0,xmin,xmax,p,w-p);
        StringBuilder b=new StringBuilder("<line x1=\"").append(p).append("\" y1=\"").append(axisY).append("\" x2=\"").append(w-p).append("\" y2=\"").append(axisY).append("\" stroke=\"#000000\"/><line x1=\"").append(axisX).append("\" y1=\"").append(p).append("\" x2=\"").append(axisX).append("\" y2=\"").append(h-p).append("\" stroke=\"#000000\"/>");
        double xt=num(v,s.xTickKey(),0), yt=num(v,s.yTickKey(),0);
        if(xt>0) for(double x=Math.ceil(xmin/xt)*xt;x<=xmax;x+=xt){int q=px(x,xmin,xmax,p,w-p);b.append("<line x1=\"").append(q).append("\" y1=\"").append(axisY-3).append("\" x2=\"").append(q).append("\" y2=\"").append(axisY+3).append("\" stroke=\"#000000\"/>");}
        if(yt>0) for(double y=Math.ceil(ymin/yt)*yt;y<=ymax;y+=yt){int q=py(y,ymin,ymax,p,h-p);b.append("<line x1=\"").append(axisX-3).append("\" y1=\"").append(q).append("\" x2=\"").append(axisX+3).append("\" y2=\"").append(q).append("\" stroke=\"#000000\"/>");}
        for(var f:s.functions()) { double k=num(v,f.coefficientKey(),1); if(f.functionKind()==CoordinateFunctionKind.INVERSE_PROPORTION){b.append(path(f,p,w,h,xmin,xmax,ymin,ymax,k,Double.NEGATIVE_INFINITY,0));b.append(path(f,p,w,h,xmin,xmax,ymin,ymax,k,0,Double.POSITIVE_INFINITY));} else b.append(path(f,p,w,h,xmin,xmax,ymin,ymax,k,xmin,xmax)); }
        for(var point:s.points()){double x=num(v,point.xKey(),0),y=num(v,point.yKey(),0);b.append("<circle cx=\"").append(px(x,xmin,xmax,p,w-p)).append("\" cy=\"").append(py(y,ymin,ymax,p,h-p)).append("\" r=\"4\" fill=\"#000000\"/>");}
        return b.toString();
    }
    private String path(CoordinateFunctionSpec f,int p,int w,int h,double xmin,double xmax,double ymin,double ymax,double k,double from,double to){StringBuilder d=new StringBuilder();int n=64;for(int i=0;i<=n;i++){double x=from==Double.NEGATIVE_INFINITY?xmin+(0-xmin)*i/(double)n:to==Double.POSITIVE_INFINITY?0+(xmax)*i/(double)n:from+(to-from)*i/(double)n;if(x==0&&f.functionKind()==CoordinateFunctionKind.INVERSE_PROPORTION)continue;double y=f.functionKind()==CoordinateFunctionKind.INVERSE_PROPORTION?k/x:k*x;if(!Double.isFinite(y)||y<ymin||y>ymax)continue;d.append(d.isEmpty()?"M ":" L ").append(px(x,xmin,xmax,p,w-p)).append(' ').append(py(y,ymin,ymax,p,h-p));}return d.isEmpty()?"":"<path d=\""+d+"\" fill=\"none\" stroke=\"#000000\"/>";}
    private int px(double x,double min,double max,int a,int b){return (int)Math.round(a+(x-min)/(max-min)*(b-a));}
    private int py(double y,double min,double max,int a,int b){return (int)Math.round(b-(y-min)/(max-min)*(b-a));}
    private double num(Map<String,SemanticResolvedValue> v,String k,double d){try{return Double.parseDouble(v.get(k).canonicalValue());}catch(Exception e){return d;}}
}
