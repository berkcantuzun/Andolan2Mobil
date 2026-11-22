package com.winlator.cmod.andolan;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.widget.InputControlsView;
import com.winlator.cmod.widget.TouchpadView;
import com.winlator.cmod.xserver.Pointer;
import com.winlator.cmod.xserver.XKeycode;
import com.winlator.cmod.xserver.XServer;

/**
 * Andolan2 oyun ici kontrolleri (Metin2 mobil tarzi):
 *  - Sag alt: buyuk SALDIR (Space), etrafinda 1-4 beceri yayi, disinda F1-F4, TOPLA (Z)
 *  - Sol alt: WASD joystick
 *  - Sol ust: ESC, Sohbet (Enter), Klavye, CTRL, Sag tik, Sag tik modu
 *  - Ust orta: tum kontrolleri gizle/goster
 */
public final class Andolan2Panel {
    private static final String PREF_VISIBLE = "andolan2_controls_visible";

    static final int RED_CENTER = 0xFF8A1414;
    static final int RED_EDGE = 0xFF2A0505;
    static final int DARK_CENTER = 0xFF3A2614;
    static final int DARK_EDGE = 0xFF120A04;
    static final int GOLD_DARK = 0xFF7A4A14;
    static final int GOLD_LIGHT = 0xFFF6CF6E;
    static final int TEXT = 0xFFFFF1D6;
    static final int ACTIVE_GLOW = 0xFFFFC24A;

    private final AppCompatActivity activity;
    private final FrameLayout root;
    private final XServer xServer;
    private final TouchpadView touchpadView;
    private final SharedPreferences prefs;
    private final float d;

    private FrameLayout layer;
    private GameButton toggleButton;
    private GameButton rightModeButton;
    private View rightClickCatcher;
    private boolean rightMode = false;

    public static Andolan2Panel attach(AppCompatActivity activity, FrameLayout root, XServer xServer,
                                       TouchpadView touchpadView, InputControlsView inputControlsView) {
        Andolan2Panel p = new Andolan2Panel(activity, root, xServer, touchpadView);
        p.build();
        return p;
    }

    /** Winlator'in kendi kontrol katmanini gizlemek icin XServerDisplayActivity'den cagrilir. */
    public static void hideWinlatorControls(InputControlsView v) {
        if (v != null) v.setVisibility(View.GONE);
    }

    private Andolan2Panel(AppCompatActivity activity, FrameLayout root, XServer xServer, TouchpadView touchpadView) {
        this.activity = activity;
        this.root = root;
        this.xServer = xServer;
        this.touchpadView = touchpadView;
        this.prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        this.d = activity.getResources().getDisplayMetrics().density;
    }

    private int dp(float v) { return Math.round(v * d); }

