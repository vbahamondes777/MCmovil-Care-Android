package cl.mcmovil.care.repository

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Repositorio local para datos simulados; no conecta con Gestioo. */
class CareRepository(context: Context) {
    private val prefs = context.getSharedPreferences("mcmovil_demo", Context.MODE_PRIVATE)

    private val database = CareDatabaseHelper(context.applicationContext)

    /** Migra una sola vez los datos anteriores sin borrar fotos, sesión ni preferencias. */
    private fun migrateIfNeeded() {
        if (prefs.getBoolean("sqlite_migration_v1", false)) return
        for (key in database.supportedKeys()) {
            val old = try { JSONArray(prefs.getString(key, "[]")) } catch (_: Exception) { JSONArray() }
            if (!database.hasData(key) && old.length() > 0) database.write(key, old)
        }
        prefs.edit().putBoolean("sqlite_migration_v1", true).commit()
    }

    fun readArray(key: String): JSONArray {
        if (key !in database.supportedKeys()) return try {
            JSONArray(prefs.getString(key, "[]"))
        } catch (_: Exception) { JSONArray() }
        migrateIfNeeded()
        return database.read(key)
    }

    fun saveArray(key: String, value: JSONArray) {
        if (key !in database.supportedKeys()) {
            prefs.edit().putString(key, value.toString()).apply()
            return
        }
        migrateIfNeeded()
        database.write(key, value)
    }

    /** Encuentra la solicitud por identificador estable de orden, no por posición. */
    fun updateRequestStatusForOrder(orderNumber: String, status: String, legacyIndex: Int = -1): Boolean {
        val requests = readArray("requests")
        var index = -1
        if (orderNumber.isNotBlank()) {
            for (i in 0 until requests.length()) {
                if (requests.getJSONObject(i).optString("orderNumber") == orderNumber) {
                    index = i
                    break
                }
            }
        }
        // Compatibilidad con órdenes antiguas que sólo guardaban requestIndex.
        if (index < 0 && legacyIndex in 0 until requests.length()) {
            val candidate = requests.getJSONObject(legacyIndex)
            if (candidate.optString("orderNumber").isBlank() ||
                candidate.optString("orderNumber") == orderNumber) index = legacyIndex
        }
        if (index < 0) return false
        requests.getJSONObject(index).put("status", status)
        saveArray("requests", requests)
        return true
    }

    /** Mantiene sincronizado el archivado de una solicitud y su orden vinculada. */
    fun setOrderArchived(orderNumber: String, archived: Boolean) {
        if (orderNumber.isBlank()) return
        val orders = readArray("work_orders")
        for (i in 0 until orders.length()) {
            val order = orders.getJSONObject(i)
            if (order.optString("number") == orderNumber) order.put("archived", archived)
        }
        saveArray("work_orders", orders)
    }

    /** Crea una orden para una solicitud activa sin duplicar órdenes existentes. */
    fun acceptRequest(requestIndex: Int): String? {
        val requests = readArray("requests")
        if (requestIndex !in 0 until requests.length()) return null
        val request = requests.getJSONObject(requestIndex)
        if (request.optBoolean("archived") || request.optString("status") == "Rechazada" ||
            request.optString("orderNumber").isNotBlank()) return null
        val next = prefs.getInt("next_work_order", 2001)
        val number = "OT-$next"
        val orders = readArray("work_orders")
        orders.put(JSONObject().put("number", number)
            .put("name", request.optString("device"))
            .put("issue", request.optString("issue"))
            .put("owner", request.optString("owner"))
            .put("status", "Recibido")
            .put("requestIndex", requestIndex))
        request.put("orderNumber", number).put("status", "Recibido")
        saveArray("work_orders", orders)
        saveArray("requests", requests)
        prefs.edit().putInt("next_work_order", next + 1).apply()
        return number
    }

    fun rejectRequest(requestIndex: Int): Boolean {
        val requests = readArray("requests")
        if (requestIndex !in 0 until requests.length()) return false
        val request = requests.getJSONObject(requestIndex)
        if (request.optBoolean("archived") || request.optString("orderNumber").isNotBlank()) return false
        request.put("status", "Rechazada")
        saveArray("requests", requests)
        return true
    }

    /** Registro único de equipos: validación y persistencia fuera de la Activity. */
    fun registerDevice(name: String, serial: String, owner: String): Boolean {
        if (name.length < 3 || serial.length < 3 || owner.isBlank()) return false
        val devices = readArray("devices")
        for (i in 0 until devices.length()) {
            if (devices.getJSONObject(i).optString("serial") == serial) return false
        }
        devices.put(JSONObject().put("name", name).put("serial", serial).put("owner", owner)
            .put("status", "En diagnóstico").put("history", "Equipo registrado")
            .put("warranty", "Por revisar").put("maintenance", "Por definir"))
        saveArray("devices", devices)
        return true
    }

    /** Elimina el equipo y sus eventos, sin alterar solicitudes u órdenes históricas. */
    fun deleteDevice(serial: String): Boolean {
        val devices = readArray("devices")
        val updated = JSONArray()
        var found = false
        for (i in 0 until devices.length()) {
            val device = devices.getJSONObject(i)
            if (device.optString("serial") == serial) found = true else updated.put(device)
        }
        if (!found) return false
        saveArray("devices", updated)
        val events = readArray("events")
        val remaining = JSONArray()
        for (i in 0 until events.length()) {
            val event = events.getJSONObject(i)
            if (event.optString("serial") != serial) remaining.put(event)
        }
        saveArray("events", remaining)
        prefs.edit().remove("product_photo_$serial").apply()
        return true
    }

    fun createQuote(device: JSONObject, description: String, amount: Long): Boolean {
        if (description.length < 5 || amount <= 0) return false
        val quotes = readArray("authorizations")
        quotes.put(JSONObject().put("device", device.optString("name"))
            .put("serial", device.optString("serial"))
            .put("owner", device.optString("owner"))
            .put("detail", "$description · $$amount")
            .put("status", "Pendiente"))
        saveArray("authorizations", quotes)
        return true
    }

    /** Solo una cotización pendiente puede recibir una decisión. */
    fun decideQuote(index: Int, status: String): Boolean {
        if (status != "Aprobada" && status != "Rechazada") return false
        val quotes = readArray("authorizations")
        if (index !in 0 until quotes.length()) return false
        val quote = quotes.getJSONObject(index)
        if (quote.optString("status") != "Pendiente") return false
        quote.put("status", status)
        saveArray("authorizations", quotes)
        return true
    }

    fun seedIfNeeded() {
        if (prefs.contains("seeded")) return
        val devices=JSONArray()
        devices.put(JSONObject().put("name","Notebook Lenovo IdeaPad").put("serial","EQ-001").put("status","En diagnóstico").put("owner","cliente").put("warranty","Hasta 15/12/2026").put("history","Ingreso al taller · 10/10/2026").put("maintenance","15/01/2027"))
        devices.put(JSONObject().put("name","PC Gamer ASUS").put("serial","EQ-002").put("status","Esperando autorización").put("owner","cliente").put("warranty","Hasta 02/03/2027").put("history","Limpieza y revisión · 12/10/2026").put("maintenance","02/04/2027"))
        saveArray("devices",devices)
        saveArray("requests",JSONArray())
        saveArray("authorizations",JSONArray().put(JSONObject().put("device","PC Gamer ASUS").put("detail","Cambio de ventilador · $25.000").put("status","Pendiente")))
        prefs.edit().putBoolean("seeded",true).apply()
    }
}
