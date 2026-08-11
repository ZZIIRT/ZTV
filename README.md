# ZTV

Лёгкое Android TV приложение для просмотра live-телеканалов с D-pad пульта.

## Что уже есть

- автозагрузка стандартного M3U/M3U8 плейлиста;
- локальный кэш последнего успешного плейлиста;
- fullscreen Media3/ExoPlayer без стандартных play/pause контролов;
- `OK` открывает список каналов и не ставит live-эфир на паузу;
- `↑/↓` и `CH+/CH-` переключают каналы;
- цифровой ввод номера канала;
- OSD при переключении;
- избранные каналы и режим переключения только по избранным;
- локальные настройки через DataStore;
- reconnect после sleep/standby с новой live-позицией;
- boot receiver для разрешённого системой автозапуска.

## Сборка

Нужен установленный Android SDK. Укажите путь одним из способов:

```properties
# local.properties
sdk.dir=C\:\\Users\\ZZII\\AppData\\Local\\Android\\Sdk
```

или задайте переменную окружения `ANDROID_HOME`.

После этого:

```powershell
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot'
.\gradlew.bat assembleDebug
```
