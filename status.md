# STATUS — AppDeudas

Última actualización: 29 oct 2026.
Estado general: **App base (`PLAN.md` fases 0–5) completa y probada a mano. Abono general
(`PLAN_ABONO_GENERAL.md`) fases 0–3 ✅ y probadas en pantalla; faltan las fases 4 y 5 (entrada
desde el menú del cliente, etiqueta "Pago general" en abonos, diálogo doble al anular,
historial y lista de pagos).** 37/37 tests instrumentados y 32/32 JVM OK en SM-A055M.
`assembleDebug` OK. Respaldos locales (solo en el celular) implementados.

### Bitácora

- **29 oct — Respaldos locales (sin nube).** La base se respalda dentro del almacenamiento
  privado de la app (`filesDir/respaldos/`), sin permisos, sin `INTERNET` y sin archivos
  externos. `Utils/RespaldoUtils.kt` + `Clases/Respaldo.kt`:
  - copia consistente con `VACUUM INTO` (API 30+) y, en versiones anteriores, `PRAGMA
    wal_checkpoint(TRUNCATE)` + copia del archivo; nombre `deudas-AAAAMMDD-HHmmss.db`.
  - **automático cada 5 días a las 2:00 a. m. (hora del celular)** con `WorkManager`
    (`androidx.work:work-runtime-ktx` 2.10.1): un trabajo único para la próxima 2:00 que, al
    correr, programa el periódico de 5 días (así el primer respaldo no se salta el intervalo).
    Corre con la app cerrada y sobrevive reinicios; Android puede retrasarlo unos minutos (más
    en Doze). `RespaldoUtils.automatico` evita duplicados el mismo día. Se conservan los
    últimos 12. El nombre de trabajo cambió a `respaldo_periodico`; al arrancar se cancela el
    viejo `respaldo_diario`.
  - restaurar valida `PRAGMA user_version` contra `DATABASE_VERSION` (un respaldo viejo
    dispararía el `onUpgrade` DROP + recreate), guarda antes una copia del estado actual,
    cierra el helper, reemplaza el `.db` (borrando `-wal`/`-shm`/`-journal`) y recrea la
    pantalla.
  - UI: menú de la toolbar "Respaldos" → `Principal/RespaldosBottomSheet.kt` +
    `sheet_respaldos.xml` + `item_respaldo.xml` + `menu/menu_clientes.xml` (crear / listar /
    restaurar con confirmación). Strings `respaldos_*`.
  - Tests nuevos `RespaldoUtilsTest` (3): crea con la misma versión, restaurar devuelve los
    datos, rechaza respaldo de otra versión. Total instrumentados 37/37 OK.

- **29 oct — Prueba manual de deudas y abonos + arreglos de fechas/avisos.** Sembrada una BD de
  prueba y recorrido en el SM-A055M: crear/editar deuda, abono parcial, abono que excede el
  saldo (error), marcar pagada, reabrir, editar valor por debajo de lo abonado (error), anular,
  filtros, abono general FIFO, reparto editado a mano, "se paga", saldo a favor, D6 al crear
  deuda, usar saldo a favor desde la hoja, anular abono de un pago general (devuelve el monto a
  favor), anular deuda con abonos de pago general (D8), marcar pagada con abono tipo `cierre`,
  inactivar/recuperar cliente reactivando el mismo celular. Todo coincidió con lo esperado y con
  la BD (`vw_pago`, `vw_resumen_cliente`).
  - **Arreglado:** las fechas se guardan en UTC y se mostraban crudas o con el día UTC
    (`2026-10-30 03:16:44`, `Pagada el 2026-10-30 03:17:26`, `Fecha: 30/10/2026` cuando en
    Colombia era 29/10). Nuevo `Utils/FechaUtils.kt` convierte UTC → `dd/MM/yyyy` local; se usa
    en `AdapterDeuda`, `AdapterAbono`, `ResumenUtils` y `RepartoUtils`.
  - **Cambiado:** los `Toast` se reemplazaron por `Snackbar` (`Utils/AvisoUtils.kt`) porque no
    se podían verificar en la jerarquía de vistas y duraban poco. Ahora los mensajes salen ~3 s
    y quedan en `uiautomator`.
  - Tests nuevos: `FechaUtilsTest` (4) y uno de fecha local en `ResumenUtilsTest`. Total JVM
    27/27; instrumentados 34/34.
