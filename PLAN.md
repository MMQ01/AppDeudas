# PLAN — AppDeudas (Android / Kotlin)

App 100 % local para llevar el control de deudas por cliente. Los clientes salen de los
contactos del celular o se crean a mano: el **id del cliente es su número de celular**.
Sin login.

Referencia de arquitectura y diseño: `C:\Users\Desarrollo\Documents\Apoyo\AppVentas`.

> Versión 2 (25 oct 2026). Incorpora las decisiones del formulario; ver sección 8.

---

## 1. Stack (mismo que AppVentas, sin red)

| Elemento | Valor |
|---|---|
| Lenguaje | Kotlin 1.9.0 |
| AGP / Gradle | 8.6.0 / 8.7 |
| compileSdk / targetSdk / minSdk | 36 / 36 / 23 |
| UI | Views XML + **ViewBinding**, Material 3 (`Theme.Material3.DayNight.NoActionBar`) |
| Persistencia | **SQLiteOpenHelper** (igual que `VentasDatabaseHelper`), sin Room |
| Listas | RecyclerView + Adapter con filtro por texto normalizado (patrón `AdapterCliente`) |
| Splash | Lottie (`Bienvenida`, 1–2 s) |
| Catálogo de versiones | `gradle/libs.versions.toml` |
| Módulo | Único `:app`, package `com.mmq01.appdeudas` |

Se quita respecto a AppVentas: Retrofit, Gson, `UnsafeOkHttpClient`, `NetworkUtils`,
`SessionManager`, `LoginActivity`, `network_security_config`, `usesCleartextTraffic`.
Se conserva: Lottie, `LoadingDialog`, animaciones `slide_in/out`, `dialog_bkg`.

Permisos: `READ_CONTACTS` (runtime). Se pide al elegir "Desde contactos" y, si está
concedido, se usa en cada apertura para refrescar nombres.

---

## 2. Estructura de paquetes

```
com.mmq01.appdeudas/
├── Bienvenida.kt                     # splash Lottie → ClientesActivity
├── Principal/
│   ├── ClientesActivity.kt           # pantalla 1: lista de clientes + FAB
│   └── ClienteManualDialog.kt        # alta manual: nombre + celular
├── Deudas/
│   ├── DeudasActivity.kt             # pantalla 2: deudas de un cliente + FAB
│   ├── DeudaFormBottomSheet.kt       # crear / editar deuda
│   └── AbonosBottomSheet.kt          # pantalla 3: abonos de una deuda + agregar abono
├── Adapter/
│   ├── AdapterCliente.kt
│   ├── AdapterDeuda.kt
│   ├── AdapterAbono.kt
│   └── InterfaceAction.kt            # OnClienteClick, OnDeudaAccion, OnAbonoEliminar
├── Clases/
│   ├── Cliente.kt                    # data class + ResumenCliente (vw_resumen_cliente)
│   ├── Deuda.kt                      # data class + DeudaDetalle (vw_deuda_detalle)
│   └── Abono.kt
├── DBHelper/
│   ├── DeudasDatabaseHelper.kt       # onConfigure FK, onCreate ejecuta assets/schema.sql
│   ├── ClienteDao.kt
│   ├── DeudaDao.kt
│   └── AbonoDao.kt
└── Utils/
    ├── ContactosHelper.kt            # leer contacto elegido; buscar nombre por número
    ├── TelefonoUtils.kt              # normalizar celular → idCliente
    ├── MonedaUtils.kt                # formato COP sin decimales ($ 1.250.000)
    └── LoadingDialog.kt
```

---

## 3. Navegación y pantallas

