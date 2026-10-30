# Plan: abono general (pago sin deuda específica)

Complemento de `PLAN.md`. Describe cómo registrar un pago del cliente que no va dirigido a
una deuda concreta, sino que se reparte entre sus deudas pendientes.

Estado: **en ejecución**. Ver "Estado de ejecución" (§10) al final del documento: se actualiza en
cada interacción para poder retomar si se cierra la terminal.

## 1. El problema

Hoy un abono siempre pertenece a **una** deuda (`abono.idDeuda NOT NULL`) y el trigger
`trg_abono_no_excede` impide que supere el saldo de esa deuda. Si el cliente dice "te
consigno $300.000 y vas descontando", el usuario tiene que ir deuda por deuda calculando
cuánto le toca a cada una. Eso es lo que se quiere resolver.

## 2. Decisiones de negocio (resolver antes de codificar)

| # | Pregunta | Opciones | Recomendación |
|---|----------|----------|---------------|
| D1 | ¿Cómo se reparte el dinero entre las deudas pendientes? | **a)** Más antigua primero (FIFO) · **b)** Saldo menor primero (cierra más deudas) · **c)** Manual · **d)** Automático pero editable antes de confirmar | **d**: FIFO como sugerencia, el usuario puede ajustar montos en la previsualización |
| D2 | ¿Qué pasa si el pago supera el total pendiente? | **a)** Rechazar con mensaje · **b)** Guardar "saldo a favor" del cliente | **a** en v1. "Saldo a favor" es una funcionalidad aparte (ver §7) |
| D3 | ¿Se conserva la trazabilidad del pago original? | **a)** No, solo quedan N abonos sueltos · **b)** Sí, tabla `pago` que agrupa los abonos | **b**: sin ella no se puede mostrar "pagó $300.000 el 28/10" ni revertirlo completo |
| D4 | ¿Cómo se revierte un pago general? | **a)** Eliminar abono por abono · **b)** Eliminar el pago y caen todos sus abonos | **b**, con confirmación. Eliminar uno solo de sus abonos se permite pero avisando |
| D5 | ¿Desde dónde se registra? | Menú largo del cliente (`MenuClienteBottomSheet`) · toolbar de `DeudasActivity` · ambos | **Ambos**: es la operación que más usa quien cobra |

Decisiones tomadas:

Cerradas el 2026-10-28 aceptando las recomendaciones (el usuario dijo "ejecuta el plan"):

- D1: **d** — FIFO sugerido, editable en la previsualización.
- D2: ~~**a** — rechazar si el pago supera el total pendiente.~~ **Cambiada a b el 2026-10-29**
  (pedido del usuario): el sobrante queda como **saldo a favor** del cliente. Ver §9.
- D3: **b** — tabla `pago` que agrupa los abonos.
- D4: **b** — eliminar el pago borra todos sus abonos (con confirmación).
- D5: **ambos** — menú del cliente y `DeudasActivity`.

## 3. Modelo de datos (cambio en `schema.sql`)

`onUpgrade` es DROP + recreate y no hay datos de producción, así que el cambio es barato
ahora y caro después.

```sql
-- Pago general del cliente; sus abonos son la distribución.
CREATE TABLE pago (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    idCliente  TEXT    NOT NULL REFERENCES cliente(idCliente) ON DELETE CASCADE,
    fecha      TEXT    NOT NULL DEFAULT (datetime('now')),
    nota       TEXT
);

-- abono: columna nueva
    idPago     INTEGER REFERENCES pago(id) ON DELETE CASCADE   -- NULL = abono directo

CREATE INDEX idx_abono_pago ON abono(idPago);

-- Vista: pago con su valor derivado
CREATE VIEW vw_pago AS
SELECT
    p.id, p.idCliente, p.fecha, p.nota,
    COALESCE(SUM(a.valor), 0)   AS valor,
    COUNT(DISTINCT a.idDeuda)   AS numDeudas
FROM pago p
LEFT JOIN abono a ON a.idPago = p.id
GROUP BY p.id;

-- Si un pago se queda sin abonos (reabrir / eliminar abono suelto), se borra.
CREATE TRIGGER trg_abono_del_pago_huerfano AFTER DELETE ON abono
WHEN OLD.idPago IS NOT NULL
BEGIN
    DELETE FROM pago
    WHERE id = OLD.idPago
      AND NOT EXISTS (SELECT 1 FROM abono WHERE idPago = OLD.idPago);
END;
```