- **29 oct — Compartir deudas por WhatsApp (`compartirwp.md`, fases 0–4).** Long-press en un
  cliente → "Compartir por WhatsApp" → vista previa (Enviar / Copiar / Cancelar).
  `Utils/ResumenUtils.kt` arma la lista (`• deuda (dd/MM/yyyy) — saldo` + `*Total pendiente:*`,
  sin saludo, sin introducción y sin saldo a favor); `Utils/WhatsAppUtils.kt` intenta chat
  directo `whatsapp://`, luego WhatsApp/Business, luego chooser y por último el portapapeles.
  `<queries>` en el manifest, **sin permiso `INTERNET`**. Probado a mano en el SM-A055M: menú,
  vista previa, Copiar (verificado pegando) y Enviar (WhatsApp avisó que el número de prueba no
  existe); cliente sin deudas solo muestra el aviso. Datos de prueba borrados (`pm clear`).
- **29 oct — Verificación de estado.** Revisados `PLAN.md`, `PLAN_ABONO_GENERAL.md` §10 y el
  código: `assembleDebug` compila; la app base (fases 0–5) está implementada (ícono propio,
  Lottie propio, modo oscuro, estados vacíos). De abono general, fases 0–3 hechas y con tests.
  **La fase 4 no está iniciada**, comprobado en el código: `MenuClienteBottomSheet` solo ofrece
  "Desde contactos"/"Manual" (sin "Abono general"), `AdapterAbono` solo pinta el badge "cierre"
  (sin etiqueta "Pago general" ni uso de `idPago`), `AbonosBottomSheet.onAbonoEliminar` abre un
  diálogo simple (no "Solo este abono" / "Todo el pago") y `confirmar_reabrir_mensaje` no avisa
  que el abono de un pago general vuelve a saldo a favor. Fase 5 (lista de pagos) tampoco
  empezada.
- **29 oct — Inactivación en vez de eliminación.** La app ya no hace DELETE. Cliente →
  `estado='inactivo'` (se reactiva con su historial si se vuelve a agregar el celular); abono y
  pago → `estado='anulado'` + `fechaAnulacion`; "Reabrir" anula el último abono activo. Triggers
  y vistas solo cuentan abonos activos. API: `ClienteDao.inactivar/reactivar`,
  `AbonoDao.anular`, `PagoDao.anular`. Textos de confirmación ajustados. `DATABASE_VERSION = 3`.
- **29 oct — Plan ampliado.** `PLAN_ABONO_GENERAL.md` §9: nota automática en cada abono de un
  pago general y saldo a favor. D2 pasó a "saldo a favor"; D6–D8 cerradas (preguntar al crear
  deuda, sin devolución en efectivo, anular deuda devuelve lo del pago general a favor).
- **29 oct — Fase 2b implementada.** `DATABASE_VERSION = 4`: `pago.valor` (recibido),
  `vw_pago.aplicado/saldoFavor`, `vw_resumen_cliente.saldoFavor`, trigger de tope por pago,
  trigger D8. Cada abono de un pago general guarda en `nota`
  `Abono general de $ 20.000 · aplicado aquí $ 300 · repartido en 3 deudas · a favor $ 4.700`.
  `PagoDao.registrar(idCliente, valorRecibido, lineas, nota)`, `saldoFavor`,
  `aplicarSaldoFavor*`. Al crear una deuda con saldo a favor sale el diálogo "¿Aplicarlo?".
  `assembleDebug`, `testDebugUnitTest` y `connectedDebugAndroidTest` (31/31) OK.
