package com.winlator.cmod.andolan;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import com.winlator.cmod.XServerDisplayActivity;
import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.contents.ContentsManager;
import com.winlator.cmod.inputcontrols.InputControlsManager;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * Andolan2 Mobil: ilk acilista Wine container'ini hazirlar, APK icindeki
 * Andolan2Launcher.exe'yi C:\Andolan2 klasorune kopyalar ve dogrudan oyunu baslatir.
 * Launcher diger oyun dosyalarini kendisi indirir.
 */
public final class Andolan2Bootstrap {
    private static final String TAG = "Andolan2";

    public static final String CONTAINER_NAME = "Andolan2";
    public static final String GAME_DIR_WIN = "C:\\Andolan2";
    public static final String LAUNCHER_EXE = "Andolan2Launcher.exe";
    public static final int CONTROLS_PROFILE_ID = 4;

    private static final String ASSET_LAUNCHER = "andolan2/" + LAUNCHER_EXE;
    private static final String ASSET_PROFILE = "inputcontrols/profiles/controls-" + CONTROLS_PROFILE_ID + ".icp";
    private static final String PREF_FIRST_SETUP = "andolan2_first_setup_done";
    private static final String PREF_AUTOSTART = "andolan2_autostart";

    private static boolean launchedThisProcess = false;

    private Andolan2Bootstrap() {}

    public static boolean isAutostartEnabled(Activity activity) {
        return PreferenceManager.getDefaultSharedPreferences(activity).getBoolean(PREF_AUTOSTART, true);
    }

    /** Uygulama soguk acildiginda bir kez otomatik baslat. */
    public static void autostart(Activity activity) {
        if (launchedThisProcess || !isAutostartEnabled(activity)) return;
        // Onceki oturum cokmusse otomatik baslatma (cokme dongusu olmasin), hata kaydini goster.
        if (CrashReport.showIfPreviousSessionCrashed(activity)) {
            launchedThisProcess = true;
            return;
        }
        launch(activity);
    }

    /** Oyun ekrani normal kapandiginda cagrilir. */
    public static void markGameClosed(android.content.Context context) {
        CrashReport.setInGame(context, false);
    }

