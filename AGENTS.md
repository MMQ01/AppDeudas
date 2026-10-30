# AGENTS.md — AppDeudas

App Android 100 % local para control de deudas por cliente. El **id del cliente es su celular
normalizado** (ej. `573001234567`). Sin login y sin red.

## Estado y fuentes de verdad

- El código actual es solo el **template "Empty Activity" de Android Studio**; la app real aún
  no está implementada.
- **`PLAN.md` manda**: arquitectura, pantallas, reglas de negocio y fases de implementación
  (0–5). Léelo completo antes de escribir código.
- `app/src/main/assets/schema.sql` es el modelo de datos final (tablas + triggers + vistas). No rediseñarlo.

- **Abono general (en curso):** `PLAN_ABONO_GENERAL.md`, sección "Estado de ejecución" (§10),
  dice en qué fase va. Léela al retomar y actualízala al final de cada interacción.

## Trampa principal: el template es Compose, el plan es Views

El scaffold trae Jetpack Compose, pero PLAN.md exige **Views XML + ViewBinding** con tema
Material 3 XML (`Theme.Material3.DayNight.NoActionBar`). Al implementar:

- Quitar las dependencias Compose y el plugin `kotlin-compose`; activar
  `buildFeatures { viewBinding = true }`.
- Eliminar `MainActivity` y `ui/theme/` del template. La entrada real es
  `Bienvenida` (splash Lottie) → `ClientesActivity`.
- **No degradar el toolchain**: AGP 9.2.1 / Kotlin 2.2.10 / Gradle 9.4.1 ya compilan. Los
  números de la tabla §1 de PLAN (Kotlin 1.9 / AGP 8.6) se copiaron del proyecto de referencia.
- minSdk: PLAN fija **23**; el scaffold trae 31 (default del IDE). Prevalece PLAN.

## Base de datos (reglas fáciles de romper)

- `SQLiteOpenHelper` (`DeudasDatabaseHelper`); **prohibido Room**.
- `onCreate` lee `assets/schema.sql` y ejecuta sentencia por
  sentencia. **No partir por `;` a secas**: los triggers llevan `;` internos. Partir solo
  cuando el acumulado tiene `BEGIN`/`END` balanceados (o usar separador `--@@`).
- `onConfigure` → `db.setForeignKeyConstraintsEnabled(true)`.
- **`saldo` y `estado` de la deuda los calculan los triggers de SQLite; nunca escribirlos
  desde Kotlin** (única excepción: anular pone `estado='anulada'`). "Marcar pagada" = INSERT
  abono `tipo='cierre'` por el saldo; "Reabrir" = anular el último abono activo.
- **Nunca DELETE desde la app**: todo "eliminar" es inactivación. Cliente → `estado='inactivo'`
  (se reactiva si se vuelve a agregar el celular); abono y pago → `estado='anulado'` +
  `fechaAnulacion`. Triggers y vistas solo cuentan abonos `estado='activo'`; anular un pago
  anula sus abonos (`trg_pago_anular`); un abono anulado no se reactiva.
- Los `RAISE(ABORT, …)` de los triggers llegan a Kotlin como `SQLiteConstraintException`;
  los DAO los traducen a mensajes de usuario.
- Los tests de triggers necesitan **Robolectric o instrumentado**; en JVM puro no existe el
  SQLite de Android.
- **Respaldos locales** (`Utils/RespaldoUtils.kt`): `filesDir/respaldos/`, sin nube ni
  `INTERNET`. `RespaldoScheduler` (WorkManager) corre uno **cada 5 días** a las 2:00 a. m.; se
  conservan 12. Restaurar exige la misma `DATABASE_VERSION` (si no, `onUpgrade` borraría todo).
  `allowBackup="false"`: la base no sale del celular ni por la nube de Google.
- `onUpgrade` = DROP + recreate, solo mientras no haya datos de producción (decisión del plan).

## Dominio

- Normalización de celular (`TelefonoUtils`): quitar espacios/guiones/paréntesis/`+`;
  10 dígitos que empiezan en 3 → anteponer `57`.
- Dinero: **COP sin decimales**, separador de miles con punto (`$ 1.250.000`) — `MonedaUtils`.
- Único permiso: `READ_CONTACTS` (runtime). **No agregar `INTERNET`**.
- Deudas anuladas: excluidas de todos los totales, ocultas salvo chip "Anuladas", solo lectura.

## Referencia externa

`C:\Users\Desarrollo\Documents\Apoyo\AppVentas` — proyecto hermano del que se copian patrones
y recursos: `VentasDatabaseHelper`, `AdapterCliente`, `LoadingDialog`, `bottomsheet_layout.xml`,
`item_cliente.xml`, `themes.xml`/`colors.xml`/animaciones. Reutilizar antes de inventar.

## Comandos (Windows / PowerShell)

```powershell
.\gradlew.bat assembleDebug                # compilar
.\gradlew.bat installDebug                 # instalar en dispositivo/emulador
.\gradlew.bat testDebugUnitTest            # tests JVM
.\gradlew.bat connectedDebugAndroidTest    # instrumentados (requiere dispositivo)
```

Usar `.\gradlew.bat`; `./gradlew` no resuelve en PowerShell.

## Convenciones

- Módulo único `:app`, package `com.mmq01.appdeudas`. Paquetes según PLAN §2
  (`Principal/`, `Deudas/`, `Adapter/`, `Clases/`, `DBHelper/`, `Utils/`).
- Identificadores y strings de UI **en español** (`Cliente`, `Deuda`, `Abono`,
  `ClientesActivity`, …), igual que PLAN.md y schema.sql.
