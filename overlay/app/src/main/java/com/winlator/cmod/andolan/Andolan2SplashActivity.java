package com.winlator.cmod.andolan;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.winlator.cmod.MainActivity;
import com.winlator.cmod.R;
import com.winlator.cmod.SettingsFragment;
import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.TarCompressorUtils;
import com.winlator.cmod.core.WineInfo;
import com.winlator.cmod.xenvironment.ImageFs;
import com.winlator.cmod.xenvironment.ImageFsInstaller;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Andolan2 acilis ekrani. Uygulamanin tek giris noktasi:
 * Wine kurulumu, container hazirligi ve oyunu baslatma burada, kendi arayuzumuzle yapilir.
 * Winlator ekranlari (MainActivity) kullaniciya hic gosterilmez.
 * Gizli ayarlar: logoya 3 saniye basili tut.
 */
public class Andolan2SplashActivity extends AppCompatActivity {
    private static final String TAG = "Andolan2Splash";

    private ProgressBar progressBar;
    private TextView statusText;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean started = false;
    private boolean crashDialogPending = false;
    private boolean pausedSinceDialog = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(buildLayout());
        AppUtils.hideSystemUI(this);

        if (CrashReport.showIfPreviousSessionCrashed(this, this::startPreparation)) {
            crashDialogPending = true;
            setStatus("Önceki oturum beklenmedik şekilde kapandı", 0);
            return;
        }
        startPreparation();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (crashDialogPending) pausedSinceDialog = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // "Paylas" sonrasi geri donuldugunde devam et
        if (crashDialogPending && pausedSinceDialog && !started) startPreparation();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) AppUtils.hideSystemUI(this);
    }

    @Override
    public void onBackPressed() {
        // Kurulum yarida kesilmesin
        if (!started) super.onBackPressed();
    }

    // ------------------------------------------------------------------ arayuz

    private View buildLayout() {
        float d = getResources().getDisplayMetrics().density;
        FrameLayout root = new FrameLayout(this);
        root.setBackground(ContextCompat.getDrawable(this, R.drawable.andolan2_bg));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding((int) (32 * d), 0, (int) (32 * d), 0);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.andolan2_logo);
        logo.setAdjustViewBounds(true);
        logo.setContentDescription("Andolan2");
        logo.setOnLongClickListener(v -> false);
        // Gizli ayarlar: 3 sn basili tut
        final Runnable openSettings = () -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        };
        logo.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN: ui.postDelayed(openSettings, 3000); break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL: ui.removeCallbacks(openSettings); break;
            }
            return true;
        });
        col.addView(logo, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, (int) (160 * d)));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        Drawable pd = ContextCompat.getDrawable(this, R.drawable.andolan2_progress);
        progressBar.setProgressDrawable(pd);
        progressBar.setMax(1000);
        progressBar.setIndeterminate(false);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams((int) (340 * d), (int) (8 * d));
        plp.topMargin = (int) (30 * d);
        col.addView(progressBar, plp);

        statusText = new TextView(this);
        statusText.setTextColor(Color.parseColor("#E6D3B0"));
        statusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        statusText.setLetterSpacing(0.05f);
        statusText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = (int) (14 * d);
        col.addView(statusText, tlp);

        root.addView(col, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        TextView footer = new TextView(this);
        String ver = "";
        try { ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) {}
        footer.setText("v" + ver + "  •  İlk açılışta kurulum birkaç dakika sürebilir");
        footer.setTextColor(Color.parseColor("#7A6A55"));
        footer.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        flp.bottomMargin = (int) (16 * d);
        root.addView(footer, flp);

        setStatus("Hazırlanıyor…", 0);
        return root;
    }

    private void setStatus(String text, int permille) {
        ui.post(() -> {
            statusText.setText(text);
            if (permille >= 0) progressBar.setProgress(permille);
        });
    }

    // ------------------------------------------------------------------ akis

    private void startPreparation() {
        if (started) return;
        started = true;
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                ImageFs imageFs = ImageFs.find(this);
                boolean needInstall = !imageFs.isValid() || imageFs.getVersion() < ImageFsInstaller.LATEST_VERSION;
                if (needInstall) {
                    if (!installSystemFiles(imageFs)) {
                        setStatus("Kurulum başarısız oldu. Uygulamayı kapatıp tekrar açın.", -1);
                        started = false;
                        return;
                    }
                }
                setStatus("Oyun hazırlanıyor…", 920);
                Andolan2Bootstrap.prepareAndLaunch(this, (msg, permille) -> setStatus(msg, permille), () -> ui.post(() -> {
                    setStatus("Başlatılıyor…", 1000);
                    // Oyun ekrani acilinca bu ekran kapanir; geri tusu uygulamadan cikar.
                    ui.postDelayed(this::finish, 1500);
                }));
            } catch (Throwable t) {
                Log.e(TAG, "Hazirlik hatasi", t);
                setStatus("Hata: " + t.getMessage(), -1);
                started = false;
            }
        });
    }

    /** ImageFsInstaller.installFromAssets ile ayni is, ama Winlator dialoglari olmadan. */
    private boolean installSystemFiles(ImageFs imageFs) {
        File rootDir = imageFs.getRootDir();
        setStatus("Sistem dosyaları kuruluyor…", 0);
        SettingsFragment.resetEmulatorsVersion(this);
        clearRootDir(rootDir);

        final long contentLength = (long) (FileUtils.getSize(this, "imagefs.txz") * (100.0f / 24));
        final AtomicLong total = new AtomicLong();
        final int[] last = {-1};
        boolean ok = TarCompressorUtils.extract(TarCompressorUtils.Type.XZ, this, "imagefs.txz", rootDir, (file, size) -> {
            if (size > 0) {
                long t = total.addAndGet(size);
                int p = (int) Math.min(800, (t * 800) / Math.max(1, contentLength));
                if (p != last[0]) {
                    last[0] = p;
                    setStatus("Sistem dosyaları kuruluyor… %" + (p / 8), p);
                }
            }
            return file;
        });
        if (!ok) return false;

        String[] versions = getResources().getStringArray(R.array.wine_entries);
        for (int i = 0; i < versions.length; i++) {
            int p = 800 + (i * 100) / Math.max(1, versions.length);
            setStatus("Oyun motoru kuruluyor… (" + (i + 1) + "/" + versions.length + ")", p);
            File outDir = new File(rootDir, "/opt/" + versions[i]);
            outDir.mkdirs();
            TarCompressorUtils.extract(TarCompressorUtils.Type.XZ, this, versions[i] + ".txz", outDir);
        }

        imageFs.createImgVersionFile(ImageFsInstaller.LATEST_VERSION);
        resetContainerImgVersions();
        return true;
    }

    private void resetContainerImgVersions() {
        ContainerManager manager = new ContainerManager(this);
        for (Container container : manager.getContainers()) {
            String imgVersion = container.getExtra("imgVersion");
            String wineVersion = container.getWineVersion();
            try {
                if (!imgVersion.isEmpty() && WineInfo.isMainWineVersion(wineVersion) && Short.parseShort(imgVersion) <= 5) {
                    container.putExtra("wineprefixNeedsUpdate", "t");
                }
            } catch (NumberFormatException ignored) {}
            container.putExtra("imgVersion", null);
            container.saveData();
        }
    }

    private static void clearRootDir(File rootDir) {
        if (rootDir.isDirectory()) {
            File[] files = rootDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory() && file.getName().equals("home")) continue;
                    FileUtils.delete(file);
                }
            }
        } else rootDir.mkdirs();
    }
}
