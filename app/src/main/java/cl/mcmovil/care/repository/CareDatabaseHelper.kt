package cl.mcmovil.care.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

/** SQLite local. Cada registro conserva su JSON para compatibilidad con las pantallas
 * existentes; las columnas relacionales permiten inspeccionar y consultar los datos.
 * No almacena fotos en la BD: se conservan en SharedPreferences como antes.
 */
class CareDatabaseHelper(context: Context) : SQLiteOpenHelper(context, "mcmovil_care.db", null, 1) {
    companion object {
        private val tables = mapOf(
            "devices" to "equipos",
            "requests" to "solicitudes",
            "work_orders" to "ordenes_trabajo",
            "authorizations" to "cotizaciones",
            "events" to "historial",
            "reports" to "informes",
            "notifications" to "notificaciones"
        )
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS usuarios (id INTEGER PRIMARY KEY AUTOINCREMENT, usuario TEXT NOT NULL UNIQUE, rol TEXT NOT NULL)")
        db.execSQL("INSERT OR IGNORE INTO usuarios(usuario,rol) VALUES ('cliente','Cliente'),('tecnico','Técnico'),('admin','Administrador')")
        for (table in tables.values) {
            db.execSQL("CREATE TABLE IF NOT EXISTS $table (posicion INTEGER PRIMARY KEY, nombre TEXT, numero_serie TEXT, propietario TEXT, estado TEXT, numero_orden TEXT, archivado INTEGER NOT NULL DEFAULT 0, datos_json TEXT NOT NULL)")
        }
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_equipos_serie ON equipos(numero_serie)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_solicitudes_orden ON solicitudes(numero_orden)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_ordenes_numero ON ordenes_trabajo(numero_orden)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Versión inicial: las futuras migraciones deben conservar los registros.
    }

    fun hasData(key: String): Boolean {
        val table = tables[key] ?: return false
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor ->
            return cursor.moveToFirst() && cursor.getInt(0) > 0
        }
    }

    fun read(key: String): JSONArray {
        val table = tables[key] ?: return JSONArray()
        val result = JSONArray()
        readableDatabase.rawQuery("SELECT datos_json FROM $table ORDER BY posicion", null).use { cursor ->
            while (cursor.moveToNext()) {
                try { result.put(JSONObject(cursor.getString(0))) } catch (_: Exception) { }
            }
        }
        return result
    }

    fun write(key: String, items: JSONArray) {
        val table = tables[key] ?: return
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(table, null, null)
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val values = android.content.ContentValues().apply {
                    put("posicion", i)
                    put("nombre", item.optString("name", item.optString("device")))
                    put("numero_serie", item.optString("serial"))
                    put("propietario", item.optString("owner"))
                    put("estado", item.optString("status"))
                    put("numero_orden", item.optString("orderNumber", item.optString("number")))
                    put("archivado", if (item.optBoolean("archived")) 1 else 0)
                    put("datos_json", item.toString())
                }
                db.insertOrThrow(table, null, values)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun supportedKeys(): Set<String> = tables.keys
}