- **29 oct — Fase 3 implementada (abono general en pantalla).** `PagoGeneralBottomSheet` (valor,
  nota, reparto editable, "quedará a favor", casilla "Usar el saldo a favor"), botón "Abono
  general" en `DeudasActivity`, "A favor: $ X" bajo el total y en la lista de clientes.
  `PagoDao.registrar(..., usarSaldoFavor)`. Build + 34/34 instrumentados OK; la app se instaló en
  el SM-A055M y abre sin errores, **pero la hoja no se ha probado a mano**. Siguiente: fase 4.
- **Ojo**: cada subida de `DATABASE_VERSION` borra y recrea la BD (`onUpgrade`), así que los
  datos de prueba del celular se pierden al instalar.

### Notas anteriores (28 oct)

Cambios 28 oct: `AbonosBottomSheet`, `AdapterAbono`, `sheet_abonos.xml`, `item_abono.xml`; creado
`layout/dialog_loader.xml` (faltaba); `parentFragmentManager.setFragmentResult` en
`DeudaFormBottomSheet`; `mipmap-anydpi` → `mipmap-anydpi-v26` (minSdk 23); atributos
margin/padding Horizontal/Vertical reemplazados por Left/Right/Top/Bottom; fix de tipo en
`MonedaUtilsTest`; `AGENTS.md` corregido (schema en assets). No hizo falta plugin kotlin-android.
**Fase 5 hecha (28 oct)**: ícono propio (moneda con `$`, adaptive v26 + `layer-list` en
`mipmap-anydpi` para API 23–25, se borraron los webp del robot), Lottie propio `splash_deudas.json`
(moneda + `$` que se dibuja), color `divisor` para modo oscuro. Estados vacíos y moneda ya
estaban. Tests instrumentados y prueba manual quedan para después (§4.3). Lottie e ícono no se
han visto en pantalla.

Fuente de verdad: `PLAN.md` (arquitectura, pantallas, reglas, fases) y
`PLAN_ABONO_GENERAL.md` §10 (estado del abono general), `schema.sql` (en
`app/src/main/assets/schema.sql`). Proyecto de referencia: `C:\Users\Desarrollo\Documents\Apoyo\AppVentas`.

---

## 1. Decisiones tomadas (y por qué)

- **Se eliminó Jetpack Compose** del scaffold. La app usa **Views XML + ViewBinding** y tema
  Material 3 XML (`Theme.Material3.DayNight.NoActionBar`), como exige PLAN.md.
- **Toolchain actual conservado**: AGP 9.2.1 / Kotlin 2.2.10 (Kotlin integrado de AGP) /
  Gradle 9.4.1. No se degradó a Kotlin 1.9 / AGP 8.6 (esos números de PLAN venían de AppVentas).
- **minSdk 23** (PLAN), compileSdk/targetSdk 36. Java/Kotlin target 11.
- **Sin Room, sin red, sin login**. Único permiso: `READ_CONTACTS` (runtime).
- `schema.sql` vive en `app/src/main/assets/`.
- Lottie e ícono propios (fase 5): `splash_deudas.json` y `mipmap-anydpi*` con moneda y `$`.
- `dialog_bkg.xml` usa el color `sheet_bg` (blanco en light, `#1C2733` en night) para que los
  BottomSheet se lean bien en modo oscuro.

---

## 2. Progreso por fase

### PLAN.md (app base)

| Fase | Estado | Notas |
|---|---|---|
| 0 — Base | ✅ Hecha | Gradle, recursos, splash, manifest |
| 1 — Datos | ✅ Hecha | DBHelper, DAOs, clases, utils, tests |
| 2 — Clientes | ✅ Hecha | Activity, adapter, contacto/manual, refresco |
| 3 — Deudas | ✅ Hecha | Lista, chips por estado, formulario, menú contextual |
| 4 — Abonos | ✅ Hecha | `AbonosBottomSheet`, `AdapterAbono`, tests |
| 5 — Pulido | ✅ Hecha | Ícono y Lottie propios, modo oscuro; revisión visual manual pendiente |