Puntos clave:

- **`pago` no guarda `valor`**: se deriva de `SUM(abono.valor)`. Así "Reabrir" (que borra el
  último abono) o eliminar un abono suelto nunca dejan un total inconsistente.
- `abono.tipo` **no cambia** (`'abono'|'cierre'`). Que venga de un pago general se sabe por
  `idPago IS NOT NULL`. No mezclar "origen" con "tipo".
- Los triggers actuales (`trg_abono_no_excede`, `trg_abono_ins`, `trg_abono_del`) siguen
  funcionando sin tocar: cada abono individual cumple `valor ≤ saldo`, y el recálculo de
  `saldo`/`estado` sigue siendo por deuda.
- `vw_resumen_cliente` no cambia: el saldo pendiente ya sale de las deudas.
- Subir `DATABASE_VERSION` en `DeudasDatabaseHelper`.

## 4. Lógica (DAO)

Nuevo `PagoDao` en `DBHelper/`:

```
previsualizar(idCliente, valor): Reparto
    deudas pendientes ORDER BY fechaCreacion ASC, id ASC
    recorre: toma min(valorRestante, saldo) por deuda hasta agotar valor
    devuelve lista [(deuda, montoAsignado)] + sobrante (si > 0 → D2)

registrar(idCliente, reparto, nota): Long
    beginTransaction
      INSERT pago
      por cada (deuda, monto > 0): INSERT abono(idDeuda, monto, 'abono', nota, idPago)
    setTransactionSuccessful / endTransaction
    → SQLiteConstraintException se traduce a ErrorBd como hoy

listar(idCliente): List<Pago>          -- desde vw_pago
eliminar(idPago)                       -- cascade borra abonos, triggers recalculan
```

Validaciones **antes** de la transacción (en Kotlin, para dar mensajes claros):

- `valor > 0`
- `valor ≤ totalPendiente(idCliente)` (D2-a)
- en el reparto editado: cada monto `≤ saldo` de su deuda y la suma `== valor`

La regla "nunca escribir `saldo`/`estado` desde Kotlin" se respeta: solo se insertan abonos.

Clases nuevas en `Clases/`:

- `Pago(id, idCliente, fecha, nota, valor, numDeudas)`
- `Reparto(lineas: List<LineaReparto>, sobrante: Long)`,
  `LineaReparto(deuda: DeudaDetalle, monto: Long)`
- `Abono` gana `idPago: Long?`; `AbonoDao.leerAbono` lo lee.

## 5. Interfaz

**`PagoGeneralBottomSheet`** (nuevo, en `Deudas/`), un solo flujo en dos pasos dentro de la
misma hoja:

```
┌──────────────────────────────────────┐
│ Abono general                        │
│ Pendiente total: $ 450.000           │
│ ┌──────────────┐ ┌─────────────┐     │
│ │ $ Valor      │ │ Nota        │     │
│ └──────────────┘ └─────────────┘     │
│                                      │
│ Se aplicará a:            (editable) │
│ ▸ Mercado 12/09    150.000 / 150.000  ✓ se paga
│ ▸ Préstamo 20/09   150.000 / 300.000 │
│ Sobrante: $ 0                        │
│                                      │
│           [ Cancelar ] [ Registrar ] │
└──────────────────────────────────────┘
```

