# Plan: compartir deudas pendientes por WhatsApp

Complemento de `PLAN.md`. Al mantener presionado un cliente en `ClientesActivity` se agrega al
menú contextual la opción **"Compartir por WhatsApp"**: arma un resumen legible de sus deudas
pendientes y abre WhatsApp con ese texto listo para enviar.

Estado: **implementado y probado** (fases 0–4). Compila, tests JVM e instrumentados OK y el
flujo se verificó a mano en el SM-A055M. Ver "Estado de ejecución" (§9) al final.

## 1. Objetivo

Que quien cobra pueda mandarle al cliente, en un solo gesto, el detalle de lo que debe
(concepto, fecha y saldo de cada deuda + total), sin escribir nada a mano y sin salir de la app
más que para pulsar "Enviar" en WhatsApp.

## 2. Restricciones del proyecto (no negociables)

- **Sin permiso `INTERNET`** (`AGENTS.md`). Compartir texto por WhatsApp **no requiere red desde
  la app**: solo se lanza un `Intent` con el texto; WhatsApp hace el envío.
- Sin permisos nuevos. `READ_CONTACTS` no se usa aquí.
- Sin cambios de base de datos ni de `schema.sql`.
- Views XML + ViewBinding, sin Compose. Identificadores y textos de UI en español.
- La app sigue 100 % local: no se sube nada a ningún servidor propio.

## 3. Cómo se puede enviar por WhatsApp (propuestas)

Android ofrece tres caminos reales. Todos son solo `Intent`; ninguno necesita permiso de red.

### Propuesta A — Chat directo del cliente con `wa.me` / esquema `whatsapp://`

```kotlin
val uri = Uri.parse("https://wa.me/573001234567?text=" + Uri.encode(texto))
startActivity(Intent(Intent.ACTION_VIEW, uri))
// o bien: "whatsapp://send?phone=573001234567&text=..."
```

- **Ventaja:** abre **directamente el chat del cliente**; un solo toque para enviar. Es lo que
  el usuario espera de "compartir con este cliente".
- **Desventajas:**
  - Si el número no tiene WhatsApp, WhatsApp muestra "el número de teléfono compartido por la
    URL es inválido" (el cliente puede tener otro número para WhatsApp).
  - Sin WhatsApp instalado, `https://wa.me` cae en el navegador (y la app **no tiene**
    `INTERNET`, aunque el navegador sí).
  - El texto viaja dentro de una URL: hay que codificarlo (`Uri.encode`) y conviene no pasarse
    de ~1.500–2.000 caracteres (WhatsApp trunca textos muy largos en algunos modelos).
  - El esquema `whatsapp://` no está documentado oficialmente por Meta (funciona, pero puede
    cambiar).

### Propuesta B — Compartir texto con `ACTION_SEND` apuntando a WhatsApp

```kotlin
val intent = Intent(Intent.ACTION_SEND).apply {
    type = "text/plain"
    putExtra(Intent.EXTRA_TEXT, texto)
}
intent.setPackage("com.whatsapp")          // o "com.whatsapp.w4b"
startActivity(intent)
```

- **Ventajas:** API oficial, sin límite práctico de longitud, sin depender de que el número esté
  en WhatsApp. WhatsApp abre su **selector de contactos** y el usuario elige a quién.
- **Desventajas:** un toque extra (elegir el contacto). Si se usa `setPackage` y WhatsApp no
  está instalado, lanza `ActivityNotFoundException` (hay que capturarla).

### Propuesta C — Selector del sistema (`Intent.createChooser`)

- **Ventaja:** máxima compatibilidad; el usuario puede mandarlo por WhatsApp, Telegram, SMS,
  correo o copiarlo.
- **Desventaja:** no es "por WhatsApp" y agrega pasos; compite con la intención del pedido.

### Propuesta D — Imagen/PDF con el resumen

Generar un PNG o PDF y compartirlo como archivo (`FileProvider`).

- **Ventaja:** se ve "bonito" y no se puede editar.
- **Desventaja:** mucho más trabajo (FileProvider, permisos de URI, tamaño, modo oscuro),
  y el cliente no puede copiar cifras. **Fuera de alcance por ahora.**

### Recomendación (híbrido A → B → C)

1. **Intento directo (A):** si hay número válido y WhatsApp/WhatsApp Business está instalado,
   abrir el chat del cliente con `whatsapp://send?phone=...&text=...` (o `wa.me`).
