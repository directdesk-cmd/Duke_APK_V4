package com.gemofgemma.core.postbox

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID

/**
 * Duke addition (Duke.APK Version 4, change 2): the estate postbox client.
 *
 * A small client for the Google Apps Script mailbox that Puppy's poller
 * drains. The wire shapes mirror the proven Duke-side sender
 * (_____Duke_Postbox_Sender_V1.PY, Duke original work): every request is a
 * POST of one JSON object carrying the shared secret; commands ride as
 * kind "command" with the same record fields the poller already executes;
 * results return as a result_manifest + result_chunk package that is
 * reassembled and verified by length and SHA-256 before it is trusted.
 *
 * The phone is mailbox "wolf": it sends to "puppy" for commands and pulls
 * its own mailbox for results. Seat messages are addressed to the seat's
 * own mailbox ("duke", "kevin") - Kevin is a destination, never the
 * switch; routing is the mailbox's mechanical work.
 *
 * No secret is baked into the application: the shared secret is typed
 * once in Settings and kept in PostboxStore.
 */
object PostboxStore {
    private const val PREFS = "duke_postbox"
    private const val KEY_SECRET = "postbox_secret"
    private const val KEY_VOICE = "voice_enabled"

    fun getSecret(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SECRET, "") ?: ""

    fun setSecret(context: Context, secret: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_SECRET, secret.trim()).apply()
    }

    fun isVoiceEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_VOICE, true)

    fun setVoiceEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_VOICE, enabled).apply()
    }
}

data class PostboxMessage(
    val id: String,
    val from: String,
    val kind: String,
    val payloadText: String
)

class PostboxClient(private val context: Context) {

    companion object {
        // The estate mailbox endpoint (Apps Script web application).
        const val POSTBOX_URL =
            "https://script.google.com/macros/s/AKfycbzgZfo76qvxGCtsJ4ieKjde6YuoR5kp-SMhXx6MqKtHg4qXKCnfHmnIIcJcB174vh8/exec"
        const val MAILBOX = "wolf"
        const val AI_PROGRAMS = "C:\\Users\\Solomon M\\My Drive\\X\\AI_WorkSpace\\Ai_Programs"
    }

    private fun secret(): String = PostboxStore.getSecret(context)

    private fun requireSecret() {
        if (secret().length < 16) {
            throw IllegalStateException(
                "The postbox secret is not set - enter it once in Settings."
            )
        }
    }

    /** One POST to the mailbox, following redirects the way urllib does. */
    private suspend fun request(fields: JSONObject): JSONObject =
        withContext(Dispatchers.IO) {
            requireSecret()
            val body = fields
            body.put("secret", secret())
            var url = URL(POSTBOX_URL)
            var payload: ByteArray? = body.toString().toByteArray(Charsets.UTF_8)
            var method = "POST"
            var hops = 0
            while (true) {
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = method
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 30_000
                conn.readTimeout = 30_000
                if (payload != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use {
                        it.write(String(payload, Charsets.UTF_8))
                    }
                }
                val code = conn.responseCode
                if (code in intArrayOf(301, 302, 303, 307, 308)) {
                    val location = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (location == null || ++hops > 5) {
                        throw IllegalStateException("Postbox redirect failed (HTTP $code)")
                    }
                    url = URL(url, location)
                    if (code == 307 || code == 308) {
                        method = "POST" // keep the body
                    } else {
                        method = "GET"
                        payload = null
                    }
                    continue
                }
                val text = try {
                    conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
                } catch (e: Exception) {
                    conn.errorStream?.bufferedReader(Charsets.UTF_8)?.readText() ?: ""
                }
                conn.disconnect()
                val result = try {
                    JSONObject(text)
                } catch (e: Exception) {
                    throw IllegalStateException("Postbox returned a non-JSON response")
                }
                if (!result.optBoolean("ok", false)) {
                    throw IllegalStateException(
                        "Postbox refused the request: ${result.optString("error", "unknown_error")}"
                    )
                }
                return@withContext result
            }
            @Suppress("UNREACHABLE_CODE")
            throw IllegalStateException("Postbox request failed")
        }