- Al escribir el valor se recalcula la lista (FIFO). El usuario puede tocar un monto y
  cambiarlo; la hoja re-equilibra el resto o muestra "Faltan por asignar $X" y bloquea
  *Registrar*.
- Chip/ícono en los ítems de la lista que quedarían en `pagada`.
- Al registrar: `Toast` "Pago registrado: $300.000 en 2 deudas", `setFragmentResult` y
  recargar la pantalla que lo abrió.
- Reutiliza `MonedaUtils`, `ErrorBd`, estilos de `sheet_abonos.xml`.

**Puntos de entrada** (D5):

- `MenuClienteBottomSheet`: opción "Abono general" (solo si `saldoPendiente > 0`).
- `DeudasActivity`: ícono en la toolbar o botón bajo el total. No un segundo FAB.

**Historial**:

- En "Ver abonos" de una deuda, los abonos con `idPago` muestran una etiqueta "Pago general"
  con la fecha. Al intentar eliminar uno: diálogo con dos opciones, *Solo este abono* /
  *Todo el pago general*.
- Nueva lista "Pagos" del cliente (accesible desde `DeudasActivity`, puede ser otra hoja):
  fecha, valor, nota, "aplicado a N deudas", botón eliminar. Es lo que se le muestra al
  cliente cuando pregunta "¿cuánto te he pagado?".

## 6. Fases de implementación

| Fase | Entregable | Prueba |
|------|-----------|--------|
| 0 | Cerrar D1–D5 | — |
| 1 | `schema.sql`: tabla `pago`, columna `abono.idPago`, índice, vista, trigger huérfano. Bump `DATABASE_VERSION` | Robolectric: insertar pago con 2 abonos, borrar pago → saldos restaurados; borrar todos los abonos → pago desaparece |
| 2 | `Clases/Pago.kt`, `Clases/Reparto.kt`, `PagoDao` con `previsualizar/registrar/listar/eliminar`. `Abono.idPago` en `AbonoDao.leerAbono` | JVM puro para `previsualizar` (lógica de reparto, sin SQLite) + Robolectric para `registrar` con rollback cuando un monto excede |
| 3 | `PagoGeneralBottomSheet` + layout + strings. Entrada desde `DeudasActivity` | Manual en dispositivo |
| 4 | Entrada desde `MenuClienteBottomSheet`. Etiqueta en `AdapterAbono` y diálogo de eliminación doble | Manual |
| 5 | Hoja "Pagos del cliente" con listado y eliminación | Manual |

Fases 1–3 ya dan la funcionalidad completa; 4 y 5 son pulido y se pueden posponer.

## 7. Lo que queda fuera (y por qué)

- ~~**Saldo a favor** (D2-b)~~: **ya no queda fuera**; desde el 2026-10-29 entra en el
  alcance. Diseño en §9.
- **Pago parcial a deudas específicas elegidas por el usuario** (marcar 2 de 5 deudas y
  repartir solo entre esas): se cubre con D1-d editando a $0 las que no quiera, sin UI extra.
- **Reparto proporcional**: raro en cobros informales; FIFO es lo que la gente espera
  ("primero lo más viejo").

## 8. Riesgos

- **Redondeo**: `valor` es `REAL` y los triggers usan tolerancia `0.0001`. Como COP no tiene
  decimales, `previsualizar` debe trabajar con enteros (`Long`) y convertir al final; evita
  que la suma del reparto quede en 299.999,99.
- **Consistencia transaccional**: si falla el tercer `INSERT abono`, el `pago` y los dos
  abonos anteriores deben deshacerse. De ahí la transacción explícita en `registrar`; no
  delegar a inserciones sueltas.
- **"Reabrir" sobre una deuda pagada por un pago general**: borra solo el abono de esa deuda;
  el pago queda con un valor menor. Es coherente con "pago.valor derivado", pero conviene
  avisarlo en el diálogo de reabrir.

## 9. Ampliación (2026-10-29): nota del abono general y saldo a favor