2. **Si falla (A):** `ACTION_SEND` con `setPackage("com.whatsapp")` y, si no, con
   `"com.whatsapp.w4b"`.
3. **Si tampoco:** `Intent.createChooser` genérico (cualquier app).
4. **Siempre disponible:** botón **"Copiar"** en la vista previa (portapapeles), como último
   recurso.

Así el caso normal es un toque, y nunca queda un callejón sin salida. El "fallo" de A no se
puede detectar de antemano (número sin WhatsApp), así que la vista previa (§5) es la red de
seguridad: el usuario ve el texto antes de que se abra WhatsApp.

## 4. Contenido del mensaje

### 4.1 Datos de origen

- `ResumenCliente`: `nombre` e `idCliente` (celular normalizado `57XXXXXXXXXX`). El saldo a
  favor **no se usa**.
- `DeudaDao.listar(idCliente, "pendiente")` → `DeudaDetalle` con `nombre`, `descripcion`,
  `saldo`, `fechaCreacion` (`yyyy-MM-dd HH:mm:ss`, UTC).
- **Solo deudas `pendiente`**; nunca pagadas ni anuladas. Monto = **`saldo`**, no `valor`.

### 4.2 Plantilla propuesta

El mensaje empieza directo con la lista: sin saludo y sin frase introductoria.

```
• {nombre deuda} ({dd/MM/yyyy}) — {saldo}
  {descripcion}                      ← solo si existe
• ...

*Total pendiente:* {total}
```

Ejemplo real (WhatsApp interpreta `*` como negrita):

```
• Mercado (12/09/2026) — $ 150.000
• Préstamo (20/09/2026) — $ 300.000
  Cuota de la moto

*Total pendiente:* $ 450.000
```

Sin "Hola", sin "Te comparto tus deudas pendientes" y **sin saldo a favor**: aunque el cliente
tenga dinero a favor, el mensaje solo muestra sus deudas pendientes y el total.

### 4.3 Decisiones de contenido

| # | Pregunta | Opciones | Decisión |
|---|---|---|---|
| D1 | ¿Qué deudas entran? | a) Pendientes · b) Pendientes + pagadas · c) Todas | **a** |
| D2 | ¿Monto por deuda? | a) `saldo` · b) `valor` original | **a** |
| D3 | ¿Orden? | a) Más antigua primero · b) Más reciente · c) Mayor saldo | **a** (lo más viejo se cobra primero) |
| D4 | ¿Incluir descripción? | a) Sí, en línea aparte · b) No | **a**, solo si no está vacía |
| D5 | ¿Incluir fecha? | a) `dd/MM/yyyy` · b) Sin fecha | **a** |
| D6 | ¿Incluir saldo a favor? | a) Sí cuando > 0 · b) No | **b — nunca** (pedido del usuario, 29 oct) |
| D7 | ¿Saludo/introducción/firma? | a) "Hola {nombre}" + intro · b) Sin saludo ni intro · c) Firma configurable | **b — sin saludo, sin intro y sin firma** (pedido del usuario, 29 oct) |
| D8 | ¿Vista previa? | a) Sí, de solo lectura + Enviar/Copiar · b) Enviar directo · c) Editable | **a** (evita mandar algo mal y cubre el fallo de A) |
| D9 | ¿Cliente sin deudas pendientes? | a) Ocultar la opción · b) Mostrarla y avisar | **b** (descubrible; el aviso explica) |

## 5. Flujo de pantalla

1. **Long-press** en un cliente de `ClientesActivity`.
2. `PopupMenu` actual (`ClientesActivity.kt:111`) suma la primera opción:
   **"Compartir por WhatsApp"** (id 3).
3. Se arma el resumen y se abre un **`AlertDialog` de vista previa**:
   - Título: "Compartir con {nombre}".
   - Texto en un `TextView` con scroll (mensaje completo, tal cual se enviará).
   - Botones: **Enviar**, **Copiar**, **Cancelar**.
   - Si no hay deudas pendientes: en vez del diálogo, `Toast`
     "No tiene deudas pendientes" y no se abre nada.
4. **Enviar** ejecuta A → B → C (§3). Si todo falla: se copia al portapapeles y se avisa
   "No se encontró WhatsApp. El resumen se copió".
5. **Copiar** → `ClipboardManager`, `Toast` "Resumen copiado". La app no cierra el diálogo
   (permite copiar y luego enviar).

Nada de esto cambia la navegación existente ni el clic normal (que sigue abriendo deudas).