### PLAN_ABONO_GENERAL.md (abono general)

| Fase | Estado | Notas |
|---|---|---|
| 0 — Decisiones | ✅ | D1–D8 cerradas |
| 1 — Esquema | ✅ | `pago`, `abono.idPago`, `vw_pago`, triggers; `DATABASE_VERSION = 4` |
| 2 — DAO + clases | ✅ | `Pago`, `Reparto`, `RepartoUtils`, `PagoDao` |
| 2b — Nota automática + saldo a favor | ✅ | Tests 31/31 OK |
| 3 — Hoja de pago general | ✅ (sin probar a mano) | 34/34 instrumentados OK |
| 4 — Menú cliente + historial | ⬜ **No iniciada** | Sin "Abono general" en el menú, sin etiqueta "Pago general", sin diálogo doble, sin aviso al reabrir |
| 5 — Lista de pagos | ⬜ **No iniciada** | |

Compilación actual: `assembleDebug` OK (29 oct 2026). Dispositivo conectado: SM-A055M
(`R92X63038QB`).

---

## 3. Qué se hizo, archivo por archivo

### Configuración (Fase 0)

- `gradle/libs.versions.toml`: fuera Compose; dentro appcompat 1.7.1, material 1.13.0,
  activity-ktx 1.13.0, constraintlayout 2.2.1, recyclerview 1.4.0, cardview 1.0.0, lottie 4.2.1.
- `build.gradle.kts` (raíz): solo plugin `com.android.application` (sin `kotlin-compose`).
- `app/build.gradle.kts`: `viewBinding = true`, `minSdk = 23`, sin Compose, con las deps nuevas.
- Eliminados: `MainActivity.kt`, `ui/theme/Color.kt|Theme.kt|Type.kt`, tests de ejemplo.

### Recursos

- `res/values/colors.xml` y `res/values-night/colors.xml` (fondo, texto, tarjetas, estados).
- `res/values/themes.xml` y `res/values-night/themes.xml` (`Theme.AppDeudas`, `DialogAnimation`).
- `res/values/strings.xml` (todos los textos en español).
- `res/anim/slide_in.xml`, `slide_out.xml`, `res/drawable/dialog_bkg.xml`, `bg_badge.xml`,
  `dialog_loader.xml`; iconos copiados de AppVentas + `ic_more_vert`.
- `assets/splash_deudas.json`, `assets/schema.sql`.
- `res/layout/activity_bienvenida.xml`; `Bienvenida.kt` (2 s → `ClientesActivity`).
- `AndroidManifest.xml`: `READ_CONTACTS`, launcher `Bienvenida`, actividades
  `Principal.ClientesActivity` y `Deudas.DeudasActivity`.

### Datos (Fase 1)

- `DBHelper/DeudasDatabaseHelper.kt`: lee `assets/schema.sql`, **split de sentencias con
  balance `BEGIN`/`END` + `CASE`/`END`** (no parte por `;` a secas), `onConfigure` con FKs,
  `onUpgrade` = DROP VIEW/TABLE + recrear.
- `DBHelper/ErrorBd.kt`: excepción con mensaje de usuario.
- `DBHelper/ClienteDao.kt`, `DeudaDao.kt`, `AbonoDao.kt`, `PagoDao.kt`: consultas de PLAN §5 y
  PLAN_ABONO_GENERAL §4; traducción de excepciones a mensajes. `saldo`/`estado`/`saldoFavor`
  nunca se actualizan desde Kotlin.
