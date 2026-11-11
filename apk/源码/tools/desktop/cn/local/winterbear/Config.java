package cn.local.winterbear;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import android.graphics.*;
/** Read-only reference fixture configuration, exported from production layout.json. */
final class Config {
    final Preferences p=new Preferences();
    private final Path assets;private final int w,h;
    Config(Path assets,Path layout,int w,int h)throws Exception{
        this.assets=assets;this.w=w;this.h=h;Properties values=new Properties();try(var in=Files.newInputStream(layout)){values.load(in);}
        ReferenceLayout geometry=new ReferenceLayout(w,h);
        for(String key:values.stringPropertyNames()){
            int value=Integer.parseInt(values.getProperty(key));
            boolean vertical=key.endsWith("Y")||key.startsWith("presetY")||key.equals("countTop")||key.equals("countBottom");
            p.values.put(key,vertical?geometry.y(value):geometry.x(value));
        }
        p.scale=geometry.scale();
    }
    InputStream openAsset(String path)throws IOException{return Files.newInputStream(assets.resolve(path));}
    int width(){return w;}int height(){return h;}
    float threshold(){return .88f;}
    Bitmap image(String key){return BitmapFactory.decodeFile(assets.resolve("reference/"+key+".png").toString());}
    boolean has(String key){return Files.isRegularFile(assets.resolve("reference/"+key+".png"));}
    boolean customRallyReady(){return false;}
    boolean rallyReady(){return has("bear_label")&&has("join_reference");}
    Rect counterRect(){return new Rect(p.getInt("countLeft",0),p.getInt("countTop",0),p.getInt("countRight",0),p.getInt("countBottom",0));}
    boolean marchReady(){return has("march_anchor");}
    static final class Preferences {
        final Map<String,Integer> values=new HashMap<>();float scale;
        boolean getBoolean(String name,boolean fallback){return name.equals("referenceLayout")||fallback;}
        int getInt(String name,int fallback){return values.getOrDefault(name,fallback);}
        float getFloat(String name,float fallback){return name.endsWith("Scale")?scale:fallback;}
    }
}