## 6. Implementación (archivo por archivo)

### Nuevo: `Utils/ResumenUtils.kt` (puro Kotlin, testeable en JVM)

```kotlin
object ResumenUtils {
    /** Ordena por fechaCreacion asc, id asc; solo recibe deudas pendientes. */
    fun construir(deudas: List<DeudaDetalle>): String
}
```

- Usa `MonedaUtils.formatear` (`$ 1.250.000`) y `dd/MM/yyyy` desde `fechaCreacion.take(10)`
  (mismo criterio que `AdapterDeuda.kt:46`; ver riesgo de zona horaria en §8).
- Sin `Context` ni nombre del cliente: los textos fijos van como constantes, así el test es
  JVM puro.
- Casos: 0 deudas (no se llama, pero devuelve total en cero), 1 deuda, varias con y sin
  descripción, montos grandes, nombres con tildes/símbolos.

### Nuevo: `Utils/WhatsAppUtils.kt`

```kotlin
object WhatsAppUtils {
    fun enviarDirecto(ctx: Context, telefono: String, texto: String): Boolean
    fun compartirConWhatsApp(ctx: Context, texto: String): Boolean
    fun copiar(ctx: Context, texto: String)
}
```

- `enviarDirecto`: `whatsapp://send?phone=<dígitos>&text=<Uri.encode(texto)>` con
  `ACTION_VIEW`; `try/catch (ActivityNotFoundException | SecurityException)`.
- `compartirConWhatsApp`: `ACTION_SEND` + `type = "text/plain"` + `EXTRA_TEXT`; probar
  `com.whatsapp`, luego `com.whatsapp.w4b`, luego chooser.
- `copiar`: `ClipboardManager` (`ClipData.newPlainText`).
- `telefono`: `idCliente.filter(Char::isDigit)` (ya viene `57…`).

### `Principal/ClientesActivity.kt`

- En `onClienteLongClick`, agregar el ítem 3 y su rama.
- `compartirDeudas(cliente)`:
  ```kotlin
  val deudas = deudaDao.listar(cliente.idCliente, "pendiente")
  if (deudas.isEmpty()) { aviso(...); return }
  val texto = ResumenUtils.construir(deudas)
  mostrarVistaPrevia(cliente, texto)
  ```
- `deudaDao` se crea en `onCreate` (hoy solo hay `clienteDao`).
- La consulta es rápida (una tabla, índice por `idCliente`); no hace falta hilo aparte.

### `res/values/strings.xml`

```
compartir_menu            "Compartir por WhatsApp"
compartir_titulo          "Compartir con %1$s"
compartir_enviar          "Enviar"
compartir_copiar          "Copiar"
compartir_copiado         "Resumen copiado"
compartir_sin_deudas      "No tiene deudas pendientes"
compartir_sin_whatsapp    "No se encontró WhatsApp. El resumen se copió"
compartir_total           "Total pendiente:"
```

(Reutilizar `accion_cancelar`; **no** hay strings de saludo, introducción ni saldo a favor.)

### `AndroidManifest.xml` (opcional, recomendado)

```xml
<queries>
    <package android:name="com.whatsapp" />
    <package android:name="com.whatsapp.w4b" />
    <intent>
        <action android:name="android.intent.action.VIEW" />
        <data android:scheme="https" />
    </intent>
</queries>
```

- Permite saber si WhatsApp está instalado (para elegir A o B sin esperar la excepción) y
  mostrar el aviso correcto. **No es un permiso**; solo visibilidad de paquetes en API 30+.
- Sin esto, el envío sigue funcionando con `try/catch`, pero no se puede consultar de antemano.

### Sin cambios

`AdapterCliente` (el long-click ya entrega `ResumenCliente` y la vista ancla), DAOs, esquema,
`MenuClienteBottomSheet` (el menú de long-press es un `PopupMenu` en la Activity, no esa hoja).

## 7. Fases

| Fase | Entregable | Prueba |
|---|---|---|
| 0 | Cerrar D1–D9 (§4.3) | — |
| 1 | `ResumenUtils` + strings + `ResumenUtilsTest` (JVM) | `testDebugUnitTest` |
| 2 | Opción en el `PopupMenu` + diálogo de vista previa + "Copiar" | Manual |
| 3 | `WhatsAppUtils` (A→B→C) + fallback a portapapeles | Manual en SM-A055M |
| 4 | `AndroidManifest` `<queries>`, pulido de textos y modo oscuro | Manual |

