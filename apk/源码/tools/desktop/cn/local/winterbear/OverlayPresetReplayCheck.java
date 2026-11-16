package cn.local.winterbear;

import android.graphics.*;
import java.nio.file.*;
import java.util.*;
import cn.local.bullseye.FloatingGuard;
import cn.local.bullseye.FloatingGuard.Box;

/** Recorded pixels with explicit synthetic overlay masks; never sends game input. */
public final class OverlayPresetReplayCheck {
    static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static Bitmap masked(Bitmap original,Box box){
        Bitmap b=new Bitmap(new java.awt.image.BufferedImage(original.getWidth(),original.getHeight(),java.awt.image.BufferedImage.TYPE_INT_RGB));var g=b.image.createGraphics();g.drawImage(original.image,0,0,null);
        g.setColor(java.awt.Color.BLACK);g.fillRect(box.left,box.top,box.width(),box.height());g.dispose();return b;
    }
    static BearFlow.Frame read(Bitmap b,Path assets,Path layout)throws Exception{
        try(Matcher m=new Matcher(new Config(assets,layout,b.getWidth(),b.getHeight()),new CounterOcr())){m.frame(b);return m.observe();}
    }
    static MarchFlow formationFlow(){
        MarchFlow f=new MarchFlow();f.dry=false;for(int i=0;i<6;i++){f.presetX[i]=100+i*100;f.presetY[i]=150;}f.setPresets(1<<3);f.start(0);
        BearFlow.Frame map=new BearFlow.Frame();map.map=true;map.worldMapChrome=true;map.marchUsed=3;map.shortcutX=980;map.shortcutY=1000;
        f.next(map,1000);require(f.next(map,1700).kind==BearFlow.Kind.NAVIGATE,"map navigation");f.complete(true,1800);
        BearFlow.Frame list=new BearFlow.Frame();list.header=true;list.rallyList=true;list.rows.add(new BearFlow.Row(0,980,800,true,40));
        require(f.next(list,3000).kind==BearFlow.Kind.JOIN,"join before formation");f.complete(true,3100);return f;
    }
    public static void main(String[] a)throws Exception{
        System.load(a[0]);org.opencv.core.Core.setNumThreads(1);Path assets=Path.of(a[1]),fixtures=Path.of(a[2]),layout=Path.of(a[3]);int checks=0;
        Bitmap reported=BitmapFactory.decodeFile(a[4]);
        Bitmap covered=masked(reported,new Box(412,165,1070,436));
        BearFlow.Frame failed=read(covered,assets,layout);require(failed.formation&&failed.deploy&&!failed.savedPreset[3],"reported fourth flag failure not reproduced");
        require(formationFlow().next(failed,4400).kind==BearFlow.Kind.WAIT,"covered flag must not be clicked");reported.recycle();covered.recycle();
        System.out.println("REPRODUCED reported fourth preset occlusion with runtime mask");
        for(String name:new String[]{"video-170.png","huawei-formation.png","giant_formation.png","beast_formation.png"}){
            Bitmap source=BitmapFactory.decodeFile(fixtures.resolve(name).toString());require(source!=null,"Missing "+name);
            for(double ratio:new double[]{.90,.98,1,1.02,1.10}){
                Bitmap image=Bitmap.createScaledBitmap(source,(int)Math.round(source.getWidth()*ratio),(int)Math.round(source.getHeight()*ratio),true);
                double s=image.getWidth()/1080.0;int w=image.getWidth(),h=image.getHeight(),top=(int)(90*s);
                float density=(float)(3*s),scale=FloatingGuard.controlScale(w,top,density);
                int gap=Math.round(8*BearOverlayGuard.panelScale(w,h,density));
                Box panel=new Box((int)(432*s),(int)(185*s),(int)(432*s)+Math.round(w*.57f),(int)(185*s)+8+Math.round(44*scale)+Math.round(32*scale)+Math.round(30*scale));
                Box clear=FloatingGuard.place(panel,new Box(0,top,w,h-(int)(60*s)),BearOverlayGuard.areas(w,h),gap);
                require(clear!=null&&clear.width()==panel.width()&&clear.height()==panel.height(),"expanded placement "+name+" "+ratio);
                Bitmap moved=masked(image,clear.padded(gap));BearFlow.Frame frame=read(moved,assets,layout);
                require(frame.formation&&frame.deploy&&frame.savedPreset[3],"fourth preset after moving "+name+" "+ratio+" "+Arrays.toString(frame.savedPreset));
                MarchFlow flow=formationFlow();BearFlow.Decision preset=flow.next(frame,4400);
                require(preset.kind==BearFlow.Kind.PRESET&&preset.x==frame.presetX[3]&&preset.y==frame.presetY[3],"wrong preset chosen");
                flow.complete(true,4500);require(flow.next(frame,5500).kind==BearFlow.Kind.DEPLOY,"preset did not lead to deploy");
                System.out.println("PASS "+name+" scale="+ratio+" expanded panel retained -> preset 4 -> DEPLOY");checks++;
                moved.recycle();if(image!=source)image.recycle();
            }source.recycle();
        }
        System.out.println("OVERLAY PRESET REPLAY PASS: "+checks+"; synthetic masks on recorded pages; no phone or game input");
    }
}
