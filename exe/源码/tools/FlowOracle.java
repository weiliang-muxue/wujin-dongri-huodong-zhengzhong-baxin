package cn.local.winterbear;
import java.io.*;
import static cn.local.winterbear.BearFlow.*;
/** Line protocol used only by the desktop/Android parity regression. */
public class FlowOracle {
 public static void main(String[] args)throws Exception{
  MarchFlow flow=new MarchFlow();flow.dry=false;java.util.Arrays.fill(flow.enabled,true);
  int[] xs={98,209,319,431,542,654};System.arraycopy(xs,0,flow.presetX,0,6);java.util.Arrays.fill(flow.presetY,427);
  BufferedReader in=new BufferedReader(new InputStreamReader(System.in));String line;
  while((line=in.readLine())!=null){
   String[] p=line.split(",");long now=Long.parseLong(p[0]);int mask=Integer.parseInt(p[1]);
   if((mask&1024)!=0){flow.nextPreset=0;flow.submitted=0;flow.start(now);}
   Frame f=new Frame();f.map=(mask&1)!=0;f.header=(mask&2)!=0;f.rallyList=(mask&4)!=0;f.formation=(mask&8)!=0;f.deploy=(mask&16)!=0;f.marchHidden=(mask&32)!=0;f.troopOverflow=(mask&64)!=0;f.autoJoin=(mask&128)!=0;
   f.marchUsed=Integer.parseInt(p[2]);int troops=Integer.parseInt(p[3]);if(troops>=0)f.troops=new TroopCount(troops,150000);
   f.shortcutX=999;f.shortcutY=1060;f.backX=65;f.backY=300;f.deployX=832;f.deployY=2301;f.dialogOkX=540;f.dialogOkY=1540;
   int row=Integer.parseInt(p[4]);if(row>0)f.rows.add(new Row(0,950,800,row==1));int flags=Integer.parseInt(p[5]);for(int i=0;i<6;i++)f.savedPreset[i]=(flags&(1<<i))!=0;
   Decision d=flow.next(f,now);if(d.kind!=Kind.WAIT&&d.kind!=Kind.PAUSE)flow.complete(Integer.parseInt(p[6])==1,now);
   System.out.println(d.kind+","+d.x+","+d.y+","+flow.phase+","+flow.nextPreset+","+flow.submitted+","+flow.running);
  }
 }
}
