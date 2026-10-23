package cl.mcmovil.care.ui

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.provider.MediaStore
import android.graphics.Bitmap
import android.net.Uri
import android.animation.LayoutTransition
import android.view.animation.AlphaAnimation
import android.view.animation.ScaleAnimation
import java.io.ByteArrayOutputStream
import android.util.Base64
import cl.mcmovil.care.repository.DemoApiRepository
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.content.Context
import android.view.View
import android.widget.*
import android.text.InputType
import org.json.JSONArray
import org.json.JSONObject
import cl.mcmovil.care.repository.CareRepository
import cl.mcmovil.care.model.DemoCredentials
import cl.mcmovil.care.viewmodel.CareViewModel

// MVP académico: datos de ejemplo y almacenamiento local, sin servicios productivos.
open class CareScreensActivity : Activity() {
    private lateinit var root: LinearLayout
    private val prefs by lazy { getSharedPreferences("mcmovil_demo", Context.MODE_PRIVATE) }
    private val repository by lazy { CareRepository(this) }
    private val viewModel = CareViewModel()
    private val api = DemoApiRepository()
    private var evidencePreview: ImageView? = null
    private val cameraCode = 301
    private val galleryCode = 302
    private val productCameraCode = 305
    private var pendingProductPhoto: String? = null
    private var pendingProductPreview: ImageView? = null
    private var productPhotoSerial: String? = null
    private val profileCameraCode = 303
    private val profileGalleryCode = 304
    private var profilePreview: ImageView? = null
    private var role = ""
    private val permissionRequestCode = 71
    private val navy = Color.rgb(23, 55, 94)
    private val blue = Color.rgb(25, 105, 182)
    private val bg = Color.rgb(245, 248, 252)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Cada apertura desde cero comienza en el selector de perfiles.
        // Los datos del taller siguen persistidos; solo se limpia la sesión.
        prefs.edit().remove("role").remove("username").apply()
        role = ""
        login()
        requestInitialPermissions()
        setupNotifications()
        if (role.isNotBlank()) { askNotificationPermission(); deliverPendingNotifications() }
    }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun page(title: String, back: (() -> Unit)? = null) {
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(bg)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutTransition = LayoutTransition(); setPadding(dp(20),dp(54),dp(20),dp(26)) }
        scroll.addView(root)
        setContentView(scroll)
        scroll.startAnimation(AlphaAnimation(0f,1f).apply { duration=230 })
        if (back != null) button("← Volver", back, false)
        label(title, 27, true)
        label("MCmovil Care · Servicio técnico", 12)
        space()
    }
    private fun label(s: String, size: Int = 16, bold: Boolean = false) {
        val t = TextView(this).apply { text=s; textSize=size.toFloat(); setTextColor(navy); if(bold) setTypeface(null,Typeface.BOLD); setPadding(0,dp(5),0,dp(8)) }
        root.addView(t)
    }
    private fun space() { root.addView(View(this).apply { layoutParams=LinearLayout.LayoutParams(1,dp(12)) }) }
    private fun button(s: String, action: () -> Unit, primary: Boolean = true) {
        val b = Button(this).apply { text=s; isAllCaps=false; setTextColor(if(primary) Color.WHITE else navy); setBackgroundTintList(android.content.res.ColorStateList.valueOf(if(primary) blue else Color.rgb(221,231,243))); setOnClickListener {
            startAnimation(ScaleAnimation(1f, 0.97f, 1f, 0.97f,
                android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
                android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f).apply {
                duration = 110
                repeatCount = 1
                repeatMode = android.view.animation.Animation.REVERSE
            })
            action()
        } }
        root.addView(b, LinearLayout.LayoutParams(-1,dp(52)).apply { bottomMargin=dp(9) })
    }
    private fun button(s: String, action: () -> Unit) = button(s, action, true)
    private fun field(hint: String, numeric: Boolean = false): EditText {
        val e=EditText(this).apply {
            this.hint=hint
            textSize=16f
            setSingleLine(true)
            setCompoundDrawablesWithIntrinsicBounds(
                if (numeric) android.R.drawable.ic_menu_edit else android.R.drawable.ic_menu_info_details,
                0, 0, 0)
            compoundDrawablePadding = dp(8)
            if(numeric) inputType=InputType.TYPE_CLASS_NUMBER
        }
        root.addView(e, LinearLayout.LayoutParams(-1,dp(55)))
        return e
    }
    private fun toast(s: String) = Toast.makeText(this,s,Toast.LENGTH_LONG).show()
    private fun arr(key: String): JSONArray = repository.readArray(key)
    private fun save(key: String, a: JSONArray) = repository.saveArray(key, a)
    private fun seed() = repository.seedIfNeeded()
    private fun owner(): String = prefs.getString("username", "cliente") ?: "cliente"
    private fun visibleDevice(d: JSONObject): Boolean = role != "Cliente" || d.optString("owner", "cliente") == owner()
    private fun visibleRequest(d: JSONObject): Boolean = role != "Cliente" || d.optString("owner", "cliente") == owner()
    private fun profileKey(): String = "profile_photo_${owner()}"
    private val notificationChannel = "mcmovil_care_avisos"
    private fun setupNotifications() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel(notificationChannel, "Avisos MCmovil Care", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }
    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 72)
        }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 72) deliverPendingNotifications()
    }
    private fun notificationAllowed(): Boolean = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    private fun notifySystem(title: String, message: String, id: Int) {
        if (!notificationAllowed()) return
        val intent = Intent(this, cl.mcmovil.care.MainActivity::class.java)
        val pending = PendingIntent.getActivity(this, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, notificationChannel) else Notification.Builder(this)
        val notification = builder.setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title).setContentText(message).setStyle(Notification.BigTextStyle().bigText(message))
            .setContentIntent(pending).setAutoCancel(true).build()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(id, notification)
    }
    private fun addNotice(recipient: String, title: String, message: String) {
        if (recipient.isBlank()) return
        val a = arr("notifications")
        val id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        a.put(JSONObject().put("recipient", recipient).put("title", title).put("message", message)
            .put("date", java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date()))
            .put("read", false).put("delivered", false).put("id", id))
        save("notifications", a)
        deliverPendingNotifications()
    }
    private fun deliverPendingNotifications() {
        if (role.isBlank() || !notificationAllowed()) return
        val a = arr("notifications")
        var changed = false
        for (i in 0 until a.length()) {
            val n = a.getJSONObject(i)
            if (n.optString("recipient") == owner() && !n.optBoolean("delivered")) {
                notifySystem(n.optString("title"), n.optString("message"), n.optInt("id", i + 100))
                n.put("delivered", true)
                changed = true
            }
        }
        if (changed) save("notifications", a)
    }
    private fun noticesPage() {
        page("Mis notificaciones", ::home)
        val a = arr("notifications")
        var found = false
        for (i in a.length() - 1 downTo 0) {
            val n = a.getJSONObject(i)
            if (n.optString("recipient") != owner()) continue
            found = true
            label("${if (n.optBoolean("read")) "✓" else "●"} ${n.optString("title")}", 18, true)
            label("${n.optString("message")}\n${n.optString("date")}")
            if (!n.optBoolean("read")) button("Marcar como leída", {
                val latest = arr("notifications")
                latest.getJSONObject(i).put("read", true)
                save("notifications", latest)
                noticesPage()
            }, false)
            space()
        }
        if (!found) label("No tienes notificaciones todavía.")
    }
    private fun noticeButton() {
        val a = arr("notifications")
        var unread = 0
        for (i in 0 until a.length()) {
            val n = a.getJSONObject(i)
            if (n.optString("recipient") == owner() && !n.optBoolean("read")) unread++
        }
        button("Mis notificaciones${if (unread > 0) " ($unread nuevas)" else ""}") { noticesPage() }
    }
    private fun deviceOwner(serial: String): String {
        val a = arr("devices")
        for (i in 0 until a.length()) {
            val d = a.getJSONObject(i)
            if (d.optString("serial") == serial) return d.optString("owner")
        }
        return ""
    }

    // La foto del equipo es requisito para cualquier operación técnica.
    private fun requireDevicePhoto(serial: String): Boolean {
        if (serial.isNotBlank() && !prefs.getString("product_photo_$serial", null).isNullOrBlank()) return true
        toast("FOTOGRAFÍA OBLIGATORIA: toma la foto del equipo desde su detalle antes de continuar")
        return false
    }

    private fun loadSavedPhoto(key: String, target: ImageView?) {
        prefs.getString(key, null)?.let {
            try {
                val bytes = Base64.decode(it, Base64.DEFAULT)
                target?.setImageBitmap(android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
            } catch (_: Exception) { }
        }
    }
    private fun profilePage() {
        page("Mi perfil", ::home)
        label("Usuario: ${owner()}", 19, true)
        label("Tipo de cuenta: $role")
        label("Mi foto de perfil", 18, true)
        profilePreview = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.rgb(221, 231, 243))
            setImageResource(android.R.drawable.ic_menu_camera)
        }
        root.addView(profilePreview, LinearLayout.LayoutParams(dp(190), dp(190)).apply { bottomMargin = dp(16) })
        loadSavedPhoto(profileKey(), profilePreview)
        button("Tomar foto de perfil") {
            if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), permissionRequestCode)
                toast("Autoriza la cámara y vuelve a pulsar el botón")
            } else {
                try { startActivityForResult(Intent(MediaStore.ACTION_IMAGE_CAPTURE), profileCameraCode) }
                catch (_: Exception) { toast("No hay una aplicación de cámara disponible") }
            }
        }
        button("Elegir foto de galería", {
            try {
                startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "image/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }, profileGalleryCode)
            } catch (_: Exception) { toast("No se pudo abrir la galería") }
        }, false)
        button("Quitar foto de perfil", {
            prefs.edit().remove(profileKey()).apply()
            profilePage()
            toast("Foto de perfil eliminada")
        }, false)
        label("Tu fotografía de perfil se guarda de forma local en este dispositivo.", 13)
    }
    private fun evidenceButtons() {
        label("Evidencia fotográfica (opcional)",18,true)
        evidencePreview = ImageView(this).apply { adjustViewBounds=true; maxHeight=dp(230) }
        root.addView(evidencePreview, LinearLayout.LayoutParams(-1,dp(180)))
        prefs.getString("evidence",null)?.let { try { val bytes=Base64.decode(it,Base64.DEFAULT); evidencePreview?.setImageBitmap(android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size)) } catch (_:Exception) {} }
        button("Tomar fotografía") {
            if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.CAMERA),permissionRequestCode)
                toast("Autoriza la cámara y vuelve a tocar el botón")
            } else try { startActivityForResult(Intent(MediaStore.ACTION_IMAGE_CAPTURE),cameraCode) } catch (_:Exception) { toast("No hay aplicación de cámara disponible") }
        }
        button("Elegir foto de galería", {
            try { startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply { type="image/*"; addCategory(Intent.CATEGORY_OPENABLE) },galleryCode) }
            catch (_:Exception) { toast("No hay selector de imágenes disponible") }
        },false)
    }
    @Deprecated("Legacy activity result API for compatibility with minSdk 24")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(resultCode!=RESULT_OK) return
        try {
            val bitmap:Bitmap? = when(requestCode) {
                cameraCode, profileCameraCode, productCameraCode -> (data?.extras?.get("data") as? Bitmap)
                galleryCode, profileGalleryCode -> data?.data?.let { uri -> contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) } }
                else -> null
            }
            if(bitmap!=null) {
                val scaled=Bitmap.createScaledBitmap(bitmap,bitmap.width.coerceAtMost(800), (bitmap.height.toFloat()*bitmap.width.coerceAtMost(800)/bitmap.width).toInt().coerceAtLeast(1),true)
                val out=ByteArrayOutputStream(); scaled.compress(Bitmap.CompressFormat.JPEG,65,out)
                if (requestCode == productCameraCode) {
                    val photo = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                    if (productPhotoSerial != null) {
                        prefs.edit().putString("product_photo_${productPhotoSerial}", photo).apply()
                        toast("Fotografía del equipo guardada")
                        productPhotoSerial = null
                    } else {
                        pendingProductPhoto = photo
                        toast("Fotografía tomada. Ya puedes registrar el equipo")
                    }
                    pendingProductPreview?.setImageBitmap(scaled)
                    return
                }
                val isProfile = requestCode == profileCameraCode || requestCode == profileGalleryCode
                prefs.edit().putString(if (isProfile) profileKey() else "evidence", Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)).apply()
                if (isProfile) profilePreview?.setImageBitmap(scaled) else evidencePreview?.setImageBitmap(scaled)
                toast(if (isProfile) "Foto de perfil guardada" else "Evidencia guardada en este dispositivo")
            }
        } catch (_:Exception) { toast("No se pudo procesar la imagen") }
    }
    private fun requestInitialPermissions() {
        if (Build.VERSION.SDK_INT >= 23 && !prefs.getBoolean("permissions_requested", false)) {
            val requested = mutableListOf(Manifest.permission.CAMERA)
            // Android 12L o anterior: acceso a imágenes con permiso de lectura.
            // Android 13+ usa el selector del sistema sin acceso general a fotos.
            if (Build.VERSION.SDK_INT <= 32) requested.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            val missing = requested.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
            prefs.edit().putBoolean("permissions_requested", true).apply()
            if (missing.isNotEmpty()) {
                android.app.AlertDialog.Builder(this)
                    .setTitle("Permisos de fotografías")
                    .setMessage("MCmovil Care solicita acceso a cámara y, en versiones antiguas de Android, lectura de fotografías. En Android 13 o superior, el selector de galería no necesita permiso. No se capturan imágenes automáticamente.")
                    .setPositiveButton("Continuar") { _, _ -> requestPermissions(missing.toTypedArray(), permissionRequestCode) }
                    .setNegativeButton("Ahora no", null)
                    .show()
            }
        }
    }
    private fun login() {
        role = ""
        page("Bienvenido a MCmovil Care")
        label("Seguimiento y cuidado de equipos tecnológicos.")
        label("Selecciona tu tipo de usuario",19,true)
        button("Soy cliente") { roleLogin("Cliente") }
        button("Soy técnico") { roleLogin("Técnico") }
        button("Soy administrador") { roleLogin("Administrador") }
        label("Accede con tus credenciales para continuar.",13)
    }
    private fun roleLogin(selectedRole: String) {
        page("Inicio de sesión · $selectedRole", ::login)
        val user = field("Usuario")
        val password = field("Contraseña").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val credentials = DemoCredentials.forRole(selectedRole)
        label("Ingresa tu usuario y contraseña.",13)
        button("Iniciar sesión") {
            if (user.text.toString().trim() == credentials.first && password.text.toString() == credentials.second) {
                role = selectedRole
                viewModel.selectedRole = role
                prefs.edit().putString("role", role).putString("username",credentials.first).apply()
                seed()
                askNotificationPermission()
                deliverPendingNotifications()
                home()
            } else {
                toast("Usuario o contraseña incorrectos para $selectedRole")
            }
        }
    }
    private fun home() {
        page("Inicio · $role")
        when (role) {
            "Cliente" -> {
                label("Mi atención y seguimiento",18,true)
                button("Mi perfil y foto") { profilePage() }
                noticeButton()
                button("Mis equipos") { devices() }
                button("Estado de mis órdenes") { orders() }
                button("Historial, garantías y mantenciones") { history() }
                button("Mis informes técnicos") { reports() }
                button("Responder cotizaciones") { authorizations() }
                button("Solicitar atención") { newRequest() }
                button("Mis solicitudes") { requests() }
            }
            "Técnico" -> {
                label("Trabajo técnico",18,true)
                button("Mi perfil y foto") { profilePage() }
                noticeButton()
                button("Equipos asignados / registrados") { devices() }
                button("Registrar equipo con foto obligatoria") { registerDevice() }
                button("Órdenes de trabajo") { orders() }
                
                button("Registrar informe y garantía") { editReport() }
                button("Crear cotización") { createQuote() }
                button("Atender solicitudes") { manageRequests() }
                button("Capturar evidencias") { evidencePage() }
            }
            "Administrador" -> {
                label("Centro de administración",18,true)
                noticeButton()
                button("Resumen administrativo") { dashboard() }
                button("Administrar equipos y eliminar") { adminDevices() }
                button("Supervisar solicitudes") { requests() }
                button("Solicitudes archivadas") { archivedRequests() }
                button("Servicios conectados") { apiPage() }
                button("Mi perfil y foto") { profilePage() }
            }
            else -> { login(); return }
        }
        space()
        button("Cerrar sesión", { role=""; prefs.edit().remove("role").remove("username").apply(); login() },false)
    }
    private fun technicianOnly(): Boolean {
        if (role == "Técnico") return true
        toast("Esta función corresponde exclusivamente al técnico")
        home()
        return false
    }
    private fun administratorOnly(): Boolean {
        if (role == "Administrador") return true
        toast("Acceso exclusivo del administrador")
        home()
        return false
    }
    private fun evidencePage() { if (!technicianOnly()) return; page("Evidencia fotográfica",::home); evidenceButtons() }
    private fun apiPage() {
        if (!administratorOnly()) return
        page("Servicios conectados", ::home)
        label("Consulta información del servicio externo. Los resultados son de referencia y no modifican los registros de tus equipos.", 14)
        button("Consultar equipos externos") { showApiSection("equipos") }
        button("Consultar órdenes externas") { showApiSection("ordenes") }
        button("Consultar informes externos") { showApiSection("informes") }
    }
    private fun showApiSection(section: String) {
        if (!administratorOnly()) return
        page("API REST · ${section.replaceFirstChar { it.uppercase() }}", ::apiPage)
        label("Fuente externa: JSONPlaceholder. Datos de referencia, independientes de las órdenes y equipos registrados en MCmovil Care.", 14)
        val result = TextView(this).apply {
            text = "Consultando servicio..."
            textSize = 16f
            setTextColor(navy)
            setPadding(0, dp(16), 0, dp(16))
        }
        root.addView(result)
        button("Reintentar consulta") { showApiSection(section) }
        api.fetch(section) { response -> runOnUiThread {
            result.text = if (response.ok) response.message + "\n\n" + response.records.joinToString("\n\n") else response.message
        } }
    }
    private fun takeProductPhoto(serial: String? = null) {
        productPhotoSerial = serial
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), permissionRequestCode)
            toast("Autoriza la cámara y vuelve a pulsar Tomar foto")
            return
        }
        try { startActivityForResult(Intent(MediaStore.ACTION_IMAGE_CAPTURE), productCameraCode) }
        catch (_: Exception) { toast("No se encontró una aplicación de cámara") }
    }
    private fun registerDevice() {
        if (!technicianOnly()) return
        pendingProductPhoto = null
        page("Registrar equipo", ::home)
        label("REQUISITO OBLIGATORIO: debes tomar una fotografía real con la cámara. Sin ella no se puede guardar el equipo.", 14)
        val name = field("Nombre del equipo")
        val serial = field("Número de serie")
        val customer = field("Usuario propietario (ej.: cliente)")
        pendingProductPreview = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_camera)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        root.addView(pendingProductPreview, LinearLayout.LayoutParams(-1, dp(190)))
        button("Tomar foto del equipo (obligatorio)") { takeProductPhoto() }
        button("Guardar equipo") {
            val n = name.text.toString().trim()
            val sn = serial.text.toString().trim()
            val c = customer.text.toString().trim()
            val errors = viewModel.validateDevice(n, sn, c)
            name.error = errors["name"]
            serial.error = errors["serial"]
            customer.error = errors["owner"]
            if (errors.isNotEmpty()) { toast("Corrige los campos indicados"); return@button }
            val photo = pendingProductPhoto
            if (photo.isNullOrBlank()) {
                toast("Debes tomar una fotografía con la cámara antes de guardar")
                return@button
            }
            if (!viewModel.registerDevice(repository, n, sn, c)) {
                serial.error = "Ya existe un equipo con ese número de serie"
                return@button
            }
            addEvent(sn,"Equipo ingresado al taller con fotografía")
            prefs.edit().putString("product_photo_$sn", photo).apply()
            pendingProductPhoto = null
            pendingProductPreview = null
            toast("Equipo registrado con fotografía obligatoria")
            devices()
        }
    }
    private fun devices() {
        page("Mis equipos",::home)
        val a=arr("devices")
        for(i in 0 until a.length()) { val d=a.getJSONObject(i); if(!visibleDevice(d)) continue; label("💻 ${d.optString("name")}",19,true); label("Serie: ${d.optString("serial")}\nEstado: ${d.optString("status")}") ; button("Ver detalle", { detail(i) },false) }
    }
    private fun detail(i:Int) {
        val d=arr("devices").getJSONObject(i)
        if (!visibleDevice(d)) { home(); return }
        page(d.optString("name"),::devices)
        val serial = d.optString("serial")
        label("Fotografía del equipo", 18, true)
        val photoView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageResource(android.R.drawable.ic_menu_camera)
        }
        root.addView(photoView, LinearLayout.LayoutParams(-1, dp(210)))
        loadSavedPhoto("product_photo_$serial", photoView)
        if (!prefs.contains("product_photo_$serial")) label("⚠ FOTOGRAFÍA OBLIGATORIA PENDIENTE. Este equipo no puede avanzar hasta tomarla.", 14)
        if (role == "Técnico") {
            button("Tomar / actualizar foto del equipo") {
                pendingProductPreview = photoView
                takeProductPhoto(serial)
            }
        }
        label("Número de serie: ${d.optString("serial")}")
        label("Estado actual: ${d.optString("status")}",19,true)
        label("Historial: ${d.optString("history")}")
        label("Garantía: ${d.optString("warranty")}")
        label("Próxima mantención: ${d.optString("maintenance")}")
        label("Informe técnico: ${d.optString("report", "Pendiente de diagnóstico")}")
        button("Ver historial de eventos", { deviceEvents(serial) }, false)
    }
    private fun setOrderArchivedForRequest(request: JSONObject, archived: Boolean) {
        viewModel.setOrderArchived(repository, request.optString("orderNumber"), archived)
    }

    private val workStatuses get() = viewModel.workStatuses

    private fun orders() {
        page(if (role == "Técnico") "Órdenes de trabajo" else "Estado de mis órdenes", ::home)
        val devices = arr("devices")
        val requestOrders = arr("work_orders")
        val allRequests = arr("requests")
        var shown = 0
        for (i in 0 until devices.length()) {
            val d = devices.getJSONObject(i)
            if (!visibleDevice(d)) continue
            shown++
            label("OT-${1001+i} · ${d.optString("name")}", 18, true)
            label("Estado: ${d.optString("status")} · Cliente: ${d.optString("owner")}")
            if (role == "Técnico") {
                orderStatusEditor("devices", i, d.optString("status"))
            }
            space()
        }
        for (i in 0 until requestOrders.length()) {
            val o = requestOrders.getJSONObject(i)
            // Las órdenes de solicitudes archivadas dejan de estar en la cola
            // activa del técnico, pero permanecen para el historial del cliente.
            if (role == "Técnico" && viewModel.isArchivedWorkOrder(o, allRequests)) continue
            if (role == "Cliente" && o.optString("owner") != owner()) continue
            shown++
            label("${o.optString("number")} · ${o.optString("name")}", 18, true)
            label("Estado: ${o.optString("status")} · Cliente: ${o.optString("owner")}")
            label("Problema: ${o.optString("issue")}", 14)
            if (role == "Técnico") {
                orderStatusEditor("work_orders", i, o.optString("status"))
            }
            space()
        }
        if (shown == 0) label("No hay órdenes de trabajo disponibles.")
    }

    private fun orderStatusEditor(key: String, index: Int, currentStatus: String) {
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, workStatuses)
        spinner.setSelection(workStatuses.indexOf(currentStatus).coerceAtLeast(0))
        root.addView(spinner)
        button("Guardar actualización") {
            if (!technicianOnly()) return@button
            val items = arr(key)
            if (index >= items.length()) { toast("La orden ya no existe"); orders(); return@button }
            val item = items.getJSONObject(index)
            val previous = item.optString("status")
            val updated = workStatuses[spinner.selectedItemPosition]
            if (previous == updated) { toast("El estado no ha cambiado"); return@button }
            item.put("status", updated)
            save(key, items)
            val name = item.optString("name")
            val recipient = item.optString("owner")
            if (key == "devices") {
                addEvent(item.optString("serial"), "Estado actualizado: $updated")
            } else {
                viewModel.syncOrderStatus(
                    repository,
                    item.optString("number"),
                    updated,
                    item.optInt("requestIndex", -1)
                )
            }
            addNotice(recipient, "Actualización de reparación", "$name: $updated")
            toast("Estado guardado y aviso enviado al cliente")
            orders()
        }
    }

    private fun history() {
        page("Historial y garantías",::home)
        val a=arr("devices")
        for(i in 0 until a.length()) { val d=a.getJSONObject(i); if(!visibleDevice(d)) continue; label(d.optString("name"),19,true); label("Atención: ${d.optString("history")}\nGarantía: ${d.optString("warranty")}\nPróxima mantención: ${d.optString("maintenance")}"); space() }
    }
    private fun authorizations() {
        if (role != "Cliente") { home(); return }
        page("Autorizaciones",::home)
        val a=arr("authorizations")
        for(i in 0 until a.length()) {
            val item=a.getJSONObject(i)
            if(!visibleQuote(item)) continue
            label(item.optString("device"),19,true)
            label("${item.optString("detail")}\nRespuesta: ${item.optString("status")}")
            if(item.optString("status")=="Pendiente" && role=="Cliente") {
                button("Aprobar", { decide(i,"Aprobada") })
                button("Rechazar", { decide(i,"Rechazada") },false)
            }
        }
    }
    private fun visibleQuote(item: JSONObject): Boolean {
        if (role != "Cliente") return true
        val quoteOwner = item.optString("owner")
        if (quoteOwner.isNotBlank()) return quoteOwner == owner()
        return visibleDeviceByName(item.optString("device"))
    }
    private fun visibleDeviceByName(name:String):Boolean {
        if(role!="Cliente") return true
        val devices=arr("devices")
        return (0 until devices.length()).any { val d=devices.getJSONObject(it); d.optString("name")==name && visibleDevice(d) }
    }
    private fun decide(i:Int,status:String) {
        val a=arr("authorizations")
        val quote=a.getJSONObject(i)
        if (role != "Cliente" || !visibleQuote(quote) || quote.optString("status") != "Pendiente") return
        if (!viewModel.decideQuote(repository, i, status, role)) return
        addEvent(quote.optString("serial"),"Cotización $status por cliente")
        addNotice("tecnico", "Respuesta a cotización", "El cliente ${owner()} ha marcado la cotización como $status.")
        toast("Autorización $status")
        authorizations()
    }
    private fun newRequest() {
        if (role != "Cliente") { home(); return }
        page("Nueva solicitud",::home)
        val equipment=field("Equipo (ej.: Notebook HP)")
        val issue=field("Describe brevemente el problema")
        button("Guardar solicitud") {
            val e=equipment.text.toString().trim(); val d=issue.text.toString().trim()
            val errors = viewModel.validateRequest(e, d)
            equipment.error = errors["device"]
            issue.error = errors["issue"]
            if(errors.isNotEmpty()) { toast("Corrige los campos indicados") } else {
                val a=arr("requests"); a.put(JSONObject().put("device",e).put("issue",d).put("status","Recibida").put("owner",owner())); save("requests",a); addNotice("tecnico", "Nueva solicitud", "${owner()} solicita atención para $e."); toast("Solicitud guardada localmente"); requests()
            }
        }
    }

    /** Repara vínculos antiguos sin utilizar posiciones que pudieron cambiar al borrar solicitudes. */
    private fun reconcileLegacyDeliveredRequests() {
        if (role != "Administrador") return
        val requests = arr("requests")
        val orders = arr("work_orders")
        var changed = false
        for (i in 0 until requests.length()) {
            val request = requests.getJSONObject(i)
            if (request.optBoolean("archived")) continue
            if (viewModel.canArchiveRequest(request, orders)) continue
            // Solo reconciliar si existe exactamente una orden entregada del mismo equipo y cliente.
            val matches = mutableListOf<JSONObject>()
            for (j in 0 until orders.length()) {
                val order = orders.getJSONObject(j)
                if (order.optString("status").trim() != "Entregado") continue
                if (order.optBoolean("archived")) continue
                if (order.optString("name").trim() != request.optString("device").trim()) continue
                if (order.optString("owner") != request.optString("owner")) continue
                val number = order.optString("number")
                if (number.isBlank()) continue
                var linkedElsewhere = false
                for (k in 0 until requests.length()) {
                    if (k != i && requests.getJSONObject(k).optString("orderNumber") == number) {
                        linkedElsewhere = true
                        break
                    }
                }
                if (!linkedElsewhere) matches.add(order)
            }
            if (matches.size == 1) {
                request.put("orderNumber", matches[0].optString("number"))
                request.put("status", "Entregado")
                changed = true
            }
        }
        if (changed) save("requests", requests)
    }

    private fun requests() {
        if (role != "Cliente" && role != "Administrador") { home(); return }
        if (role == "Administrador") reconcileLegacyDeliveredRequests()
        page(if (role == "Administrador") "Supervisar solicitudes" else "Mis solicitudes", ::home)
        val a = arr("requests")
        var shown = 0
        for (i in 0 until a.length()) {
            val r = a.getJSONObject(i)
            if (!visibleRequest(r)) continue
            // Las solicitudes archivadas siguen visibles al cliente como historial.
            if (role == "Administrador" && r.optBoolean("archived")) continue
            shown++
            label("Solicitud ${i + 1} · ${r.optString("device")}", 18, true)
            label("Problema: ${r.optString("issue")}\nEstado: ${r.optString("status")}" +
                if (r.optBoolean("archived")) "\nArchivada (historial)" else "")
            if (role == "Administrador" && viewModel.canArchiveRequest(r, arr("work_orders"))) {
                button("Archivar solicitud", {
                    android.app.AlertDialog.Builder(this)
                        .setTitle("¿Archivar solicitud?")
                        .setMessage("La solicitud saldrá de la lista activa, pero se conservarán su orden, el historial y los avisos del cliente.")
                        .setNegativeButton("Cancelar", null)
                        .setPositiveButton("Archivar") { _, _ ->
                            if (role != "Administrador") return@setPositiveButton
                            val latest = arr("requests")
                            if (i < latest.length()) {
                                val current = latest.getJSONObject(i)
                                if (!current.optBoolean("archived") && viewModel.canArchiveRequest(current, arr("work_orders"))) {
                                    current.put("archived", true)
                                    setOrderArchivedForRequest(current, true)
                                    save("requests", latest)
                                    toast("Solicitud archivada")
                                }
                            }
                            requests()
                        }.show()
                }, false)
            }
            space()
        }
        if (shown == 0) label("No hay solicitudes en esta sección.")
        if (role == "Administrador") {
            val orders = arr("work_orders")
            val currentRequests = arr("requests")
            var orphanCount = 0
            for (j in 0 until orders.length()) {
                val order = orders.getJSONObject(j)
                if (order.optBoolean("archived") || order.optString("status").trim() != "Entregado") continue
                val number = order.optString("number")
                if (number.isBlank()) continue
                var linked = false
                for (k in 0 until currentRequests.length()) {
                    if (currentRequests.getJSONObject(k).optString("orderNumber") == number) {
                        linked = true
                        break
                    }
                }
                if (linked) continue
                orphanCount++
                label("Orden antigua entregada sin solicitud: $number · ${order.optString("name")}", 14)
                button("Archivar orden antigua $number", {
                    android.app.AlertDialog.Builder(this)
                        .setTitle("¿Archivar orden entregada?")
                        .setMessage("La orden $number dejará de aparecer en la cola activa del técnico. Se conservará como historial; no se borrarán los datos.")
                        .setNegativeButton("Cancelar", null)
                        .setPositiveButton("Archivar") { _, _ ->
                            if (role != "Administrador") return@setPositiveButton
                            val latest = arr("work_orders")
                            for (n in 0 until latest.length()) {
                                val item = latest.getJSONObject(n)
                                if (item.optString("number") == number && item.optString("status").trim() == "Entregado") {
                                    item.put("archived", true)
                                }
                            }
                            save("work_orders", latest)
                            toast("Orden antigua archivada")
                            requests()
                        }.show()
                }, false)
            }
            if (orphanCount > 0) label("Estas órdenes antiguas no tienen solicitud vinculada. Se archivan como historial de órdenes, no como solicitudes nuevas.", 13)
        }
        if (role == "Administrador") button("Ver solicitudes archivadas", ::archivedRequests, false)
    }

    private fun archivedRequests() {
        if (!administratorOnly()) return
        page("Solicitudes archivadas", ::home)
        val a = arr("requests")
        var shown = 0
        for (i in 0 until a.length()) {
            val r = a.getJSONObject(i)
            if (viewModel.isActiveRequest(r)) continue
            shown++
            label("Solicitud ${i + 1} · ${r.optString("device")}", 18, true)
            label("Cliente: ${r.optString("owner")}\nProblema: ${r.optString("issue")}\nEstado: ${r.optString("status")}\nOrden: ${r.optString("orderNumber", "Sin orden")}")
            button("Eliminar solicitud archivada", {
                if (role != "Administrador") return@button
                android.app.AlertDialog.Builder(this)
                    .setTitle("¿Eliminar solicitud definitivamente?")
                    .setMessage("Esta solicitud se borrará de las solicitudes archivadas y del historial de solicitudes del cliente. No se eliminarán las órdenes de trabajo ni las notificaciones relacionadas. Esta acción no se puede deshacer.")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Eliminar definitivamente") { _, _ ->
                        if (role != "Administrador") return@setPositiveButton
                        val latest = arr("requests")
                        if (i !in 0 until latest.length() || !latest.getJSONObject(i).optBoolean("archived")) {
                            toast("La solicitud ya no está archivada")
                            archivedRequests()
                            return@setPositiveButton
                        }
                        val remaining = JSONArray()
                        for (j in 0 until latest.length()) {
                            if (j != i) remaining.put(latest.getJSONObject(j))
                        }
                        // Las órdenes referencian el índice de la solicitud: mantener
                        // sus referencias válidas al eliminar un elemento del arreglo.
                        val orders = arr("work_orders")
                        for (j in 0 until orders.length()) {
                            val order = orders.getJSONObject(j)
                            if (order.has("requestIndex")) {
                                val oldIndex = order.optInt("requestIndex", -1)
                                when {
                                    oldIndex == i -> order.remove("requestIndex")
                                    oldIndex > i -> order.put("requestIndex", oldIndex - 1)
                                }
                            }
                        }
                        // La orden se conserva como historial, pero no reaparece
                        // en la cola del técnico al borrar su solicitud archivada.
                        val deletedNumber = latest.getJSONObject(i).optString("orderNumber")
                        for (j in 0 until orders.length()) {
                            if (orders.getJSONObject(j).optString("number") == deletedNumber && deletedNumber.isNotBlank()) {
                                orders.getJSONObject(j).put("archived", true)
                            }
                        }
                        save("work_orders", orders)
                        save("requests", remaining)
                        toast("Solicitud eliminada definitivamente")
                        archivedRequests()
                    }.show()
            }, false)
            button("Restaurar a solicitudes activas", {
                if (role != "Administrador") return@button
                val latest = arr("requests")
                if (i < latest.length()) {
                    latest.getJSONObject(i).put("archived", false)
                    setOrderArchivedForRequest(latest.getJSONObject(i), false)
                    save("requests", latest)
                    toast("Solicitud restaurada")
                }
                archivedRequests()
            }, false)
            space()
        }
        if (shown == 0) {
            label("No hay solicitudes archivadas.")
            label("Las solicitudes eliminadas definitivamente no se pueden recuperar desde esta pantalla. Las órdenes de trabajo conservadas no equivalen a solicitudes archivadas.", 14)
        }
        button("Volver a solicitudes activas", ::requests, false)
    }
    private fun updateStatus() {
        if (!technicianOnly()) return
        orders()
    }
    private fun addEvent(serial: String, description: String) {
        if (serial.isBlank()) return
        val events = arr("events")
        events.put(JSONObject().put("serial",serial).put("detail",description)
            .put("date",java.text.SimpleDateFormat("dd/MM/yyyy HH:mm",java.util.Locale.getDefault()).format(java.util.Date())))
        save("events",events)
    }
    private fun deviceEvents(serial: String) {
        val deviceArray=arr("devices")
        val permitted=(0 until deviceArray.length()).any {
            val d=deviceArray.getJSONObject(it)
            d.optString("serial")==serial && visibleDevice(d)
        }
        if (!permitted) { home(); return }
        page("Historial de eventos",::devices)
        val events=arr("events")
        var found=false
        for(i in events.length()-1 downTo 0) {
            val e=events.getJSONObject(i)
            if(e.optString("serial")!=serial) continue
            label("${e.optString("date")} · ${e.optString("detail")}")
            found=true
        }
        if(!found) label("Sin eventos adicionales. Consulta también el historial inicial del equipo.")
    }
    private fun reports() {
        page("Informes técnicos",::home)
        val a=arr("devices")
        var found=false
        for(i in 0 until a.length()) {
            val d=a.getJSONObject(i)
            if(!visibleDevice(d)) continue
            found=true
            label(d.optString("name"),19,true)
            label("Diagnóstico e informe: ${d.optString("report","Pendiente de diagnóstico")}")
            label("Garantía: ${d.optString("warranty","Por revisar")}")
            button("Ver detalle", { detail(i) }, false)
        }
        if(!found) label("No tienes equipos con informes disponibles.")
    }
    private fun editReport() {
        if (!technicianOnly()) return
        page("Registrar informe técnico",::home)
        val a=arr("devices")
        for(i in 0 until a.length()) {
            val d=a.getJSONObject(i)
            label("${d.optString("name")} · ${d.optString("serial")}",18,true)
            val report=field("Diagnóstico y trabajos realizados")
            report.setText(d.optString("report"))
            val warranty=field("Garantía / vencimiento")
            warranty.setText(d.optString("warranty"))
            val maintenance=field("Próxima mantención")
            maintenance.setText(d.optString("maintenance"))
            button("Guardar informe") {
                if (!requireDevicePhoto(d.optString("serial"))) return@button
                if(report.text.toString().trim().length<10) { report.error="Escribe al menos 10 caracteres"; return@button }
                if(warranty.text.isBlank()) { warranty.error="Indica garantía o sin garantía"; return@button }
                if(maintenance.text.isBlank()) { maintenance.error="Indica fecha o no aplica"; return@button }
                val current=arr("devices")
                current.getJSONObject(i).put("report",report.text.toString().trim())
                    .put("warranty",warranty.text.toString().trim())
                    .put("maintenance",maintenance.text.toString().trim())
                save("devices",current)
                addEvent(d.optString("serial"),"Informe técnico y garantía actualizados")
                toast("Informe disponible para el cliente")
                editReport()
            }
            space()
        }
    }
    private fun createQuote() {
        if (!technicianOnly()) return
        page("Nueva cotización",::home)
        val devices=arr("devices")
        for(i in 0 until devices.length()) {
            val d=devices.getJSONObject(i)
            label(d.optString("name"),18,true)
            val description=field("Trabajo propuesto")
            val price=field("Precio en CLP",true)
            button("Enviar cotización al cliente") {
                if (!requireDevicePhoto(d.optString("serial"))) return@button
                val desc=description.text.toString().trim()
                val amount=price.text.toString().toLongOrNull()
                val errors = viewModel.validateQuote(desc, price.text.toString())
                description.error = errors["description"]
                price.error = errors["price"]
                if (errors.isNotEmpty() || amount == null) { toast("Corrige los campos indicados"); return@button }
                if (!viewModel.createQuote(repository, d, desc, amount, role)) {
                    toast("No se pudo guardar la cotización")
                    return@button
                }
                addEvent(d.optString("serial"),"Cotización enviada al cliente: $desc")
                addNotice(d.optString("owner"), "Cotización pendiente", "Tienes una cotización de $$amount para ${d.optString("name")}.")
                toast("Cotización creada")
                home()
            }
        }
    }
    private fun manageRequests() {
        if (!technicianOnly()) return
        page("Atender solicitudes", ::home)
        val requests = arr("requests")
        if (requests.length() == 0) label("No hay solicitudes registradas")
        for (i in 0 until requests.length()) {
            val r = requests.getJSONObject(i)
            if (r.optBoolean("archived")) continue
            label("Solicitud ${i+1} · ${r.optString("device")}", 18, true)
            label("Cliente: ${r.optString("owner")}\nProblema: ${r.optString("issue")}\nEstado: ${r.optString("status")}")
            val linked = r.optString("orderNumber")
            if (linked.isNotBlank()) {
                label("Orden generada: $linked", 14, true)
            } else if (r.optString("status") != "Rechazada") {
                button("Aceptar y generar orden de trabajo") {
                    if (!technicianOnly()) return@button
                    val latest = arr("requests")
                    if (i >= latest.length()) { manageRequests(); return@button }
                    val request = latest.getJSONObject(i)
                    if (request.optString("orderNumber").isNotBlank()) {
                        toast("Esta solicitud ya tiene una orden")
                        manageRequests()
                        return@button
                    }
                    val number = viewModel.acceptRequest(repository, i)
                    if (number == null) {
                        toast("La solicitud ya fue atendida o no está disponible")
                        manageRequests()
                        return@button
                    }
                    addNotice(request.optString("owner"), "Solicitud aceptada", "Se creó la orden $number para ${request.optString("device")}.")
                    toast("Orden $number creada correctamente")
                    manageRequests()
                }
                button("Rechazar solicitud") {
                    if (!technicianOnly()) return@button
                    val latest = arr("requests")
                    val request = latest.getJSONObject(i)
                    if (request.optString("orderNumber").isNotBlank()) { manageRequests(); return@button }
                    if (!viewModel.rejectRequest(repository, i)) {
                        toast("No se puede rechazar esta solicitud")
                        manageRequests()
                        return@button
                    }
                    addNotice(request.optString("owner"), "Solicitud rechazada", "La solicitud de ${request.optString("device")} fue rechazada.")
                    manageRequests()
                }
            }
            space()
        }
        button("Ver órdenes de trabajo", ::orders)
    }
    private fun adminDevices() {
        if (role != "Administrador") { home(); return }
        page("Administrar equipos", ::home)
        label("Solo el administrador puede eliminar equipos. Esta acción es permanente.", 14)
        val devices = arr("devices")
        if (devices.length() == 0) label("No hay equipos registrados")
        for (i in 0 until devices.length()) {
            val d = devices.getJSONObject(i)
            val serial = d.optString("serial")
            label("${d.optString("name")} · Serie: $serial", 17, true)
            button("Eliminar equipo $serial", {
                android.app.AlertDialog.Builder(this)
                    .setTitle("¿Eliminar equipo?")
                    .setMessage("Se eliminará ${d.optString("name")} ($serial), su fotografía, historial y eventos asociados. No se puede deshacer.")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Eliminar") { _, _ ->
                        if (role != "Administrador") { toast("Acceso denegado"); return@setPositiveButton }
                        if (!viewModel.deleteDevice(repository, serial, role)) {
                            toast("No se pudo eliminar el equipo")
                            return@setPositiveButton
                        }
                        toast("Equipo eliminado")
                        adminDevices()
                    }.show()
            }, false)
        }
    }
    private fun dashboard() {
        if (!administratorOnly()) return
        page("Panel administrativo",::home)
        label("Equipos registrados: ${arr("devices").length()}",20,true)
        val allRequests = arr("requests")
        val archivedCount = (0 until allRequests.length()).count { allRequests.getJSONObject(it).optBoolean("archived") }
        label("Solicitudes activas: ${allRequests.length() - archivedCount}",20,true)
        label("Solicitudes archivadas: $archivedCount",20,true)
        label("Autorizaciones: ${arr("authorizations").length()}",20,true)
        label("Información almacenada en este dispositivo.")
        button("Ver solicitudes",::requests)
        button("Ver archivadas",::archivedRequests)
    }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if(role.isEmpty()) login() else home() }
}
