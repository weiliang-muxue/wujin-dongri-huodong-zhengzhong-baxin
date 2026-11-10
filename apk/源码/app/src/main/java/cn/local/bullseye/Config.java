package cn.local.bullseye;
import android.graphics.PointF;
import android.graphics.Bitmap;
import org.json.*;
import java.util.*;

public class Config {
    public float[] region,countRect,exitRect;
    public PointF start;
    public List<PointF> targets=new ArrayList<>();
    public int count,width,height;
    public boolean matchesReferences(Bitmap home,Bitmap board){
        return width>0&&height>0&&targets.size()==12&&start!=null
                &&region!=null&&region.length==4&&countRect!=null&&countRect.length==4
                &&exitRect!=null&&exitRect.length==4
                &&home!=null&&board!=null&&home.getWidth()==width&&home.getHeight()==height
                &&board.getWidth()==width&&board.getHeight()==height;
    }
    static JSONArray array(float[] a)throws JSONException {JSONArray j=new JSONArray();for(float v:a)j.put((double)v);return j;}
    static float[] floats(JSONArray j)throws JSONException {float[] a=new float[j.length()];for(int i=0;i<a.length;i++)a[i]=(float)j.getDouble(i);return a;}
    public String json()throws JSONException {
        JSONObject j=new JSONObject();j.put("region",array(region));j.put("countRect",array(countRect));j.put("start",array(new float[]{start.x,start.y}));
        JSONArray ts=new JSONArray();for(PointF p:targets)ts.put(array(new float[]{p.x,p.y}));j.put("targets",ts);
        j.put("exitRect",array(exitRect));
        j.put("count",count);j.put("width",width);j.put("height",height);return j.toString();
    }
    public static Config read(String s)throws JSONException {
        JSONObject j=new JSONObject(s);Config c=new Config();c.region=floats(j.getJSONArray("region"));c.countRect=floats(j.getJSONArray("countRect"));
        float[] a=floats(j.getJSONArray("start"));c.start=new PointF(a[0],a[1]);
        JSONArray ts=j.getJSONArray("targets");for(int i=0;i<ts.length();i++){a=floats(ts.getJSONArray(i));c.targets.add(new PointF(a[0],a[1]));}
        c.exitRect=j.has("exitRect")?floats(j.getJSONArray("exitRect")):Vision.exitRegion(c.targets);
        c.count=j.getInt("count");c.width=j.getInt("width");c.height=j.getInt("height");return c;
    }
}
