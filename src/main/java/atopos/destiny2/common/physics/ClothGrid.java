package atopos.destiny2.common.physics;

import java.util.ArrayList;
import java.util.List;

/** Pure numerical PBD cloth; invoked only by the client renderer, never by server ticks. */
public final class ClothGrid {
    public record Point(double x, double y, double z) {
        public Point add(Point p) { return new Point(x+p.x,y+p.y,z+p.z); }
        public Point sub(Point p) { return new Point(x-p.x,y-p.y,z-p.z); }
        public Point mul(double s) { return new Point(x*s,y*s,z*s); }
        public double dot(Point p) { return x*p.x+y*p.y+z*p.z; }
        public double length() { return Math.sqrt(dot(this)); }
        public Point lerp(Point b,double t) { return mul(1-t).add(b.mul(t)); }
    }
    @FunctionalInterface public interface Collision { Point project(Point point); }
    public record Capsule(Point a, Point b, double radius) implements Collision {
        public Point project(Point p) {
            Point ab=b.sub(a);
            double t=Math.clamp(p.sub(a).dot(ab)/Math.max(1e-12,ab.dot(ab)),0,1);
            Point center=a.add(ab.mul(t)), d=p.sub(center);
            double len=d.length();
            return len>=radius?p:center.add(len<1e-9?new Point(radius,0,0):d.mul(radius/len));
        }
    }
    public record Box(double x0,double y0,double z0,double x1,double y1,double z1) implements Collision {
        public Point project(Point p) {
            if(p.x<=x0||p.x>=x1||p.y<=y0||p.y>=y1||p.z<=z0||p.z>=z1)return p;
            double[] d={p.x-x0,x1-p.x,p.y-y0,y1-p.y,p.z-z0,z1-p.z};
            int k=0;for(int i=1;i<6;i++)if(d[i]<d[k])k=i;
            return switch(k){case 0->new Point(x0,p.y,p.z);case 1->new Point(x1,p.y,p.z);
                case 2->new Point(p.x,y0,p.z);case 3->new Point(p.x,y1,p.z);
                case 4->new Point(p.x,p.y,z0);default->new Point(p.x,p.y,z1);};
        }
    }
    private record Link(int a,int b,double length,double stiffness) {}
    public final int columns, rows;
    private final Point[] p, previous, lastTarget;
    private final List<Link> links=new ArrayList<>();
    private double accumulator;
    public static final double STEP=1.0/60;

    public ClothGrid(int columns,int rows,Point[] targets) {
        if(columns<2||rows<2||targets.length!=columns*rows)throw new IllegalArgumentException("grid dimensions");
        this.columns=columns;this.rows=rows;p=targets.clone();previous=targets.clone();lastTarget=targets.clone();
        for(int y=0;y<rows;y++)for(int x=0;x<columns;x++){
            int a=y*columns+x;
            if(x+1<columns)link(a,a+1,1);
            if(y+1<rows)link(a,a+columns,1);
            if(x+1<columns&&y+1<rows){link(a,a+columns+1,.85);link(a+1,a+columns,.85);}
            if(y+2<rows)link(a,a+2*columns,.22);
            if(x+2<columns)link(a,a+2,.22);
        }
    }
    private void link(int a,int b,double stiffness){links.add(new Link(a,b,p[a].sub(p[b]).length(),stiffness));}
    public Point point(int index){return p[index];}
    public void reset(Point[] target){
        System.arraycopy(target,0,p,0,p.length);System.arraycopy(target,0,previous,0,p.length);
        System.arraycopy(target,0,lastTarget,0,p.length);accumulator=0;
    }
    public void advance(double elapsed,Point[] target,List<? extends Collision> collisions){
        if(!Double.isFinite(elapsed)||elapsed<0||target.length!=p.length)throw new IllegalArgumentException("step");
        if(elapsed>.25||lastTarget[0].sub(target[0]).length()>4){reset(target);return;}
        if(elapsed==0)return;
        double oldAccumulator=accumulator;accumulator+=elapsed;
        int steps=(int)Math.floor((accumulator+1e-10)/STEP);
        for(int step=0;step<steps;step++){
            double alpha=Math.clamp(((step+1)*STEP-oldAccumulator)/elapsed,0,1);
            for(int i=0;i<columns;i++)p[i]=lastTarget[i].lerp(target[i],alpha);
            for(int i=columns;i<p.length;i++){
                Point old=p[i];
                // Damped Verlet velocity retains inertia when anchors turn or stop.
                p[i]=p[i].add(p[i].sub(previous[i]).mul(.985)).add(new Point(0,-9.81*STEP*STEP,0));
                previous[i]=old;
            }
            for(int pass=0;pass<10;pass++){
                for(Link l:links){
                    Point d=p[l.b].sub(p[l.a]);double len=d.length();if(len<1e-10)continue;
                    double wa=l.a<columns?0:1,wb=l.b<columns?0:1;
                    if(wa+wb==0)continue;
                    Point correction=d.mul((len-l.length)/len*l.stiffness/(wa+wb));
                    if(wa>0)p[l.a]=p[l.a].add(correction);
                    if(wb>0)p[l.b]=p[l.b].sub(correction);
                }
                for(int i=columns;i<p.length;i++)for(Collision collision:collisions){
                    Point projected=collision.project(p[i]);
                    if(projected.sub(p[i]).length()>1e-10){
                        Point velocity=p[i].sub(previous[i]).mul(.3);
                        p[i]=projected;previous[i]=projected.sub(velocity);
                    }
                }
            }
        }
        accumulator=Math.max(0,accumulator-steps*STEP);
        for(int i=0;i<columns;i++)p[i]=target[i];
        System.arraycopy(target,0,lastTarget,0,p.length);
    }
    /** Bilinear world-space displacement of the control net, not an animation curve. */
    public Point displacement(double u,double v,Point[] target){
        double x=Math.clamp(u,0,1)*(columns-1),y=Math.clamp(v,0,1)*(rows-1);
        int ix=Math.min(columns-2,(int)x),iy=Math.min(rows-2,(int)y),i=iy*columns+ix;
        Point a=p[i].sub(target[i]).lerp(p[i+1].sub(target[i+1]),x-ix);
        Point b=p[i+columns].sub(target[i+columns]).lerp(p[i+columns+1].sub(target[i+columns+1]),x-ix);
        return a.lerp(b,y-iy);
    }
    public double maxStructuralStretch(){
        double max=1;for(Link l:links)if(l.stiffness==1)max=Math.max(max,p[l.a].sub(p[l.b]).length()/l.length);
        return max;
    }
}