```
Bienvenida (splash Lottie 1–2 s)
   └─> ClientesActivity
          ├─ onResume: recarga lista; si hay permiso de contactos, refresca nombres
          │     de clientes con contactoUri (PhoneLookup por idCliente)
          ├─ SearchView (filtra por nombre / teléfono)
          ├─ RecyclerView: card con icono, nombre, teléfono,
          │     "N pendientes" y TOTAL PENDIENTE (saldoPendiente)
          ├─ Empty state: "Aún no tienes clientes, toca + para agregar"
          ├─ FAB (+) → menú (BottomSheet) con 2 opciones:
          │     ├─ "Desde contactos" → pedir READ_CONTACTS → PickContact
          │     │     → leer nombre + números del contacto
          │     │     → varios números: AlertDialog para elegir
          │     │     → sin número: ClienteManualDialog con el nombre precargado
          │     │     → normalizar → si ya existe: Toast + abrir; si no: INSERT
          │     └─ "Manual" → ClienteManualDialog (nombre*, celular*)
          │           → normalizar → validar 10 dígitos → INSERT (origen='manual')
          ├─ long-press en card → "Editar nombre" / "Eliminar cliente" (confirmar; cascade)
          └─ tap en card ──> DeudasActivity(idCliente)
                               ├─ Toolbar: nombre + teléfono, atrás
                               ├─ Header: TOTAL PENDIENTE del cliente
                               ├─ Chips: Pendientes | Pagadas | Anuladas | Todas
                               ├─ RecyclerView de deudas (item_deuda):
                               │     nombre, descripción, valor, saldo,
                               │     barra de progreso abonado/valor,
                               │     badge estado (+ fechaPago si pagada)
                               │     menú ⋮ por ítem (según estado):
                               │       pendiente: Abonar · Marcar pagada · Editar · Anular
                               │       pagada:    Ver abonos · Reabrir · Editar · Anular
                               │       anulada:   Ver abonos (solo lectura)
                               ├─ Empty state por filtro
                               ├─ FAB (+) → DeudaFormBottomSheet (crear)
                               │     Nombre*, Descripción, Valor* (numérico) → INSERT saldo=valor
                               ├─ Editar → DeudaFormBottomSheet (editar)
                               │     UPDATE nombre/descripcion/valor; trigger recalcula saldo/estado
                               │     (bajar el valor por debajo de lo abonado da error visible)
                               ├─ Marcar pagada → confirmar → INSERT abono(valor=saldo, tipo='cierre')
                               ├─ Reabrir → confirmar → DELETE último abono (trigger → pendiente)
                               ├─ Anular → confirmar → UPDATE estado='anulada', fechaAnulacion
                               └─ Abonar / Ver abonos ──> AbonosBottomSheet(idDeuda)
                                      ├─ Header: valor · abonado · saldo
                                      ├─ Lista de abonos (fecha, valor, nota, badge "cierre")
                                      │     swipe/ícono eliminar abono (confirmar) → trigger recalcula
                                      └─ Form: Valor* (≤ saldo) + Nota → INSERT abono
                                            si saldo queda en 0 → estado pagada automático
```

Reglas de UI:
- Formularios en **BottomSheetDialogFragment** (estilo `bottomsheet_layout.xml` de AppVentas).
- Cards con el patrón `item_cliente.xml` (CardView radius 8dp, fondo `bg_card`, texto blanco).
- Deudas anuladas: ocultas por defecto, visibles con el chip "Anuladas", sin acciones de edición.
- Al volver de `DeudasActivity` o cerrar `AbonosBottomSheet` se recargan lista y totales.
- Valores con `MonedaUtils`: pesos colombianos, sin decimales, separador de miles con punto.
- Los totales (header y cards) nunca incluyen deudas anuladas.

---

## 4. Base de datos (`schema.sql`)

### Modelo

- `cliente(idCliente TEXT PK, nombre, telefonoOriginal, contactoUri, origen, estado, fechas)`
- `deuda(id, idCliente FK, nombre, descripcion, valor, saldo, estado, fechaPago, fechaAnulacion, fechas)`
- `abono(id, idDeuda FK, valor, tipo['abono'|'cierre'], fecha, nota)`
- Vistas: `vw_resumen_cliente` (lista principal) y `vw_deuda_detalle` (deuda + totalAbonado).

### Reglas de negocio implementadas en SQLite (triggers)

| Evento | Efecto |
|---|---|
| INSERT abono | Rechaza si deuda anulada o si valor > saldo. Recalcula `saldo = valor − Σabonos`; si llega a 0 → `estado='pagada'`, `fechaPago=now`. |
| DELETE abono | Recalcula saldo; si saldo > 0 → `estado='pendiente'`, `fechaPago=NULL` (así funciona "Reabrir"). |
| UPDATE deuda.valor | Rechaza si valor < Σabonos. Recalcula saldo y estado (subir el valor reabre; bajarlo hasta Σabonos la cierra). |
| UPDATE cliente / deuda | Mantiene `fechaModificacion`. |

