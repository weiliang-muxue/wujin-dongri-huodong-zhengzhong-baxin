package cn.local.winterbear;

import java.util.Comparator;
import static cn.local.winterbear.BearFlow.*;

/** Reserves a bounded batch of march slots, then follows rallies without a map round trip per tap. */
final class MarchFlow {
    enum Phase { MAP, ENTER_LIST, FIND, FORMATION, SEND, VERIFY, DISMISS_OVERFLOW, RETURN_LIST, RETURN_MAP }
    static final long CAPACITY_MAX_AGE=90_000;
    final int[] presetX=new int[6],presetY=new int[6];
    final boolean[] enabled=new boolean[6];
    boolean dry=true,running;
    int maxMinutes=30,nextPreset,submitted;
    Phase phase=Phase.MAP;
    private Phase afterAction;
    private Kind pending;
    private long deadline,waitUntil,phaseSince,lastScroll,checkedAt=-1,stableSince,unknownSince=-1,returnSince,retryAt;
    private int stableUsed=-9,stableReads,selected=-1,lastNav=-1,backAttempts,remaining,attemptedMask,failedRowY=-1,lastJoinY=-1,dialogAttempts;
    private long avoidRowUntil;
    private long dialogSince,markerMissingSince=-1;
    private boolean charged;
    void start(long now){
        running=true;pending=null;phase=Phase.MAP;phaseSince=now;waitUntil=now;lastScroll=now;
        deadline=now+Math.max(1,Math.min(30,maxMinutes))*60_000L;
        checkedAt=-1;unknownSince=-1;lastNav=-1;remaining=0;charged=false;attemptedMask=0;retryAt=0;
        failedRowY=-1;lastJoinY=-1;dialogAttempts=0;avoidRowUntil=0;markerMissingSince=-1;resetEvidence();nextPreset=Math.floorMod(nextPreset,6);
    }
    void stop(){running=false;pending=null;checkedAt=-1;remaining=0;resetEvidence();}
    boolean pending(){return pending!=null;}
    boolean expired(long now){return now>=deadline;}
    long remainingMillis(long now){return Math.max(0,deadline-now);}
    private void resetEvidence(){stableUsed=-9;stableReads=0;}
    private Decision waitFor(String message){return new Decision(Kind.WAIT,0,0,message);}
    private Decision pause(String message){stop();return new Decision(Kind.PAUSE,0,0,message);}
    private Decision act(Kind kind,int x,int y,Phase next,String message){pending=kind;afterAction=next;return new Decision(kind,x,y,message);}
    private int choosePreset(){
        for(int n=0;n<6;n++){int i=(nextPreset+n)%6;if((attemptedMask&(1<<i))==0&&enabled[i]&&presetX[i]>0&&presetY[i]>0)return i;}return -1;
    }
    private void returnToMap(long now){phase=Phase.RETURN_MAP;returnSince=now;backAttempts=0;checkedAt=-1;remaining=0;resetEvidence();}
    private void returnToList(long now){phase=Phase.RETURN_LIST;returnSince=now;backAttempts=0;unknownSince=-1;}
    private void avoidLastRow(long now){failedRowY=lastJoinY;avoidRowUntil=now+12000;}
    private Decision unrecognized(long now,String message){
        if(unknownSince<0)unknownSince=now;
        return now-unknownSince>=15000?pause(message):waitFor(message+" · 正在重新识别");
    }
    private Decision navigate(int node,int x,int y,long now,String message){
        if(lastNav==node)return now-phaseSince>8000?pause("页面没有切换，请检查入口取样"):waitFor("等待页面切换");
        lastNav=node;phaseSince=now;return act(Kind.NAVIGATE,x,y,Phase.ENTER_LIST,message);
    }
    private Decision nextFormation(long now,String reason){
        attemptedMask|=1<<selected;selected=choosePreset();markerMissingSince=-1;
        if(selected<0){retryAt=now+8000;returnToMap(now);return waitFor(reason+" · 本轮预设都不可用，稍后重查");}
        phase=Phase.FORMATION;phaseSince=now;unknownSince=-1;
        return waitFor(reason+" · 尝试第 "+(selected+1)+" 个预设");
    }
    Decision next(Frame f,long now){
        if(!running)return waitFor("已暂停");
        if(expired(now))return pause("已达到本次运行时长");
        if(pending!=null||now<waitUntil)return waitFor("等待界面响应");
        if((f.map&&f.header)||(f.map&&f.formation)||(f.header&&f.formation))return pause("页面识别冲突，请重新取样");
        boolean marchHidden=f.marchHidden&&!MarchCount.valid(f.marchUsed);
        if(dry){
            if(f.map)return waitFor(marchHidden?"已识别地图 · 未显示行军栏，可尝试加入 · 只识别":!MarchCount.valid(f.marchUsed)?"行军数字未读清 · 只识别":"行军 "+f.marchUsed+"/6 · "+(f.marchUsed<6?"有空位":"已满，等待")+" · 只识别");
            if(f.header)return waitFor("已识别集结页 · 绿色＋可尝试加入 · 只识别");
            return waitFor("等待游戏地图或集结列表 · 只识别");
        }
        // Native auto-join is not a stop condition: it handles other activities.
        if(phase==Phase.DISMISS_OVERFLOW){
            if(f.troopOverflow){
                if(now-dialogSince>=8000)return pause("超编提示未关闭，请检查游戏响应");
                if(now-dialogSince>=3000&&dialogAttempts<2&&f.dialogOkX>0){dialogAttempts++;return act(Kind.NAVIGATE,f.dialogOkX,f.dialogOkY,Phase.DISMISS_OVERFLOW,"再次关闭超编提示，不重复跳过预设");}
                return waitFor("等待超编提示关闭");
            }
            if(f.formation||f.deploy){phase=Phase.FORMATION;phaseSince=now;unknownSince=-1;}
            else if(f.header){returnToList(now);}
            else if(f.map){phase=Phase.MAP;checkedAt=-1;remaining=0;resetEvidence();}
            else return unrecognized(now,"等待超编提示关闭后的页面");
        }
        if(f.troopOverflow&&f.dialogOkX>0){
            if(phase!=Phase.VERIFY&&phase!=Phase.SEND&&phase!=Phase.FORMATION)return pause("请先处理当前游戏提示");
            if(charged){remaining++;charged=false;}
            if(selected>=0)attemptedMask|=1<<selected;selected=choosePreset();markerMissingSince=-1;
            dialogSince=now;dialogAttempts=1;
            return act(Kind.NAVIGATE,f.dialogOkX,f.dialogOkY,Phase.DISMISS_OVERFLOW,"士兵超出上限，关闭提示后换下一个预设");
        }
        if(phase==Phase.RETURN_MAP){
            if(f.map){phase=Phase.MAP;phaseSince=now;unknownSince=-1;attemptedMask=0;resetEvidence();return waitFor("已回地图，检查新空位");}
            if(now-returnSince>20000||backAttempts>=6)return pause("未能返回野外地图，请核对当前页面");
            if(f.worldX>0){backAttempts++;return act(Kind.NAVIGATE,f.worldX,f.worldY,Phase.RETURN_MAP,"进入野外检查空位");}
            if((f.header||f.formation||f.deploy||f.warX>0||f.rallyX>0)&&f.backX>0){backAttempts++;return act(Kind.BACK,f.backX,f.backY,Phase.RETURN_MAP,"返回地图检查新的空位");}
            return waitFor("等待可识别的返回页面");
        }
        if(phase==Phase.MAP){
            if(now<retryAt)return waitFor("等待编队恢复，稍后再试");
            if(!f.map){
                if(f.worldX>0)return act(Kind.NAVIGATE,f.worldX,f.worldY,Phase.MAP,"进入野外查看行军状态");
                if(f.header||f.formation||f.warX>0||f.rallyX>0){returnToMap(now);return waitFor("先确认地图状态，再连续跟车");}
                return unrecognized(now,"未识别游戏地图");
            }
            int reading=marchHidden?-2:f.marchUsed;
            if(!marchHidden&&!MarchCount.valid(reading)){resetEvidence();return unrecognized(now,"行军数字模糊，等待清晰画面");}
            unknownSince=-1;
            if(reading!=stableUsed){stableUsed=reading;stableSince=now;stableReads=1;}
            else if(now-stableSince>=500)stableReads++;
            if(stableReads<2)return waitFor(marchHidden?"地图未显示行军栏 · 确认后直接尝试加入":"行军 "+reading+"/6 · 再确认一次");
            if(reading==6){checkedAt=-1;return waitFor("行军 6/6 · 等待队伍返回");}
            if(remainingMillis(now)<12000)return waitFor("本轮即将结束，停止新上车");
            remaining=marchHidden?6:6-reading;attemptedMask=0;
            selected=choosePreset();if(selected<0)return pause("没有已录入并启用的预设编队");
            checkedAt=now;phase=Phase.ENTER_LIST;lastNav=-1;phaseSince=now;
        }
        if(f.map&&(phase==Phase.FIND||phase==Phase.FORMATION||phase==Phase.SEND)){
            phase=Phase.MAP;checkedAt=-1;remaining=0;resetEvidence();unknownSince=-1;
            return waitFor("游戏已回地图，重新检查空位继续");
        }
        if(phase==Phase.ENTER_LIST||phase==Phase.FIND||phase==Phase.FORMATION||phase==Phase.SEND||phase==Phase.RETURN_LIST){
            if(checkedAt<0||now-checkedAt>CAPACITY_MAX_AGE||remaining<=0){returnToMap(now);return waitFor("本批空位已用完或需更新，回地图检查");}
        }
        if(phase==Phase.ENTER_LIST){
            if(f.header&&f.rallyList){phase=Phase.FIND;phaseSince=now;lastScroll=now;unknownSince=-1;}
            else if(f.map){
                if(f.marchUsed==6){phase=Phase.MAP;checkedAt=-1;resetEvidence();return waitFor("行军已变为 6/6，等待返回");}
                if(f.shortcutX>0)return navigate(0,f.shortcutX,f.shortcutY,now,"打开红色集结入口");
                if(f.allianceX>0)return navigate(0,f.allianceX,f.allianceY,now,"打开联盟");
                phase=Phase.MAP;checkedAt=-1;resetEvidence();waitUntil=now+2000;return waitFor("等待红色集结入口出现");
            }else if(f.warX>0)return navigate(1,f.warX,f.warY,now,"打开联盟战争");
            else if(f.rallyX>0)return navigate(2,f.rallyX,f.rallyY,now,"打开集结列表");
            else if(f.header&&f.backX>0){returnToList(now);backAttempts++;return act(Kind.BACK,f.backX,f.backY,Phase.RETURN_LIST,"退出集结详情，返回列表");}
            else return unrecognized(now,"未识别进入集结的中间页面");
        }
        if(phase==Phase.RETURN_LIST){
            if(f.header&&f.rallyList){phase=Phase.FIND;phaseSince=now;unknownSince=-1;}
            else if(f.map){phase=Phase.MAP;checkedAt=-1;resetEvidence();return waitFor("已回地图，继续检查空位");}
            else if(now-returnSince>=20000||backAttempts>=6)return pause("未能返回集结列表，停止重复返回");
            else if((f.header||f.formation||f.deploy)&&f.backX>0){backAttempts++;return act(Kind.BACK,f.backX,f.backY,Phase.RETURN_LIST,"此集结已变化，返回列表继续找");}
            else return unrecognized(now,"等待回到集结列表");
        }
        if(phase==Phase.FIND){
            if(!f.header||!f.rallyList||f.deploy)return unrecognized(now,"等待集结列表");
            unknownSince=-1;
            Row row=f.rows.stream().filter(r->r.order>=0&&r.available&&r.x>0&&r.y>0&&(now>=avoidRowUntil||Math.abs(r.y-failedRowY)>60))
                .min(Comparator.comparingInt(r->r.order)).orElse(null);
            if(row!=null){
                if(remainingMillis(now)<8000)return waitFor("本轮即将结束，停止新上车");
                selected=choosePreset();
                if(selected<0){retryAt=now+8000;returnToMap(now);return waitFor("本轮已检查全部预设，稍后重查");}
                lastJoinY=row.y;charged=false;markerMissingSince=-1;
                return act(Kind.JOIN,row.x,row.y,Phase.FORMATION,"点击绿色＋ · 预设 "+(selected+1)+" · 本批余 "+remaining+" 次");
            }
            if(now-lastScroll>=5000){lastScroll=now;return act(Kind.SCROLL,0,0,Phase.FIND,"暂时没有可加入的巨熊，翻看下一屏");}
            return waitFor("等待可加入的绿色＋");
        }
        if(phase==Phase.FORMATION){
            if(selected<0){retryAt=now+8000;returnToMap(now);return waitFor("预设暂时不可用，稍后重查");}
            if((f.formation||f.deploy)&&!f.header){
                if(!f.savedPreset[selected]){
                    if(markerMissingSince<0)markerMissingSince=now;
                    return now-markerMissingSince>=6000?pause("第 "+(selected+1)+" 个预设旗帜持续未匹配，请检查旗帜栏位置"):waitFor("等待第 "+(selected+1)+" 个预设旗帜显示清晰");
                }
                markerMissingSince=-1;
                return act(Kind.PRESET,presetX[selected],presetY[selected],Phase.SEND,"选择顶部第 "+(selected+1)+" 个预设");
            }
            if(f.header&&!f.rallyList){avoidLastRow(now);returnToList(now);return waitFor("集结已发车或进入详情，换下一队");}
            if(now-phaseSince>5000&&f.header){avoidLastRow(now);phase=Phase.FIND;return waitFor("未进入出征页，跳过刚才的集结");}
            return unrecognized(now,"等待出征页面");
        }
        if(phase==Phase.SEND){
            if(f.header){returnToList(now);avoidLastRow(now);return waitFor("集结状态已变化，继续下一队");}
            if(f.deploy&&!f.header&&!f.map){
                if(f.troops!=null&&!f.troops.usable())return nextFormation(now,"当前预设兵力为空或超出上限");
                if(f.troops==null)return now-phaseSince>6000?nextFormation(now,"编队兵力未读清"):waitFor("检查当前预设的实际兵力");
                return act(Kind.DEPLOY,f.deployX,f.deployY,Phase.VERIFY,"提交第 "+(selected+1)+" 个预设出征");
            }
            return unrecognized(now,"等待出征按钮");
        }
        if(phase==Phase.VERIFY){
            if((f.header||f.map)&&!f.deploy){
                submitted++;nextPreset=(selected+1)%6;attemptedMask|=1<<selected;charged=false;unknownSince=-1;
                if(f.header&&f.rallyList&&remaining>0&&choosePreset()>=0&&now-checkedAt<CAPACITY_MAX_AGE){phase=Phase.FIND;phaseSince=now;return waitFor("第 "+(selected+1)+" 个预设已提交 · 留在列表继续下一队");}
                returnToMap(now);return waitFor("本批已提交，检查新的空位");
            }
            if(now-phaseSince>7000&&(f.formation||f.deploy)){
                // Never repeat a deploy click while its outcome is ambiguous.
                nextPreset=(selected+1)%6;attemptedMask|=1<<selected;returnToList(now);avoidLastRow(now);
                return waitFor("未确认出征结果，保留一次空位占用，返回列表继续");
            }
            return unrecognized(now,"等待出征结果");
        }
        return waitFor("等待画面");
    }
    void complete(boolean success,long now){
        Kind kind=pending;pending=null;if(!running||kind==null)return;
        if(!success||expired(now)){stop();return;}
        if(kind==Kind.DEPLOY){remaining=Math.max(0,remaining-1);charged=true;nextPreset=(selected+1)%6;attemptedMask|=1<<selected;}
        if(phase!=afterAction){phase=afterAction;phaseSince=now;unknownSince=-1;}
        waitUntil=now+(kind==Kind.PRESET?550:700);
    }
}
