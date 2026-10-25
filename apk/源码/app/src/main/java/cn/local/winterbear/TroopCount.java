package cn.local.winterbear;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Selected troops / march capacity, not the march-slot counter. */
final class TroopCount {
    final int selected,capacity;
    TroopCount(int selected,int capacity){this.selected=selected;this.capacity=capacity;}
    boolean usable(){return selected>0&&selected<=capacity;}
    static TroopCount parse(String text){
        if(text==null)return null;
        String clean=text.replaceAll("\\s", "").replace('，',',');
        String number="(?:[0-9]{1,7}|[0-9]{1,3}(?:[,.][0-9]{3}){1,2})";
        Matcher match=Pattern.compile("^("+number+")[/／]("+number+")[!！]?$",Pattern.UNICODE_CHARACTER_CLASS).matcher(clean);
        if(!match.matches())return null;
        int selected=Integer.parseInt(match.group(1).replace(",","").replace(".","")),capacity=Integer.parseInt(match.group(2).replace(",","").replace(".",""));
        return capacity>=100&&capacity<=2_000_000&&selected<=2_000_000?new TroopCount(selected,capacity):null;
    }
}
