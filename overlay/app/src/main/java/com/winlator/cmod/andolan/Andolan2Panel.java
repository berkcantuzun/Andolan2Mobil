package com.winlator.cmod.andolan;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.widget.InputControlsView;
import com.winlator.cmod.widget.TouchpadView;
import com.winlator.cmod.xserver.Pointer;
import com.winlator.cmod.xserver.XKeycode;
import com.winlator.cmod.xserver.XServer;

/**
 * Oyun ekraninin ustunde acilir/kapanir tus paneli.
 * "☰ TUŞLAR" tutamagina dokununca panel acilir/kapanir; durum hatirlanir.
 */
public final class Andolan2Panel {
    private static final String PREF_OPEN = "andolan2_panel_open";
    private static final int COLOR_BG = 0x88202020;
    private static final int COLOR_BG_ACTIVE = 0xCCE0A030;
    private static final int COLOR_STROKE = 0xAAFFFFFF;

    private final AppCompatActivity activity;
    private final FrameLayout root;
    private final XServer xServer;
    private final TouchpadView touchpadView;
    private final InputControlsView inputControlsView;
    private final SharedPreferences prefs;
    private final float density;

    private GridLayout grid;
    private TextView handle;
    private View rightClickCatcher;
    private TextView rightModeButton;
    private boolean rightMode = false;

    public static Andolan2Panel attach(AppCompatActivity activity, FrameLayout root, XServer xServer,
                                       TouchpadView touchpadView, InputControlsView inputControlsView) {
        Andolan2Panel panel = new Andolan2Panel(activity, root, xServer, touchpadView, inputControlsView);
        panel.build();
        return panel;
    }

    private Andolan2Panel(AppCompatActivity activity, FrameLayout root, XServer xServer,
                          TouchpadView touchpadView, InputControlsView inputControlsView) {
        this.activity = activity;
        this.root = root;
        this.xServer = xServer;
        this.touchpadView = touchpadView;
        this.inputControlsView = inputControlsView;
        this.prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        this.density = activity.getResources().getDisplayMetrics().density;
    }

    private int dp(float v) { return Math.round(v * density); }

    @SuppressLint("ClickableViewAccessibility")
    private void build() {
        // 1) Sag tik modu: acikken ekrana her dokunus sag tik olur.
        rightClickCatcher = new View(activity);
        rightClickCatcher.setBackgroundColor(0x18FF4040);
        rightClickCatcher.setVisibility(View.GONE);
        rightClickCatcher.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_UP) rightClickAt(e.getX(), e.getY());
            return true;
        });
        root.addView(rightClickCatcher, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // 2) Tus izgarasi (sag kenar)
        grid = new GridLayout(activity);
        grid.setColumnCount(3);
        grid.setPadding(dp(4), dp(4), dp(4), dp(4));

        addMouse("SOL", Pointer.Button.BUTTON_LEFT);
        addMouse("SAĞ", Pointer.Button.BUTTON_RIGHT);
        rightModeButton = addAction("SAĞ\nMOD", this::toggleRightMode);

        addKey("1", XKeycode.KEY_1);
        addKey("2", XKeycode.KEY_2);
        addKey("3", XKeycode.KEY_3);
        addKey("4", XKeycode.KEY_4);
        addKey("F1", XKeycode.KEY_F1);
        addKey("F2", XKeycode.KEY_F2);
        addKey("F3", XKeycode.KEY_F3);
        addKey("F4", XKeycode.KEY_F4);
        addKey("Z\nTOPLA", XKeycode.KEY_Z);

        addKey("SPACE", XKeycode.KEY_SPACE);
        addSticky("CTRL", XKeycode.KEY_CTRL_L);
        addKey("ESC", XKeycode.KEY_ESC);

        addKey("ENTER", XKeycode.KEY_ENTER);
        addAction("⌨", () -> AppUtils.showKeyboard(activity));
        addAction("JOY", this::toggleJoystick);

        FrameLayout.LayoutParams gridLp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.END | Gravity.CENTER_VERTICAL);
        gridLp.rightMargin = dp(6);
        root.addView(grid, gridLp);

        // 3) Ac/kapa tutamagi (ust orta)
        handle = makeButton("☰ TUŞLAR", 13);
        handle.setPadding(dp(14), dp(6), dp(14), dp(6));
        handle.setOnClickListener(v -> setOpen(grid.getVisibility() != View.VISIBLE));
        FrameLayout.LayoutParams hLp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        hLp.topMargin = dp(4);
        root.addView(handle, hLp);

        setOpen(prefs.getBoolean(PREF_OPEN, true));
    }

    private void setOpen(boolean open) {
        grid.setVisibility(open ? View.VISIBLE : View.GONE);
        handle.setText(open ? "✕ KAPAT" : "☰ TUŞLAR");
        if (!open && rightMode) toggleRightMode();
        prefs.edit().putBoolean(PREF_OPEN, open).apply();
    }

    // ---- buton tipleri ----

    @SuppressLint("ClickableViewAccessibility")
    private void addKey(String label, XKeycode key) {
        TextView b = makeGridButton(label);
        b.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    setActive(b, true);
                    xServer.injectKeyPress(key);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    setActive(b, false);
                    xServer.injectKeyRelease(key);
                    break;
            }
            return true;
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void addMouse(String label, Pointer.Button button) {
        TextView b = makeGridButton(label);
        b.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    setActive(b, true);
                    xServer.injectPointerButtonPress(button);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    setActive(b, false);
                    xServer.injectPointerButtonRelease(button);
                    break;
            }
            return true;
        });
    }

    /** Bir dokunusta basili kalir, ikinci dokunusta birakilir (CTRL gibi). */
    private void addSticky(String label, XKeycode key) {
        TextView b = makeGridButton(label);
        final boolean[] down = {false};
        b.setOnClickListener(v -> {
            down[0] = !down[0];
            setActive(b, down[0]);
            if (down[0]) xServer.injectKeyPress(key); else xServer.injectKeyRelease(key);
        });
    }

    private TextView addAction(String label, Runnable action) {
        TextView b = makeGridButton(label);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    // ---- islemler ----

    private void toggleRightMode() {
        rightMode = !rightMode;
        rightClickCatcher.setVisibility(rightMode ? View.VISIBLE : View.GONE);
        setActive(rightModeButton, rightMode);
    }

    private void rightClickAt(float viewX, float viewY) {
        float[] p = touchpadView.mapToXServer(viewX, viewY);
        xServer.injectPointerMove((int) p[0], (int) p[1]);
        xServer.injectPointerButtonPress(Pointer.Button.BUTTON_RIGHT);
        root.postDelayed(() -> xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_RIGHT), 40);
    }

    private void toggleJoystick() {
        if (inputControlsView == null) return;
        boolean show = inputControlsView.getVisibility() != View.VISIBLE;
        inputControlsView.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    // ---- gorunum ----

    private TextView makeGridButton(String label) {
        TextView b = makeButton(label, 12);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = dp(54);
        lp.height = dp(42);
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        grid.addView(b, lp);
        return b;
    }

    private TextView makeButton(String label, int sp) {
        TextView b = new TextView(activity);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(sp);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        b.setIncludeFontPadding(false);
        setActive(b, false);
        return b;
    }

    private void setActive(TextView b, boolean active) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(10));
        bg.setColor(active ? COLOR_BG_ACTIVE : COLOR_BG);
        bg.setStroke(dp(1), COLOR_STROKE);
        b.setBackground(bg);
    }
}
