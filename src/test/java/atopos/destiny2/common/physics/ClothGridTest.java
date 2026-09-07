package atopos.destiny2.common.physics;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ClothGridTest {
    private ClothGrid.Point[] target(double x,double z) {
        ClothGrid.Point[] p=new ClothGrid.Point[63];
        for(int y=0;y<9;y++)for(int i=0;i<7;i++)p[y*7+i]=new ClothGrid.Point(x+i*.08,1.5-y*.12,z);
        return p;
    }
    @Test void gravityPinsAndConstraintsRemainStable() {
        var target=target(0,0);var grid=new ClothGrid(7,9,target);
        for(int i=0;i<600;i++)grid.advance(ClothGrid.STEP,target,List.of());
        for(int i=0;i<7;i++)assertEquals(target[i],grid.point(i));
        assertTrue(grid.maxStructuralStretch()<1.08);
        assertTrue(grid.point(62).y()<target[62].y());
    }
    @Test void translatingAnchorProducesLagNotRigidMotion() {
        var grid=new ClothGrid(7,9,target(0,0));
        for(int i=1;i<=15;i++)grid.advance(ClothGrid.STEP,target(0,i*.02),List.of());
        assertEquals(.3,grid.point(0).z(),1e-9);
        assertTrue(Math.abs(grid.point(59).z()-.3)>.025);
    }
    @Test void thirtyAndOneHundredTwentyFpsAgree() {
        var a=new ClothGrid(7,9,target(0,0));var b=new ClothGrid(7,9,target(0,0));
        for(int i=1;i<=30;i++)a.advance(1.0/30,target(0,i/30.0*.25),List.of());
        for(int i=1;i<=120;i++)b.advance(1.0/120,target(0,i/120.0*.25),List.of());
        for(int i=0;i<63;i++)assertTrue(a.point(i).sub(b.point(i)).length()<.002);
    }
    @Test void collisionProjectsInsideCapsulesAndBlocks() {
        var capsule=new ClothGrid.Capsule(new ClothGrid.Point(0,0,0),new ClothGrid.Point(0,1,0),.2);
        assertEquals(.2,capsule.project(new ClothGrid.Point(.05,.5,0)).x(),1e-9);
        var block=new ClothGrid.Box(-10,-10,-10,10,.8,10);
        var t=target(0,0);var grid=new ClothGrid(7,9,t);
        for(int i=0;i<180;i++)grid.advance(ClothGrid.STEP,t,List.of(block));
        for(int i=7;i<63;i++)assertTrue(grid.point(i).y()>=.8-1e-9);
    }
    @Test void teleportAndLongAbsenceResetInsteadOfExploding() {
        var grid=new ClothGrid(7,9,target(0,0));var t=target(50,50);
        grid.advance(ClothGrid.STEP,t,List.of());assertEquals(t[62],grid.point(62));
        t=target(50,51);grid.advance(1,t,List.of());assertEquals(t[62],grid.point(62));
    }
    @Test void pauseDoesNotAdvanceAndEntitiesAreIndependent() {
        var t=target(0,0);var a=new ClothGrid(7,9,t);var b=new ClothGrid(7,9,t);
        a.advance(ClothGrid.STEP,t,List.of());var p=a.point(62);
        a.advance(0,t,List.of());assertEquals(p,a.point(62));assertEquals(t[62],b.point(62));
    }
    @Test void distantWorldCoordinatesRetainPrecision() {
        var a=new ClothGrid(7,9,target(0,0));var b=new ClothGrid(7,9,target(29000000,29000000));
        for(int i=0;i<120;i++){a.advance(ClothGrid.STEP,target(0,0),List.of());b.advance(ClothGrid.STEP,target(29000000,29000000),List.of());}
        assertTrue(a.point(62).sub(b.point(62).sub(new ClothGrid.Point(29000000,0,29000000))).length()<.0001);
    }
}