    public static void launch(final Activity activity) {
        launchedThisProcess = true;
        final Handler ui = new Handler(Looper.getMainLooper());
        Toast.makeText(activity, "Andolan2 hazırlanıyor...", Toast.LENGTH_SHORT).show();

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                applyFirstRunPreferences(activity);
                ensureControlsProfile(activity);

                ContainerManager manager = new ContainerManager(activity);
                Container container = findContainer(manager);
                if (container == null) {
                    ContentsManager contentsManager = new ContentsManager(activity);
                    contentsManager.syncContents();
                    container = createContainer(manager, contentsManager);
                }
                if (container == null) {
                    ui.post(() -> Toast.makeText(activity, "Andolan2 container oluşturulamadı", Toast.LENGTH_LONG).show());
                    return;
                }
                manager.activateContainer(container);

                File gameDir = new File(container.getRootDir(), ".wine/drive_c/Andolan2");
                if (!gameDir.isDirectory()) gameDir.mkdirs();
                File launcher = new File(gameDir, LAUNCHER_EXE);
                // Launcher kendini guncelleyebilir; sadece yoksa kopyala.
                if (!launcher.isFile() || launcher.length() == 0) copyAsset(activity.getAssets(), ASSET_LAUNCHER, launcher);

                File shortcut = writeShortcut(container);

                final int containerId = container.id;
                final String shortcutPath = shortcut.getPath();
                ui.post(() -> {
                    CrashReport.setInGame(activity, true);
                    Intent intent = new Intent(activity, XServerDisplayActivity.class);
                    intent.putExtra("container_id", containerId);
                    intent.putExtra("shortcut_path", shortcutPath);
                    intent.putExtra("shortcut_name", CONTAINER_NAME);
                    intent.putExtra("disableXinput", "0");
                    activity.startActivity(intent);
                });
            } catch (Exception e) {
                Log.e(TAG, "Baslatma hatasi", e);
                ui.post(() -> Toast.makeText(activity, "Andolan2 başlatılamadı: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private static Container findContainer(ContainerManager manager) {
        for (Container c : manager.getContainers()) {
            if (CONTAINER_NAME.equals(c.getName())) return c;
        }
        return null;
    }

    private static Container createContainer(ContainerManager manager, ContentsManager contentsManager) throws Exception {
        JSONObject data = new JSONObject();
        data.put("name", CONTAINER_NAME);
        data.put("screenSize", "1280x720");
        data.put("wineVersion", "proton-9.0-x86_64");
        data.put("emulator", "box64");
        data.put("wow64Mode", true);           // Metin2 istemcisi 32-bit
        data.put("lc_all", "tr_TR.UTF-8");
        data.put("showFPS", false);
        data.put("fullscreenStretched", false);
        // Arayuzden olusturulan container ile ayni varsayilanlar
        data.put("envVars", Container.DEFAULT_ENV_VARS);
        data.put("graphicsDriver", Container.DEFAULT_GRAPHICS_DRIVER);
        data.put("graphicsDriverConfig", Container.DEFAULT_GRAPHICSDRIVERCONFIG);
        data.put("dxwrapper", Container.DEFAULT_DXWRAPPER);
        data.put("dxwrapperConfig", Container.DEFAULT_DXWRAPPERCONFIG);
        data.put("ddrawrapper", Container.DEFAULT_DDRAWRAPPER);
        data.put("audioDriver", Container.DEFAULT_AUDIO_DRIVER);
        data.put("wincomponents", Container.DEFAULT_WINCOMPONENTS);
        data.put("drives", Container.DEFAULT_DRIVES);
        data.put("startupSelection", (int) Container.STARTUP_SELECTION_ESSENTIAL);
        data.put("box64Version", com.winlator.cmod.core.DefaultVersion.BOX64);
        data.put("box64Preset", com.winlator.cmod.box86_64.Box86_64Preset.COMPATIBILITY);
        data.put("fexcoreVersion", com.winlator.cmod.core.DefaultVersion.FEXCORE);
        data.put("desktopTheme", com.winlator.cmod.core.WineThemeManager.DEFAULT_DESKTOP_THEME);
        data.put("rcfileId", 0);
        data.put("midiSoundFont", "");

        // createContainer private oldugu icin async API'yi senkron bekliyoruz.
        final Object lock = new Object();
        final Container[] result = new Container[1];
        final boolean[] done = {false};
        new Handler(Looper.getMainLooper()).post(() ->
            manager.createContainerAsync(data, contentsManager, c -> {
                synchronized (lock) { result[0] = c; done[0] = true; lock.notifyAll(); }
            })
        );
        synchronized (lock) {
            long deadline = System.currentTimeMillis() + 10 * 60 * 1000L;
            while (!done[0] && System.currentTimeMillis() < deadline) lock.wait(1000);
        }
        return result[0];
    }

    private static File writeShortcut(Container container) throws Exception {
        File desktopDir = container.getDesktopDir();
        if (!desktopDir.isDirectory()) desktopDir.mkdirs();
        File file = new File(desktopDir, CONTAINER_NAME + ".desktop");

        // Shortcut.java icindeki StringUtils.unescape her "\" icin dosyada "\\\\" bekliyor.
        String exePath = (GAME_DIR_WIN + "\\" + LAUNCHER_EXE).replace("\\", "\\\\\\\\");
        String content =
            "[Desktop Entry]\n" +
            "Name=" + CONTAINER_NAME + "\n" +
            "Exec=env WINEPREFIX=\"/home/xuser/.wine\" wine " + exePath + "\n" +
            "Type=Application\n" +
            "StartupNotify=true\n" +
            "Path=" + GAME_DIR_WIN.replace("\\", "\\\\\\\\") + "\n" +
            "StartupWMClass=andolan2client.exe\n" +
            "\n" +
            "[Extra Data]\n" +
            "controlsProfile=" + CONTROLS_PROFILE_ID + "\n" +
            "lc_all=tr_TR.UTF-8\n";
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }

    private static void ensureControlsProfile(Activity activity) {
        File dst = new File(InputControlsManager.getProfilesDir(activity), "controls-" + CONTROLS_PROFILE_ID + ".icp");
        if (dst.isFile()) return; // kullanicinin duzenlemelerini koru
        try {
            copyAsset(activity.getAssets(), ASSET_PROFILE, dst);
        } catch (Exception e) {
            Log.w(TAG, "Kontrol profili kopyalanamadi", e);
        }
    }

    private static void applyFirstRunPreferences(Activity activity) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        if (prefs.getBoolean(PREF_FIRST_SETUP, false)) return;
        prefs.edit()
            .putBoolean("touchscreen_toggle", true)        // dokunulan yere tikla (Metin2 icin dogal)
            .putBoolean("touchscreen_timeout_enabled", false)
            .putBoolean(PREF_FIRST_SETUP, true)
            .apply();
    }

    private static void copyAsset(AssetManager assets, String assetPath, File dst) throws Exception {
        File parent = dst.getParentFile();
        if (parent != null && !parent.isDirectory()) parent.mkdirs();
        try (InputStream in = assets.open(assetPath); OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
    }
}
