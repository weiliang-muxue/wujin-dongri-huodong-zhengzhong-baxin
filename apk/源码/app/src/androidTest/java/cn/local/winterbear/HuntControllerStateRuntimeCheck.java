package cn.local.winterbear;

import android.content.Context;
import android.content.ContextWrapper;
import android.os.Looper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Exercises the controller state guards without attaching a window or sending input. */
public final class HuntControllerStateRuntimeCheck {
    private static void require(boolean value,String message){
        if(!value)throw new AssertionError(message);
    }
    private static Object get(Object target,String name)throws Exception{
        Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);
    }
    private static void set(Object target,String name,Object value)throws Exception{
        Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);
    }
    public static void main(String[] args){
        try{
            Looper.prepareMainLooper();
            Class<?> threadClass=Class.forName("android.app.ActivityThread");
            Object thread=threadClass.getMethod("systemMain").invoke(null);
            Context system=(Context)threadClass.getMethod("getSystemContext").invoke(thread);
            Context context=new ContextWrapper(system){
                @Override public Object getSystemService(String name){
                    // These cases never show a panel. A headless ART process has
                    // no display manager, so avoid creating a WindowManager.
                    return Context.WINDOW_SERVICE.equals(name)?null:super.getSystemService(name);
                }
            };
            Method attach=ContextWrapper.class.getDeclaredMethod("attachBaseContext",Context.class);
            attach.setAccessible(true);
            int checks=0;
            for(HuntFlow.Mode mode:HuntFlow.Mode.values()){
                BearService host=new BearService();attach.invoke(host,context);
                HuntController controller=new HuntController(host);
                try{
                    HuntFlow.Options options=new HuntFlow.Options(mode,mode==HuntFlow.Mode.GIANT?8:30,2,100,18);
                    HuntFlow flow=new HuntFlow(options);flow.start(1000);
                    flow.cansUsed=98;flow.submitted=7;flow.nextPreset=4;
                    flow.marches.observe(new HuntMarches.Row[0],1000,false);
                    require(flow.marches.observe(new HuntMarches.Row[]{new HuntMarches.Row(0,180,HuntMarches.Type.RALLY)},2000,true),"March fixture invalid");
                    ModeGate.Mode owner=mode==HuntFlow.Mode.GIANT?ModeGate.Mode.GIANT:ModeGate.Mode.BEAST;
                    set(controller,"flow",flow);set(controller,"ownership",owner);
                    set(controller,"modeToken",host.modes.select(owner));set(controller,"started",true);
                    set(controller,"busy",true);set(controller,"generation",7L);
                    long oldToken=controller.preparationToken();
                    controller.begin();
                    require(controller.preparationToken()==oldToken&&(boolean)get(controller,"busy")&&flow.running,"Duplicate begin invalidated active capture");checks++;

                    controller.prepare(new HuntFlow.Options(mode,options.level,2,100,18));
                    require(get(controller,"flow")==flow&&flow.cansUsed==98&&flow.submitted==7&&flow.nextPreset==4&&flow.marches.active()==1,"Equal settings reset session state");
                    require(!flow.running&&!(boolean)get(controller,"busy")&&controller.matchesOptions(options),"Equal settings did not pause existing task");checks++;
                    long pausedToken=controller.preparationToken();
                    controller.beginIfPrepared(oldToken);
                    require(controller.preparationToken()==pausedToken&&!flow.running,"Stale automatic start resumed paused task");checks++;

                    controller.pause("测试暂停");
                    controller.beginIfPrepared(pausedToken);
                    require(!flow.running&&flow.cansUsed==98,"Explicit pause did not cancel prepared start");checks++;
                    long endedToken=controller.preparationToken();controller.stop();controller.beginIfPrepared(endedToken);
                    require(!controller.hasTask()&&!controller.matchesOptions(options),"Ended task restarted");checks++;
                }finally{controller.destroy();}
            }
            System.out.println("ANDROID HUNT CONTROLLER PASS: "+checks+" state checks; duplicate begin, equal options, pause/end cancellation; no windows or gestures");
        }catch(Throwable error){error.printStackTrace(System.out);System.exit(1);}
    }
}
