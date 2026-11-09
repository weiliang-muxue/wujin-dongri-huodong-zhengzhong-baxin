package cn.local.winterbear;

import android.content.SharedPreferences;
import java.io.File;
import java.io.FileInputStream;

/** Real Config import/resize code with isolated in-memory preference storage. */
public final class PresetSettingsRuntimeCheck {
    private static void require(boolean value,String message){
        if(!value)throw new AssertionError(message);
    }

    public static void main(String[] args){
        try{
            File assets=new File(args[0]),work=new File(args[1]);
            SharedPreferences preferences=CompatibilityRuntimeCheck.preferences();
            Config config=new Config(preferences,work,path->new FileInputStream(new File(assets,path)));
            require(config.presetSelection()==63,"First-use default changed");
            preferences.edit().putBoolean("enabled0",false).putBoolean("enabled1",true)
                    .putBoolean("enabled2",false).putBoolean("enabled3",false)
                    .putBoolean("enabled4",true).putBoolean("enabled5",false).putInt("nextPreset",4).apply();
            require(config.presetSelection()==18&&preferences.getInt("nextPreset",-1)==4,"Legacy settings lost");
            config.setPresetSelection(36);
            config.fillMissingReferenceLayout(1080,2400);
            require(config.presetSelection()==36,"First map connection replaced selection");
            preferences.edit().putInt("nextPreset",5).apply();
            config.adaptReferenceLayout(1152,2376);
            require(config.presetSelection()==36&&preferences.getInt("nextPreset",-1)==5,"Resolution change replaced selection");
            for(int selection=1;selection<=63;selection++){
                config.setPresetSelection(selection);
                Config reopened=new Config(preferences,work,path->new FileInputStream(new File(assets,path)));
                require(reopened.presetSelection()==selection,"Selected presets not restored");
                require(preferences.getInt("nextPreset",-1)==Integer.numberOfTrailingZeros(selection),"Starting preset mismatch");
                for(int preset=0;preset<6;preset++){
                    require(reopened.enabled(preset)==((selection&(1<<preset))!=0),"Stored bear switch mismatch");
                }
            }
            config.setPresetSelection(0);
            require(config.presetSelection()==0,"Empty edit silently selected a preset");
            System.out.println("ANDROID PRESET SETTINGS PASS: 68 cases; legacy switches, all selections, first map import and resize");
        }catch(Throwable error){error.printStackTrace(System.out);System.exit(1);}
    }
}
