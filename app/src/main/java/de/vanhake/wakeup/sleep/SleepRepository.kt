package de.vanhake.wakeup.sleep

import android.content.Context
import android.os.UserManager
import android.util.Log
import de.vanhake.wakeup.data.JsonFile
import de.vanhake.wakeup.sleep.analysis.NightRecording
import de.vanhake.wakeup.sleep.analysis.SleepAnalyzer
import de.vanhake.wakeup.sleep.analysis.SleepReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Short facts about a night for the history list. */
data class NightSummary(
    val start: Long,
    val end: Long,
    val sleepMinutes: Int,
    val snoreMinutes: Int,
    val apneasPerHour: Float?,
    val score: Int,
    val interrupted: Boolean,
    /** [SleepRepository.ANALYSIS_VERSION] the values were computed with. */
    val version: Int = SleepRepository.ANALYSIS_VERSION,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("start", start)
        put("end", end)
        put("sleepMinutes", sleepMinutes)
        put("snoreMinutes", snoreMinutes)
        apneasPerHour?.let { put("apneasPerHour", it.toDouble()) }
        put("score", score)
        put("interrupted", interrupted)
        put("version", version)
    }

    companion object {
        fun fromJson(o: JSONObject) = NightSummary(
            start = o.getLong("start"),
            end = o.getLong("end"),
            sleepMinutes = o.optInt("sleepMinutes"),
            snoreMinutes = o.optInt("snoreMinutes"),
            apneasPerHour = if (o.has("apneasPerHour")) o.getDouble("apneasPerHour").toFloat() else null,
            score = o.optInt("score"),
            interrupted = o.optBoolean("interrupted"),
            version = o.optInt("version", 1),
        )

        fun of(night: NightRecording, report: SleepReport) = NightSummary(
            start = night.start,
            end = night.end,
            sleepMinutes = report.sleepMinutes,
            snoreMinutes = report.snoreMinutes,
            apneasPerHour = report.apneasPerHour,
            score = report.score,
            interrupted = night.interrupted,
        )
    }
}

/**
 * Settings, recorded nights and audio samples of the sleep analysis, all stored on the device only.
 *
 * The settings live in device-protected storage, because the automatic start is scheduled again
 * right after a reboot, before the phone is unlocked. Nights, the running checkpoint and the audio
 * samples are health data and live in the normal, credential-encrypted storage, which only opens
 * after the first unlock. Until then [nights] is empty and nothing can be recorded.
 */
object SleepRepository {
    private const val TAG = "SleepRepository"
    /** Nights whose full recording (and so the detailed report) is kept. */
    private const val MAX_DETAILED_NIGHTS = 120

    /** Nights kept in the history (about ten years); only the summary of older ones. */
    private const val MAX_NIGHTS = 3660

    /** Increase when [SleepAnalyzer] changes, so stored summaries are computed again. */
    const val ANALYSIS_VERSION = 2
    const val CLIP_LIFETIME_MS = 24 * 60 * 60 * 1000L

    private const val SLEEP_DIR = "sleep"
    private const val SETTINGS_NAME = "settings.json"

    private lateinit var settingsFile: JsonFile
    private lateinit var indexFile: JsonFile
    private lateinit var checkpointFile: JsonFile
    private lateinit var appContext: Context
    lateinit var clipDir: File
        private set

    /** Whether nights and samples have been loaded, i.e. the phone was unlocked since boot. */
    @Volatile
    var available = false
        private set

    private val _settings = MutableStateFlow(SleepSettings())
    val settings: StateFlow<SleepSettings> = _settings.asStateFlow()

    private val _nights = MutableStateFlow<List<NightSummary>>(emptyList())

    /** Newest first. */
    val nights: StateFlow<List<NightSummary>> = _nights.asStateFlow()

    /**
     * Loads the settings and, once the phone has been unlocked since boot, the nights. Safe to
     * call repeatedly: a call after the unlock loads what a call before it had to leave out.
     */
    @Synchronized
    fun init(context: Context) {
        if (!::settingsFile.isInitialized) initSettings(context)
        if (!available && isUnlocked()) initPrivate()
    }

