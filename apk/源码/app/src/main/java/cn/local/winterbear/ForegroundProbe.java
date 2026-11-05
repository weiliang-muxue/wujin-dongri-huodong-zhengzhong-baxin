package cn.local.winterbear;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.ArrayList;
import java.util.List;

final class ForegroundProbe {
    private static String owner(AccessibilityNodeInfo root){
        if(root==null)return "";
        try{CharSequence name=root.getPackageName();return name==null?"":name.toString();}
        finally{root.recycle();}
    }
    static String read(AccessibilityService service){
        String active="";
        List<AccessibilityWindowInfo> windows=null;
        try{
            active=owner(service.getRootInActiveWindow());
            windows=service.getWindows();
            List<ForegroundWindows.Window> observed=new ArrayList<>();
            if(windows!=null)for(AccessibilityWindowInfo w:windows){
                String name=owner(w.getRoot());
                boolean app=w.getType()==AccessibilityWindowInfo.TYPE_APPLICATION;
                boolean own=w.getType()==AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY&&name.equals(service.getPackageName());
                observed.add(new ForegroundWindows.Window(name,w.getLayer(),app,own,w.isActive(),w.isFocused()));
            }
            return ForegroundWindows.select(service.getPackageName(),active,observed);
        }catch(RuntimeException unavailable){return active;}
        finally{if(windows!=null)for(AccessibilityWindowInfo w:windows)w.recycle();}
    }
}
