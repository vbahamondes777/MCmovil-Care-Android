package cl.mcmovil.care.viewmodel

import org.json.JSONArray
import org.json.JSONObject
import cl.mcmovil.care.repository.CareRepository

/** Lógica de presentación y reglas de visibilidad de órdenes y solicitudes. */
class CareViewModel {
    var selectedRole: String = ""

    /** Acciones de la interfaz: las operaciones de persistencia viven en Repository. */
    fun acceptRequest(repository: CareRepository, index: Int): String? = repository.acceptRequest(index)
    fun rejectRequest(repository: CareRepository, index: Int): Boolean = repository.rejectRequest(index)
    fun syncOrderStatus(repository: CareRepository, number: String, status: String, legacyIndex: Int): Boolean =
        repository.updateRequestStatusForOrder(number, status, legacyIndex)
    fun setOrderArchived(repository: CareRepository, number: String, archived: Boolean) =
        repository.setOrderArchived(number, archived)

    fun registerDevice(repository: CareRepository, name: String, serial: String, owner: String): Boolean =
        repository.registerDevice(name, serial, owner)
    fun deleteDevice(repository: CareRepository, serial: String, role: String): Boolean =
        role == "Administrador" && repository.deleteDevice(serial)
    fun createQuote(repository: CareRepository, device: JSONObject, description: String, amount: Long, role: String): Boolean =
        role == "Técnico" && repository.createQuote(device, description, amount)
    fun decideQuote(repository: CareRepository, index: Int, status: String, role: String): Boolean =
        role == "Cliente" && repository.decideQuote(index, status)

    /** Validaciones de negocio reutilizables, independientes de los widgets Android. */
    fun validateDevice(name: String, serial: String, owner: String): Map<String, String> {
        val errors = linkedMapOf<String, String>()
        if (name.trim().length < 3) errors["name"] = "Ingresa al menos 3 caracteres"
        if (serial.trim().length < 3) errors["serial"] = "Ingresa un número de serie válido"
        if (owner.trim().isEmpty()) errors["owner"] = "Ingresa el usuario propietario"
        return errors
    }

    fun validateRequest(device: String, issue: String): Map<String, String> {
        val errors = linkedMapOf<String, String>()
        if (device.trim().length !in 3..70) errors["device"] = "Escribe entre 3 y 70 caracteres"
        if (issue.trim().length !in 10..250) errors["issue"] = "Describe el problema entre 10 y 250 caracteres"
        return errors
    }

    fun validateQuote(description: String, price: String): Map<String, String> {
        val errors = linkedMapOf<String, String>()
        if (description.trim().length < 5) errors["description"] = "Describe el trabajo (mínimo 5 caracteres)"
        if ((price.trim().toLongOrNull() ?: 0L) <= 0L) errors["price"] = "Ingresa un precio mayor a cero"
        return errors
    }

    val workStatuses = listOf("Recibido", "En diagnóstico", "Esperando autorización", "En reparación", "Esperando repuestos", "Reparado", "Entregado")

    /** Permite archivar una solicitud cerrada aunque una versión anterior no haya
     * sincronizado su estado con el de la orden asociada. */
    fun canArchiveRequest(request: JSONObject, workOrders: JSONArray): Boolean {
        if (request.optBoolean("archived")) return false
        if (request.optString("status").trim() in listOf("Entregado", "Finalizada", "Finalizado", "Rechazada")) return true
        val number = request.optString("orderNumber")
        if (number.isBlank()) return false
        for (i in 0 until workOrders.length()) {
            val order = workOrders.getJSONObject(i)
            if (order.optString("number") == number &&
                order.optString("status").trim() in listOf("Entregado", "Finalizada", "Finalizado")) return true
        }
        return false
    }

    fun isActiveRequest(request: JSONObject): Boolean = !request.optBoolean("archived")

    /** Una orden archivada nunca debe volver a la cola activa del técnico.
     * Soporta órdenes antiguas guardadas antes de incorporar la marca archived.
     */
    fun isArchivedWorkOrder(order: JSONObject, requests: JSONArray): Boolean {
        if (order.optBoolean("archived")) return true
        val number = order.optString("number")
        val requestIndex = order.optInt("requestIndex", -1)
        for (j in 0 until requests.length()) {
            val request = requests.getJSONObject(j)
            if (!request.optBoolean("archived")) continue
            if (number.isNotBlank() && number == request.optString("orderNumber")) return true
            if (requestIndex == j && number.isNotBlank() && request.optString("orderNumber").isBlank()) return true
        }
        // Las órdenes antiguas de solicitudes ya eliminadas pueden no tener vínculo.
        // Si fueron entregadas y no existe una solicitud activa asociada, no son trabajo pendiente.
        if (order.optString("status") == "Entregado" && number.isNotBlank()) {
            var linkedRequest = false
            for (j in 0 until requests.length()) {
                val request = requests.getJSONObject(j)
                if (number == request.optString("orderNumber")) {
                    linkedRequest = true
                    break
                }
            }
            // Sólo las órdenes antiguas sin solicitud vinculada se ocultan.
            if (!linkedRequest && order.has("requestIndex")) return true
        }
        return false
    }

}
