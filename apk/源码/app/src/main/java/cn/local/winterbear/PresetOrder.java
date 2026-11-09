package cn.local.winterbear;

/** The six saved game formations, selected independently of the march limit. */
final class PresetOrder {
    static final int COUNT = 6;
    static final int ALL = (1 << COUNT) - 1;

    static boolean valid(int selection) {
        return selection > 0 && (selection & ~ALL) == 0;
    }

    static int next(int selection, int start) {
        for (int offset = 0; offset < COUNT; offset++) {
            int preset = Math.floorMod(start + offset, COUNT);
            if ((selection & (1 << preset)) != 0) return preset;
        }
        return -1;
    }
}