Pedido del usuario: *"cuando sea abono general necesito que en el abono nota quede el msg
diciendo de cuánto fue el abono general, porque pueden abonar 20k general pero la deuda solo
necesitaba 300 y lo demás se reparte en las demás deudas; también se debe manejar el saldo a
favor"*. Esta sección **reemplaza** lo que contradiga en §2 (D2), §3, §4, §5 y §7.

### 9.1 Nota automática en cada abono del pago general

Ejemplo: el cliente da **$ 20.000** de abono general. La deuda A solo necesitaba $ 300, la B
$ 5.000 y la C $ 10.000. Se aplican $ 300 + $ 5.000 + $ 10.000 = $ 15.300 y sobran $ 4.700.

Cada abono generado lleva en `abono.nota` el contexto del pago completo:

```
Abono general de $ 20.000 · aplicado aquí $ 300 · repartido en 3 deudas · a favor $ 4.700
Abono general de $ 20.000 · aplicado aquí $ 300 · repartido en 3 deudas · a favor $ 4.700 · <nota del usuario>
```

- El tramo "a favor $ X" solo aparece si hubo sobrante; la nota del usuario va al final si la
  escribió. El prefijo fijo `Abono general de` permite reconocerlas.
- Se arma **en Kotlin al registrar** (`PagoDao.registrar`, formato con `MonedaUtils`), dentro de
  la misma transacción. Es una **foto del momento**: si después se anula un abono del pago, la
  nota no cambia (el monto recibido sigue siendo $ 20.000, que es lo que se quiere recordar).
- `pago.nota` guarda solo la nota del usuario (sin el texto automático).
- En "Ver abonos" la nota se muestra completa; la etiqueta "Pago general" (fase 4) se sigue
  basando en `idPago IS NOT NULL`, no en el texto.

### 9.2 Saldo a favor

**Modelo**: el saldo a favor sale del dinero recibido en pagos generales que no está aplicado a
ninguna deuda. Para eso el pago **sí guarda el valor recibido** (cambia el punto clave de §3
"pago no guarda valor"):

```sql
-- pago: columna nueva
    valor  REAL NOT NULL CHECK (valor > 0)    -- lo que entregó el cliente

-- vw_pago
    p.valor                                         AS valor,       -- recibido
    COALESCE(SUM(a.valor), 0)                       AS aplicado,    -- abonos activos
    p.valor - COALESCE(SUM(a.valor), 0)             AS saldoFavor   -- 0 si el pago está anulado

-- vw_resumen_cliente: columna nueva
    saldoFavor = SUM(vw_pago.saldoFavor) de los pagos activos del cliente
```

- **Nunca se escribe `saldoFavor` desde Kotlin**: es derivado, igual que `deuda.saldo`.
- Nuevo trigger `trg_abono_no_excede_pago` (BEFORE INSERT ON abono WHEN `idPago` NOT NULL):
  `RAISE(ABORT, 'El abono supera el valor del pago')` si lo aplicado del pago pasaría de
  `pago.valor`.
- **Un pago sin abonos activos ya no se anula solo**: se elimina `trg_abono_pago_huerfano`. Ese
  pago queda con todo su valor como saldo a favor (el cliente sí entregó la plata).
- Se permite registrar un pago general **aunque el cliente no tenga deudas pendientes**: todo
  queda a favor.

**Efecto en las operaciones existentes:**

| Operación | Qué pasa con el dinero |
|---|---|
| Registrar pago de $ 20.000 con $ 15.300 pendientes | 3 abonos por $ 15.300; $ 4.700 a favor |
| Anular **un** abono del pago (o reabrir esa deuda) | Su monto vuelve al saldo a favor del cliente |
| Anular **todo** el pago | Se anulan sus abonos y su saldo a favor desaparece (no se recibió) |
| Anular la deuda que tenía un abono del pago | Se anulan sus abonos con `idPago` y ese monto vuelve a favor (D8) |