- `Clases/`: `Cliente`/`ResumenCliente`, `Deuda`/`DeudaDetalle`, `Abono` (con `idPago`), `Pago`
  (con `valor`, `aplicado`, `saldoFavor`), `Reparto`/`LineaReparto`/`DeudaPendiente`,
  `FuenteFavor`/`AplicacionFavor`.
- `Utils/`: `TelefonoUtils`, `MonedaUtils`, `ContactosHelper`, `LoadingDialog`, `RepartoUtils`,
  `ResumenUtils`, `WhatsAppUtils`, `FechaUtils`, `AvisoUtils`, `RespaldoUtils`.
- Tests: `test/.../TelefonoUtilsTest.kt`, `MonedaUtilsTest.kt`, `RepartoUtilsTest.kt`,
  `ResumenUtilsTest.kt`, `FechaUtilsTest.kt` (JVM);
  `androidTest/.../DeudasDatabaseHelperTest.kt`, `PagoDaoTest.kt`, `PagoEsquemaTest.kt`,
  `RespaldoUtilsTest.kt`.

### Clientes (Fase 2)

- `Principal/ClientesActivity.kt`: lista, búsqueda, FAB → menú, permiso + `PickContact`,
  selección de número, alta manual, edición de nombre, inactivación, refresco de nombres desde
  contactos en `onResume`.
- `Principal/MenuClienteBottomSheet.kt`, `Principal/ClienteManualDialog.kt`.
- `Adapter/AdapterCliente.kt` (filtro por nombre/teléfono normalizado, "A favor: $ X"),
  `Adapter/InterfaceAction.kt`.
- Layouts: `activity_clientes.xml`, `item_cliente.xml`, `sheet_menu_cliente.xml`,
  `dialog_cliente.xml`, `dialog_nombre.xml`.

### Deudas y abonos (Fases 3–4)

- `Deudas/DeudasActivity.kt`: toolbar con nombre/teléfono, total pendiente, botón "Abono
  general", "A favor: $ X", chips Pendientes/Pagadas/Anuladas/Todas, lista, FAB, menú contextual
  por estado, confirmaciones (pagar/reabrir/anular), resultados vía `FragmentResult`.
- `Deudas/DeudaFormBottomSheet.kt`: crear/editar con validaciones; diálogo D6 de saldo a favor.
- `Deudas/AbonosBottomSheet.kt`: historial de abonos, alta y anulación (diálogo simple).
- `Deudas/PagoGeneralBottomSheet.kt`: valor, nota, reparto editable, saldo a favor.
- `Adapter/AdapterDeuda.kt`, `Adapter/AdapterAbono.kt`, `Adapter/AdapterReparto.kt`.
- Layouts: `activity_deudas.xml`, `item_deuda.xml`, `sheet_deuda_form.xml`, `sheet_abonos.xml`,
  `item_abono.xml`, `sheet_pago_general.xml`, `item_reparto.xml`.

### Docs

- `AGENTS.md` actualizado (schema en assets, plan de abono general, inactivación, toolchain).
- `PLAN_ABONO_GENERAL.md` §10 actualizado con el estado real.

### Privacidad y rendimiento (30 oct)

- `AndroidManifest.xml`: `allowBackup="false"` y se quitaron `dataExtractionRules` /
  `fullBackupContent` (borrados `res/xml/backup_rules.xml` y `data_extraction_rules.xml`).
  Así Android no sube la base a Google ni la copia entre equipos: la única salida son los
  respaldos locales y su restauración manual.
- `ClientesActivity`: se quitó el `cargarClientes()` de `onCreate` porque `onResume` ya lo
  hace (se consultaba dos veces al abrir la pantalla).

---

## 4. Lo que falta (en orden)

### 4.1 Fase 4 — abono general: menú del cliente + historial

1. `MenuClienteBottomSheet`: opción "Abono general" (solo si `saldoPendiente > 0`); al pulsarla,
   abrir `PagoGeneralBottomSheet` para ese cliente y refrescar `ClientesActivity`.
