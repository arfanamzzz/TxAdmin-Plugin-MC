# TxAdmin untuk Paper 26.3

Menu admin ala txAdmin (FiveM). Java 25, Paper 26.3. Plugin ini adalah Paper plugin (paper-plugin.yml).

## Build
- Ganti isi `src` + `pom.xml` di repo dengan file ini (hapus `plugin.yml` lama bila ada).
- `.github/workflows/build.yml`: `java-version: '25'` (temurin).
- Hasil: `target/TxAdmin.jar`

## Pasang
1. Upload `TxAdmin.jar` ke `plugins/`.
2. Start server sekali, edit `plugins/TxAdmin/config.yml` -> isi `admins:` dengan nama Minecraft kamu.
3. Restart server. Di game ketik `/tx`.

## Keybind tombol bebas
Options > Controls > Key Binds > **Quick Actions** (bawaan G) -> ganti ke tombol apa saja (Home, 0, ', F1, ...).
Tombol itu membuka panel TxAdmin (menu, noclip, daftar pemain, god, heal).