### 9.3 Usar el saldo a favor

- **Al crear una deuda** a un cliente con saldo a favor > 0, se aplica desde ahí (D6).
- **En la hoja de abono general**, el saldo a favor actual se puede sumar a lo que se reparte
  ("Usar $ 4.700 a favor").
- Aplicar saldo a favor = **insertar abonos** con `idPago` = el pago que tenía sobrante, del más
  antiguo al más nuevo (FIFO por `pago.fecha`). No se crea un pago nuevo. Nota del abono:
  `Saldo a favor del abono general de $ 20.000 (28/10/2026)`.
- `PagoDao` gana `saldoFavor(idCliente): Long` y `aplicarSaldoFavor(idCliente, idDeuda, monto)`.

### 9.4 Decisiones nuevas

| # | Pregunta | Opciones | Recomendación |
|---|----------|----------|---------------|
| D6 | ¿Al crear una deuda se aplica sola el saldo a favor? | **a)** Automático · **b)** Preguntar con diálogo "Tiene $ X a favor, ¿aplicarlo?" · **c)** Solo manual | **b**, con "Sí" por defecto |
| D7 | ¿Se puede devolver el saldo a favor en efectivo? | **a)** No en v1 (para corregir: anular el pago y registrarlo bien) · **b)** Sí, con registro `devolucion` | **a** en v1 |
| D8 | Al anular una deuda con abonos de un pago general, ¿qué pasa con ese dinero? | **a)** Se anulan esos abonos y el dinero vuelve a favor · **b)** Queda en la deuda anulada | **a**: no se pierde plata del cliente |

Cerradas el 2026-10-29 por el usuario, todas con la recomendación:

- D6: **b** — al crear la deuda se pregunta "Tiene $ X a favor, ¿aplicarlo?" (Sí por defecto).
- D7: **a** — no hay devolución en efectivo en v1.
- D8: **a** — al anular la deuda se anulan sus abonos de pagos generales y el dinero vuelve a
  favor. (Los abonos directos de esa deuda no cambian: siguen sin contar, como hoy.)

### 9.5 Cambios en lo ya hecho (fases 1–2)

- `schema.sql`: `pago.valor`, `vw_pago` con `aplicado`/`saldoFavor`, `vw_resumen_cliente` con
  `saldoFavor`, `trg_abono_no_excede_pago`, quitar `trg_abono_pago_huerfano`. Subir
  `DATABASE_VERSION` a 4.
- `RepartoUtils.fifo`: el sobrante deja de ser error y se devuelve como `saldoFavor`.
  `RepartoUtils.validar`: la suma de líneas debe ser **≤** valor (antes ==); cada línea ≤ saldo.
- `PagoDao.registrar(idCliente, valorRecibido, lineas, nota)`: inserta el pago con `valor`, los
  abonos con la nota de §9.1; quita la validación "supera el total pendiente".
- `Pago` gana `aplicado` y `saldoFavor`; `ResumenCliente` gana `saldoFavor`.
- UI (fase 3): la hoja muestra "Saldo a favor quedará: $ X" en lugar de bloquear por sobrante;
  la lista de clientes y `DeudasActivity` muestran el saldo a favor cuando es > 0.
- Tests: actualizar `RepartoUtilsTest`, `PagoDaoTest`, `PagoEsquemaTest` (ya no hay pago
  huérfano anulado); nuevos: nota automática, sobrante a favor, aplicar a favor a deuda nueva,
  tope por `pago.valor`.

## 10. Estado de ejecución

> Se actualiza al final de cada interacción. Para retomar: leer esta sección, ir a la primera
> fase que no esté en ✅ y continuar.

Última actualización: 2026-10-29

