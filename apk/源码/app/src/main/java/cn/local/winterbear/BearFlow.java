package cn.local.winterbear;

import java.util.ArrayList;
import java.util.List;

/** Shared observation and decision types. Runtime logic lives in MarchFlow. */
public final class BearFlow {
    public static final int SLOTS = 6;
    public enum Kind { WAIT, JOIN, PRESET, DEPLOY, SCROLL, PAUSE, NAVIGATE, BACK }
    public static final class Row {
        public final int order, x, y;
        public final boolean available;
        public Row(int order,int x,int y) {this(order,x,y,true);}
        public Row(int order,int x,int y,boolean available) {this.order=order;this.x=x;this.y=y;this.available=available;}
    }
    public static final class Frame {
        public boolean header, deploy;
        public boolean map, formation, autoJoin;
        public boolean marchHidden, rallyList, troopOverflow, worldMapChrome;
        public int dialogOkX,dialogOkY;
        public TroopCount troops;
        public int marchUsed=-1;
        public int shortcutX,shortcutY,allianceX,allianceY,warX,warY,rallyX,rallyY,worldX,worldY,backX,backY;
        public final boolean[] savedPreset=new boolean[SLOTS];
        public int deployX, deployY;
        public final List<Row> rows = new ArrayList<>();
    }
    public static final class Decision {
        public final Kind kind;
        public final int x,y;
        public final String message;
        Decision(Kind kind, int x, int y, String message) { this.kind=kind;this.x=x;this.y=y;this.message=message; }
    }
}
