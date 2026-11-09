package cn.local.winterbear;

import android.os.Bundle;
import android.os.Looper;
import java.lang.reflect.Field;

/** Tests the Activity's actual Bundle serialization helpers without starting a game. */
public final class HuntStateRuntimeCheck {
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void set(HuntActivity activity,String name,Object value)throws Exception{
        Field field=HuntActivity.class.getDeclaredField(name);field.setAccessible(true);field.set(activity,value);
    }
    private static Object get(HuntActivity activity,String name)throws Exception{
        Field field=HuntActivity.class.getDeclaredField(name);field.setAccessible(true);return field.get(activity);
    }
    public static void main(String[] args){
        try{
            if(Looper.getMainLooper()==null)Looper.prepareMainLooper();
            int checks=0;
            for(HuntFlow.Mode mode:HuntFlow.Mode.values())for(boolean cans:new boolean[]{false,true}){
                HuntActivity original=new HuntActivity();original.restoreSettings(null,mode);
                set(original,"level",7);set(original,"limit",3);set(original,"canLimit",98);
                set(original,"presetSelection",18);set(original,"cans",cans);set(original,"hadTask",true);
                Bundle saved=new Bundle();original.saveSettings(saved);
                HuntActivity recreated=new HuntActivity();recreated.restoreSettings(saved,mode);
                for(String name:new String[]{"mode","level","limit","canLimit","presetSelection","cans","hadTask"}){
                    require(get(original,name).equals(get(recreated,name)),"Lost saved setting "+name);
                }
                recreated.restoreSettings(null,mode);
                require(get(recreated,"level").equals(mode==HuntFlow.Mode.GIANT?8:30),"New task retained level");
                require(get(recreated,"presetSelection").equals(PresetOrder.ALL),"New task retained formations");
                require(get(recreated,"canLimit").equals(100)&&get(recreated,"cans").equals(false)&&get(recreated,"hadTask").equals(false),"New task retained stamina allowance");
                HuntFlow.Mode other=mode==HuntFlow.Mode.GIANT?HuntFlow.Mode.BEAST:HuntFlow.Mode.GIANT;
                recreated.restoreSettings(saved,other);
                require(get(recreated,"mode")==other&&get(recreated,"cans").equals(false),"Settings leaked to another activity");
                checks++;
            }
            System.out.println("ANDROID HUNT STATE PASS: "+checks+" Bundle round trips, new-task reset and mode isolation; no Activity lifecycle or game gestures");
        }catch(Throwable error){error.printStackTrace(System.out);System.exit(1);}
    }
}
