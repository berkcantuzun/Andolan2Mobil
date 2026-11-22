package com.winlator.cmod.andolan;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.Toast;

import com.winlator.cmod.core.ProcessHelper;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

public final class Andolan2Diag {
    private static final int MAX_LINES = 1500;
    private static final ArrayDeque<String> lines = new ArrayDeque<>();
    private static BufferedWriter writer;
    private static long startMs;
    private static final SimpleDateFormat TIME = new SimpleDateFormat("HH:mm:ss", Locale.US);

    private Andolan2Diag() {}

    public static synchronized void attach(Context context) {
        lines.clear();
        startMs = System.currentTimeMillis();
        try {
            if (writer != null) writer.close();
        } catch (Exception ignored) {}
        try {
            File f = new File(context.getFilesDir(), "andolan2_wine.log");
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f, false), StandardCharsets.UTF_8));
        } catch (Exception e) {
            writer = null;
        }
        add("=== Andolan2 oturumu basladi " + new Date() + " | " + Build.MANUFACTURER + " " + Build.MODEL + " Android " + Build.VERSION.RELEASE);
        ProcessHelper.addDebugCallback(Andolan2Diag::add);
    }

    public static synchronized void add(String line) {
        if (line == null) return;
        long sec = (System.currentTimeMillis() - startMs) / 1000;
        String l = "[" + sec + "s] " + line;
        lines.addLast(l);
        while (lines.size() > MAX_LINES) lines.removeFirst();
        try {
            if (writer != null) { writer.write(l); writer.write('\n'); writer.flush(); }
        } catch (Exception ignored) {}
    }

    public static synchronized String snapshot(int lastN) {
        StringBuilder sb = new StringBuilder();
        sb.append("Andolan2 Wine kaydi - ").append(TIME.format(new Date())).append('\n');
        int skip = Math.max(0, lines.size() - lastN);
        int i = 0;
        for (String l : lines) {
            if (i++ < skip) continue;
            sb.append(l).append('\n');
        }
        if (lines.isEmpty()) sb.append("(Wine henuz hicbir cikti vermedi)\n");
        return sb.toString();
    }

    public static synchronized int lineCount() { return lines.size(); }

    public static synchronized String lastLine() { return lines.isEmpty() ? "" : lines.peekLast(); }

    public static void share(Activity activity) {
        String text = snapshot(400);
        try {
            ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("Andolan2", text));
            Toast.makeText(activity, "Kayıt panoya kopyalandı", Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {}
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "Andolan2 Wine kaydı");
        send.putExtra(Intent.EXTRA_TEXT, text);
        activity.startActivity(Intent.createChooser(send, "Kaydı paylaş"));
    }
}
