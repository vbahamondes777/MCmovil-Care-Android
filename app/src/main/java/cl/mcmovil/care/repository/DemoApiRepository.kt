package cl.mcmovil.care.repository

import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/** Adaptador académico: traduce datos públicos de JSONPlaceholder a registros recibidos.
 * No representa el backend de MCmovil ni sincroniza registros locales.
 */
class DemoApiRepository {
    data class DemoResult(val ok: Boolean, val message: String, val records: List<String> = emptyList())

    private fun getArray(path: String): JSONArray {
        val connection = URL("https://jsonplaceholder.typicode.com/$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 7000
            connection.readTimeout = 7000
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            return JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
        } finally { connection.disconnect() }
    }

    fun fetch(section: String, callback: (DemoResult) -> Unit) {
        Thread {
            val result = try {
                val records = when (section) {
                    "equipos" -> {
                        val data = getArray("users?_limit=5")
                        (0 until data.length()).map { i ->
                            val user = data.getJSONObject(i)
                            "Equipo EXT-${100 + user.getInt("id")} · Contacto: ${user.getString("name")} · Referencia EXT-${1000 + i}"
                        }
                    }
                    "ordenes" -> {
                        val data = getArray("todos?_limit=8")
                        (0 until data.length()).map { i ->
                            val item = data.getJSONObject(i)
                            "Orden externa #${item.getInt("id")} · ${if (item.getBoolean("completed")) "Finalizada" else "En proceso"} · ${item.getString("title")}" 
                        }
                    }
                    "informes" -> {
                        val data = getArray("posts?_limit=5")
                        (0 until data.length()).map { i ->
                            val item = data.getJSONObject(i)
                            "Informe externo #${item.getInt("id")} · ${item.getString("title")}"
                        }
                    }
                    else -> error("Sección desconocida")
                }
                DemoResult(true, "Consulta exitosa · ${records.size} registros recibidos", records)
            } catch (e: Exception) {
                DemoResult(false, "No fue posible consultar el servicio externo (${e.javaClass.simpleName}). Comprueba Internet y reintenta.")
            }
            callback(result)
        }.start()
    }
}
