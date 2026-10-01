# Contrato de coordinacion multi-agente

Este archivo es la fuente de verdad para la colaboracion entre **opencode** (coordinador)
y **Hermes** (worker). Ambos deben leerlo antes de tocar codigo.

Motivo: los dos agentes comparten el mismo directorio de trabajo. No existen worktrees
separados porque el proyecto no usa git worktrees; el aislamiento se hace por
**reparto de archivos**, no por separacion de arboles.

## 1. Roles

| Agente | Rol | Responsabilidad |
|---|---|---|
| opencode | Coordinador | Descompone en tareas, despacha, revisa, integra. Es el unico que crea el Run de orquestacion. |
| Hermes | Worker | Ejecuta las tareas asignadas dentro de su zona de archivos. Informa al buzon. |

## 2. Reparto de archivos (limite-duro)

### Hermes — capa visual y tema
- `app/src/main/java/com/example/music/ui/theme/Color.kt`
- `app/src/main/java/com/example/music/ui/theme/Theme.kt`
- `app/src/main/java/com/example/music/ui/theme/Typography.kt`
- `app/src/main/java/com/example/music/ui/components/VinylArtwork.kt`
- `app/src/main/java/com/example/music/ui/preview/Previews.kt`
- `app/src/main/java/com/example/music/ui/screens/SettingsScreen.kt`
- `app/src/main/java/com/example/music/settings/AppSettings.kt`

### opencode — capa funcional
- `app/src/main/java/com/example/music/ui/screens/HomeScreen.kt`
- `app/src/main/java/com/example/music/ui/screens/PlayerScreen.kt`
- `app/src/main/java/com/example/music/ui/viewmodel/MusicViewModel.kt`
- `app/src/main/java/com/example/music/player/MusicManager.kt`
- `app/src/main/java/com/example/music/player/PlaybackService.kt`
- `app/src/main/java/com/example/music/network/CoverApi.kt`
- `app/src/main/java/com/example/music/network/NetworkClient.kt`
- `app/src/main/java/com/example/music/network/LyricsApi.kt`

### Compartido — solo lectura, pactar antes de editar
- `MainActivity.kt`
- `app/src/main/java/com/example/music/ui/navigation/MusicNavHost.kt`
- `app/src/main/java/com/example/music/ui/screens/FavoritesScreen.kt`
- `app/src/main/java/com/example/music/ui/screens/FoldersScreen.kt`
- `app/src/main/java/com/example/music/ui/screens/SearchScreen.kt`
- `app/src/main/java/com/example/music/model/**`
- `app/src/main/java/com/example/music/database/**`
- `app/src/main/java/com/example/music/scanner/**`
- `app/src/main/java/com/example/music/repository/**`

**Regla:** antes de editar un archivo compartido, mandar un mensaje al buzon y esperar
respuesta. No asumir permiso.

**Regla de APIs públicas:** `VinylArtwork` (Hermes) se usa en `PlayerScreen` (opencode).
Si Hermes cambia la firma de un componente, debe anunciarlo. Un cambio de firma sin aviso
rompe la compilacion del otro.

## 3. Protocolo de comunicacion

Usa `orca orchestration`. El Run lo crea el coordinador una vez, desde su propia terminal.

```bash
# Solo el coordinador, una vez por sesion:
orca orchestration run-create --objective "Terminar reproductor Android" --json

# Antes de empezar a trabajar, y cada N minutos:
orca orchestration check --peek --format --json

# Avisar al otro:
orca orchestration send --to <handle> --text "<mensaje>" --json

# Responder:
orca orchestration reply --json
```

`orca orchestration check` revisa **tu** buzon. No entrega nada remoto: para que el otro
agente continue, hay que **mandarle** el mensaje a el.

## 4. Regla de trabajo continuo (el bug que nos trajo aqui)

**No detenerse a preguntar. No terminar un turno solo porque se acabo un tema.**

- Ante una duda: elige la opcion mas segura, dejala comentada en el codigo, y sigue.
- Solo se detiene un turno por: (a) orden explicita de Daniel, (b) un bloqueo real
  (falta una API key, una dependencia caida, permiso denegado), (c) tarea terminada
  **y** reportada por el buzon.
- Si el alcance se agota antes que las tareas, pedir mas trabajo al buzon, no terminar
  el turno en silencio.

Motivo: un turno que termina sin avisar deja al otro agente esperando indefinidamente.
Eso ya ocurrio una vez: opencode termino con "sigo por ahi salvo que quieras que cambie
el orden" y Hermes nunca se entero.

## 5. Disciplina de commits

- El proyecto **no estaba** bajo git. Se inicializo para este flujo. Todo cambio debe
  ser recuperable.
- Commit antes de empezar una tarea, y al terminarla. Nunca dos agentes en medio commit.
- Mensajes en el estilo del repo: Conventional Commits en espanol.
- Antes de commitear, revisar que el diff solo toca archivos de tu zona.

## 6. Verificacion

El proyecto corre sobre JDK 21 (fijado en `gradle.properties`). **JDK 25 rompe KSP/Room.**

```bash
./gradlew clean assembleDebug --no-daemon --no-configuration-cache --rerun-tasks
```

`--no-configuration-cache` es obligatorio: la property lo activa y queda obsoleta tras
cambios en Room/KSP, causando errores silenciosos de classpath.

Instalar y lanzar en el emulador `emulator-5554`:

```bash
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start -n com.example.music/.MainActivity
```

Como ambos agentes compilan el mismo `app/build/`, **nunca dos builds simultaneos**. Si el
otro esta compilando, esperar.

## 7. Limitaciones conocidas

- Cada `am start` re-escanea MediaStore (`deleteAll` + `reInsert`), lo que borra
  `isFavorite` por cancion. Los favoritos no sobreviven a un relanzamiento todavia.
- Este archivo no sustituye a un PR review humano.