> Fuera de este plan (hecho el 2026-10-29): **respaldos locales** de la base dentro del
> almacenamiento privado de la app (sin nube ni permisos). `Utils/RespaldoUtils.kt`,
> `Utils/RespaldoScheduler.kt` + `RespaldoWorker` (WorkManager: cada 5 días a las 2:00 a. m.
> hora del celular), `Principal/RespaldosBottomSheet.kt`, menú "Respaldos" en la toolbar de
> clientes. Se conservan los últimos 12; restaurar exige la misma `DATABASE_VERSION`.
> Ver `status.md` (bitácora).

| Fase | Estado | Notas |
|------|--------|-------|
| 0 Decisiones | ✅ | D1–D5 cerradas con las recomendaciones (§2). D2 cambió a **b** (saldo a favor). D6–D8 cerradas con las recomendaciones (§9.4) |
| 1 Esquema | ✅ | `schema.sql` con `pago`, `abono.idPago`, índices, `vw_pago`, trigger huérfano. `DATABASE_VERSION = 2`. 13/13 tests instrumentados OK en SM-A055M |
| 2 DAO + clases | ✅ | `Pago`, `Reparto`, `RepartoUtils`, `PagoDao`. Tests: `RepartoUtilsTest` (JVM) y `PagoDaoTest` (instrumentado); 20/20 instrumentados OK |
| 2b Nota automática + saldo a favor | ✅ | Esquema v4, `RepartoUtils`, `PagoDao`, diálogo D6. 31/31 instrumentados OK en SM-A055M (2026-10-29) |
| 3 Hoja de pago general | ✅ | `PagoGeneralBottomSheet`, botón en `DeudasActivity`, saldo a favor visible. 34/34 instrumentados OK. Probada a mano (29 oct): FIFO, reparto editado, sobrante a favor, D6, usar saldo a favor, anular abono y D8 |
| 4 Entrada desde menú cliente + historial | ⬜ | |
| 5 Lista de pagos | ⬜ | |

Hecho en la fase 1:
- `assets/schema.sql`: tabla `pago`, columna `abono.idPago` (FK con cascade), `idx_abono_pago`,
  `idx_pago_cliente`, `vw_pago`, `trg_abono_del_pago_huerfano`.
- `DeudasDatabaseHelper`: versión 2; `onUpgrade` también elimina `vw_pago` y `pago`.
- `Clases/Abono.kt`: `idPago: Long? = null`; `AbonoDao.leerAbono` lo lee.
- `androidTest/.../PagoEsquemaTest.kt`: 5 tests (vista, eliminar pago restaura saldos, pago
  huérfano se borra, borrar un abono deja el resto, cascade desde cliente).

Hecho en la fase 2:
- `Clases/Pago.kt`, `Clases/Reparto.kt` (`DeudaPendiente`, `LineaReparto`, `Reparto`).
- `Utils/RepartoUtils.kt`: `fifo(deudas, valor)` y `validar(lineas, valor)` en `Long`.
- `DBHelper/PagoDao.kt`: `totalPendiente`, `previsualizar`, `registrar` (transacción, solo
  inserta abonos), `listar`, `obtener`, `eliminar`.
- API para la UI: `registrar(idCliente, lineas, nota)` recibe las `LineaReparto` ya editadas;
  `RepartoUtils.validar` devuelve el mensaje de error o null.

Cambio transversal (2026-10-29): **inactivación en vez de eliminación**. `abono` y `pago`
tienen `estado` (`activo`/`anulado`) y `fechaAnulacion`; `DATABASE_VERSION = 3`. API:
`ClienteDao.inactivar/reactivar`, `AbonoDao.anular`, `PagoDao.anular` (antes `eliminar`);
`reabrir` anula el último abono activo. `PagoDao.listar` solo devuelve pagos activos.
D4 queda así: "eliminar el pago" = anularlo, y sus abonos se anulan solos. Tests
ejecutados en la fase 2b (31/31 OK).

