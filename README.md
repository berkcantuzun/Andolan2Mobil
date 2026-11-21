# Andolan2 Mobil

Andolan2'yi Android'de çalıştıran uygulama. Winlator Cmod (coffincolors/winlator, `cmod_bionic` dalı, GPL-3.0) üzerine kurulu.
Bu depoda sadece Andolan2 değişiklikleri var (`overlay/`). Derleme sırasında Winlator Cmod kaynak kodu ve
büyük Wine dosyaları otomatik indirilir.

## Uygulama ne yapar
1. İlk açılışta Wine sistem dosyalarını kurar (telefona göre 2–5 dakika).
2. `Andolan2` adında bir container oluşturur (Proton 9 x86_64 + Box64, WoW64, 1280x720, tr_TR).
3. APK içindeki `Andolan2Launcher.exe`'yi `C:\Andolan2\` klasörüne kopyalar ve çalıştırır.
   Launcher oyunun geri kalanını sunucudan indirir ve istemciyi açar.
4. Oyun ekranında:
   - Üstte **☰ TUŞLAR**: sağdaki tuş panelini açar/kapatır (son durum hatırlanır).
   - Panel: SOL, SAĞ, SAĞ MOD (açıkken her dokunuş sağ tık olur), 1–4, F1–F4, Z (topla), SPACE,
     CTRL (bir dokunuşta basılı kalır), ESC, ENTER, ⌨ (telefon klavyesi), JOY (joystick'i gizle/göster).
   - Sol altta WASD joystick, panelin solunda SALDIR (Space).
   - Ekranda bir yere dokunmak = oraya sol tık.
5. Oyundan çıkınca sol menüde **▶ Andolan2 Oyna** ile yeniden başlatılır.

## APK nasıl derlenir (GitHub Actions, ücretsiz)
1. github.com'da yeni bir depo aç (ör. `andolan2-mobil`). Private olabilir.
2. Bu klasördeki her şeyi (gizli `.github` klasörü dahil) depoya yükle:
   - Git ile: `git init && git add . && git commit -m ilk && git branch -M main && git remote add origin <depo-url> && git push -u origin main`
   - Web ile: "Add file → Upload files" ile sürükle-bırak. `.github` klasörü yüklenmezse
     "Add file → Create new file" ile yol olarak `.github/workflows/build-apk.yml` yaz ve dosyanın içeriğini yapıştır.
3. Depoda **Actions** sekmesi → "Andolan2 APK" → çalışıyor olmalı (yoksa "Run workflow").
4. ~20–40 dakika sonra işin sayfasının altında **Artifacts → Andolan2-APK** çıkar. İndir, zip'i aç, `Andolan2.apk`'yı telefona kur.

## Launcher güncellenirse
Yeni exe'yi `overlay/app/src/main/assets/andolan2/Andolan2Launcher.exe` ile değiştir, push et. APK yeniden derlenir.
(Telefonda zaten kurulu olan launcher kendini güncelleyebiliyorsa buna gerek yok.)

## Değişen dosyalar (overlay/)
- `java/com/winlator/cmod/andolan/Andolan2Bootstrap.java`: kurulum + otomatik başlatma (yeni)
- `java/com/winlator/cmod/andolan/Andolan2Panel.java`: açılır kapanır tuş paneli (yeni)
- `MainActivity.java`: otomatik başlatma, menü girişi, izin akışı
- `XServerDisplayActivity.java`: paneli ekrana ekler (2 satır)
- `widget/TouchpadView.java`: `mapToXServer` (sağ tık modu için)
- `assets/andolan2/Andolan2Launcher.exe`, `assets/inputcontrols/profiles/controls-4.icp` (joystick + SALDIR)
- Uygulama adı ve simgesi, `res/menu/main_menu.xml`

## Bilinen sınırlar
- Paket adı `com.winlator.cmod` olarak kaldı, çünkü Wine ortamında bu ada bağlı sabit yollar var.
  Aynı telefonda orijinal Winlator Cmod ile birlikte kurulamaz.
- Sadece 64-bit (arm64) telefonlar.
- Adreno (Snapdragon) GPU'lar en iyi sonucu verir. Mali GPU'da görüntü gelmezse: menü → Containers → Andolan2 → düzenle → Graphics Driver: VirGL.
- Sunucunun anti-cheat sistemi Wine'ı engelliyorsa oyun açılmayabilir.
- Lisans: Winlator Cmod GPL-3.0. APK'yı başkalarına dağıtırsan kaynak kodunu (bu depo + taban commit) da paylaşman gerekir.