`saldo` y `estado` nunca se escriben desde Kotlin salvo para `anulada`. Esto evita
inconsistencias entre pantallas.

### Cambios respecto a la versión 1 del esquema

| v1 | v2 | Motivo (respuesta del formulario) |
|---|---|---|
| `recaudo` + `recaudo_deuda` (puente, reparto entre deudas) | `abono(idDeuda)` directo | Se eligió "cada deuda acepta varios pagos" y valor libre; el reparto entre deudas ya no aplica. |
| saldo/estado manuales | derivados por triggers | Abonos desde fase 1 obligan a una sola fuente de verdad. |
| — | `cliente.contactoUri`, `cliente.origen` | Alta manual + refrescar nombre desde contactos. |
| — | `deuda.fechaAnulacion`, `abono.tipo` | Eliminar = anular; distinguir el abono de cierre de "Marcar pagada". |
| CHECK `saldo <= valor` | solo `saldo >= 0` | El trigger garantiza la relación; el CHECK estático chocaba con el orden de las actualizaciones. |

### Carga en la app
`DeudasDatabaseHelper.onCreate` lee `assets/schema.sql` y ejecuta sentencia por sentencia.
El split no puede ser por `;` simple por los triggers: se parte por `;` solo cuando el
acumulado tiene balanceado `BEGIN`/`END` (o se usa un separador `--@@` entre sentencias).
`onConfigure` → `db.setForeignKeyConstraintsEnabled(true)`.
`onUpgrade` → DROP + onCreate mientras no haya datos de producción.

---

## 5. Consultas clave (DAO)

```sql
-- Lista principal
SELECT * FROM vw_resumen_cliente WHERE estado = 'activo' ORDER BY nombre;

-- Deudas de un cliente por filtro
SELECT * FROM vw_deuda_detalle WHERE idCliente = ? AND estado = ? ORDER BY fechaCreacion DESC;
SELECT * FROM vw_deuda_detalle WHERE idCliente = ? ORDER BY fechaCreacion DESC;   -- Todas

-- Total pendiente del cliente
SELECT COALESCE(SUM(saldo),0) FROM deuda WHERE idCliente = ? AND estado = 'pendiente';

-- Abonar
INSERT INTO abono(idDeuda, valor, tipo, nota) VALUES (?, ?, 'abono', ?);

-- Marcar pagada
INSERT INTO abono(idDeuda, valor, tipo, nota)
SELECT id, saldo, 'cierre', 'Marcada como pagada' FROM deuda WHERE id = ? AND saldo > 0;

-- Reabrir (borra el último abono)
DELETE FROM abono WHERE id = (SELECT id FROM abono WHERE idDeuda = ? ORDER BY fecha DESC, id DESC LIMIT 1);

-- Anular
UPDATE deuda SET estado='anulada', fechaAnulacion=datetime('now') WHERE id = ?;

-- Editar
UPDATE deuda SET nombre=?, descripcion=?, valor=? WHERE id = ? AND estado != 'anulada';

-- Refrescar nombre desde contactos
UPDATE cliente SET nombre = ? WHERE idCliente = ? AND nombre != ?;

-- Abonos de una deuda
SELECT * FROM abono WHERE idDeuda = ? ORDER BY fecha DESC, id DESC;
```

Los `RAISE(ABORT, ...)` de los triggers llegan a Kotlin como `SQLiteConstraintException`;
el DAO los traduce a mensajes para el usuario (Snackbar).

---

## 6. Fases de implementación

### Fase 0 — Proyecto base (½ día)
1. Crear proyecto `AppDeudas`, package `com.mmq01.appdeudas`, copiar `libs.versions.toml`
   de AppVentas quitando Retrofit/Gson. ViewBinding on.
