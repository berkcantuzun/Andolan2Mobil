package com.winlator.cmod.andolan;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Environment;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Oyun ekrani acikken uygulama cokerse, bir sonraki acilista kendi logcat kaydimizdan
 * hatayi bulup ekranda gosterir (Kopyala / Paylas / Tekrar dene).
 */
public final class CrashReport {
    private static final String PREF_IN_GAME = "andolan2_in_game";

    private CrashReport() {}

    public static void setInGame(Context context, boolean inGame) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(PREF_IN_GAME, inGame).commit();
    }

    /** @return true ise dialog gosterildi; otomatik baslatma yapilmamali. */
    public static boolean showIfPreviousSessionCrashed(final Activity activity, final Runnable onRetry) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        if (!prefs.getBoolean(PREF_IN_GAME, false)) return false;
        setInGame(activity, false);

        final String report = buildReport();
        saveToFiles(activity, report);

        TextView tv = new TextView(activity);
        tv.setText(report);
        tv.setTextSize(10);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextIsSelectable(true);
        int pad = (int) (12 * activity.getResources().getDisplayMetrics().density);
        tv.setPadding(pad, pad, pad, pad);
        ScrollView sv = new ScrollView(activity);
        sv.addView(tv);

        new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Andolan2 kapandı - hata kaydı")
            .setView(sv)
            .setCancelable(false)
            .setPositiveButton("Tekrar dene", (d, w) -> onRetry.run())
            .setNeutralButton("Paylaş", (d, w) -> {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_SUBJECT, "Andolan2 hata kaydı");
                send.putExtra(Intent.EXTRA_TEXT, report);
                activity.startActivity(Intent.createChooser(send, "Hata kaydını paylaş"));
            })
            .setNegativeButton("Kopyala", (d, w) -> {
                ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("Andolan2", report));
                Toast.makeText(activity, "Kopyalandı", Toast.LENGTH_SHORT).show();
                onRetry.run();
            })
            .show();
        return true;
    }

    private static String buildReport() {
        StringBuilder head = new StringBuilder();
        head.append("Cihaz: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
            .append(" | Android ").append(Build.VERSION.RELEASE)
            .append(" | SoC: ").append(Build.VERSION.SDK_INT >= 31 ? Build.SOC_MODEL : Build.HARDWARE)
            .append('\n');

        List<String> lines = new ArrayList<>();
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"logcat", "-d", "-v", "time", "-t", "4000"});
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) lines.add(line);
            }
        } catch (Exception e) {
            head.append("logcat okunamadi: ").append(e).append('\n');
        }

        // Son cokmeyi bul: Java (FATAL EXCEPTION) veya native (Fatal signal)
        int start = -1;
        for (int i = lines.size() - 1; i >= 0; i--) {
            String l = lines.get(i);
            if (l.contains("FATAL EXCEPTION") || l.contains("Fatal signal") || l.contains("*** *** ***")) { start = i; break; }
        }
        StringBuilder body = new StringBuilder();
        if (start >= 0) {
            int from = Math.max(0, start - 40);
            int to = Math.min(lines.size(), start + 120);
            for (int i = from; i < to; i++) body.append(lines.get(i)).append('\n');
        } else {
            // Cokme satiri yoksa son uyari/hatalari ver
            int count = 0;
            for (int i = lines.size() - 1; i >= 0 && count < 200; i--) {
                String l = lines.get(i);
                if (l.length() > 19 && (l.charAt(19) == 'E' || l.charAt(19) == 'W' || l.contains("XServerDisplay") || l.contains("Andolan2"))) {
                    body.insert(0, l + "\n");
                    count++;
                }
            }
            if (body.length() == 0) body.append("(kayit bulunamadi)\n");
        }
        return head + "\n" + body;
    }

    private static void saveToFiles(Context context, String report) {
        byte[] bytes = report.getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream out = new FileOutputStream(new File(context.getFilesDir(), "andolan2_hata.txt"))) {
            out.write(bytes);
        } catch (Exception ignored) {}
        try {
            File dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            try (FileOutputStream out = new FileOutputStream(new File(dl, "Andolan2_hata.txt"))) {
                out.write(bytes);
            }
        } catch (Exception ignored) {}
    }
}
