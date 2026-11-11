package cn.local.winterbear;

/** Search/launch loop. Counts are observations; a click alone never confirms a dispatch. */
final class HuntFlow {
    enum Mode { GIANT, BEAST }
    enum Page { UNKNOWN, MAP, SEARCH, GIANT, BEAST, RALLY, FORMATION, ENERGY, ERROR }
    enum Kind { WAIT, STOP, OPEN, CATEGORY, LESS, MORE, SEARCH, TARGET, LAUNCH, PRESET, DEPLOY, BACK, DISMISS, USE, CLOSE }
    enum Phase { MAP, SEARCH, RESULT, RALLY, FORMATION, SEND, VERIFY, RECOVER }
    static final class Options {
        final Mode mode; final int level,limit,canLimit,presetSelection;
        Options(Mode mode,int level,int limit,int canLimit){
            this(mode,level,limit,canLimit,PresetOrder.ALL);
        }
        Options(Mode mode,int level,int limit,int canLimit,int presetSelection){
            if(mode==null||level<1||level>99||limit<1||limit>6||canLimit<0||canLimit>9999)throw new IllegalArgumentException("设置超出范围");
            if(!PresetOrder.valid(presetSelection))throw new IllegalArgumentException("请至少选择一个有效编队");
            this.mode=mode;this.level=level;this.limit=limit;this.canLimit=canLimit;
            this.presetSelection=presetSelection;
        }
        boolean sameTask(Options other){
            return other!=null&&mode==other.mode&&level==other.level&&limit==other.limit
                    &&canLimit==other.canLimit&&presetSelection==other.presetSelection;
        }
    }
    static final class Frame {
        int width,height;
        Page page=Page.UNKNOWN; int used=-1,level=-1,category=-1,stock=-1,energy=-1;
        HuntMarches.Row[] marches;
        boolean marchPanelAbsent,lowEnergy; final boolean[] presets=new boolean[6];
        long age; final int[][] points=new int[Kind.values().length][2];final int[][] flags=new int[6][2];
        void point(Kind k,int x,int y){points[k.ordinal()]=new int[]{x,y};}
        boolean contains(int x,int y){return x>0&&y>0&&x<width&&y<height;}
    }
    static final class Action {
        final Kind kind;final String message;final int preset;
        Action(Kind k,String m,int p){kind=k;message=m;preset=p;}
    }
    final Options options; Phase phase=Phase.MAP; boolean running;
    final HuntMarches marches=new HuntMarches();
    int nextPreset,submitted,cansUsed; private int selected,stableUsed=-2,stableCount;
    private int lastLevel=-1,levelFailures,categoryAttempts,batchLeft,stockBefore=-1,energyBefore=-1;
    private boolean awaitingCan,closingEnergy;private Kind pending,interrupted;private long waitUntil,since,unknownSince=-1;
    private long absentSince=-1,absentLast=-1;
    HuntFlow(Options options){this.options=options;nextPreset=PresetOrder.next(options.presetSelection,0);}
    // The session ends on stamina/can allowance or a manual stop, not elapsed time.
    void start(long now){running=true;phase=Phase.MAP;since=now;waitUntil=now;stableCount=0;pending=null;interrupted=null;}
    // Pausing invalidates callbacks in the controller. Resume preserves the session's limits.
    void stop(){running=false;pending=null;}
    void suspend(long now,boolean dispatched){
        Kind action=pending;pending=null;running=false;
        if(action==null)return;
        if(action==Kind.USE&&!dispatched){cansUsed--;awaitingCan=false;}
        if(action==Kind.CLOSE&&!dispatched)interrupted=Kind.CLOSE;
        if(!dispatched)return;
        if(action==Kind.DEPLOY)phase(Phase.VERIFY,now);
        else if(action==Kind.PRESET)phase(Phase.FORMATION,now);
        else if(action==Kind.OPEN||action==Kind.SEARCH||action==Kind.TARGET||action==Kind.LAUNCH||action==Kind.CLOSE||action==Kind.DISMISS)interrupted=action;
    }
    void resume(long now){running=true;pending=null;waitUntil=now;since=now;unknownSince=-1;absentSince=absentLast=-1;}
    boolean pending(){return pending!=null;}
    private Action waitFor(String s){return new Action(Kind.WAIT,s,-1);}
    private Action halt(String s){stop();return new Action(Kind.STOP,s,-1);}
    private Action act(Kind k,String s){pending=k;return new Action(k,s,selected);}
    private void phase(Phase p,long now){phase=p;since=now;unknownSince=-1;}
    private Action unknown(long now,String message){if(unknownSince<0)unknownSince=now;return now-unknownSince>18000?halt(message+"，已暂停"):waitFor(message);}
    static int batch(int cap,int used,int stock){int n=Math.min(4,Math.max(0,cap-used));return n>0&&stock>=n?n:0;}
    private Action reconcileInterrupted(Frame frame,long now){
        Page page=frame.page;
        Kind action=interrupted;
        Phase observed=null;
        if(action==Kind.CLOSE){
            if(page==Page.ENERGY){interrupted=null;closingEnergy=true;since=now;return act(Kind.CLOSE,"关闭已补充的体力窗口");}
            if(page==Page.FORMATION){closingEnergy=false;observed=Phase.SEND;}
        }else if(action==Kind.DISMISS){
            if(page==Page.ERROR){interrupted=null;return act(Kind.DISMISS,"关闭提示，返回地图重新搜索");}
            if(page!=Page.UNKNOWN)observed=Phase.RECOVER;
        }else if(page==Page.MAP)observed=Phase.MAP;
        else if(action==Kind.OPEN&&page==Page.SEARCH)observed=Phase.SEARCH;
        else if(action==Kind.SEARCH){
            if(page==Page.SEARCH)observed=Phase.SEARCH;
            if(page==(options.mode==Mode.GIANT?Page.GIANT:Page.BEAST))observed=Phase.RESULT;
        }else if(action==Kind.TARGET){
            if(page==(options.mode==Mode.GIANT?Page.GIANT:Page.BEAST))observed=Phase.RESULT;
            if(page==Page.RALLY&&options.mode==Mode.GIANT)observed=Phase.RALLY;
            if(page==Page.FORMATION)observed=Phase.FORMATION;
        }else if(action==Kind.LAUNCH){
            if(page==Page.RALLY)observed=Phase.RALLY;
            if(page==Page.FORMATION)observed=Phase.FORMATION;
        }
        if(observed==null)return unknown(now,"确认上次操作结果");
        interrupted=null;phase(observed,now);
        if(observed==Phase.MAP)stableCount=0;
        return null;
    }
    Action next(Frame f,long now){
        if(!running||pending!=null)return waitFor("已暂停");
        if(f.age<0||f.age>4000){absentSince=absentLast=-1;return unknown(now,"画面更新较慢，等待新截图");}
        if(f.page!=Page.MAP||!f.marchPanelAbsent)absentSince=absentLast=-1;
        if(now<waitUntil)return waitFor("等待游戏响应");
        if(interrupted!=null){Action recovery=reconcileInterrupted(f,now);if(recovery!=null)return recovery;}
        if(f.page==Page.ENERGY){
            if(closingEnergy)return now-since>10000?halt("体力窗口未关闭，已暂停"):waitFor("等待体力窗口关闭");
            if(phase!=Phase.VERIFY&&phase!=Phase.SEND)return halt("请先关闭体力窗口，回地图开始");
            if(options.canLimit==0)return halt("体力不足；本次未开启使用罐头");
            if(awaitingCan){
                if(f.stock>=0&&f.energy>=0&&f.stock==stockBefore-1&&f.energy>=energyBefore+10){awaitingCan=false;batchLeft--;}
                else return now-since>10000?halt("未确认罐头使用结果，已暂停；额度已预留"):waitFor("确认罐头使用结果");
            }
            if(batchLeft==0&&stockBefore>=0){stockBefore=-1;closingEnergy=true;since=now;return act(Kind.CLOSE,"已补充体力，返回出征");}
            if(batchLeft==0){
                if(cansUsed>=options.canLimit)return halt("已达到本次罐头上限");
                if(f.stock<0||f.energy<0)return unknown(now,"读取库存罐头与体力");
                batchLeft=batch(options.canLimit,cansUsed,f.stock);
                if(batchLeft==0)return halt("库存不足本次所需罐头，已停止");
            }
            if(f.stock<batchLeft||f.energy<0)return halt("罐头库存不足或数量不清，已停止");
            stockBefore=f.stock;energyBefore=f.energy;
            // Reserve before dispatch; cancellation/timeout never grants extra budget.
            cansUsed++;awaitingCan=true;since=now;
            return act(Kind.USE,"补充罐头 "+cansUsed+" / "+options.canLimit);
        }
        if(awaitingCan)return halt("罐头使用结果未确认，已暂停");
        if(closingEnergy){
            if(f.page!=Page.FORMATION)return unknown(now,"等待返回出征编队");
            closingEnergy=false;
        }
        if(f.page==Page.ERROR&&(phase==Phase.VERIFY||phase==Phase.SEND||phase==Phase.FORMATION))return act(Kind.DISMISS,"关闭提示，返回地图重新搜索");
        if(phase==Phase.RECOVER){
            if(f.page==Page.MAP){phase(Phase.MAP,now);stableCount=0;waitUntil=now+2500;return waitFor("重新检查队列");}
            if(f.page==Page.FORMATION||f.page==Page.SEARCH||f.page==Page.GIANT||f.page==Page.BEAST||f.page==Page.RALLY)return act(Kind.BACK,"返回地图");
            return unknown(now,"等待返回地图");
        }
        if(phase==Phase.VERIFY){
            if(f.page==Page.MAP&&f.used>=0){
                if(f.marches!=null&&f.marches.length==f.used&&marches.observe(f.marches,now-f.age,true)){submitted++;nextPreset=PresetOrder.next(options.presetSelection,selected+1);phase(Phase.MAP,now);stableCount=0;return waitFor(activity()+" "+marches.active()+"/"+options.limit+" 队 · 已出征 "+submitted+" 次");}
                return now-since>15000?halt("未确认新增任务队伍，已暂停，请检查出征结果"):waitFor("确认本次出征队伍");
            }
            return unknown(now,"等待出征结果");
        }
        if(phase==Phase.MAP){
            if(f.page==Page.SEARCH||f.page==Page.GIANT||f.page==Page.BEAST||f.page==Page.RALLY||f.page==Page.FORMATION)
                return act(Kind.BACK,"返回地图开始本次任务");
            if(f.page!=Page.MAP)return unknown(now,"请停在野外地图");
            if(f.marchPanelAbsent){
                stableUsed=-2;stableCount=0;
                long capturedAt=now-f.age;
                if(!marches.mayConfirmEmpty(capturedAt)){
                    absentSince=absentLast=-1;
                    if(marches.awaitingReturn()){unknownSince=-1;return waitFor("等待已确认返程的队伍回城");}
                    return unknown(now,"未读到行军栏，保留本次队伍，等待重新识别");
                }
                if(capturedAt<=absentLast)return waitFor("等待新画面确认行军栏");
                if(absentSince<0||capturedAt-absentLast>4000)absentSince=capturedAt;
                absentLast=capturedAt;
                if(capturedAt-absentSince<1000)return waitFor("再次确认地图行军栏");
                marches.observe(new HuntMarches.Row[0],capturedAt,false);
                unknownSince=-1;
                selected=nextPreset;lastLevel=-1;levelFailures=0;categoryAttempts=0;
                return act(Kind.OPEN,"打开地图搜索");
            }
            if(f.used<0)return unknown(now,"读取地图行军数量");
            if(f.used!=stableUsed){stableUsed=f.used;stableCount=1;return waitFor("确认行军 "+f.used+"/6");}
            if(++stableCount<2)return waitFor("再次确认行军数量");
            if(f.marches==null||f.marches.length!=f.used)return unknown(now,"读取任务队伍，请保持行军列表可见");
            unknownSince=-1;
            marches.observe(f.marches,now-f.age,false);
            if(marches.active()>=options.limit)return waitFor(activity()+" "+marches.active()+"/"+options.limit+" 队 · 等待本次队伍回城");
            if(f.used>=6)return waitFor("总行军 6/6 · 等待空位");
            selected=nextPreset;lastLevel=-1;levelFailures=0;categoryAttempts=0;
            return act(Kind.OPEN,"打开地图搜索");
        }
        if(phase==Phase.SEARCH){
            if(f.page!=Page.SEARCH)return unknown(now,"等待搜索面板");
            if(f.category!=options.mode.ordinal()){
                if(categoryAttempts++>=3)return halt("未确认目标分类，请检查搜索面板");
                return act(Kind.CATEGORY,"选择"+(options.mode==Mode.GIANT?"冰原巨兽":"野兽"));
            }
            if(f.level<1)return unknown(now,"读取搜索等级");
            unknownSince=-1;
            if(f.level!=options.level){
                if(f.level==lastLevel)levelFailures++;else levelFailures=0;
                if(levelFailures>=3)return halt("无法调整到所选等级，请检查已解锁等级");
                lastLevel=f.level;
                return act(f.level>options.level?Kind.LESS:Kind.MORE,"调整等级："+f.level+" → "+options.level);
            }
            return act(Kind.SEARCH,"搜索 "+options.level+" 级目标");
        }
        if(phase==Phase.RESULT){
            Page expected=options.mode==Mode.GIANT?Page.GIANT:Page.BEAST;
            if(f.page!=expected)return unknown(now,"等待对应目标搜索结果");
            if(f.level<1)return unknown(now,"目标等级未读清，正在重新识别");
            if(f.level!=options.level)return halt("目标为 "+f.level+" 级，与设置 "+options.level+" 级不同，已暂停");
            return act(Kind.TARGET,options.mode==Mode.GIANT?"点击集结":"点击攻击");
        }
        if(phase==Phase.RALLY){
            if(f.page!=Page.RALLY)return unknown(now,"等待集结时间窗口");
            return act(Kind.LAUNCH,"按游戏默认时间发起集结");
        }
        if(phase==Phase.FORMATION||phase==Phase.SEND){
            if(f.page!=Page.FORMATION)return unknown(now,"等待出征编队");
            if(!f.presets[selected])return unknown(now,"读取第 "+(selected+1)+" 个预设旗帜");
            if(phase==Phase.FORMATION)return act(Kind.PRESET,"选择第 "+(selected+1)+" 个预设");
            // Use the selected preset directly. Troop-number OCR is not a dispatch gate.
            return act(Kind.DEPLOY,f.lowEnergy?"体力不足，打开补充窗口":"出征第 "+(selected+1)+" 个预设");
        }
        return unknown(now,"等待识别游戏页面");
    }
    private String activity(){return options.mode==Mode.GIANT?"巨兽":"野兽";}
    void complete(boolean success,long now){
        Kind k=pending;pending=null;if(!running||k==null)return;
        if(!success){stop();return;}
        waitUntil=now+(k==Kind.LESS||k==Kind.MORE?350:850);unknownSince=-1;
        switch(k){
            case OPEN:phase(Phase.SEARCH,now);break;
            case SEARCH:phase(Phase.RESULT,now);break;
            case TARGET:phase(options.mode==Mode.GIANT?Phase.RALLY:Phase.FORMATION,now);break;
            case LAUNCH:phase(Phase.FORMATION,now);break;
            case PRESET:phase(Phase.SEND,now);break;
            case DEPLOY:phase(Phase.VERIFY,now);break;
            case CLOSE:phase(Phase.SEND,now);batchLeft=0;break;
            case DISMISS:phase(Phase.RECOVER,now);break;
            default:break;
        }
    }
}
