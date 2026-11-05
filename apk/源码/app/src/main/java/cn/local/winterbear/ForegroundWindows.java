package cn.local.winterbear;

import java.util.List;

/** Selects the visible app without mistaking our accessibility controls for an app switch. */
final class ForegroundWindows {
    static final class Window {
        final String owner;
        final int layer;
        final boolean application,ownOverlay,active,focused;
        Window(String owner,int layer,boolean application,boolean ownOverlay,boolean active,boolean focused){
            this.owner=owner==null?"":owner;this.layer=layer;this.application=application;
            this.ownOverlay=ownOverlay;this.active=active;this.focused=focused;
        }
    }
    static String select(String own,String root,List<Window> windows){
        root=root==null?"":root;
        Window app=null;
        for(Window w:windows)if(w.application&&(app==null||w.layer>app.layer))app=w;
        if(app==null)return root;
        // A notification shade, keyboard or another accessibility overlay can
        // intercept input. Only this app's TYPE_ACCESSIBILITY_OVERLAY is ignored.
        for(Window w:windows)if(!w.application&&!w.ownOverlay&&(w.active||w.focused)&&w.layer>=app.layer)
            return "";
        if(app.owner.isEmpty())return "";
        // Preserve an explicitly reported different app/system window when the
        // interactive-window list is incomplete; never reuse a cached game name.
        if(!root.isEmpty()&&!root.equals(own)&&!root.equals(app.owner))return "";
        return app.owner;
    }
}
