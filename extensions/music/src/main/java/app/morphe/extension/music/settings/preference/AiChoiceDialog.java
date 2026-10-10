/*
 * Copyright 2026 Morphe.
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.music.settings.preference;

import android.app.Dialog;
import android.content.Context;
import android.util.Pair;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import java.util.function.IntConsumer;
import app.morphe.extension.shared.ui.CustomDialog;
import app.morphe.extension.shared.ui.Dim;

/** Themed single-choice list. Plain AlertDialog items are unreadable on the app's dark dialog background. */
public final class AiChoiceDialog {
    private AiChoiceDialog() { }

    public static void show(Context context, String title, String[] labels, IntConsumer selected) {
        Pair<Dialog, LinearLayout> ui = CustomDialog.create(context, title, null, null,
                null, null, () -> { }, null, null, false);
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            Button button = CustomDialog.createButton(context, ui.first, labels[i],
                    () -> selected.accept(index), false, true);
            button.setSingleLine(false);
            button.setEllipsize(null);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (i > 0) params.topMargin = Dim.dp8;
            list.addView(button, params);
        }
        ScrollView scroll = new ScrollView(context);
        scroll.addView(list);
        // Insert before the helper's closing button row.
        ui.second.addView(scroll, ui.second.getChildCount() - 1, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        ui.first.show();
    }
}