    @SuppressLint("ClickableViewAccessibility")
    private void build() {
        rightClickCatcher = new View(activity);
        rightClickCatcher.setBackgroundColor(0x14FF3030);
        rightClickCatcher.setVisibility(View.GONE);
        rightClickCatcher.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_UP) rightClickAt(e.getX(), e.getY());
            return true;
        });
        root.addView(rightClickCatcher, new FrameLayout.LayoutParams(-1, -1));

        layer = new FrameLayout(activity);
        layer.setMotionEventSplittingEnabled(true);
        root.addView(layer, new FrameLayout.LayoutParams(-1, -1));

        // ---------------- sag alt: saldiri kumesi ----------------
        final float ax = 100, ay = 92;   // saldiri merkezi (sagdan, alttan dp)
        GameButton attack = new GameButton(activity, GameButton.STYLE_ATTACK, null, GameButton.ICON_SWORDS);
        bindKey(attack, XKeycode.KEY_SPACE);
        placeBR(attack, ax, ay, 54);

        XKeycode[] skills = {XKeycode.KEY_1, XKeycode.KEY_2, XKeycode.KEY_3, XKeycode.KEY_4};
        float[] skillAngles = {92, 124, 156, 188};
        for (int i = 0; i < 4; i++) {
            GameButton b = new GameButton(activity, GameButton.STYLE_RED, String.valueOf(i + 1), 0);
            bindKey(b, skills[i]);
            placeArc(b, ax, ay, 108, skillAngles[i], 28);
        }

        XKeycode[] fkeys = {XKeycode.KEY_F1, XKeycode.KEY_F2, XKeycode.KEY_F3, XKeycode.KEY_F4};
        float[] fAngles = {100, 125, 150, 175};
        for (int i = 0; i < 4; i++) {
            GameButton b = new GameButton(activity, GameButton.STYLE_DARK, "F" + (i + 1), 0);
            bindKey(b, fkeys[i]);
            placeArc(b, ax, ay, 176, fAngles[i], 22);
        }

        GameButton pickup = new GameButton(activity, GameButton.STYLE_DARK, "TOPLA", 0);
        pickup.setSmallText(true);
        bindKey(pickup, XKeycode.KEY_Z);
        placeArc(pickup, ax, ay, 100, 55, 23);

        // ---------------- sol alt: joystick ----------------
        Joystick joy = new Joystick(activity);
        FrameLayout.LayoutParams jlp = new FrameLayout.LayoutParams(dp(170), dp(170), Gravity.BOTTOM | Gravity.START);
        jlp.leftMargin = dp(48);
        jlp.bottomMargin = dp(18);
        layer.addView(joy, jlp);

        // ---------------- sol ust: yardimci tuslar ----------------
        float colX = 34, col2X = 86, rowY = 64, step = 52;
        GameButton esc = new GameButton(activity, GameButton.STYLE_DARK, "ESC", 0);
        esc.setSmallText(true);
        bindKey(esc, XKeycode.KEY_ESC);
        placeTL(esc, colX, rowY, 21);

        GameButton chat = new GameButton(activity, GameButton.STYLE_DARK, null, GameButton.ICON_CHAT);
        bindKey(chat, XKeycode.KEY_ENTER);
        placeTL(chat, colX, rowY + step, 21);

        GameButton kb = new GameButton(activity, GameButton.STYLE_DARK, null, GameButton.ICON_KEYBOARD);
        kb.setOnClickListener(v -> AppUtils.showKeyboard(activity));
        placeTL(kb, colX, rowY + step * 2, 21);

        GameButton ctrl = new GameButton(activity, GameButton.STYLE_DARK, "CTRL", 0);
        ctrl.setSmallText(true);
        final boolean[] ctrlDown = {false};
        ctrl.setOnClickListener(v -> {
            ctrlDown[0] = !ctrlDown[0];
            ctrl.setActive(ctrlDown[0]);
            if (ctrlDown[0]) xServer.injectKeyPress(XKeycode.KEY_CTRL_L); else xServer.injectKeyRelease(XKeycode.KEY_CTRL_L);
        });
        placeTL(ctrl, colX, rowY + step * 3, 21);

        GameButton rclick = new GameButton(activity, GameButton.STYLE_DARK, "SAĞ", 0);
        rclick.setSmallText(true);
        bindMouse(rclick, Pointer.Button.BUTTON_RIGHT);
        placeTL(rclick, col2X, rowY, 21);

        rightModeButton = new GameButton(activity, GameButton.STYLE_DARK, "SAĞ\nMOD", 0);
        rightModeButton.setSmallText(true);
        rightModeButton.setOnClickListener(v -> toggleRightMode());
        placeTL(rightModeButton, col2X, rowY + step, 21);

        // ---------------- ust orta: gizle/goster ----------------
        toggleButton = new GameButton(activity, GameButton.STYLE_PILL, null, GameButton.ICON_EYE);
        toggleButton.setOnClickListener(v -> setVisible(layer.getVisibility() != View.VISIBLE));
        toggleButton.setOnLongClickListener(v -> { Andolan2Diag.share(activity); return true; }); // uzun bas: Wine kaydini paylas
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(dp(54), dp(28), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        tlp.topMargin = dp(4);
        root.addView(toggleButton, tlp);

        setVisible(prefs.getBoolean(PREF_VISIBLE, true));
    }

    private void setVisible(boolean visible) {
        layer.setVisibility(visible ? View.VISIBLE : View.GONE);
        toggleButton.setActive(!visible);
        if (!visible && rightMode) toggleRightMode();
        prefs.edit().putBoolean(PREF_VISIBLE, visible).apply();
    }

    // ---------------- yerlesim ----------------

    private void placeBR(View v, float cx, float cy, float r) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(r * 2), dp(r * 2), Gravity.BOTTOM | Gravity.END);
        lp.rightMargin = dp(cx - r);
        lp.bottomMargin = dp(cy - r);
        layer.addView(v, lp);
    }

    /** Saldiri merkezinden aci (0=sag, 90=yukari, 180=sol) ve uzaklikla yerlestir. */
    private void placeArc(View v, float ax, float ay, float dist, float angleDeg, float r) {
        double a = Math.toRadians(angleDeg);
        float cx = ax - (float) (Math.cos(a) * dist);
        float cy = ay + (float) (Math.sin(a) * dist);
        placeBR(v, cx, cy, r);
    }

    private void placeTL(View v, float cx, float cy, float r) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(r * 2), dp(r * 2), Gravity.TOP | Gravity.START);
        lp.leftMargin = dp(cx - r);
        lp.topMargin = dp(cy - r);
        layer.addView(v, lp);
    }

    // ---------------- giris ----------------

    @SuppressLint("ClickableViewAccessibility")
    private void bindKey(GameButton b, XKeycode key) {
        b.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: b.setPressedState(true); xServer.injectKeyPress(key); break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: b.setPressedState(false); xServer.injectKeyRelease(key); break;
            }
            return true;
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void bindMouse(GameButton b, Pointer.Button button) {
        b.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: b.setPressedState(true); xServer.injectPointerButtonPress(button); break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: b.setPressedState(false); xServer.injectPointerButtonRelease(button); break;
            }
            return true;
        });
    }

    private void toggleRightMode() {
        rightMode = !rightMode;
        rightClickCatcher.setVisibility(rightMode ? View.VISIBLE : View.GONE);
        rightModeButton.setActive(rightMode);
    }

    private void rightClickAt(float viewX, float viewY) {
        float[] p = touchpadView.mapToXServer(viewX, viewY);
        xServer.injectPointerMove((int) p[0], (int) p[1]);
        xServer.injectPointerButtonPress(Pointer.Button.BUTTON_RIGHT);
        root.postDelayed(() -> xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_RIGHT), 40);
    }

    // =====================================================================
    // WASD joystick
    // =====================================================================
    private final class Joystick extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float knobX, knobY;
        private boolean w, a, s, dKey;

        Joystick(Context c) { super(c); }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f, R = getWidth() * 0.40f;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE: {
                    float dx = (e.getX() - cx) / R, dy = (e.getY() - cy) / R;
                    float len = (float) Math.hypot(dx, dy);
                    if (len > 1) { dx /= len; dy /= len; }
                    knobX = dx; knobY = dy;
                    boolean nw = false, na = false, ns = false, nd = false;
                    if (len > 0.28f) {
                        double ang = Math.toDegrees(Math.atan2(-dy, dx));
                        if (ang < 0) ang += 360;
                        nd = ang < 67.5 || ang > 292.5;
                        nw = ang > 22.5 && ang < 157.5;
                        na = ang > 112.5 && ang < 247.5;
                        ns = ang > 202.5 && ang < 337.5;
                    }
                    setKeys(nw, na, ns, nd);
                    invalidate();
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    knobX = knobY = 0;
                    setKeys(false, false, false, false);
                    invalidate();
                    return true;
            }
            return true;
        }

        private void setKeys(boolean nw, boolean na, boolean ns, boolean nd) {
            if (nw != w) { w = nw; key(XKeycode.KEY_W, nw); }
            if (na != a) { a = na; key(XKeycode.KEY_A, na); }
            if (ns != s) { s = ns; key(XKeycode.KEY_S, ns); }
            if (nd != dKey) { dKey = nd; key(XKeycode.KEY_D, nd); }
        }

        private void key(XKeycode k, boolean down) {
            if (down) xServer.injectKeyPress(k); else xServer.injectKeyRelease(k);
        }

        @Override
        protected void onDraw(Canvas c) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f, R = getWidth() * 0.40f;
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new RadialGradient(cx, cy, R, new int[]{0x55301A08, 0x99120A04}, null, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, R, paint);
            paint.setShader(new SweepGradient(cx, cy, new int[]{GOLD_DARK, GOLD_LIGHT, GOLD_DARK, GOLD_LIGHT, GOLD_DARK}, null));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3 * d);
            c.drawCircle(cx, cy, R, paint);
            paint.setStrokeWidth(1 * d);
            paint.setShader(null);
            paint.setColor(0x55F6CF6E);
            c.drawCircle(cx, cy, R * 0.55f, paint);
            paint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 4; i++) {
                boolean on = (i == 0 && w) || (i == 1 && dKey) || (i == 2 && s) || (i == 3 && a);
                paint.setColor(on ? ACTIVE_GLOW : 0xCCF6CF6E);
                c.save();
                c.rotate(i * 90, cx, cy);
                Path p = new Path();
                float tip = cy - R + 7 * d, base = tip + 10 * d;
                p.moveTo(cx, tip); p.lineTo(cx - 8 * d, base); p.lineTo(cx + 8 * d, base); p.close();
                c.drawPath(p, paint);
                c.restore();
            }
            float kx = cx + knobX * R * 0.6f, ky = cy + knobY * R * 0.6f, kr = R * 0.38f;
            paint.setShader(new RadialGradient(kx - kr * 0.3f, ky - kr * 0.3f, kr * 1.3f,
                new int[]{0xFF9A2A1A, RED_CENTER, RED_EDGE}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(kx, ky, kr, paint);
            paint.setShader(new SweepGradient(kx, ky, new int[]{GOLD_DARK, GOLD_LIGHT, GOLD_DARK, GOLD_LIGHT, GOLD_DARK}, null));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.5f * d);
            c.drawCircle(kx, ky, kr, paint);
            paint.setShader(null);
        }
    }

    // =====================================================================
    // Yuvarlak oyun dugmesi
    // =====================================================================
    static final class GameButton extends View {
        static final int STYLE_RED = 0, STYLE_DARK = 1, STYLE_ATTACK = 2, STYLE_PILL = 3;
        static final int ICON_SWORDS = 1, ICON_CHAT = 2, ICON_KEYBOARD = 3, ICON_EYE = 4;

        private final int style;
        private final String label;
        private final int icon;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float dd;
        private boolean pressedState, active, smallText;

        GameButton(Context c, int style, String label, int icon) {
            super(c);
            this.style = style;
            this.label = label;
            this.icon = icon;
            this.dd = c.getResources().getDisplayMetrics().density;
            setClickable(true);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null); // setShadowLayer icin
        }

        void setSmallText(boolean v) { smallText = v; invalidate(); }
        void setActive(boolean v) { active = v; invalidate(); }
        void setPressedState(boolean v) { pressedState = v; invalidate(); }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) setPressedState(true);
            if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) setPressedState(false);
            return super.onTouchEvent(e);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h / 2f;
            boolean lit = pressedState || active;

            if (style == STYLE_PILL) {
                RectF r = new RectF(1 * dd, 1 * dd, w - 1 * dd, h - 1 * dd);
                paint.setStyle(Paint.Style.FILL);
                paint.setShader(new LinearGradient(0, 0, 0, h, lit ? 0xEE5A3A14 : 0xCC2A1A0C, 0xCC0E0804, Shader.TileMode.CLAMP));
                c.drawRoundRect(r, h / 2, h / 2, paint);
                paint.setShader(null);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1.5f * dd);
                paint.setColor(lit ? GOLD_LIGHT : 0xCCB98A3E);
                c.drawRoundRect(r, h / 2, h / 2, paint);
                drawIcon(c, cx, cy, h * 0.55f, lit);
                return;
            }

            float ring = (style == STYLE_ATTACK ? 4f : 2.5f) * dd;
            float R = Math.min(w, h) / 2f - ring;

            paint.setStyle(Paint.Style.FILL);
            paint.setShader(null);
            paint.setColor(0x66000000);
            c.drawCircle(cx, cy + 1.5f * dd, R + ring * 0.5f, paint);

            int center, edge;
            if (style == STYLE_DARK) { center = lit ? 0xFF5A3A1A : DARK_CENTER; edge = DARK_EDGE; }
            else { center = lit ? 0xFFC02A1A : RED_CENTER; edge = RED_EDGE; }
            paint.setShader(new RadialGradient(cx - R * 0.25f, cy - R * 0.35f, R * 1.35f,
                new int[]{center, edge}, null, Shader.TileMode.CLAMP));
            paint.setAlpha(style == STYLE_DARK ? 215 : 235);
            c.drawCircle(cx, cy, R, paint);
            paint.setAlpha(255);

            paint.setShader(new LinearGradient(0, cy - R, 0, cy, 0x40FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawOval(new RectF(cx - R * 0.75f, cy - R * 0.92f, cx + R * 0.75f, cy - R * 0.05f), paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(ring);
            paint.setShader(new SweepGradient(cx, cy,
                lit ? new int[]{GOLD_LIGHT, 0xFFFFF4C8, GOLD_LIGHT, 0xFFFFF4C8, GOLD_LIGHT}
                    : new int[]{GOLD_DARK, GOLD_LIGHT, GOLD_DARK, GOLD_LIGHT, GOLD_DARK}, null));
            c.drawCircle(cx, cy, R, paint);
            if (style == STYLE_ATTACK) {
                paint.setStrokeWidth(1.2f * dd);
                paint.setShader(null);
                paint.setColor(0x99F6CF6E);
                c.drawCircle(cx, cy, R * 0.80f, paint);
            }
            paint.setShader(null);

            if (icon != 0) drawIcon(c, cx, cy, R * (style == STYLE_ATTACK ? 1.1f : 1.0f), lit);
            if (label != null) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(TEXT);
                paint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setShadowLayer(3 * dd, 0, 1 * dd, 0xFF000000);
                String[] lines = label.split("\n");
                float size;
                if (lines.length > 1) size = R * 0.46f;
                else if (smallText) size = R * (label.length() > 4 ? 0.42f : 0.52f);
                else size = label.length() > 1 ? R * 0.70f : R * 0.95f;
                paint.setTextSize(size);
                float lh = size * 1.05f;
                float y0 = cy - (lines.length - 1) * lh / 2f + size * 0.36f;
                for (int i = 0; i < lines.length; i++) c.drawText(lines[i], cx, y0 + i * lh, paint);
                paint.clearShadowLayer();
            }
        }

        private void drawIcon(Canvas c, float cx, float cy, float R, boolean lit) {
            paint.setShader(null);
            paint.setColor(lit ? 0xFFFFF4C8 : GOLD_LIGHT);
            paint.setShadowLayer(3 * dd, 0, 1 * dd, 0xFF000000);
            switch (icon) {
                case ICON_SWORDS: {
                    paint.setStyle(Paint.Style.FILL);
                    for (int s = -1; s <= 1; s += 2) {
                        c.save();
                        c.rotate(45 * s, cx, cy);
                        float L = R * 0.62f, bw = R * 0.09f;
                        Path blade = new Path();
                        blade.moveTo(cx, cy - L);
                        blade.lineTo(cx + bw, cy - L + bw * 2.2f);
                        blade.lineTo(cx + bw, cy + L * 0.45f);
                        blade.lineTo(cx - bw, cy + L * 0.45f);
                        blade.lineTo(cx - bw, cy - L + bw * 2.2f);
                        blade.close();
                        c.drawPath(blade, paint);
                        c.drawRect(cx - R * 0.24f, cy + L * 0.45f, cx + R * 0.24f, cy + L * 0.45f + bw * 1.6f, paint);
                        c.drawRect(cx - bw * 0.8f, cy + L * 0.45f, cx + bw * 0.8f, cy + L * 0.85f, paint);
                        c.drawCircle(cx, cy + L * 0.9f, bw * 1.3f, paint);
                        c.restore();
                    }
                    break;
                }
                case ICON_CHAT: {
                    paint.setStyle(Paint.Style.FILL);
                    RectF r = new RectF(cx - R * 0.5f, cy - R * 0.38f, cx + R * 0.5f, cy + R * 0.22f);
                    c.drawRoundRect(r, R * 0.15f, R * 0.15f, paint);
                    Path tail = new Path();
                    tail.moveTo(cx - R * 0.2f, cy + R * 0.2f); tail.lineTo(cx - R * 0.32f, cy + R * 0.45f); tail.lineTo(cx, cy + R * 0.2f); tail.close();
                    c.drawPath(tail, paint);
                    paint.clearShadowLayer();
                    paint.setColor(DARK_EDGE);
                    for (int i = -1; i <= 1; i++) c.drawCircle(cx + i * R * 0.22f, cy - R * 0.08f, R * 0.07f, paint);
                    break;
                }
                case ICON_KEYBOARD: {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(1.6f * dd);
                    RectF r = new RectF(cx - R * 0.55f, cy - R * 0.32f, cx + R * 0.55f, cy + R * 0.32f);
                    c.drawRoundRect(r, R * 0.1f, R * 0.1f, paint);
                    paint.setStyle(Paint.Style.FILL);
                    for (int row = 0; row < 2; row++)
                        for (int col = 0; col < 5; col++)
                            c.drawRect(cx - R * 0.42f + col * R * 0.2f, cy - R * 0.18f + row * R * 0.17f,
                                cx - R * 0.42f + col * R * 0.2f + R * 0.1f, cy - R * 0.18f + row * R * 0.17f + R * 0.09f, paint);
                    c.drawRect(cx - R * 0.25f, cy + R * 0.16f, cx + R * 0.25f, cy + R * 0.23f, paint);
                    break;
                }
                case ICON_EYE: {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(1.6f * dd);
                    Path eye = new Path();
                    eye.moveTo(cx - R * 0.7f, cy);
                    eye.quadTo(cx, cy - R * 0.75f, cx + R * 0.7f, cy);
                    eye.quadTo(cx, cy + R * 0.75f, cx - R * 0.7f, cy);
                    c.drawPath(eye, paint);
                    paint.setStyle(Paint.Style.FILL);
                    c.drawCircle(cx, cy, R * 0.2f, paint);
                    if (lit) {
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(2f * dd);
                        c.drawLine(cx - R * 0.6f, cy + R * 0.45f, cx + R * 0.6f, cy - R * 0.45f, paint);
                    }
                    break;
                }
            }
            paint.clearShadowLayer();
        }
    }
}
