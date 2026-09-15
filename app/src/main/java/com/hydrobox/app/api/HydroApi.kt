package com.hydrobox.app.api

import com.hydrobox.app.config.HydroBoxEnvironment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class ApiSensor(
    val id: Int,
    val nombre: String,
    val codigo: String?,
    val tipo: String?,
    val unidad: String?,
    val descripcion: String?
)

data class ApiHortaliza(
    val id: Int,
    val nombre: String
)

data class ApiMedicion(
    val ph: Float?,
    val orp: Float?,
    val waterTemp: Float?,
    val airTemp: Float?,
    val humidity: Float?,
    val level: Float?,
    val fecha: String?
)

class HttpException(val code: Int, body: String?) : Exception("HTTP $code: $body")

object HydroApi {

    private val baseUrl: String
        get() = HydroBoxEnvironment.current.api.baseUrl

    private suspend fun request(
        method: String,
        path: String,
        body: JSONObject? = null
    ): String = withContext(Dispatchers.IO) {
        val url = URL("$baseUrl/${path.trimStart('/')}")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 15_000
            doInput = true
            setRequestProperty("Accept", "application/json")

            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }

        if (body != null) {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use {
                it.write(body.toString())
            }
        }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()

        if (code !in 200..299) {
            throw HttpException(code, text)
        }
        text
    }

    private fun firstObject(raw: String): JSONObject? {
        if (raw.isBlank()) return null
        val root = JSONTokener(raw).nextValue()
        return when (root) {
            is JSONObject -> root
            is JSONArray  -> if (root.length() > 0 && root.get(0) is JSONObject) root.getJSONObject(0) else null
            else          -> null
        }
    }

    private fun arrayOfObjects(raw: String): List<JSONObject> {
        if (raw.isBlank()) return emptyList()
        val root = JSONTokener(raw).nextValue()
        return when (root) {
            is JSONArray -> (0 until root.length()).mapNotNull { idx -> root.optJSONObject(idx) }
            is JSONObject -> listOf(root)
            else -> emptyList()
        }
    }

    // -------------------- HORTALIZAS --------------------

    suspend fun getHortalizaActual(): ApiHortaliza? {
        val raw = request("GET", "hortaliza/actual")
        val obj = firstObject(raw) ?: return null

        val id   = obj.optInt("id_hortaliza", obj.optInt("id", 0))
        val name = obj.optString("nombre", "")
        if (id == 0 || name.isBlank()) return null
        return ApiHortaliza(id = id, nombre = name)
    }

    suspend fun cambiarHortaliza(idHortaliza: Int): Boolean {
        val body = JSONObject().apply { put("id_hortaliza", idHortaliza) }
        return try {
            request("POST", "hortaliza/cambiar", body)
            true
        } catch (_: Exception) {
            false
        }
    }

    // -------------------- HISTORIAL --------------------

    suspend fun getRegistroMediciones(): List<ApiMedicion> {
        val raw = request("GET", "registro-mediciones")
        val list = arrayOfObjects(raw)
        return list.map { obj ->
            val ph        = obj.optDouble("ph_value", Double.NaN)
            val ce        = obj.optDouble("ce_value", Double.NaN)
            val tagua     = obj.optDouble("tagua_value", Double.NaN)
            val us        = obj.optDouble("us_value", Double.NaN)
            val tam       = obj.optDouble("tam_value", Double.NaN)
            val hum       = obj.optDouble("hum_value", Double.NaN)
            val orpField  = if (obj.has("orp_value")) obj.optDouble("orp_value", Double.NaN) else ce

            val fechaStr  = obj.optString("fecha", "")
            val fecha     = fechaStr.ifBlank { null }

            ApiMedicion(
                ph        = ph.takeIf { !it.isNaN() }?.toFloat(),
                orp       = orpField.takeIf { !it.isNaN() }?.toFloat(),
                waterTemp = tagua.takeIf { !it.isNaN() }?.toFloat(),
                airTemp   = tam.takeIf { !it.isNaN() }?.toFloat(),
                humidity  = hum.takeIf { !it.isNaN() }?.toFloat(),
                level     = us.takeIf { !it.isNaN() }?.toFloat(),
                fecha     = fecha
            )
        }
    }

    suspend fun getSensores(): List<ApiSensor> {
        val raw = request("GET", "sensores")
        val list = arrayOfObjects(raw)
        return list.map { obj ->
            val id = obj.optInt("id", obj.optInt("id_sensor", 0))

            val nombre = obj.optString("nombre",
                obj.optString("name", "")
            )

            val codigo = obj.optString("codigo", "")
                .takeIf { it.isNotBlank() }

            val tipo = (if (obj.has("tipo"))
                obj.optString("tipo", "")
            else
                obj.optString("type", "")
                    ).takeIf { it.isNotBlank() }

            val unidad = (if (obj.has("unidad"))
                obj.optString("unidad", "")
            else
                obj.optString("unit", "")
                    ).takeIf { it.isNotBlank() }

            val descripcion = (if (obj.has("descripcion"))
                obj.optString("descripcion", "")
            else
                obj.optString("description", "")
                    ).takeIf { it.isNotBlank() }

            ApiSensor(
                id = id,
                nombre = if (nombre.isBlank()) "Sensor $id" else nombre,
                codigo = codigo,
                tipo = tipo,
                unidad = unidad,
                descripcion = descripcion
            )
        }
    }
}