    private suspend fun send(to: String, kind: String, clientId: String, payload: JSONObject): String {
        val fields = JSONObject()
            .put("action", "send")
            .put("to", to)
            .put("from", MAILBOX)
            .put("kind", kind)
            .put("client_id", clientId)
            .put("payload", payload)
        return request(fields).optString("id", "")
    }

    /** Send one command record to Puppy; the poller files and runs it. */
    suspend fun sendCommand(
        commandId: String,
        shell: String,
        command: String,
        timeoutSeconds: Int = 120
    ): String {
        val record = JSONObject()
            .put("command_id", commandId)
            .put("shell", shell)
            .put("command", command)
            .put("return_to", MAILBOX)
            .put("timeout_seconds", timeoutSeconds)
        return send("puppy", "command", commandId, record)
    }

    /** A seat-addressed chat message (Duke or Kevin are destinations). */
    suspend fun sendSeatMessage(seat: String, text: String): String {
        val payload = JSONObject()
            .put("text", text)
            .put("from", MAILBOX)
        return send(seat, "chat", UUID.randomUUID().toString(), payload)
    }

    private suspend fun pullPage(offset: Int, limit: Int = 20): Pair<List<JSONObject>, Int> {
        val fields = JSONObject()
            .put("action", "pull")
            .put("to", MAILBOX)
            .put("limit", limit)
            .put("offset", offset)
        val result = request(fields)
        val messages = mutableListOf<JSONObject>()
        val array: JSONArray = result.optJSONArray("messages") ?: JSONArray()
        for (i in 0 until array.length()) {
            array.optJSONObject(i)?.let { messages.add(it) }
        }
        return messages to result.optInt("remaining_count", messages.size)
    }

    private suspend fun pullAll(maxMessages: Int = 200): List<JSONObject> {
        val all = mutableListOf<JSONObject>()
        var offset = 0
        while (all.size < maxMessages) {
            val (page, total) = pullPage(offset)
            if (page.isEmpty()) break
            all.addAll(page)
            offset += page.size
            if (offset >= total) break
        }
        return all
    }

    suspend fun ack(ids: List<String>) {
        val unique = ids.filter { it.isNotEmpty() }.distinct()
        var start = 0
        while (start < unique.size) {
            val batch = JSONArray()
            unique.subList(start, minOf(start + 20, unique.size)).forEach { batch.put(it) }
            request(
                JSONObject()
                    .put("action", "ack")
                    .put("to", MAILBOX)
                    .put("ids", batch)
            )
            start += 20
        }
    }

    /** The wolf mailbox, newest shapes flattened for display. */
    suspend fun pullInbox(): List<PostboxMessage> {
        return pullAll().map { message ->
            val payload = message.optJSONObject("payload")
            PostboxMessage(
                id = message.optString("id", ""),
                from = message.optString("from", ""),
                kind = message.optString("kind", ""),
                payloadText = payload?.toString(1) ?: message.optString("payload", "")
            )
        }
    }

