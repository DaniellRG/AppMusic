# TEAM_KICKOFF — Music (Android)

## Equipo
- **Hermes** (coordinador): planifica, acota alcance, gestiona handoffs. NO escribe código Kotlin.
- **Opencode**: escribe código Kotlin en zonas asignadas (HomeScreen/PlayerScreen/MusicViewModel/MusicManager/PlaybackService/network/LyricsCache/repository).

## Reglas de zonas
- Cada agente posee una zona de archivos exclusiva. NUNCA tocar la zona del otro sin coordinación explícita.
- El coordinador (Hermes) solo modifica: `ui/theme/Color.kt`, `ui/theme/Theme.kt`, `ui/theme/Typography.kt`, `ui/components/VinylArtwork.kt`, `ui/preview/Previews.kt`, `ui/screens/SettingsScreen.kt`, `settings/AppSettings.kt`.
- TODO lo de `repository/`, `viewmodel/`, `scanner/`, red y players → zona de opencode → coordinar vía handoff JSON.

## Workflow
1. El coordinador lee `docs/agent-state.md` (estado único de verdad).
2. Planifica → actualiza `docs/agent-state.md` → escribe `docs/handoff-latest.json` → pasa el turno.
3. El siguiente agente toma `docs/handoff-latest.json` y ejecuta.
4. Un agente no comienza hasta que `next_role` lo señale o `block_reason` esté vacío.

## Build verificado
```
./gradlew clean assembleDebug --no-daemon --no-configuration-cache --rerun-tasks
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start -n com.example.music/.MainActivity
```
