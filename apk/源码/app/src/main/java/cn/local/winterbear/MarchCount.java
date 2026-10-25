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
}
