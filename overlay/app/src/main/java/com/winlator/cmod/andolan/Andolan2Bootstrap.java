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
    private static final String LAUNCHER_CFG = "url=https://cdn.andolan2.com/\r\nexe=Andolan2Client.exe\r\n";

    private static final String ASSET_LAUNCHER = "andolan2/" + LAUNCHER_EXE;
    private static final String ASSET_PROFILE = "inputcontrols/profiles/controls-" + CONTROLS_PROFILE_ID + ".icp";
    private static final String PREF_FIRST_SETUP = "andolan2_first_setup_done";
    private static final String PREF_AUTOSTART = "andolan2_autostart";

    private static boolean launchedThisProcess = false;

    private Andolan2Bootstrap() {}

    public static boolean isAutostartEnabled(Activity activity) {
        return PreferenceManager.getDefaultSharedPreferences(activity).getBoolean(PREF_AUTOSTART, true);
    }

    /** Eski MainActivity girisi icin; artik giris noktasi Andolan2SplashActivity. */
    public static void autostart(Activity activity) {
        // bilerek bos: Winlator ekrani (gizli ayarlar) acildiginda oyun kendiliginden baslamasin
    }

    public interface StatusCallback { void onStatus(String message, int permille); }

    /** Oyun ekrani normal kapandiginda cagrilir. */
    public static void markGameClosed(android.content.Context context) {
        CrashReport.setInGame(context, false);
    }

    /** Winlator menusunden elle baslatma. */
    public static void launch(final Activity activity) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                prepareAndLaunch(activity, null, null);
            } catch (Exception e) {
                Log.e(TAG, "Baslatma hatasi", e);
                new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(activity, "Andolan2 başlatılamadı: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    /** Arka plan thread'inden cagrilir: container'i hazirlar ve oyun ekranini acar. */
    public static void prepareAndLaunch(final Activity activity, StatusCallback status, Runnable onLaunched) throws Exception {
        launchedThisProcess = true;
        final Handler ui = new Handler(Looper.getMainLooper());
        applyFirstRunPreferences(activity);
        ensureControlsProfile(activity);

        ContainerManager manager = new ContainerManager(activity);
        Container container = findContainer(manager);
        if (container == null) {
            if (status != null) status.onStatus("Oyun ortamı oluşturuluyor…", 940);
            ContentsManager contentsManager = new ContentsManager(activity);
            contentsManager.syncContents();
            container = createContainer(manager, contentsManager);
        }
        if (container == null) throw new IllegalStateException("Oyun ortamı oluşturulamadı");
        repairContainer(container);
        manager.activateContainer(container);

        if (status != null) status.onStatus("Oyun dosyaları hazırlanıyor…", 980);
        File gameDir = new File(container.getRootDir(), ".wine/drive_c/Andolan2");
        if (!gameDir.isDirectory()) gameDir.mkdirs();
        File launcher = new File(gameDir, LAUNCHER_EXE);
        if (!launcher.isFile() || launcher.length() == 0) copyAsset(activity.getAssets(), ASSET_LAUNCHER, launcher);
        // Launcher yaninda launcher.cfg olmadan calismiyor ("launcher.cfg bulunamadi veya url satiri yok")
        File cfg = new File(gameDir, "launcher.cfg");
        if (!cfg.isFile() || !FileUtilsCompat.contains(cfg, "url=")) {
            try (OutputStream out = new FileOutputStream(cfg)) {
                out.write(LAUNCHER_CFG.getBytes(StandardCharsets.UTF_8));
            }
        }

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
            activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            if (onLaunched != null) onLaunched.run();
        });
    }

    /** Eski surumlerde bozuk kaydedilen DXVK ayarini onar. */
    private static void repairContainer(Container container) {
        String cfg = container.getDXWrapperConfig();
        if (cfg == null || !cfg.contains("version=")) {
            Log.w(TAG, "dxwrapperConfig onariliyor: '" + cfg + "'");
            container.setDXWrapperConfig(Container.DEFAULT_DXWRAPPERCONFIG);
            container.putExtra("dxwrapper", null);
            container.saveData();
        }
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
        data.put("ddrawrapper", Container.DEFAULT_DDRAWRAPPER);
        data.put("dxwrapperConfig", Container.DEFAULT_DXWRAPPERCONFIG);
        data.put("audioDriver", Container.DEFAULT_AUDIO_DRIVER);
        data.put("wincomponents", Container.DEFAULT_WINCOMPONENTS);
        data.put("drives", Container.DEFAULT_DRIVES);
        data.put("startupSelection", (int) Container.STARTUP_SELECTION_ESSENTIAL);
        data.put("box64Version", com.winlator.cmod.core.DefaultVersion.BOX64);
        data.put("box64Preset", com.winlator.cmod.box86_64.Box86_64Preset.COMPATIBILITY);
        data.put("fexcoreVersion", com.winlator.cmod.core.DefaultVersion.FEXCORE);
        data.put("desktopTheme", "DARK,COLOR,#000000");
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
            "envVars=WINEDEBUG=err+all,fixme-all\n" +
            "lc_all=tr_TR.UTF-8\n";
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }

    private static void ensureControlsProfile(Activity activity) {
        File dst = new File(InputControlsManager.getProfilesDir(activity), "controls-" + CONTROLS_PROFILE_ID + ".icp");
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

    private static final class FileUtilsCompat {
        static boolean contains(File f, String needle) {
            try {
                byte[] b = java.nio.file.Files.readAllBytes(f.toPath());
                return new String(b, StandardCharsets.UTF_8).contains(needle);
            } catch (Exception e) { return false; }
        }
    }
}
