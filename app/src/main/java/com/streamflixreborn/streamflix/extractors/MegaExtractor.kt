package com.streamflixreborn.streamflix.extractors

import com.streamflixreborn.streamflix.models.Video
import androidx.media3.common.MimeTypes
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class MegaExtractor : Extractor() {
    override val name = "Mega"
    override val mainUrl = "https://mega.nz"

    private val apiUrl = "https://g.api.mega.co.nz/cs"

    override suspend fun extract(url: String): Video {
        // 1. Extraer ID y Key de la URL
        // Ejemplo: https://mega.nz/embed/yE8DlTAL#K4uPH6HTOWqAdSRtA4K-Mbht_e7X-FJws9lIqSaPKXI
        val regex = Regex("mega\\.nz/(?:embed|file)/([^#?]+)#([^#?&]+)")
        val match = regex.find(url) ?: throw Exception("URL de Mega no válida")

        val fileId = match.groupValues[1]
        val fileKeyBase64 = match.groupValues[2]

        // 2. Preparar la petición a la API de Mega
        val jsonRequest = JSONArray().put(
            JSONObject().apply {
                put("a", "g")    // Acción: Get
                put("g", 1)    // ¡IMPORTANTE!: Solicitar enlace de descarga (g-link)
                put("p", fileId) // Public handle (ID del archivo)
                put("ssl", 1)    // Forzar HTTPS en el enlace resultante
            }
        ).toString()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://g.api.mega.co.nz/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(MegaService::class.java)
        val body = jsonRequest.toRequestBody("application/json".toMediaType())

        val response = service.getMetaData(body)
        val responseBody = response.string()

        val jsonResponse = JSONArray(responseBody).getJSONObject(0)

        if (jsonResponse.has("e")) {
            val error = jsonResponse.getInt("e")
            throw Exception("Error de la API de Mega: $error")
        }

        val downloadUrl = jsonResponse.getString("g")
        val fileSize = jsonResponse.getLong("s")
        val encryptedAttributes = jsonResponse.getString("at")

        // 3. Descifrar Atributos (Nombre del archivo)
        val fileKey = decryptBase64(fileKeyBase64)
        val fileName = try {
            decryptAttributes(encryptedAttributes, fileKey)
        } catch (e: Exception) {
            "Mega_Video_$fileId.mp4"
        }

        return Video(
            source = downloadUrl,
            type = MimeTypes.VIDEO_MP4,
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "X-Mega-Key" to fileKeyBase64,
                "X-Mega-Size" to fileSize.toString()
            )
        )
    }

    private fun decryptAttributes(at: String, key: ByteArray): String {
        val atBytes = decryptBase64(at)

        // La clave de Mega para atributos es especial (XOR de las dos mitades de la clave de 32 bytes)
        val derivedKey = ByteArray(16)
        for (i in 0 until 16) {
            derivedKey[i] = (key[i].toInt() xor key[i + 16].toInt()).toByte()
        }

        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(derivedKey, "AES"), IvParameterSpec(ByteArray(16)))
        val decrypted = cipher.doFinal(atBytes)

        // Quitar prefijo "MEGA" y parsear JSON
        val attrStr = String(decrypted, Charsets.UTF_8).substringAfter("MEGA")
        val json = JSONObject(attrStr.substringBeforeLast("}") + "}")
        return json.optString("n", "video.mp4")
    }

    private fun decryptBase64(base64: String): ByteArray {
        val normalized = base64.replace("-", "+").replace("_", "/")
        val padded = when (normalized.length % 4) {
            2 -> "$normalized=="
            3 -> "$normalized="
            else -> normalized
        }
        return Base64.getDecoder().decode(padded)
    }

    interface MegaService {
        @POST("cs")
        @Headers("Content-Type: application/json")
        suspend fun getMetaData(
            @Body body: okhttp3.RequestBody,
            @Query("id") id: Int = 0
        ): okhttp3.ResponseBody
    }
}
