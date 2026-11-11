package cn.local.winterbear;

import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;

/** Tracks this task's marches, not the total occupied slots. Ambiguous rows keep their quota. */
final class HuntMarches {
    enum Type {
        UNKNOWN, MEAT, WOOD, COAL, IRON, RALLY, RETURN;
        boolean resource(){return this==MEAT||this==WOOD||this==COAL||this==IRON;}
    }
    static final class Row {
        final long icon;
        final int seconds;
        final Type type;
        Row(long icon,int seconds){this(icon,seconds,Type.UNKNOWN);}
        Row(long icon,int seconds,Type type){this.icon=icon;this.seconds=seconds;this.type=type;}
    }
    private Row[] previous;
    private long observedAt;
    private Set<Integer> owners=new HashSet<>();

    int active(){int n=0;for(int mask:owners)n=Math.max(n,Integer.bitCount(mask));return n;}

    boolean awaitingReturn(){
        if(previous==null||previous.length==0)return false;
        for(Row row:previous)if(row.type!=Type.RETURN||row.seconds<0)return false;
        return true;
    }
    // Missing the heading is not an empty roster. Only a new/empty session or
    // explicitly returning rows whose clocks have expired can support that claim.
    boolean mayConfirmEmpty(long at){
        if(previous==null||previous.length==0)return true;
        if(!awaitingReturn())return false;
        for(Row row:previous)if(at<observedAt+row.seconds*1000L+1000)return false;
        return true;
    }

    boolean observe(Row[] rows,long at,boolean confirmingDispatch){
        if(rows==null||rows.length>6)return false;
        for(Row r:rows)if(r==null||r.type==null||(r.seconds<0&&r.type==Type.UNKNOWN))return false;
        if(previous==null){
            if(confirmingDispatch)return false;
            previous=rows;observedAt=at;owners.add(0);return true;
        }
        // Retain every maximal assignment: equal countdowns must not make old external
        // rows indistinguishable from the newly dispatched row.
        List<int[]> alignments=new ArrayList<>();int[] best={-1};
        align(rows,at,0,0,new int[previous.length],alignments,best);
        int unobserved=previous.length-best[0];
        // A new row is evidence of dispatch. A click or a changed total alone is not.
        if(confirmingDispatch&&rows.length-best[0]<=unobserved)return false;
        Set<Integer> next=new HashSet<>();
        int eligible=0;
        for(int j=0;j<rows.length;j++)if(!rows[j].type.resource())eligible|=1<<j;
        for(int[] match:alignments){
          int matchedNew=0;for(int j:match)if(j>=0)matchedNew|=1<<j;
          // A harvest timer can restart without changing the occupied-slot total.
          // Such a row must never inherit the hunt team's quota when it returns.
          int free=eligible&~matchedNew;
          if(confirmingDispatch&&free==0)continue;
          for(int old:owners){
            int fixed=0,unmatchedOwned=0;
            for(int i=0;i<previous.length;i++)if((old&(1<<i))!=0){
                if(match[i]>=0){if(!rows[match[i]].type.resource())fixed|=1<<match[i];}
                else unmatchedOwned++;
            }
            int keep=Math.min(Integer.bitCount(free),unmatchedOwned+(confirmingDispatch?1:0));
            // Keep the largest possible task count until actual disappearance proves return.
            // An unrelated gathering return therefore cannot free a hunt slot.
            for(int mask=free;;mask=(mask-1)&free){
                if(Integer.bitCount(mask)==keep)next.add(fixed|mask);
                if(mask==0)break;
            }
          }
        }
        if(next.isEmpty())return false;
        owners=next;previous=rows;observedAt=at;return true;
    }
    private void align(Row[] rows,long at,int i,int used,int[] mapping,List<int[]> all,int[] best){
        if(i==previous.length){
            int n=Integer.bitCount(used);if(n>best[0]){all.clear();best[0]=n;}
            if(n==best[0])all.add(mapping.clone());return;
        }
        if(Integer.bitCount(used)+previous.length-i<best[0])return;
        for(int j=0;j<rows.length;j++)if((used&(1<<j))==0&&same(previous[i],observedAt,rows[j],at)){
            mapping[i]=j;align(rows,at,i+1,used|(1<<j),mapping,all,best);
        }
        mapping[i]=-1;align(rows,at,i+1,used,mapping,all,best);
    }
    private static boolean same(Row a,long aAt,Row b,long bAt){
        if(a.type!=Type.UNKNOWN&&b.type!=Type.UNKNOWN){
            if(a.type!=b.type)return false;
            // Resource identity survives arrival / harvesting / return. A bad
            // countdown must not transfer the hunt's identity onto this row.
            if(a.type.resource())return true;
            // A generic return flag can belong to either an old external team
            // or our rally after a phase change. Without clocks, do not force
            // it to match the external team and release our slot prematurely.
            if(a.seconds<0||b.seconds<0)return false;
        }
        return Long.bitCount(a.icon^b.icon)<=8&&Math.abs(aAt+a.seconds*1000L-bAt-b.seconds*1000L)<=5500;
    }
}
