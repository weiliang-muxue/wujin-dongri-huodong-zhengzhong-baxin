package cn.local.winterbear;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/** Real Config/Matcher/OpenCV and screenshots. Only ML Kit's number reading is supplied by annotations. */
public final class CompatibilityRuntimeCheck {
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static SharedPreferences preferences(){
        Map<String,Object> values=new HashMap<>();
        Map<String,Object> pending=new HashMap<>();java.util.Set<String> removed=new java.util.HashSet<>();boolean[] clear={false};
        Object editor=Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),new Class[]{SharedPreferences.Editor.class},(proxy,method,args)->{
            String name=method.getName();
            if(name.startsWith("put")){pending.put((String)args[0],args[1]);removed.remove(args[0]);return proxy;}
            if(name.equals("remove")){pending.remove(args[0]);removed.add((String)args[0]);return proxy;}
            if(name.equals("clear")){clear[0]=true;return proxy;}
            if(name.equals("apply")||name.equals("commit")){if(clear[0])values.clear();for(String key:removed)values.remove(key);values.putAll(pending);pending.clear();removed.clear();clear[0]=false;}
            return name.equals("commit")?true:null;
        });
        return (SharedPreferences)Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),new Class[]{SharedPreferences.class},(proxy,method,args)->{
            String name=method.getName();
            if(name.equals("edit"))return editor;
            if(name.equals("getAll"))return new HashMap<>(values);
            if(name.equals("contains"))return values.containsKey(args[0]);
            if(name.startsWith("get"))return values.getOrDefault(args[0],args[1]);
            return null;
        });
    }
    static Config config(File assets,File work,int w,int h)throws Exception{
        Config result=new Config(preferences(),work,path->new FileInputStream(new File(assets,path)));
        result.fillMissingReferenceLayout(w,h);return result;
    }
    static BearFlow.Frame read(Config config,File file,int count,boolean verifyCountCrop){
        Bitmap bitmap=BitmapFactory.decodeFile(file.getAbsolutePath());require(bitmap!=null,"Missing "+file);
        return read(config,bitmap,count,verifyCountCrop);
    }
    static BearFlow.Frame read(Config config,Bitmap bitmap,int count,boolean verifyCountCrop){
        Matcher.Numbers annotated=new Matcher.Numbers(){
            public int read(Bitmap screenshot,Rect region){
                if(verifyCountCrop)require(region.contains(new Rect(320,623,383,651)),"Huawei counter clipped: "+region);
                return count;
            }
            public void close(){}
        };
        try(Matcher matcher=new Matcher(config,annotated)){matcher.frame(bitmap);return matcher.observe();}
        finally{bitmap.recycle();}
    }
    // Explicitly synthetic: preserve game glyph proportions while moving bottom/center anchors.
    static Bitmap wider(File file,boolean center){
        Bitmap source=BitmapFactory.decodeFile(file.getAbsolutePath());
        Bitmap result=Bitmap.createBitmap(1152,2376,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(result);Paint p=new Paint(Paint.FILTER_BITMAP_FLAG);
        float scale=1152/1080f,delta=2376-2400*scale;
        if(center){c.translate(0,delta/2);c.scale(scale,scale);c.drawBitmap(source,0,0,p);}
        else{
            c.save();c.clipRect(0,0,1152,1300);c.scale(scale,scale);c.drawBitmap(source,0,0,p);c.restore();
            c.clipRect(0,1300,1152,2376);c.translate(0,delta);c.scale(scale,scale);c.drawBitmap(source,0,0,p);
        }
        source.recycle();return result;
    }
    public static void main(String[] args){
        try{run(args);}catch(Throwable error){error.printStackTrace(System.out);System.out.flush();System.exit(1);}
    }
    static void run(String[] args)throws Exception{
        System.load(args[0]+"/libc++_shared.so");System.load(args[0]+"/libopencv_java4.so");
        File fixtures=new File(args[1]),assets=new File(args[2]),work=new File(args[3]);int checks=0;
        Config huawei=config(assets,new File(work,"huawei"),1152,2376);
        for(int count:new int[]{6,-1}){
            BearFlow.Frame frame=read(huawei,new File(fixtures,"huawei-map.png"),count,true);
            require(frame.map&&!frame.marchHidden,"Visible Huawei march panel mistaken for hidden");
            require(frame.shortcutX>1000&&frame.shortcutY>1050&&frame.shortcutY<1200,"Huawei red shortcut missed");
            MarchFlow flow=MarchFlowTest.flow();flow.start(0);
            for(long time:new long[]{0,1000,2000})require(flow.next(frame,time).kind==BearFlow.Kind.WAIT,"Full or unreadable Huawei marches must wait");
            System.out.println("COMPAT IMAGE PASS: Huawei map count="+count);checks++;
        }
        Config xiaomi=config(assets,new File(work,"xiaomi"),1080,2400);
        BearFlow.Frame turtles=read(xiaomi,new File(fixtures,"xiaomi-list.png"),-1,false);
        require(turtles.rallyList&&turtles.rows.isEmpty(),"Turtles must not be joined as bears");checks++;
        System.out.println("COMPAT IMAGE PASS: Xiaomi turtle list is not bear list");
        for(int second:new int[]{170,250,270,315,358,411,691,718,751,771,780,816,820}){
            BearFlow.Frame f=read(xiaomi,new File(fixtures,"video-"+second+".png"),-1,false);
            if(second==250||second==270)require(f.map&&f.marchHidden,"Old hidden march / "+second);
            if(second==358)require(f.troopOverflow,"Old overflow");
            if(second==691)require(!f.rallyList,"Detail mistaken for list");
            if(second==315||second==411||second==771||second==780||second==816)require(f.rallyList&&!f.rows.isEmpty(),"Old bear list / "+second);
            if(second==411||second==771)for(BearFlow.Row r:f.rows)require(r.y>1000,"Old gray first plus / "+second);
            if(second==816)for(BearFlow.Row r:f.rows)require(r.y<1800,"Old gray third plus");
            if(second==170||second==718||second==751||second==820){
                require(f.deploy&&f.formation,"Old formation / "+second);
                for(boolean saved:f.savedPreset)require(saved,"Old preset / "+second);
            }
            checks++;System.out.println("COMPAT IMAGE PASS: old recording "+second);System.out.flush();
        }
        BearFlow.Frame wideFormation=read(huawei,wider(new File(fixtures,"video-170.png"),false),-1,false);
        require(wideFormation.formation&&wideFormation.deploy,"Wide synthetic formation");
        for(boolean saved:wideFormation.savedPreset)require(saved,"Wide synthetic preset");
        BearFlow.Frame wideList=read(huawei,wider(new File(fixtures,"video-315.png"),false),-1,false);
        require(wideList.rallyList&&!wideList.rows.isEmpty(),"Wide synthetic bear list");
        BearFlow.Frame widePopup=read(huawei,wider(new File(fixtures,"reported-overflow.png"),true),-1,false);
        require(widePopup.troopOverflow,"Wide synthetic overflow");
        xiaomi.p.edit().putInt("nextPreset",4).putBoolean("enabled2",false).apply();
        // 2.0.4 has no referenceLayout flag. Only its unmodified reference data may migrate.
        xiaomi.p.edit().remove("referenceLayout").apply();
        require(xiaomi.canAdaptReference(),"Legacy reference upgrade rejected");
        xiaomi.p.edit().putInt("presetX0",99).apply();
        require(!xiaomi.canAdaptReference(),"Legacy manual preset would be overwritten");
        xiaomi.p.edit().putInt("presetX0",98).apply();
        xiaomi.adaptReferenceLayout(1152,2376);
        require(xiaomi.p.getInt("nextPreset",0)==4&&!xiaomi.enabled(2),"Reference migration reset selections");
        require(xiaomi.p.getInt("map_cityY",0)==2338,"Reference bottom not anchored");
        Bitmap crop=Bitmap.createBitmap(30,30,Bitmap.Config.ARGB_8888);
        try{xiaomi.saveCrop("header",crop,new Rect(0,0,30,30));}finally{crop.recycle();}
        require(!xiaomi.canAdaptReference(),"Manual sample overwritten");
        System.out.println("ANDROID COMPAT PASS: "+checks+" real image cases; 3 synthetic wide-screen cases; Config migration preserves choices/manual samples; numeric OCR substituted by annotations; no gestures");
    }
}
