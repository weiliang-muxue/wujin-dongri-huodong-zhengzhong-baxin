package cn.local.winterbear;

import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;

/** Tracks this task's marches, not the total occupied slots. Ambiguous rows keep their quota. */
final class HuntMarches {
    static final class Row {
        final long icon;
        final int seconds;
        Row(long icon,int seconds){this.icon=icon;this.seconds=seconds;}
    }
    private Row[] previous;
    private long observedAt;
    private Set<Integer> owners=new HashSet<>();

    int active(){int n=0;for(int mask:owners)n=Math.max(n,Integer.bitCount(mask));return n;}

    boolean observe(Row[] rows,long at,boolean confirmingDispatch){
        if(rows==null||rows.length>6)return false;
        for(Row r:rows)if(r==null||r.seconds<0)return false;
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
        for(int[] match:alignments){
          int matchedNew=0;for(int j:match)if(j>=0)matchedNew|=1<<j;
          int free=((1<<rows.length)-1)&~matchedNew;
          for(int old:owners){
            int fixed=0,unmatchedOwned=0;
            for(int i=0;i<previous.length;i++)if((old&(1<<i))!=0){
                if(match[i]>=0)fixed|=1<<match[i];else unmatchedOwned++;
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
        return Long.bitCount(a.icon^b.icon)<=8&&Math.abs(aAt+a.seconds*1000L-bAt-b.seconds*1000L)<=5500;
    }
}