    /**
     * Wait for a command's result package, reassemble it, and verify it
     * by length and SHA-256 - the sender discipline, mirrored.
     * Returns (result_text, output_text).
     */
    suspend fun collectResult(commandId: String, timeoutMs: Long = 120_000): Pair<String, String> {
        val deadline = System.currentTimeMillis() + timeoutMs
        var manifest: JSONObject? = null
        var manifestId = ""
        val chunks = mutableMapOf<Int, String>()
        val chunkIds = mutableMapOf<Int, String>()
        while (System.currentTimeMillis() < deadline) {
            val messages = pullAll()
            val statusIds = mutableListOf<String>()
            for (message in messages) {
                val payload = message.optJSONObject("payload") ?: continue
                if (payload.optString("command_id") != commandId) continue
                val kind = message.optString("kind", "")
                val messageId = message.optString("id", "")
                when (kind) {
                    "command_status" -> {
                        if (payload.optString("status") == "rejected") {
                            throw IllegalStateException("Puppy rejected the command $commandId")
                        }
                        if (messageId.isNotEmpty()) statusIds.add(messageId)
                    }
                    "result_manifest" -> {
                        manifest = payload
                        manifestId = messageId
                    }
                    "result_chunk" -> {
                        val index = payload.optInt("index", -1)
                        if (index >= 0) {
                            chunks[index] = payload.optString("text", "")
                            chunkIds[index] = messageId
                        }
                    }
                }
            }
            if (statusIds.isNotEmpty()) ack(statusIds)
            val ready = manifest
            if (ready != null) {
                val total = ready.optInt("total_chunks", -1)
                val totalChars = ready.optInt("total_chars", -1)
                if (total >= 1 && chunks.keys == (0 until total).toSet()) {
                    val combined = buildString {
                        for (i in 0 until total) append(chunks[i])
                    }
                    check(combined.length == totalChars) {
                        "Result package length does not match its manifest"
                    }
                    check(sha256Hex(combined) == ready.optString("sha256")) {
                        "Result package hash does not match its manifest"
                    }
                    val pack = JSONObject(combined)
                    val ids = mutableListOf(manifestId)
                    ids.addAll(chunkIds.values)
                    ack(ids)
                    return pack.optString("result_text", "") to pack.optString("output_text", "")
                }
            }
            delay(3_000)
        }
        throw IllegalStateException("Timed out waiting for the result of $commandId")
    }

    private fun sha256Hex(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    // -- The estate's own operations, composed in the proven shapes ------

    /** Read the Queue Manager's state file through the lane. */
    suspend fun readQueueStateJson(): String {
        val commandId = "App_QM_State_" + System.currentTimeMillis()
        sendCommand(
            commandId, "powershell",
            "Get-Content -LiteralPath '$AI_PROGRAMS\\Queue_Manager_State_V1.JSON' -Raw",
            timeoutSeconds = 60
        )
        val (_, output) = collectResult(commandId, 90_000)
        return output
    }

    /** Drop one flat Queue Manager action (the proven file pattern). */
    suspend fun sendQueueAction(action: JSONObject) {
        val stamp = System.currentTimeMillis()
        val actionJson = action.toString().replace("'", "''")
        val ps = buildString {
            append("\$J='").append(actionJson).append("'\n")
            append("\$P='").append(AI_PROGRAMS)
            append("\\Queue_Manager_In\\Action_App_").append(stamp).append(".JSON'\n")
            append("[IO.File]::WriteAllText(\$P, \$J, (New-Object Text.UTF8Encoding(\$false)))\n")
            append("Write-Output 'ACTION_WRITTEN'")
        }
        val commandId = "App_QM_Action_$stamp"
        sendCommand(commandId, "powershell", ps, timeoutSeconds = 60)
        collectResult(commandId, 60_000)
    }

    /**
     * The voice switch: persist the choice on the phone and carry the
     * flip to Puppy as a postbox control command - the flag file is
     * written and the flip is receipted into the voice switch record
     * (Kevin's record family). The Voice Player honors the flag from
     * its next version (Solomon, 2026-10-24 11:39).
     */
    suspend fun setVoiceEnabled(enabled: Boolean) {
        PostboxStore.setVoiceEnabled(context, enabled)
        val word = if (enabled) "on" else "off"
        val ps = buildString {
            append("\$Dir='").append(AI_PROGRAMS).append("'\n")
            append("[IO.File]::WriteAllText((Join-Path \$Dir '_____Voice_Enabled_V1.TXT'), '")
            append(word).append("', (New-Object Text.UTF8Encoding(\$false)))\n")
            append("\$Rec = @{ at = (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'); enabled = ")
            append(if (enabled) "\$true" else "\$false")
            append("; source = 'wolf_app' } | ConvertTo-Json -Compress\n")
            append("[IO.File]::AppendAllText((Join-Path \$Dir 'Voice_Switch_Record_V1.JSONL'), ")
            append("\$Rec + \"`n\", (New-Object Text.UTF8Encoding(\$false)))\n")
            append("Write-Output 'VOICE_FLAG_WRITTEN'")
        }
        val commandId = "App_Voice_" + System.currentTimeMillis()
        sendCommand(commandId, "powershell", ps, timeoutSeconds = 60)
        collectResult(commandId, 60_000)
    }
}
