package com.mmq01.appdeudas.Utils

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import com.mmq01.appdeudas.Clases.Respaldo
import com.mmq01.appdeudas.DBHelper.DeudasDatabaseHelper
import com.mmq01.appdeudas.DBHelper.ErrorBd
import java.io.File
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Respaldos de la base guardados en el almacenamiento privado de la app: siempre en este
 * celular, sin nube, sin permisos y sin archivos fuera de la app.
 *
 * - Uno automático por día (lo llama `ClientesActivity`) y los manuales que pida el usuario.
 * - Se conservan los últimos [MAXIMO]; los más viejos se borran.
 * - Restaurar solo acepta respaldos de la misma [DeudasDatabaseHelper.DATABASE_VERSION], porque
 *   `onUpgrade` es DROP + recreate y un respaldo viejo borraría los datos.
 */
object RespaldoUtils {

    private const val CARPETA = "respaldos"
    private const val PREFIJO = "deudas"
    private const val EXTENSION = ".db"
    private const val MAXIMO = 12

    private const val PATRON_NOMBRE = "'deudas-'yyyyMMdd-HHmmss"
    private const val PATRON_DIA = "yyyyMMdd"
    private const val PATRON_TEXTO = "dd/MM/yyyy HH:mm"

    fun carpeta(context: Context): File = File(context.filesDir, CARPETA)

    /** Más recientes primero. */
    fun listar(context: Context): List<Respaldo> {
        val archivos = carpeta(context).listFiles { archivo ->
            archivo.isFile && archivo.name.endsWith(EXTENSION)
        } ?: return emptyList()
        return archivos.map { Respaldo(it, fechaDe(it)) }.sortedByDescending { it.fecha }
    }

    @Throws(ErrorBd::class)
    fun crear(context: Context): Respaldo {
        val origen = context.getDatabasePath(DeudasDatabaseHelper.DATABASE_NAME)
        if (!origen.exists()) throw ErrorBd("Todavía no hay datos que respaldar")

        val directorio = carpeta(context)
        if (!directorio.exists() && !directorio.mkdirs()) {
            throw ErrorBd("No se pudo crear la carpeta de respaldos")
        }

        val base = PREFIJO + "-" + SimpleDateFormat(PATRON_DIA + "-HHmmss", Locale.US)
            .format(Date())
        val destino = File(directorio, nombreLibre(directorio, base))
        try {
            copiar(origen, destino)
        } catch (e: Exception) {
            destino.delete()
            throw ErrorBd("No se pudo crear el respaldo")
        }
        podar(context)
        return Respaldo(destino, fechaDe(destino))
    }

    /** Crea el respaldo del día si aún no existe. Silencioso: never lanza. */
    fun automatico(context: Context): Respaldo? = try {
        if (!context.getDatabasePath(DeudasDatabaseHelper.DATABASE_NAME).exists()) {
            null
        } else {
            val hoy = SimpleDateFormat(PATRON_DIA, Locale.US).format(Date())
            val yaHay = listar(context).any {
                SimpleDateFormat(PATRON_DIA, Locale.US).format(Date(it.fecha)) == hoy
            }
            if (yaHay) {
                podar(context)
                null
            } else {
                crear(context)
            }
        }
    } catch (e: Exception) {
        null
    }

    fun podar(context: Context, maximo: Int = MAXIMO) {
        listar(context).drop(maximo).forEach { it.archivo.delete() }
    }

    /**
     * Reemplaza la base actual por la del respaldo. El llamador debe cerrar el
     * `DeudasDatabaseHelper` antes y recrear la pantalla después.
     */
    @Throws(ErrorBd::class)
    fun restaurar(context: Context, respaldo: Respaldo) {
        if (!respaldo.archivo.exists()) throw ErrorBd("El respaldo ya no existe")

        val version = versionBase(respaldo.archivo)
        if (version != DeudasDatabaseHelper.DATABASE_VERSION) {
            throw ErrorBd("Ese respaldo es de otra versión de la app")
        }

        val actual = context.getDatabasePath(DeudasDatabaseHelper.DATABASE_NAME)
        actual.parentFile?.mkdirs()

        val temporal = File(actual.parentFile, actual.name + ".restaurando")
        respaldo.archivo.copyTo(temporal, overwrite = true)

        File(actual.path + "-wal").delete()
        File(actual.path + "-shm").delete()
        File(actual.path + "-journal").delete()

        if (!temporal.renameTo(actual)) {
            temporal.copyTo(actual, overwrite = true)
            temporal.delete()
        }
    }

    fun versionBase(archivo: File): Int = try {
        SQLiteDatabase.openDatabase(archivo.path, null, SQLiteDatabase.OPEN_READONLY)
            .use { it.version }
    } catch (e: SQLiteException) {
        -1
    }

    fun textoFecha(respaldo: Respaldo): String =
        SimpleDateFormat(PATRON_TEXTO, Locale("es", "CO")).format(Date(respaldo.fecha))

    fun tamanoTexto(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1048576.0)
        bytes >= 1024 -> String.format(Locale.US, "%d KB", bytes / 1024)
        else -> "$bytes B"
    }

    /**
     * Copia consistente: `VACUUM INTO` (API 30+) incluye lo que esté en el WAL. En versiones
     * anteriores se hace checkpoint y se copia el archivo principal.
     */
    private fun copiar(origen: File, destino: File) {
        if (volcarConVacuum(origen, destino)) return
        try {
            SQLiteDatabase.openDatabase(origen.path, null, SQLiteDatabase.OPEN_READWRITE)
                .use { db ->
                    db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null)
                        .use { it.moveToFirst() }
                }
        } catch (e: Exception) {
            // Sin checkpoint igual se copia; puede faltar lo último del WAL.
        }
        origen.copyTo(destino, overwrite = true)
    }

    private fun volcarConVacuum(origen: File, destino: File): Boolean = try {
        destino.delete()
        SQLiteDatabase.openDatabase(origen.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            val ruta = destino.absolutePath.replace("'", "''")
            db.execSQL("VACUUM INTO '$ruta'")
        }
        destino.exists() && destino.length() > 0
    } catch (e: Exception) {
        destino.delete()
        false
    }

    private fun nombreLibre(directorio: File, base: String): String {
        var nombre = base + EXTENSION
        var intento = 2
        while (File(directorio, nombre).exists()) {
            nombre = "$base-$intento$EXTENSION"
            intento++
        }
        return nombre
    }

    private fun fechaDe(archivo: File): Long = try {
        SimpleDateFormat(PATRON_NOMBRE, Locale.US).parse(archivo.name)?.time
            ?: archivo.lastModified()
    } catch (e: ParseException) {
        archivo.lastModified()
    }
}
