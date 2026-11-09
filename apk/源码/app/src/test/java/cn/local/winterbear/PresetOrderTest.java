package cn.local.winterbear;

import org.junit.Test;
import static org.junit.Assert.*;

public class PresetOrderTest {
    @Test public void acceptsExactlyTheNonemptySixPresetSelections() {
        for (int selection = 1; selection <= 63; selection++) assertTrue(PresetOrder.valid(selection));
        for (int selection : new int[]{0, -1, 64, 65, 127}) assertFalse(PresetOrder.valid(selection));
    }

    @Test public void everySelectionCyclesInAscendingOrderFromEveryStart() {
        for (int selection = 1; selection <= 63; selection++) {
            for (int start = -6; start <= 12; start++) {
                int expected = Math.floorMod(start, 6);
                while ((selection & (1 << expected)) == 0) expected = (expected + 1) % 6;
                assertEquals(expected, PresetOrder.next(selection, start));
            }
        }
    }

    @Test public void singleSelectionAlwaysUsesThatPreset() {
        for (int preset = 0; preset < 6; preset++) {
            for (int start = 0; start <= 6; start++) assertEquals(preset, PresetOrder.next(1 << preset, start));
        }
    }

    @Test public void emptySelectionHasNoFallback() {
        assertEquals(-1, PresetOrder.next(0, 0));
    }
}
