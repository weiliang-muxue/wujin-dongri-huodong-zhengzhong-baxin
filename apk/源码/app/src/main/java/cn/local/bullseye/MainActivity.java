package cn.local.bullseye;
import android.app.*;
import android.os.*;
import android.provider.Settings;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    Bitmap home,board;Config config;Vision vision;ImageView preview;TextView result,log;EditText count;Button start,first,second;RadioGroup multiples;
    final ExecutorService worker=Executors.newSingleThreadExecutor();boolean analyzing=false;int selected=1;
    int dp(float x){return Math.round(x*getResources().getDisplayMetrics().density);}
    TextView label(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(Color.rgb(37,66,53));t.setPadding(0,dp(8),0,dp(8));return t;}
    Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setMinHeight(dp(48));return b;}
    @Override public void onCreate(Bundle state){super.onCreate(state);vision=new Vision();
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(22),dp(18),dp(22),dp(24));root.setBackgroundColor(Color.rgb(244,246,245));scroll.addView(root);setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars());scroll.setPadding(i.left,i.top,i.right,i.bottom);return insets;});
        TextView title=label("靶心助手",28);title.setTypeface(null,Typeface.BOLD);root.addView(title);root.addView(label(getString(R.string.app_version_label),14));
        root.addView(label("上传首页 → 上传 12 个靶子 → 自动识别 → 开始",14));
        first=button("1  上传首页全图");second=button("2  上传靶子全图");root.addView(first);root.addView(second);first.setOnClickListener(v->pick(1));second.setOnClickListener(v->pick(2));
        preview=new ImageView(this);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);preview.setBackgroundColor(Color.rgb(230,237,232));preview.setContentDescription("截图预览与自动识别的靶子位置");root.addView(preview,new LinearLayout.LayoutParams(-1,dp(260)));
        result=label("等待两张截图，无需手动标记位置。",15);root.addView(result);
        root.addView(label("本次使用券数（自动识别，可更正）",13));count=new EditText(this);count.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);count.setTextSize(18);count.setHint("等待识别");root.addView(count);
        root.addView(label("每轮倍率（自己选择）",13));multiples=new RadioGroup(this);multiples.setOrientation(LinearLayout.HORIZONTAL);for(int n:new int[]{1,5}){RadioButton r=new RadioButton(this);r.setText(n+" 倍");r.setId(n==1?R.id.multiplier_one:R.id.multiplier_five);r.setMinHeight(dp(48));multiples.addView(r);}multiples.check(R.id.multiplier_one);root.addView(multiples);
        root.addView(label("请先把游戏设为相同倍率。只读取总券数，不识别、不切换倍率；不足所选倍率时停止并提示。结算后自动返回首页。",13));
        start=button("开始运行");start.setTextColor(Color.WHITE);start.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(22,120,85)));start.setEnabled(false);root.addView(start);start.setOnClickListener(v->startRun());
        log=label("截图与识别均在手机本地处理。Android 11 及以上可用。",12);root.addView(log);
    }
    void pick(int kind){if(analyzing)return;if(GameService.instance!=null&&GameService.instance.running){toast("请先停止当前运行再更换截图");return;}selected=kind;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,kind);}
    @Override protected void onActivityResult(int request,int code,Intent data){super.onActivityResult(request,code,data);if(code!=RESULT_OK||data==null||data.getData()==null)return;
        try(InputStream in=getContentResolver().openInputStream(data.getData())){Bitmap b=BitmapFactory.decodeStream(in);if(b==null)throw new IOException("图片无法读取");Rect screen=getWindowManager().getMaximumWindowMetrics().getBounds();if(b.getWidth()!=screen.width()||b.getHeight()!=screen.height())throw new IOException("请上传这台手机当前方向的全屏原图，不要裁剪或压缩");
            if(request==1){home=b;first.setText("1  首页图已上传 · 更换");}else{board=b;second.setText("2  靶子图已上传 · 更换");}preview.setImageBitmap(b);config=null;start.setEnabled(false);count.setText("");if(home!=null&&board!=null)analyze();
        }catch(Exception e){toast(e.getMessage());}
    }
    void analyze(){analyzing=true;first.setEnabled(false);second.setEnabled(false);result.setText("正在自动识别次数、按钮和 12 个靶子…");
        worker.execute(()->{try{Config c=vision.analyze(home,board);saveBitmap("home.png",home);saveBitmap("board.png",board);runOnUiThread(()->{config=c;count.setText(String.valueOf(c.count));result.setText("已识别 "+c.count+" 张券 · 12 / 12 个靶子\n全屏 "+c.width+" × "+c.height);Bitmap marked=board.copy(Bitmap.Config.ARGB_8888,true);Canvas canvas=new Canvas(marked);Paint paint=new Paint(3);paint.setColor(Color.rgb(22,170,110));paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(3,c.width/180f));for(PointF p:c.targets)canvas.drawCircle(p.x*c.width,p.y*c.height,c.width/45f,paint);preview.setImageBitmap(marked);start.setEnabled(true);finishAnalysis();});
        }catch(Exception e){runOnUiThread(()->{result.setText(e.getMessage());finishAnalysis();});}});
    }
    void finishAnalysis(){analyzing=false;first.setEnabled(true);second.setEnabled(true);}
    void saveBitmap(String name,Bitmap b)throws IOException{try(FileOutputStream out=openFileOutput(name,MODE_PRIVATE)){b.compress(Bitmap.CompressFormat.PNG,100,out);}}
    void startRun(){if(config==null)return;if(GameService.instance!=null&&GameService.instance.running){toast("运行中，请使用悬浮按钮控制");return;}
        int n;try{n=Integer.parseInt(count.getText().toString());if(n<=0||n>99999)throw new NumberFormatException();}catch(Exception e){toast("券数需为 1～99999 的整数");return;}
        if(GameService.instance==null){new AlertDialog.Builder(this).setTitle("开启屏幕操作权限").setMessage("请在辅助功能中开启「靶心助手」。仅在你开始运行后读取屏幕和点击已识别位置；可随时暂停或停止。截图不会上传。").setPositiveButton("去设置",(d,w)->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))).setNegativeButton("暂不",null).show();return;}
        config.count=n;config.multiplier=multiples.getCheckedRadioButtonId()==R.id.multiplier_five?5:1;
        new AlertDialog.Builder(this).setTitle("准备开始").setMessage("你选择了 "+config.multiplier+" 倍，请先把游戏内倍率也设为 "+config.multiplier+" 倍。程序不识别、不切换游戏倍率。\n\n请保持游戏位置与截图一致，停在首页或完整的 12 靶子页。开始后 5 秒执行，请切回游戏，不要手动操作游戏。可用悬浮按钮暂停 / 继续 / 停止。").setPositiveButton("5 秒后开始",(d,w)->{try{String data=config.json();try(FileOutputStream out=openFileOutput("config.json",MODE_PRIVATE)){out.write(data.getBytes(StandardCharsets.UTF_8));}GameService.instance.begin(config);moveTaskToBack(true);}catch(Exception e){toast(e.getMessage());}}).setNegativeButton("返回",null).show();
    }
    void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();log.setText(message);}
    @Override protected void onDestroy(){super.onDestroy();worker.shutdown();if(!analyzing)vision.close();}
}
