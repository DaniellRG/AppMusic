# Handoff Schema (`docs/handoff-latest.json`)

Formato estricto del handoff → que cada agente produce/consumge al cambiar de turno.

## Campos

| Campo | Tipo | Requisito | Descripción |
|---|---|---|---|
| `status` | string | Obligatorio | `"ok"` / `"blocked"` |
| `changed_files` | array<string> | Obligatorio | Rutas relativas a la raíz del proyecto que este agente modificó. |
| `summary` | string | Obligatorio (max 3 líneas) | Qué hizo / qué planea. No más de 3 líneas. |
| `risks` | array<string> | Obligatorio | Riesgos reales detectados. |
| `commands_to_verify` | array<string> | Obligatorio | Comandos shell para verificar el work. |
| `artifacts` | array<object> | Obligatorio | Artefactos producidos (APK, reportes, etc.). |
| `next_role` | string | Obligatorio | `"hermes"` / `"opencode"` / `"claude"` / etc. |
| `block_reason` | string | Obligatorio (puede ser `""`) | Por qué está bloqueado. |

## Invariante
- Un agente NUNCA pasa el turno a sí mismo → `next_role` debe apuntar al otro.
- `block_reason` vacío → el siguiente agente puede proseguir inmediatamente.
- `status: "blocked"` → el siguiente agente NO proseguir → espera señal de `hermes`.
