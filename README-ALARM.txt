ALARM BANGUN (Android) - cara pakai singkat

1) Upload SEMUA isi folder ini ke repo GitHub (ke root repo, bukan foldernya):
   .github/  app/  build.gradle  settings.gradle  gradle.properties
2) Taruh file MP3 pilihanmu di root repo yang sama (atau di 1 folder, mis. "mp3/").
   Semua .mp3 otomatis masuk ke APK dan muncul di pilihan "Suara alarm".
3) Buka tab Actions > "Build APK" > tunggu hijau > buka run-nya > Artifacts > AlarmBangun-APK.
   Download, extract zip-nya, install app-debug.apk di HP.
4) Buka app > tombol "Izin & tes": beresin semua yang [BELUM], lalu coba TES ALARM
   dan kunci HP untuk memastikan bunyi di layar terkunci.

Ganti/tambah MP3 = upload MP3 baru ke repo, lalu build ulang (Actions > Run workflow).
