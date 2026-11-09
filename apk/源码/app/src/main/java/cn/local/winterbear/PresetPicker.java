package cn.local.winterbear;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.function.IntConsumer;

/** Shared by the activity settings and the in-game task menu. */
final class PresetPicker extends LinearLayout {
    private final CheckBox[] choices = new CheckBox[PresetOrder.COUNT];
    private final TextView hint;

    PresetPicker(Context context, int initial, IntConsumer changed) {
        super(context);
        setOrientation(VERTICAL);
        LinearLayout heading = new LinearLayout(context);
        heading.setGravity(android.view.Gravity.CENTER_VERTICAL);
        addView(heading, new LayoutParams(-1, -2));
        heading.addView(Ui.text(context, "使用编队", 16, Ui.TEXT), new LayoutParams(0, -2, 1));
        Button all = Ui.button(context, heading, "全选", false, () -> select(PresetOrder.ALL));
        Button clear = Ui.button(context, heading, "清空", false, () -> select(0));
        for (Button button : new Button[]{all, clear}) {
            button.setMinWidth(0);
            button.setMinimumWidth(0);
            button.setMinHeight(0);
            button.setMinimumHeight(0);
            button.setPadding(0, 0, 0, 0);
            button.setTextSize(13);
            LayoutParams size = new LayoutParams(Ui.dp(context, 52), Ui.dp(context, 48));
            size.leftMargin = Ui.dp(context, 8);
            button.setLayoutParams(size);
        }
        for (int row = 0; row < 2; row++) {
            LinearLayout line = new LinearLayout(context);
            addView(line, new LayoutParams(-1, -2));
            for (int column = 0; column < 3; column++) {
                int preset = row * 3 + column;
                CheckBox choice = new CheckBox(context);
                choice.setText("第 " + (preset + 1) + " 个");
                choice.setContentDescription("使用第 " + (preset + 1) + " 个预设编队");
                choice.setTextColor(Ui.TEXT);
                choice.setButtonTintList(ColorStateList.valueOf(Ui.ACCENT));
                choice.setSingleLine(true);
                choice.setMinHeight(Ui.dp(context, 48));
                choice.setMinimumHeight(Ui.dp(context, 48));
                choice.setAutoSizeTextTypeUniformWithConfiguration(12, 15, 1, TypedValue.COMPLEX_UNIT_SP);
                choice.setChecked((initial & (1 << preset)) != 0);
                choices[preset] = choice;
                line.addView(choice, new LayoutParams(0, -2, 1));
            }
        }
        hint = Ui.text(context, "按选中的编号顺序循环使用。", 13, Ui.MUTED);
        addView(hint);
        for (CheckBox choice : choices) {
            choice.setOnCheckedChangeListener((button, checked) -> {
                validateSelection();
                if (changed != null) changed.accept(selection());
            });
        }
    }

    int selection() {
        int selection = 0;
        for (int preset = 0; preset < choices.length; preset++) {
            if (choices[preset].isChecked()) selection |= 1 << preset;
        }
        return selection;
    }

    private void select(int selection) {
        for (int preset = 0; preset < choices.length; preset++) {
            choices[preset].setChecked((selection & (1 << preset)) != 0);
        }
        validateSelection();
    }

    boolean validateSelection() {
        boolean valid = PresetOrder.valid(selection());
        hint.setText(valid ? "按选中的编号顺序循环使用。" : "请至少选择一个编队");
        hint.setTextColor(valid ? Ui.MUTED : Ui.DANGER);
        return valid;
    }
}
