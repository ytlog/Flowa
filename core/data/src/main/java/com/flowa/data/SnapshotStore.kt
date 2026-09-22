package com.flowa.data

import android.util.AtomicFile
import com.flowa.domain.AppState
import org.json.JSONObject
import java.io.File

/** State and its encrypted credential commit together, so a failed write cannot mix versions. */
internal class SnapshotStore(file: File, private val secrets: SecretStore) {
    private val snapshot = AtomicFile(file)

    fun read(): AppState? {
        if (!snapshot.baseFile.exists() && !File(snapshot.baseFile.path + ".bak").exists()) return null
        val original = snapshot.openRead().bufferedReader().use { JSONObject(it.readText()) }
        val migrated = SnapshotMigrations.migrate(original)
        val key = if (migrated.has("encryptedApiKey")) secrets.decrypt(migrated.getString("encryptedApiKey"))
        else secrets.readLegacy()
        val state = JsonCodec.decode(migrated, key)
        if (migrated !== original || !original.has("encryptedApiKey")) write(state)
        return state
    }

    fun write(state: AppState) {
        val bytes = JsonCodec.encode(state)
            .put("encryptedApiKey", secrets.encrypt(state.settings.apiKey))
            .toString().toByteArray()
        val stream = snapshot.startWrite()
        try {
            stream.write(bytes)
            snapshot.finishWrite(stream)
        } catch (failure: Throwable) {
            snapshot.failWrite(stream)
            throw failure
        }
    }
}