2. Copiar `themes.xml`, `colors.xml`, `anim/`, `dialog_bkg`, `LoadingDialog`, Lottie.
3. `AndroidManifest`: `READ_CONTACTS`, sin INTERNET. `Bienvenida` como LAUNCHER.

### Fase 1 — Datos (1 día)
4. `assets/schema.sql` + `DeudasDatabaseHelper` (onConfigure FK, split con balance BEGIN/END).
5. Data classes `Cliente`, `ResumenCliente`, `Deuda`, `DeudaDetalle`, `Abono`.
6. `ClienteDao`, `DeudaDao`, `AbonoDao` con las consultas de la sección 5 y traducción de
   `SQLiteConstraintException`.
7. `TelefonoUtils.normalizar()` (quitar espacios, guiones, paréntesis, `+`; 10 dígitos que
   empiezan en 3 → anteponer `57`). Tests JUnit del helper y de los triggers
   (Robolectric o instrumentado) para abonar / marcar pagada / reabrir / editar valor.

### Fase 2 — Pantalla clientes (1 día)
8. `ClientesActivity` + layout (SearchView, RecyclerView, FAB, empty state).
9. `AdapterCliente` + `item_cliente.xml` (nombre, teléfono, pendientes, total).
10. Menú del FAB: "Desde contactos" / "Manual".
11. `ContactosHelper.leerContacto(uri)`: nombre + lista de números; `ClienteManualDialog`.
12. `ContactosHelper.buscarNombre(numero)` + refresco en `onResume` (en hilo de fondo).
13. Long-press: editar nombre / eliminar cliente.

### Fase 3 — Pantalla deudas (1½ días)
14. `DeudasActivity` + layout (toolbar, header total, chips, RecyclerView, FAB).
15. `AdapterDeuda` + `item_deuda.xml` con barra de progreso y menú ⋮ contextual por estado.
16. `DeudaFormBottomSheet` (crear / editar) con validaciones: nombre no vacío, valor > 0.
17. Acciones: Marcar pagada, Reabrir, Anular (todas con confirmación).

### Fase 4 — Abonos (1 día)
18. `AbonosBottomSheet` + `AdapterAbono` + `item_abono.xml`.
19. Form de abono con validación valor ≤ saldo; eliminar abono con confirmación.
20. Refresco de header y lista de deudas al cerrar el sheet.

### Fase 5 — Pulido (½ día)
21. Formato moneda, estados vacíos, modo oscuro (`values-night`), icono y Lottie de la app.
22. `AGENTS.md` del proyecto, siguiendo el formato de AppVentas.

**Fuera de alcance:** exportar / compartir estado de cuenta, recordatorios, respaldo en la nube,
fecha de vencimiento de deudas.

---

## 7. Supuestos que quedan

- Un abono pertenece a una sola deuda. Si el cliente paga un monto que cubre varias deudas,
  el usuario lo registra como varios abonos (uno por deuda).
- "Reabrir" elimina el último abono. Para reabrir sin perder abonos reales se sube el valor
  de la deuda desde "Editar".
- El refresco de nombres solo aplica a clientes con `origen='contacto'`; los manuales se editan
  dentro de la app.
- Eliminar cliente sí es borrado real (cascade), porque el cliente no tiene estado "anulado"
  en el flujo; si se quiere historial se cambia a `estado='inactivo'`.

---

## 8. Decisiones tomadas (formulario del 25 oct 2026)

| # | Pregunta | Decisión |
|---|---|---|
| 1 | Recaudos / abonos | **Implementar abonos parciales desde la fase 1.** Valor libre (no hay regla de valor fijo). |
| 2 | Contacto sin número | **Permitir escribir el número a mano** (nombre del contacto precargado). |
| 3 | Alta manual de cliente | **Sí**, opción "Manual" en el menú del FAB. |
| 4 | Deuda pagada | **Permitir editar y reabrir.** |
| 5 | Eliminar deuda | **Marcar como anulada**, conservar historial. |
| 6 | Moneda | **Pesos colombianos sin decimales.** |
| 7 | Pantalla de inicio | **Splash con Lottie**, como AppVentas. |
| 8 | Nombre vs contactos | **Refrescar el nombre al abrir la app.** |

Sin notas ni comentarios adicionales.
