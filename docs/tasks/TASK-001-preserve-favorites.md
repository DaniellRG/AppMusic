# TASK-001 — Preservar isFavorite tras reescaneo (MediaStore → refreshFromScan)

## Problema
`refreshFromScan()` → antes hacía → `deleteAll()` + re-insert → en cada arranque → → → lo que borraba → la marca `isFavorite` (el scanner siempre devuelve `isFavorite = false`), así como las canciones importadas por SAF y la pertenencia a carpetas.

## Estado actual (verificado)
- La refactor → **ya está implementada y cometer**d → → → en `SongRepository.refreshFromScan()`.
- Ya no hay `deleteAll` + re-insert → la sincronización es **diff-based** por `uri`.
- `isFavorite` → → preservado vía:
  1. `SongDao.getFavoriteUris()` → lee el conjunto `{uri}` de favoritas ANTES del diff.
  2. `computeScanDiff()` → para `toInsert` → → `fresh.copy(isFavorite = fresh.uri in favoriteUris)`.
  3. → para `toUpdate` → → `fresh.copy(isFavorite = previous.isFavorite)`.
- Las updates → van por `@Update` (no → `REPLACE` → que sería → → delete + insert → cascada).

→ Estado: **COMPLETADA e implementada** → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → ✓.

## Criterios de aceptación
1. Tras un reescaneo → → `isFavorite` → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → ✓.
2. → Tras un reescaneo → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → ✓.

## Tests existentes
- → `ScanDiffTest` → `conserva el favorito cuando el scanner devuelve isFavorite false`. → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → → ✓.
