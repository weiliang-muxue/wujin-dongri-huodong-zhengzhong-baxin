package cn.local.winterbear;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Only an unambiguous, complete x/6 observation is evidence. No OCR guesses. */
final class MarchCount {
    private static final Pattern COUNTER=Pattern.compile("^([0-6])\\s*[/／]\\s*6$");
    static boolean valid(int used){return used>=0&&used<=6;}
    static int used(String text) {
        if(text==null)return -1;
        Matcher m=COUNTER.matcher(text.trim());
        return m.matches()?Integer.parseInt(m.group(1)):-1;
    }
    /** Scenery may contribute another OCR line; only one complete counter is evidence. */
    static int fromLines(String text){
        if(text==null)return -1;
        int value=-1,count=0;
        for(String line:text.split("\\R")){int candidate=used(line);if(candidate>=0){value=candidate;count++;}}
        return count==1?value:-1;
    }
}