La fase 2 ya sirve (se puede copiar y pegar en WhatsApp); la 3 es la que cumple el pedido.

## 8. Riesgos y casos borde

- **Número sin WhatsApp:** A muestra error de WhatsApp. Mitigación: fallback a B (selector de
  contactos) y botón Copiar.
- **Número con formato raro:** usar solo dígitos de `idCliente`; `TelefonoUtils` ya garantiza
  `57…` para celulares colombianos.
- **Longitud:** con muchas deudas (raro) la URL crece; por encima de ~1.500 caracteres usar B
  directamente. Regla: `if (texto.length > 1500) B else A`.
- **Fecha UTC:** `fechaCreacion` viene en UTC; `take(10)` puede mostrar el día anterior cerca
  de medianoche. Hoy la app ya hace eso en la lista; se mantiene por consistencia (corregir en
  toda la app es un cambio aparte).
- **Privacidad:** el resumen sale por WhatsApp a un número que el usuario elige; la app no
  guarda ni registra el envío. No se agrega analítica.
- **WhatsApp cambia el esquema `whatsapp://`:** el fallback B/C evita que la función muera.
- **Texto con `&`, `#`, `%`, emojis o saltos de línea:** obligatorio `Uri.encode` en A; en B/C
  el texto va tal cual.
- **Modo oscuro:** el `AlertDialog` usa el tema de la app; verificar el `TextView` de preview.
- **Cliente inactivo:** no aparece en la lista, así que no aplica.
- **`ActivityNotFoundException` en API 23–36:** capturar siempre; no asumir que WhatsApp existe.

## 9. Estado de ejecución

> Se actualiza al final de cada interacción.

Última actualización: 2026-10-29

| Fase | Estado | Notas |
|---|---|---|
| 0 Decisiones | ✅ | D1–D5, D8, D9 con las recomendaciones. D6 y D7 cerradas: **sin saldo a favor, sin saludo, sin introducción y sin firma**. Confirmado: **híbrido A→B→C**, vista previa **solo lectura** y **descripción sí (si existe)** |
| 1 Resumen + tests | ✅ | `Utils/ResumenUtils.kt` + `ResumenUtilsTest` (6 tests JVM) |
| 2 Menú + vista previa | ✅ | Opción "Compartir por WhatsApp" en el `PopupMenu`, diálogo con Enviar/Copiar/Cancelar |
| 3 Envío WhatsApp | ✅ | `Utils/WhatsAppUtils.kt` (chat directo → selector WhatsApp/Business → chooser → portapapeles) |
| 4 Pulido | ✅ | `<queries>` en el manifest, strings; `assembleDebug` + `testDebugUnitTest` OK (29 oct) |

Pendiente: solo confirmar el texto precargado en un chat real con un número que sí tenga
WhatsApp (el número de prueba no existe en WhatsApp, y esa app mostró su aviso propio).

Pruebas del 29 oct (SM-A055M, datos sembrados en la BD):
- Long-press → menú con "Compartir por WhatsApp" como primera opción. OK.
- Vista previa con el texto exacto: dos deudas, descripción, fechas `dd/MM/yyyy` y total. OK.
- **Copiar** dejó el resumen en el portapapeles (verificado pegándolo en el buscador). OK.
- **Enviar** abrió WhatsApp por el chat directo; WhatsApp avisó que el número de prueba no
  existe en WhatsApp (comportamiento esperado, no de la app). OK.
- Cliente sin deudas pendientes: no abre diálogo, solo el aviso. OK.
- Las fechas del resumen usan la **fecha local** del equipo (`FechaUtils`), no el día UTC.
- `testDebugUnitTest`: 27/27. `connectedDebugAndroidTest`: 34/34. Manifest sin `INTERNET`.

Archivos tocados: `Utils/ResumenUtils.kt`, `Utils/WhatsAppUtils.kt`,
`Principal/ClientesActivity.kt`, `res/values/strings.xml`, `AndroidManifest.xml`,
`test/.../ResumenUtilsTest.kt`. Sin cambios en BD, DAOs ni esquema.

## 10. Fuera de alcance

- Enviar a **varios clientes** o "compartir todo".
- Envío automático, programado o recordatorios.
- Imagen/PDF del estado de cuenta.
- Elegir qué deudas incluir (v1 manda todas las pendientes).
- Estado de cuenta con abonos (el pedido es solo lo pendiente).
- Guardar/historial de envíos.