2. `AdapterAbono`: etiqueta "Pago general" (con fecha) cuando `abono.idPago != null`, sin
   depender del texto de la nota.
3. `AbonosBottomSheet.onAbonoEliminar`: si el abono tiene `idPago`, diálogo con dos opciones
   *Solo este abono* (`AbonoDao.anular`) / *Todo el pago general* (`PagoDao.anular`).
4. `confirmar_reabrir_mensaje`: avisar que si el abono era de un pago general, ese monto vuelve
   al saldo a favor del cliente.

### 4.2 Fase 5 — hoja "Pagos del cliente"

- Lista con fecha, valor, nota, "aplicado a N deudas" y botón anular (`PagoDao.listar/anular`),
  accesible desde `DeudasActivity` (o desde el menú del cliente).

### 4.3 Probar

- `.\gradlew.bat testDebugUnitTest` (JVM) — ✅ 32/32.
- `.\gradlew.bat connectedDebugAndroidTest` — ✅ 37/37 en SM-A055M (incluye `RespaldoUtilsTest`).
- **Prueba manual hecha (29 oct, con BD sembrada)**:
  - Deudas: crear, editar, abonar parcial, abono que excede el saldo (error), marcar pagada
    (abono `cierre`), reabrir, editar valor por debajo de lo abonado (error), anular, filtros.
  - Abono general: FIFO, "Se paga", reparto editado a mano, validación de monto > saldo,
    sobrante a favor, nota automática, D6 al crear deuda, usar saldo a favor en la hoja,
    registrar sin deudas (todo a favor), anular abono del pago y anular deuda con abonos del
    pago (D8).
  - Cliente: inactivar y reactivar con el mismo celular (conserva historial).
- **Pendiente manual**: confirmar el texto precargado en un chat real de WhatsApp (solo se pudo
  verificar con un número que no existe en WhatsApp) y ver ícono/Lottie del splash en pantalla.

---

## 5. Pendientes / riesgos conocidos

- Fase 4 no iniciada: el menú del cliente aún no permite abonar en general (solo
  `DeudasActivity`), y anular un abono de un pago general anula **solo ese abono** sin ofrecer
  anular todo el pago. Tampoco hay etiqueta "Pago general" en el historial ni aviso al reabrir.
- Fase 5 no iniciada: falta la hoja "Pagos del cliente".
- `helper.close()` en `onDestroy` de Activities junto con tareas del `Executors` en
  `ClientesActivity`: si una tarea de contactos termina después de destruir la Activity podría
  usar un helper cerrado. Bajo riesgo.
- `MonedaUtils.parsear` quita puntos y cambia coma por punto; el campo de valor usa teclado
  numérico entero, suficiente para COP.
- El esquema usa `REAL` para dinero; se respeta tal cual (PLAN manda).
- Las fechas se guardan en UTC (`datetime('now')`) y se convierten a local solo al mostrarlas
  (`FechaUtils`); cualquier pantalla nueva debe usar `FechaUtils` en vez de `fecha.take(10)`.
- Subir `DATABASE_VERSION` borra los datos: no hay datos de producción.
- Los respaldos viven en `filesDir/respaldos/` (privado de la app): no se ven desde un
  explorador de archivos ni salen del teléfono. Restaurar uno de otra `DATABASE_VERSION` se
  rechaza a propósito (dispararía el `onUpgrade` DROP + recreate).

---

## 6. Comandos rápidos

```powershell
# Config / build
.\gradlew.bat :app:tasks --console=plain
.\gradlew.bat assembleDebug --console=plain
.\gradlew.bat installDebug                   # requiere dispositivo/emulador
.\gradlew.bat testDebugUnitTest --console=plain
.\gradlew.bat connectedDebugAndroidTest      # requiere dispositivo/emulador
```

Usar `.\gradlew.bat`; `./gradlew` no resuelve en PowerShell.

