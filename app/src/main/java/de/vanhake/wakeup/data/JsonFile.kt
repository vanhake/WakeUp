package de.vanhake.wakeup.data

import android.content.Context
import android.util.Log
import androidx.core.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException

/**
 * Atomically written JSON file. By default in device-protected storage (readable before the
 * first unlock, needed for alarms); with [deviceProtected] = false in the normal,
 * credential-encrypted storage that only opens after the user unlocked the phone.
 */
class JsonFile(context: Context, name: String, deviceProtected: Boolean = true) {
    private val file = AtomicFile(File(baseDir(context, deviceProtected), name))

    companion object {
        fun baseDir(context: Context, deviceProtected: Boolean): File {
            val app = context.applicationContext
            return if (deviceProtected) app.createDeviceProtectedStorageContext().filesDir else app.filesDir
        }
    }

    /** File content, or null if it does not exist or cannot be read. */
    fun read(): String? = try {
        String(file.readFully(), Charsets.UTF_8)
    } catch (e: FileNotFoundException) {
        null
    } catch (e: Exception) {
        Log.e("JsonFile", "Could not read ${file.baseFile}", e)
        null
    }

    fun delete() = file.delete()

    fun exists() = file.baseFile.exists()

    fun write(json: String) {
        val out = file.startWrite()
        try {
            out.write(json.toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
    }
}
