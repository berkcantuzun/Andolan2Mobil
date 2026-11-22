package com.winlator.cmod.core;

import android.app.Activity;
import android.app.Dialog;
import android.view.Window;
import android.view.WindowManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import com.winlator.cmod.R;

public class PreloaderDialog {
    private final Activity activity;
    private Dialog dialog;
    // Andolan2: sure sayaci, dokununca gizle, otomatik gizleme, kayit paylasma
    private final Handler ticker = new Handler(Looper.getMainLooper());
    private long shownAt;
    private boolean startup;
    private static final long AUTO_HIDE_MS = 60_000;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (dialog == null || !dialog.isShowing()) return;
            long sec = (System.currentTimeMillis() - shownAt) / 1000;
            TextView hint = dialog.findViewById(R.id.A2Hint);
            TextView detail = dialog.findViewById(R.id.A2Detail);
            View share = dialog.findViewById(R.id.A2Share);
            if (hint != null) hint.setText(sec + " sn  •  Arkayı görmek için ekrana dokun");
            if (detail != null) detail.setText(com.winlator.cmod.andolan.Andolan2Diag.lastLine());
            if (share != null && startup && sec >= 15) share.setVisibility(View.VISIBLE);
            if (startup && System.currentTimeMillis() - shownAt >= AUTO_HIDE_MS) { close(); return; }
            ticker.postDelayed(this, 1000);
        }
    };

    public PreloaderDialog(Activity activity) {
        this.activity = activity;
    }

    private void create() {
        if (dialog != null) return;
        dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setContentView(R.layout.preloader_dialog);

        Window window = dialog.getWindow();
        if (window != null) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            if (Build.VERSION.SDK_INT >= 28) {
                WindowManager.LayoutParams lp = window.getAttributes();
                lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                window.setAttributes(lp);
            }
        }
        View root = dialog.findViewById(R.id.A2Root);
        if (root != null) root.setOnClickListener(v -> { if (startup) close(); });
        View share = dialog.findViewById(R.id.A2Share);
        if (share != null) share.setOnClickListener(v -> com.winlator.cmod.andolan.Andolan2Diag.share(activity));
    }

    public synchronized void show(int textResId) {
        if (isShowing()) return;
        close();
        if (dialog == null) create();
        ((TextView)dialog.findViewById(R.id.TextView)).setText(textResId);
        startup = textResId == R.string.starting_up;
        shownAt = System.currentTimeMillis();
        View share = dialog.findViewById(R.id.A2Share);
        if (share != null) share.setVisibility(View.GONE);
        TextView hint = dialog.findViewById(R.id.A2Hint);
        if (hint != null) hint.setText(startup ? "Arkayı görmek için ekrana dokun" : "");
        dialog.show();
        ticker.removeCallbacks(tick);
        if (startup) ticker.postDelayed(tick, 1000);
    }

    public void showOnUiThread(final int textResId) {
        activity.runOnUiThread(() -> show(textResId));
    }

    public synchronized void close() {
        ticker.removeCallbacks(tick);
        if (startup) com.winlator.cmod.andolan.Andolan2Panel.reveal();
        try {
            if (dialog != null) {
                dialog.dismiss();
            }
        }
        catch (Exception e) {}
    }

    public void closeOnUiThread() {
        activity.runOnUiThread(this::close);
    }

    public boolean isShowing() {
        return dialog != null && dialog.isShowing();
    }
}