    private fun isUnlocked(): Boolean = appContext.getSystemService(UserManager::class.java).isUserUnlocked

    private fun initSettings(context: Context) {
        appContext = context.applicationContext
        File(JsonFile.baseDir(appContext, true), SLEEP_DIR).mkdirs()
        settingsFile = JsonFile(appContext, "$SLEEP_DIR/$SETTINGS_NAME")
        _settings.value = settingsFile.read()?.let {
            try {
                SleepSettings.fromJson(JSONObject(it))
            } catch (e: Exception) {
                Log.e(TAG, "Could not parse settings", e)
                null
            }
        } ?: SleepSettings()
    }

    private fun initPrivate() {
        migrateToCredentialStorage()
        clipDir = File(File(JsonFile.baseDir(appContext, false), SLEEP_DIR), "clips").apply { mkdirs() }
        indexFile = privateFile("index.json")
        checkpointFile = privateFile("current.json")
        _nights.value = indexFile.read()?.let {
            try {
                val arr = JSONArray(it)
                (0 until arr.length()).map { i -> NightSummary.fromJson(arr.getJSONObject(i)) }
            } catch (e: Exception) {
                Log.e(TAG, "Could not parse night index", e)
                null
            }
        }.orEmpty()
        available = true
        // A night whose recording was killed is saved with the data recorded so far.
        recoverCheckpoint()
        pruneExpired()
    }

    private fun privateFile(name: String) = JsonFile(appContext, "$SLEEP_DIR/$name", deviceProtected = false)

    /**
     * Earlier versions kept nights and samples in device-protected storage. Moves them once into
     * credential-encrypted storage. Copy and delete, because a rename between the two storages
     * fails: they use different encryption keys.
     */
    private fun migrateToCredentialStorage() {
        val old = File(JsonFile.baseDir(appContext, true), SLEEP_DIR)
        val new = File(JsonFile.baseDir(appContext, false), SLEEP_DIR)
        old.walkBottomUp().filter { it.isFile && !it.name.startsWith(SETTINGS_NAME) }.forEach { src ->
            val dest = File(new, src.relativeTo(old).path)
            try {
                dest.parentFile?.mkdirs()
                src.copyTo(dest, overwrite = true)
                dest.setLastModified(src.lastModified()) // clips expire 24 h after they were written
                src.delete()
            } catch (e: Exception) {
                Log.e(TAG, "Could not move ${src.name} to credential-encrypted storage", e)
            }
        }
        old.walkBottomUp().filter { it.isDirectory && it != old }.forEach { it.delete() }
    }

    @Synchronized
    fun updateSettings(settings: SleepSettings) {
        settingsFile.write(settings.toJson().toString())
        _settings.value = settings
        pruneExpired()
    }

    /** Deletes nights older than [SleepSettings.keepDays]. */
    @Synchronized
    fun pruneExpired(now: Long = System.currentTimeMillis()) {
        if (!available) return
        val expired = expiredNights(_nights.value.map { it.start }, _settings.value.keepDays, now).toSet()
        if (expired.isEmpty()) return
        expired.forEach { deleteFiles(it) }
        saveIndex(_nights.value.filterNot { it.start in expired })
    }

    /** Deletes every recorded night and audio sample. The settings stay. */
    @Synchronized
    fun deleteAllNights() {
        if (!available) return
        File(JsonFile.baseDir(appContext, false), SLEEP_DIR).listFiles().orEmpty()
            .filter { it.isFile && it.name.startsWith("night-") }
            .forEach { it.delete() }
        deleteAllClips()
        saveIndex(emptyList())
    }