Hecho en la fase 2b (2026-10-29):
- `schema.sql` (v4): `pago.valor` (recibido); `vw_pago` con `valor`, `aplicado`, `saldoFavor`,
  `numDeudas`; `vw_resumen_cliente` con `saldoFavor` (ahora va después de `vw_pago`);
  `trg_abono_no_excede_pago` (tope por `pago.valor` y no abonar desde pago anulado);
  `trg_deuda_anular_libera_pagos` (D8); se quitó `trg_abono_pago_huerfano`.
- `RepartoUtils`: `Reparto.saldoFavor` (antes `sobrante`); `validar` acepta asignar menos que el
  valor; `repartirFavor` (FIFO por pago); `notaAbonoGeneral`, `notaSaldoFavor`, `fechaCorta`.
- `Clases`: `Pago(valor, aplicado, saldoFavor, …)`, `FuenteFavor`, `AplicacionFavor`,
  `ResumenCliente.saldoFavor`.
- `PagoDao`: `registrar(idCliente, valorRecibido, lineas, nota)` (sin tope por pendiente; nota
  automática en cada abono; `pago.nota` = solo la del usuario), `saldoFavor`,
  `aplicarSaldoFavor(idCliente, lineas)`, `aplicarSaldoFavorADeuda(idCliente, idDeuda)`.
- `DeudaFormBottomSheet`: al crear una deuda con saldo a favor > 0, diálogo D6 (Aplicar / Ahora
  no). Strings `saldo_favor_*`; `confirmar_anular_mensaje` avisa que lo de pagos generales vuelve
  a favor.
- Tests: `RepartoUtilsTest` (+3), `PagoDaoTest` (+9), `PagoEsquemaTest` (+2, ajustados).

Hecho en la fase 3 (2026-10-29):
- `Deudas/PagoGeneralBottomSheet.kt` + `sheet_pago_general.xml`, `Adapter/AdapterReparto.kt` +
  `item_reparto.xml`. Valor recibido y nota; al escribir el valor se reparte (más antigua
  primero) y cada monto se edita a mano; resumen "Asignado X · Quedará a favor Y"; error en rojo
  y "Registrar" bloqueado si el reparto no es válido; "Se paga" en las deudas que se saldan;
  casilla "Usar el saldo a favor ($ X)" (suma el saldo existente a lo repartible).
- `PagoDao.registrar(..., usarSaldoFavor)`: consume primero el saldo a favor (pago más antiguo),
  el resto sale del dinero nuevo; solo crea pago nuevo si `valorRecibido > 0` (devuelve 0 si no).
- `DeudasActivity`: botón "Abono general" junto al total y "A favor: $ X" bajo el total.
  `AdapterCliente`: "N pendientes · A favor: $ X" en la lista de clientes.
- Tests: `PagoDaoTest` +3 (usar saldo a favor, solo con saldo a favor, sin valor ni montos).

Decisiones tomadas sin preguntar (ajustables): el botón vive junto al total (no en la toolbar);
el reparto se recalcula entero cada vez que cambia el valor o la casilla (se pierden ajustes
manuales); se permite registrar con deudas en cero (todo queda a favor).

Siguiente paso, fase 4: opción "Abono general" en `MenuClienteBottomSheet`, etiqueta "Pago
general" en `AdapterAbono`, diálogo doble al anular un abono de un pago ("Solo este abono" /
"Todo el pago") y aviso en "Reabrir". Antes conviene probar la fase 3 a mano en el celular.

Notas para retomar:
- No hay Robolectric: los tests de triggers van en `androidTest/` (hay un dispositivo
  conectado, ver `adb devices`); la lógica pura de reparto va en `test/` (JVM).
- Antes de dar una fase por ✅ debe compilar con `.\gradlew.bat assembleDebug` y pasar
  `.\gradlew.bat connectedDebugAndroidTest`.
- Prueba manual del 29 oct: los mensajes de la app ahora son `Snackbar` (`AvisoUtils`), no
  `Toast`, y las fechas se muestran locales con `FechaUtils` (la BD sigue en UTC).
