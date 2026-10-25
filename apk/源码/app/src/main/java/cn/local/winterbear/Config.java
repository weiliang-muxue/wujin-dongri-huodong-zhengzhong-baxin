package cn.local.winterbear;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.json.JSONObject;
import org.json.JSONArray;

final class Config {
    static final int SLOTS = BearFlow.SLOTS;
    static final String[][] NAV={{"shortcut","右侧红色集结入口"},{"world","城内的野外按钮"},{"map_city","野外地图的城镇按钮"},{"alliance","地图底部联盟按钮"},{"war","联盟页的战争按钮"},{"rally_tab","战争页的集结标签"},{"back","左上角返回箭头"},{"formation","出征页固定标记（战争增益）"}};
    final Context context;
    final SharedPreferences p;
    private final File templateDirectory;
    Config(Context context) {
        this(context,false);
    }
    private Config(Context context,boolean probe) {
        this.context = context.getApplicationContext();
        p = context.getSharedPreferences(probe?"bear_reference_probe":"bear", Context.MODE_PRIVATE);
        templateDirectory=probe?new File(context.getCacheDir(),"reference-probe"):context.getFilesDir();
        if(!templateDirectory.isDirectory())templateDirectory.mkdirs();
        if(probe){p.edit().clear().apply();return;}
        if(p.getInt("selectionSchema",0)<2) {
            // Keep page anchors and preset points, but never reuse name-relative offsets.
            p.edit().putInt("selectionSchema",2).putBoolean("dry",true).apply();
        }
        if(p.getInt("marchSchema",0)<1)p.edit().putInt("marchSchema",1).putBoolean("dry",true).apply();
        if(width()==1080&&height()==2400&&p.getInt("assetSchema",0)<2){
            try{fillMissingReferenceLayout();p.edit().putInt("assetSchema",2).apply();}catch(Exception ignored){}
        }
    }
    static Config referenceProbe(Context context) throws Exception {
        Config probe=new Config(context,true);probe.importReferenceLayout();return probe;
    }
    File file(String key) { return new File(templateDirectory, key + ".png"); }
    boolean has(String key) { return file(key).isFile(); }
    Bitmap image(String key) { return BitmapFactory.decodeFile(file(key).getAbsolutePath()); }
    String label(int slot) { return "预设编队 " + (slot + 1); }
    boolean customRallyReady(){return p.getBoolean("rallySampleReady",false)&&has("bear_row")&&has("join_row");}
    boolean rallyReady() { return customRallyReady()||(has("bear_label")&&has("join_reference")); }
    boolean ready(int slot) {
        int x=p.getInt("presetX"+slot,0),y=p.getInt("presetY"+slot,0);
        return has("preset_marker"+slot)&&x>0&&y>0&&x<width()&&y<height();
    }
    boolean enabled(int slot) { return p.getBoolean("enabled" + slot, false); }
    boolean dry() { return p.getBoolean("dry", true); }
    boolean mapVerified(){return !target().isEmpty()&&marchReady()&&p.getBoolean("mapVerified",false);}
    void markMapVerified(String app,int w,int h,int used){p.edit().putString("target",app).putInt("width",w).putInt("height",h).putBoolean("mapVerified",true).putInt("lastMapUsed",used).apply();}
    Rect counterRect(){return new Rect(p.getInt("countLeft",0),p.getInt("countTop",0),p.getInt("countRight",0),p.getInt("countBottom",0));}
    boolean marchReady(){Rect r=counterRect();return has("march_anchor")&&r.width()>=12&&r.height()>=12&&r.left>=0&&r.top>=0&&r.right<=width()&&r.bottom<=height();}
    static String captureLabel(String mode,int slot){
        if(mode.equals("auto_map"))return "自动连接游戏地图";
        if(mode.equals("march"))return "地图行军文字与 x/6 数字";
        if(mode.equals("rally"))return "巨熊标记与加入按钮";
        if(mode.equals("preset"))return "顶部第 "+(slot+1)+" 个预设旗帜";
        if(mode.equals("header"))return "集结页标题";
        if(mode.equals("deploy"))return "可点击的出征按钮";
        for(String[] entry:NAV)if(entry[0].equals(mode))return entry[1];
        return mode;
    }
    void importReferenceLayout() throws Exception {
        referenceLayout(true);
    }
    void fillMissingReferenceLayout() throws Exception {
        referenceLayout(false);
    }
    private void referenceLayout(boolean replace) throws Exception {
        JSONObject layout;
        try(InputStream in=context.getAssets().open("reference/layout.json");java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)bytes.write(buffer,0,n);
            layout=new JSONObject(bytes.toString("UTF-8"));
        }
        int w=layout.getInt("width"),h=layout.getInt("height");
        if(width()!=0&&(width()!=w||height()!=h))throw new IllegalArgumentException("此布局仅对应 1080 × 2400 截图，请在本机自行取样");
        SharedPreferences.Editor e=p.edit().putInt("width",w).putInt("height",h);
        boolean fillCounter=replace||!marchReady();
        if(replace)e.putBoolean("dry",true).putInt("nextPreset",0).putBoolean("mapVerified",false);
        JSONObject crops=layout.getJSONObject("crops");java.util.Iterator<String> keys=crops.keys();
        while(keys.hasNext()){
            String key=keys.next();JSONArray rect=crops.getJSONArray(key);
            if(!replace&&has(key)&&!(fillCounter&&key.equals("march_anchor")))continue;
            try(InputStream in=context.getAssets().open("reference/"+key+".png");FileOutputStream out=new FileOutputStream(file(key))){byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);}
            e.putInt(key+"X",rect.getInt(0)+rect.getInt(2)/2).putInt(key+"Y",rect.getInt(1)+rect.getInt(3)/2);
        }
        JSONArray count=layout.getJSONArray("counter");
        if(fillCounter)e.putInt("countLeft",count.getInt(0)).putInt("countTop",count.getInt(1)).putInt("countRight",count.getInt(2)).putInt("countBottom",count.getInt(3));
        JSONArray presets=layout.getJSONArray("presets");
        for(int i=0;i<6;i++){
            if(replace||p.getInt("presetX"+i,0)<=0||p.getInt("presetY"+i,0)<=0)e.putInt("presetX"+i,presets.getJSONArray(i).getInt(0)).putInt("presetY"+i,presets.getJSONArray(i).getInt(1));
            if(replace||!p.contains("enabled"+i))e.putBoolean("enabled"+i,true);
        }
        e.apply();
    }
    int minutes() { return Math.max(1,Math.min(30,p.getInt("minutes", 30))); }
    float threshold() { return p.getFloat("threshold", .88f); }
    String target() { return p.getString("target", ""); }
    int width() { return p.getInt("width", 0); }
    int height() { return p.getInt("height", 0); }
    void saveCrop(String key, Bitmap source, Rect rect) throws IOException {
        Bitmap crop = Bitmap.createBitmap(source, rect.left, rect.top, rect.width(), rect.height());
        try (FileOutputStream out = new FileOutputStream(file(key))) {
            if (!crop.compress(Bitmap.CompressFormat.PNG, 100, out)) throw new IOException("无法保存模板");
        } finally { if (crop != source) crop.recycle(); }
        p.edit().putInt(key + "X", rect.centerX()).putInt(key + "Y", rect.centerY()).apply();
    }
    String validate() {
        if (target().isEmpty()) return "请先在地图录入行军区域，绑定目标游戏";
        if (!marchReady())return "请录入地图行军文字与 x/6 数字区域";
        if (dry())return null;
        return automaticError();
    }
    String automaticError(){
        if(!mapVerified())return "请先连接游戏，核对真实地图的行军数字";
        if (!has("header")) return "请录入集结页标题";
        if (!has("rally_tab")) return "请补录战争页的集结标签，用于区分集结列表和详情页";
        if (!has("deploy")) return "请录入出征按钮";
        if (!rallyReady()) return "请连接地图载入巨熊素材，或在高级设置补录巨熊标记";
        if (!has("back")||!has("world")||!has("formation"))return "请补录返回箭头、野外按钮和出征页固定标记";
        if (!has("shortcut")&&!(has("alliance")&&has("war")&&has("rally_tab")))return "请录入红色集结入口，或联盟→战争→集结的完整路线";
        int count = 0;
        for (int i = 0; i < SLOTS; i++) if (enabled(i)) {
            if (!ready(i)) return label(i) + " 还缺预设编队位置";
            count++;
        }
        return count == 0 ? "请完成并启用至少一个编队" : null;
    }
    void clearCalibration() {
        for (String key : new String[]{"header", "deploy", "bear_row", "join_row","join_reference","bear_reference","bear_label","march_anchor"}) file(key).delete();
        for(String[] entry:NAV)file(entry[0]).delete();
        SharedPreferences.Editor e = p.edit().remove("target").remove("width").remove("height").remove("rallySampleReady").remove("countLeft").remove("countRight").remove("countTop").remove("countBottom").remove("mapVerified").remove("lastMapUsed").putInt("nextPreset",0).putBoolean("dry",true);
        for (int i=0; i<SLOTS; i++) {
            file("leader"+i).delete(); file("bear"+i).delete(); file("join"+i).delete();
            file("preset_marker"+i).delete();
            e.remove("presetX"+i).remove("presetY"+i).remove("enabled"+i);
        }
        e.apply();
    }
}
