package com.shaterguy.chatgptpromptscheduler;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

public final class Ui {
    private Ui() {}

    public static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static boolean isTablet(Context context) {
        return context.getResources().getConfiguration().smallestScreenWidthDp >= 600;
    }

    public static int color(Context context, int resource) {
        return context.getColor(resource);
    }

    public static LinearLayout content(Context context) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(context, 20), dp(context, 12), dp(context, 20), dp(context, 28));
        return root;
    }

    public static TextView title(Context context, String text) {
        TextView view = text(context, text, 24, R.color.text_primary);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setPadding(0, dp(context, 8), 0, dp(context, 8));
        if (Build.VERSION.SDK_INT >= 28) view.setAccessibilityHeading(true);
        return view;
    }

    public static TextView section(Context context, String text) {
        TextView view = text(context, text, 17, R.color.text_primary);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setPadding(0, dp(context, 16), 0, dp(context, 8));
        if (Build.VERSION.SDK_INT >= 28) view.setAccessibilityHeading(true);
        return view;
    }

    public static TextView body(Context context, String text) {
        TextView view = text(context, text, 14, R.color.text_secondary);
        view.setPadding(0, dp(context, 4), 0, dp(context, 4));
        return view;
    }

    public static TextView label(Context context, String text) {
        TextView view = text(context, text, 13, R.color.text_secondary);
        view.setPadding(0, dp(context, 8), 0, dp(context, 4));
        return view;
    }

    public static TextView emphasis(Context context, String text) {
        TextView view = text(context, text, 16, R.color.text_primary);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setPadding(0, dp(context, 8), 0, dp(context, 8));
        return view;
    }

    private static TextView text(Context context, String text, int size, int role) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color(context, role));
        view.setLineSpacing(dp(context, 2), 1f);
        return view;
    }

    public static LinearLayout card(Context context, LinearLayout parent) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 16));
        card.setBackgroundResource(R.drawable.panel_background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(context, 8), 0, dp(context, 8));
        parent.addView(card, params);
        return card;
    }

    public static Button button(Context context, String text, View.OnClickListener listener) {
        return styledButton(context, text, listener, false);
    }

    public static Button primaryButton(Context context, String text, View.OnClickListener listener) {
        return styledButton(context, text, listener, true);
    }

    private static Button styledButton(Context context, String text, View.OnClickListener listener, boolean primary) {
        Button button = new Button(context);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setMinHeight(dp(context, 48));
        button.setMinimumHeight(dp(context, 48));
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10));
        button.setGravity(Gravity.CENTER);
        button.setTextColor(color(context, primary ? R.color.on_accent : R.color.text_primary));
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color(context, primary ? R.color.accent : R.color.control_surface));
        shape.setCornerRadius(dp(context, 10));
        if (!primary) shape.setStroke(dp(context, 1), color(context, R.color.hairline));
        button.setBackground(new RippleDrawable(
                ColorStateList.valueOf(color(context, R.color.touch_highlight)), shape, null));
        button.setOnClickListener(listener);
        return button;
    }

    public static void navigation(Activity activity, LinearLayout root, String selected) {
        LinearLayout navigation = row(activity);
        String[] keys = {"schedules", "logs", "settings"};
        String[] labels = {"예약", "실행 기록", "설정"};
        Class<?>[] destinations = {MainActivity.class, LogsActivity.class, SettingsActivity.class};
        for (int i = 0; i < keys.length; i++) {
            final Class<?> destination = destinations[i];
            final boolean current = keys[i].equals(selected);
            Button item = button(activity, labels[i], v -> {
                if (!current) activity.startActivity(new Intent(activity, destination)
                        .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            });
            item.setSelected(current);
            item.setContentDescription(labels[i] + (current ? ", 현재 화면" : ", 화면 열기"));
            if (current) {
                item.setTextColor(color(activity, R.color.accent));
                GradientDrawable shape = new GradientDrawable();
                shape.setColor(color(activity, R.color.accent_surface));
                shape.setCornerRadius(dp(activity, 10));
                item.setBackground(shape);
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            params.setMargins(dp(activity, 2), dp(activity, 4), dp(activity, 2), dp(activity, 12));
            navigation.addView(item, params);
        }
        root.addView(navigation);
    }

    public static LinearLayout disclosure(Context context, LinearLayout parent, String closedLabel) {
        LinearLayout details = new LinearLayout(context);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setVisibility(View.GONE);
        Button opener = button(context, closedLabel + " 보기", v -> {});
        opener.setOnClickListener(v -> {
            boolean visible = details.getVisibility() == View.VISIBLE;
            details.setVisibility(visible ? View.GONE : View.VISIBLE);
            opener.setText(closedLabel + (visible ? " 보기" : " 접기"));
            opener.setContentDescription(opener.getText() + (visible ? ", 접힘" : ", 펼쳐짐"));
        });
        opener.setContentDescription(closedLabel + " 보기, 접힘");
        parent.addView(opener);
        parent.addView(details);
        return details;
    }

    public static void input(Context context, TextView input) {
        input.setMinHeight(dp(context, 48));
        input.setTextSize(15);
        input.setTextColor(color(context, R.color.text_primary));
    }

    public static void selector(Context context, Spinner spinner, String label) {
        spinner.setMinimumHeight(dp(context, 48));
        spinner.setContentDescription(label);
    }

    public static LinearLayout row(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    public static void addWeighted(LinearLayout row, View view) {
        row.addView(view, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    }

    public static LinearLayout actionGrid(Context context, View... actions) {
        LinearLayout grid = new LinearLayout(context);
        grid.setOrientation(LinearLayout.VERTICAL);
        int width = context.getResources().getConfiguration().screenWidthDp;
        float scale = context.getResources().getConfiguration().fontScale;
        int columns = UiDisplayPolicy.actionColumns(width, scale, actions.length);
        for (int index = 0; index < actions.length; index += columns) {
            LinearLayout row = row(context);
            for (int column = 0; column < columns && index + column < actions.length; column++) {
                View child = actions[index + column];
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                params.setMargins(dp(context, 2), dp(context, 4), dp(context, 2), dp(context, 4));
                row.addView(child, params);
            }
            grid.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        return grid;
    }

    public static ScrollView scroll(Context context) {
        ScrollView scroll = new ScrollView(context);
        scroll.setBackgroundColor(color(context, R.color.page_surface));
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        return scroll;
    }

    public static void setContent(Activity activity, View content) {
        activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (Build.VERSION.SDK_INT >= 30) activity.getWindow().setDecorFitsSystemWindows(false);
        final int baseLeft = content.getPaddingLeft();
        final int baseTop = content.getPaddingTop();
        final int baseRight = content.getPaddingRight();
        final int baseBottom = content.getPaddingBottom();
        content.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            int left;
            int top;
            int right;
            int bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets bars = windowInsets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                Insets ime = windowInsets.getInsets(WindowInsets.Type.ime());
                left = bars.left; top = bars.top; right = bars.right;
                bottom = Math.max(bars.bottom, ime.bottom);
            } else {
                left = windowInsets.getSystemWindowInsetLeft(); top = windowInsets.getSystemWindowInsetTop();
                right = windowInsets.getSystemWindowInsetRight(); bottom = windowInsets.getSystemWindowInsetBottom();
            }
            view.setPadding(baseLeft + left, baseTop + top, baseRight + right, baseBottom + bottom);
            return windowInsets;
        });
        activity.setContentView(content);
        content.requestApplyInsets();
    }
}
