package cn.local.winterbear;

import android.os.SystemClock;
import cn.local.bullseye.GameService;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Real controller guards with isolated fields, without initializing OCR or a window. */
public final class BullseyeSessionRuntimeCheck {
    private static final Class<?>[] REQUEST={long.class,long.class};
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void set(GameService service,String name,Object value)throws Exception{
        Field field=GameService.class.getDeclaredField(name);field.setAccessible(true);field.set(service,value);
    }
    private static Object call(GameService service,String name,Class<?>[] types,Object... values)throws Exception{
        Method method=GameService.class.getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(service,values);
    }
    private static GameService isolated()throws Exception{
        Class<?> type=Class.forName("sun.misc.Unsafe");Object unsafe=null;
        for(Field field:type.getDeclaredFields())if(Modifier.isStatic(field.getModifiers())&&field.getType()==type){
            field.setAccessible(true);unsafe=field.get(null);break;
        }
        require(unsafe!=null,"Unsafe unavailable for isolated controller test");
        GameService service=(GameService)type.getMethod("allocateInstance",Class.class).invoke(unsafe,GameService.class);
        set(service,"tapLock",new Object());set(service,"generation",7L);
        return service;
    }
    public static void main(String[] args){
        try{
            GameService service=isolated();int checks=0;
            long first=(long)call(service,"beginCapture",new Class<?>[0]);
            require((boolean)call(service,"expireCapture",REQUEST,first,7L)&&!service.awaitingCapture(),"Lost screenshot callback retained busy");checks++;
            long next=(long)call(service,"beginCapture",new Class<?>[0]);
            require(!(boolean)call(service,"captureArrived",REQUEST,first,7L)&&service.awaitingCapture(),"Late screenshot entered new session");checks++;
            require(!(boolean)call(service,"finishCapture",new Class<?>[]{long.class},first)&&service.awaitingCapture(),"Late screenshot cleared new busy");checks++;
            require((boolean)call(service,"captureArrived",REQUEST,next,7L),"Current screenshot could not start processing");checks++;
            require(!(boolean)call(service,"expireCapture",REQUEST,next,7L)&&service.awaitingCapture(),"Capture watchdog released active processor");checks++;
            call(service,"cancelCaptureWait",new Class<?>[0]);
            require(service.awaitingCapture(),"Pause released bitmap processing ownership");checks++;
            require((boolean)call(service,"finishCapture",new Class<?>[]{long.class},next)&&!service.awaitingCapture(),"Processor did not release busy");checks++;
            long paused=(long)call(service,"beginCapture",new Class<?>[0]);call(service,"cancelCaptureWait",new Class<?>[0]);
            require(!service.awaitingCapture()&&!(boolean)call(service,"captureArrived",REQUEST,paused,7L),"Cancelled wait accepted late callback");checks++;
            set(service,"generation",8L);long restarted=(long)call(service,"beginCapture",new Class<?>[0]);
            require(!(boolean)call(service,"expireCapture",REQUEST,restarted,7L)&&service.awaitingCapture(),"Old session timed out new capture");checks++;
            call(service,"cancelCaptureWait",new Class<?>[0]);
            long oldGesture=(long)call(service,"beginGesture",new Class<?>[0]);
            call(service,"finishGesture",REQUEST,oldGesture,8L);long currentGesture=(long)call(service,"beginGesture",new Class<?>[0]);
            require(!(boolean)call(service,"finishGesture",REQUEST,oldGesture,8L),"Old gesture completed new gesture");checks++;
            require(!(boolean)call(service,"finishGesture",REQUEST,currentGesture,7L),"Old session completed new gesture");checks++;
            require((boolean)call(service,"finishGesture",REQUEST,currentGesture,8L)&&!(boolean)call(service,"finishGesture",REQUEST,currentGesture,8L),"Gesture completion repeated");checks++;
            set(service,"running",true);set(service,"frameRevision",1L);set(service,"overlayRevision",1L);
            set(service,"frameCapturedAt",SystemClock.elapsedRealtime()-4001);
            require(!(boolean)call(service,"frameCurrent",new Class<?>[0]),"Expired screenshot accepted");checks++;
            set(service,"frameCapturedAt",SystemClock.elapsedRealtime());
            require((boolean)call(service,"frameCurrent",new Class<?>[0]),"Fresh screenshot rejected");
            set(service,"overlayRevision",2L);
            require(!(boolean)call(service,"frameCurrent",new Class<?>[0]),"Moved overlay retained old screenshot");checks++;
            System.out.println("ANDROID BULLSEYE SESSION PASS: "+checks+" callback ownership, timeout recovery and frame freshness checks; no OCR, windows or gestures");
        }catch(Throwable error){error.printStackTrace(System.out);System.exit(1);}
    }
}