    /** Analyses and stores a finished night. Nights shorter than 10 minutes are discarded. */
    @Synchronized
    fun saveNight(night: NightRecording): NightSummary? {
        if (!available) return null
        if (night.end - night.start < 10 * 60_000L) {
            night.clips.forEach { clipFile(it.file).delete() }
            return null
        }
        // Analyse what is stored (values rounded in the file), so the summary matches the report.
        val json = night.toJson()
        val summary = NightSummary.of(night, SleepAnalyzer.analyze(NightRecording.fromJson(json)))
        nightFile(night.start).write(json.toString())
        val all = (listOf(summary) + _nights.value.filterNot { it.start == night.start }).sortedByDescending { it.start }
        all.drop(MAX_DETAILED_NIGHTS).filter { nightFile(it.start).exists() }.forEach { deleteFiles(it.start) }
        all.drop(MAX_NIGHTS).forEach { deleteFiles(it.start) }
        saveIndex(all.take(MAX_NIGHTS))
        pruneExpired()
        return summary
    }

    /** Replaces the stored summary if the freshly computed [report] gives other values. */
    @Synchronized
    fun syncSummary(night: NightRecording, report: SleepReport) {
        val summary = NightSummary.of(night, report)
        val list = _nights.value
        if (list.none { it.start == night.start } || summary in list) return
        saveIndex(list.map { if (it.start == night.start) summary else it })
    }

    /**
     * Computes summaries again that were stored by an older analysis or differ from the
     * report (earlier versions analysed the unrounded data). Slow; call off the main thread.
     */
    fun refreshOutdatedSummaries() {
        if (!available) return
        val outdated = _nights.value.filter { it.version < ANALYSIS_VERSION }
        if (outdated.isEmpty()) return
        val fresh = outdated.associate { old ->
            old.start to (loadNight(old.start)?.let { NightSummary.of(it, SleepAnalyzer.analyze(it)) } ?: old.copy(version = ANALYSIS_VERSION))
        }
        synchronized(this) {
            saveIndex(_nights.value.map { fresh[it.start] ?: it })
        }
    }

    fun loadNight(start: Long): NightRecording? = if (!available) null else nightFile(start).read()?.let {
        try {
            NightRecording.fromJson(JSONObject(it))
        } catch (e: Exception) {
            Log.e(TAG, "Could not parse night $start", e)
            null
        }
    }

    @Synchronized
    fun deleteNight(start: Long) {
        if (!available) return
        deleteFiles(start)
        saveIndex(_nights.value.filterNot { it.start == start })
    }

    /** Only the plain file name counts, so a stored name can never point outside [clipDir]. */
    fun clipFile(name: String): File = File(clipDir, File(name).name)

    /** Stores the recording so far, so a night is not lost if the app gets killed. */
    fun writeCheckpoint(night: NightRecording) {
        if (!available) return
        try {
            checkpointFile.write(night.toJson().toString())
        } catch (e: Exception) {
            Log.w(TAG, "Could not write checkpoint", e)
        }
    }

    fun clearCheckpoint() {
        if (available) checkpointFile.delete()
    }

    /** Turns the checkpoint of a recording that was killed into a (shortened) night. */
    @Synchronized
    fun recoverCheckpoint() {
        if (!available) return
        val json = checkpointFile.read() ?: return
        clearCheckpoint()
        try {
            saveNight(NightRecording.fromJson(JSONObject(json)).copy(interrupted = true))
        } catch (e: Exception) {
            Log.e(TAG, "Could not recover interrupted night", e)
        }
    }

    /** Deletes audio samples older than 24 hours; returns when the next one expires, or null. */
    @Synchronized
    fun deleteExpiredClips(now: Long = System.currentTimeMillis()): Long? {
        if (!available) return null
        val files = clipDir.listFiles().orEmpty()
        var next: Long? = null
        for (f in files) {
            val expires = f.lastModified() + CLIP_LIFETIME_MS
            if (expires <= now) {
                f.delete()
            } else {
                next = minOf(next ?: expires, expires)
            }
        }
        return next
    }

    fun deleteAllClips() {
        if (!available) return
        clipDir.listFiles().orEmpty().forEach { it.delete() }
    }

    private fun nightFile(start: Long) = privateFile("night-$start.json")

    private fun deleteFiles(start: Long) {
        loadNight(start)?.clips?.forEach { clipFile(it.file).delete() }
        nightFile(start).delete()
    }

    private fun saveIndex(list: List<NightSummary>) {
        indexFile.write(JSONArray(list.map { it.toJson() }).toString())
        _nights.value = list
    }
}
