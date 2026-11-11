package cn.local.winterbear;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Looper;
import android.util.SparseArray;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;

/** Measures and renders the actual shared Android controls without game input. */
public final class OverlayUiRuntimeCheck {
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void layout(View view,int width){
        view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        view.layout(0,0,width,view.getMeasuredHeight());
        preDraw(view);
    }
    private static void preDraw(View view){
        // Detached Views have separate observers. A real ViewRoot dispatches
        // these callbacks before drawing, including TextView autosizing.
        if(view instanceof TextView)((TextView)view).onPreDraw();
        if(view instanceof ViewGroup){
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++)preDraw(group.getChildAt(i));
        }
    }
    private static View text(View root,String label){
        if(root instanceof TextView&&label.contentEquals(((TextView)root).getText()))return root;
        if(root instanceof ViewGroup){
            ViewGroup group=(ViewGroup)root;
            for(int i=0;i<group.getChildCount();i++){
                View found=text(group.getChildAt(i),label);if(found!=null)return found;
            }
        }
        return null;
    }
    private static void save(View root,File file)throws Exception{
        // Capture the settled checked state; the headless runner has no display
        // manager to drive AnimatedVectorDrawable's intermediate frames.
        root.jumpDrawablesToCurrentState();
        Bitmap bitmap=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bitmap));
        try(FileOutputStream output=new FileOutputStream(file)){bitmap.compress(Bitmap.CompressFormat.PNG,100,output);}
        bitmap.recycle();
    }
    public static void main(String[] args){
        try{
            Looper.prepareMainLooper();
            Class<?> threadClass=Class.forName("android.app.ActivityThread");
            Object thread=threadClass.getMethod("systemMain").invoke(null);
            Context system=(Context)threadClass.getMethod("getSystemContext").invoke(thread);
            // The headless ART runner has no WindowManager binder. Supply only
            // display capabilities inside this test process to render Views.
            Class<?> managerType=Class.forName("android.view.IWindowManager");
            Object manager=java.lang.reflect.Proxy.newProxyInstance(managerType.getClassLoader(),new Class[]{managerType},(proxy,method,values)->{
                if(method.getReturnType()==boolean.class)return false;
                if(method.getReturnType()==int.class)return 0;
                return null;
            });
            java.lang.reflect.Field managerField=Class.forName("android.view.WindowManagerGlobal").getDeclaredField("sWindowManagerService");
            managerField.setAccessible(true);managerField.set(null,manager);
            File output=new File(args[0]);output.mkdirs();int checks=0,presetChecks=0,statusChecks=0;
            for(int width:new int[]{620,720,1080,1152,1260,1344})for(float font:new float[]{1f,1.3f}){
                Configuration configuration=new Configuration(system.getResources().getConfiguration());
                configuration.densityDpi=Math.round(width/360f*160);configuration.fontScale=font;
                WindowManager window=(WindowManager)java.lang.reflect.Proxy.newProxyInstance(
                        WindowManager.class.getClassLoader(),new Class[]{WindowManager.class},(proxy,method,values)->{
                            if(method.getName().endsWith("WindowMetrics"))return new WindowMetrics(new Rect(0,0,width,width*20/9),WindowInsets.CONSUMED);
                            return null;
                        });
                Context context=new ContextThemeWrapper(system.createConfigurationContext(configuration),android.R.style.Theme_Material){
                    @Override public Object getSystemService(String name){
                        return Context.WINDOW_SERVICE.equals(name)?window:super.getSystemService(name);
                    }
                };
                java.lang.reflect.Field configurations=ViewConfiguration.class.getDeclaredField("sConfigurations");
                configurations.setAccessible(true);
                @SuppressWarnings("unchecked") SparseArray<ViewConfiguration> cached=(SparseArray<ViewConfiguration>)configurations.get(null);
                cached.put((int)(100*context.getResources().getDisplayMetrics().density),new ViewConfiguration());
                int[] chosen={-1};
                PresetPicker presets=new PresetPicker(context,PresetOrder.ALL,value->chosen[0]=value);
                int pickerWidth=width-Ui.dp(context,40);
                layout(presets,pickerWidth);
                require(presets.selection()==PresetOrder.ALL,"Initial preset selection lost");
                for(int preset=1;preset<=6;preset++){
                    TextView choice=(TextView)text(presets,"第 "+preset+" 个");
                    require(choice.getHeight()>=Ui.dp(context,48),"Preset touch area too small at "+width+"/"+font+": "+choice.getHeight());
                    require(choice.getLayout()!=null&&choice.getLayout().getEllipsisCount(0)==0,"Preset label clipped");
                    choice.performClick();
                }
                require(chosen[0]==0&&!presets.validateSelection(),"Empty preset selection accepted");
                text(presets,"第 5 个").performClick();
                require(chosen[0]==16&&presets.selection()==16&&presets.validateSelection(),"Chosen preset changed");
                text(presets,"第 2 个").performClick();
                require(presets.selection()==18,"Multiple preset selection lost");
                text(presets,"全选").performClick();require(presets.selection()==63,"Select all failed");
                text(presets,"清空").performClick();require(presets.selection()==0,"Clear selection failed");
                text(presets,"第 2 个").performClick();text(presets,"第 5 个").performClick();
                layout(presets,pickerWidth);
                if(width==1080&&font==1f)save(presets,new File(output,"preset-picker.png"));
                presetChecks++;
                int expectedHeight=-1,expectedCollapsed=-1;
                for(String label:new String[]{"打熊","靶心","巨兽","野兽"}){
                    int[] calls=new int[3];
                    OverlayFold.Controls ui=new OverlayFold.Controls(context,width,width/12,label,()->calls[0]++,()->calls[1]++);
                    int[] currentWidth={ui.width};
                    OverlayFold fold=new OverlayFold(context,ui.panel,ui.title,label,ui.width,ui.compactWidth,ui.titleHeight,
                            value->currentWidth[0]=value,()->calls[2]++);
                    ui.status.setText("等待队伍回城后继续当前任务");layout(ui.panel,currentWidth[0]);
                    int height=ui.panel.getHeight();
                    if(expectedHeight<0)expectedHeight=height;
                    require(height==expectedHeight,"Different activity control size");
                    for(String state:new String[]{"开始","暂停","继续"}){
                        ui.run.setText(state);layout(ui.panel,currentWidth[0]);
                        require(ui.panel.getHeight()==height,"Pause resized the controls");
                        ui.run.performClick();
                    }
                    text(ui.panel,"结束").performClick();text(ui.panel,"切换").performClick();
                    require(calls[0]==3&&calls[1]==1&&calls[2]==1,"Control callback missing");
                    require(ui.run.getLayout()!=null&&ui.run.getLayout().getLineWidth(0)>0,"Run label has no text layout");
                    if(width==1080&&font==1f)save(ui.panel,new File(output,label+"-expanded.png"));
                    fold.setCollapsed(true);layout(ui.panel,currentWidth[0]);
                    if(expectedCollapsed<0)expectedCollapsed=ui.panel.getHeight();
                    require(ui.panel.getHeight()==expectedCollapsed&&currentWidth[0]==ui.compactWidth,"Different collapsed size");
                    require(!ui.run.isShown(),"Collapsed controls still cover the game");
                    String[] messages={"回到游戏开始","正在加入队伍","总行军 6/6 · 等待空位","等待本次队伍回城","等待游戏窗口恢复","已手动暂停","继续当前任务","等待游戏响应"};
                    String[] states={"待开始","运行中","等待空位","等待回城","等待游戏","已暂停","运行中","运行中"};
                    for(int index=0;index<states.length;index++){
                        boolean running=index!=0&&index!=5;
                        CharSequence previous=ui.title.getText();
                        fold.updateStatus(running,index==5?"已暂停":"待开始",messages[index]);
                        if(index==7)require(previous==ui.title.getText(),"Short action wait rewrote the running label");
                        layout(ui.panel,currentWidth[0]);
                        require((label+"\n"+states[index]).contentEquals(ui.title.getText()),"Collapsed status stale");
                        require(ui.title.getContentDescription().toString().contains("点按展开"),"Expand action unlabeled");
                        require(fold.isCollapsed()&&currentWidth[0]==ui.compactWidth&&ui.panel.getHeight()==expectedCollapsed,"Status change resized or expanded controls");
                        require(ui.title.getLayout()!=null&&ui.title.getLayout().getLineCount()==2,"Collapsed state clipped");
                        for(int line=0;line<2;line++){
                            require(ui.title.getLayout().getEllipsisCount(line)==0,"Collapsed state ellipsized");
                            require(ui.title.getLayout().getLineWidth(line)<=ui.title.getWidth()-ui.title.getCompoundPaddingLeft()-ui.title.getCompoundPaddingRight(),"Collapsed state exceeds width");
                        }
                        require(ui.title.getLayout().getLineBottom(1)<=ui.title.getHeight()-ui.title.getCompoundPaddingTop()-ui.title.getCompoundPaddingBottom(),"Collapsed state exceeds height");
                        statusChecks++;
                    }
                    if(width==1080&&font==1f)save(ui.panel,new File(output,label+"-collapsed.png"));
                    ui.title.performClick();layout(ui.panel,currentWidth[0]);
                    require(!fold.isCollapsed()&&ui.panel.getHeight()==height,"Cannot expand again");checks++;
                }
            }
            System.out.println("ANDROID OVERLAY UI PASS: "+checks+" actual View layouts; pause/start/end/switch/fold callbacks; no game gestures");
            System.out.println("ANDROID PRESET PICKER PASS: "+presetChecks+" layouts; single, multiple, empty selection and callbacks");
            System.out.println("ANDROID COLLAPSED STATUS PASS: "+statusChecks+" transitions; all activities, six widths and two font scales; fixed bounds and tap-to-expand");
        }catch(Throwable error){error.printStackTrace(System.out);System.exit(1);}
    }
}
