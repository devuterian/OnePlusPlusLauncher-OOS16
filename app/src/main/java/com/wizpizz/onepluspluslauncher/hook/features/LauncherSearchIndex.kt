package com.wizpizz.onepluspluslauncher.hook.features

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.LocaleList
import android.os.Process
import android.os.UserHandle
import android.util.Log
import com.wizpizz.onepluspluslauncher.hook.features.HookUtils.TAG
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Launcher-process state for app search: cached [AppSearchEngine.Entry] per app,
 * English labels (computed once in the background and kept on disk), and launch frecency.
 *
 * No timers or polling: work only happens when the user searches or launches an app.
 */
internal object LauncherSearchIndex {

    private const val ENGLISH_CACHE_FILE = "searchup_english_labels.tsv"
    private const val USAGE_PREFS = "searchup_usage"
    private const val USAGE_KEY = "data"
    private const val LAUNCH_DEDUPE_MS = 1500L

    private class EnglishLabel(val versionCode: Long, val label: String)

    /** id -> (entry, English label it was built with). */
    private val entries = ConcurrentHashMap<String, Pair<AppSearchEngine.Entry, String?>>()
    private val englishByComponent = ConcurrentHashMap<String, EnglishLabel>()
    private val verifiedComponents = ConcurrentHashMap.newKeySet<String>()
    private val pendingComponents = ConcurrentHashMap.newKeySet<String>()

    val usage = UsageRanking()

    @Volatile private var appContext: Context? = null
    @Volatile private var lastLaunchId: String? = null
    @Volatile private var lastLaunchAt = 0L

    private val worker: ExecutorService by lazy {
        Executors.newSingleThreadExecutor { runnable ->
            Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                runnable.run()
            }, "SearchUp-index").apply { isDaemon = true }
        }
    }

    fun ensureInitialized(context: Context) {
        if (appContext != null) return
        synchronized(this) {
            if (appContext != null) return
            val app = context.applicationContext ?: context
            try {
                usage.load(app.getSharedPreferences(USAGE_PREFS, Context.MODE_PRIVATE).getString(USAGE_KEY, null))
                readEnglishCache(app)
            } catch (e: Throwable) {
                Log.e(TAG, "[SearchIndex] init failed: ${e.message}")
            }
            appContext = app
        }
    }

    fun appId(component: ComponentName, user: UserHandle?): String =
        component.flattenToShortString() + "#" + (user?.hashCode() ?: 0)

    /** Returns a cached entry, rebuilding it only when the label or English label changed. */
    fun entryFor(id: String, component: ComponentName, label: String): AppSearchEngine.Entry {
        val english = englishByComponent[component.flattenToShortString()]?.label
        val cached = entries[id]
        if (cached != null && cached.first.label == label && cached.second == english) return cached.first
        val entry = AppSearchEngine.Entry(id, label, english?.takeIf { it.isNotEmpty() }, component.packageName)
        entries[id] = entry to english
        return entry
    }

    /**
     * Queues English-label lookups for apps whose label is not already Latin.
     * Called from the search thread; the actual lookup runs on a background thread.
     */
    fun requestEnglishLabels(components: List<ComponentName>) {
        val context = appContext ?: return
        val todo = ArrayList<ComponentName>()
        for (component in components) {
            val key = component.flattenToShortString()
            if (key in verifiedComponents) continue
            if (pendingComponents.add(key)) todo.add(component)
        }
        if (todo.isEmpty()) return
        worker.execute { resolveEnglishLabels(context, todo) }
    }

    fun recordLaunch(id: String) {
        val now = System.currentTimeMillis()
        if (id == lastLaunchId && now - lastLaunchAt < LAUNCH_DEDUPE_MS) return
        lastLaunchId = id
        lastLaunchAt = now
        usage.recordLaunch(id, now)
        val context = appContext ?: return
        try {
            context.getSharedPreferences(USAGE_PREFS, Context.MODE_PRIVATE)
                .edit().putString(USAGE_KEY, usage.serialize(now)).apply()
        } catch (e: Throwable) {
            Log.e(TAG, "[SearchIndex] saving usage failed: ${e.message}")
        }
    }

    /** Drops cached entries for apps that are no longer installed. Cheap when nothing changed. */
    fun retainOnly(current: List<Pair<Any, AppSearchEngine.Entry>>) {
        if (entries.size <= current.size) return
        val ids = HashSet<String>(current.size * 2)
        current.forEach { ids.add(it.second.id) }
        entries.keys.retainAll(ids)
    }

    private fun resolveEnglishLabels(context: Context, components: List<ComponentName>) {
        val started = System.currentTimeMillis()
        val pm = context.packageManager
        var computed = 0
        // One package at a time so only one package's resources are held in memory.
        for ((pkg, packageComponents) in components.groupBy { it.packageName }) {
            val version = try { pm.getPackageInfo(pkg, 0).longVersionCode } catch (_: Throwable) { -1L }
            var englishContext: Context? = null
            var contextLoaded = false
            for (component in packageComponents) {
                val key = component.flattenToShortString()
                try {
                    val cached = englishByComponent[key]
                    if (cached == null || cached.versionCode != version) {
                        if (!contextLoaded) {
                            englishContext = englishContext(context, pkg)
                            contextLoaded = true
                        }
                        englishByComponent[key] = EnglishLabel(version, englishLabel(pm, englishContext, component))
                        computed++
                    }
                } catch (_: Throwable) {
                } finally {
                    verifiedComponents.add(key)
                    pendingComponents.remove(key)
                }
            }
        }
        if (computed > 0) writeEnglishCache(context)
        Log.d(TAG, "[SearchIndex] English labels: checked=${components.size} computed=$computed in ${System.currentTimeMillis() - started}ms")
    }

    private fun englishContext(context: Context, pkg: String): Context? = try {
        val packageContext = context.createPackageContext(pkg, 0)
        val configuration = Configuration(packageContext.resources.configuration)
        configuration.setLocales(LocaleList(Locale.US))
        packageContext.createConfigurationContext(configuration)
    } catch (_: Throwable) {
        null
    }

    private fun englishLabel(pm: PackageManager, englishContext: Context?, component: ComponentName): String {
        if (englishContext == null) return ""
        val activityInfo = pm.getActivityInfo(component, PackageManager.MATCH_DISABLED_COMPONENTS)
        val labelRes = if (activityInfo.labelRes != 0) activityInfo.labelRes else activityInfo.applicationInfo.labelRes
        if (labelRes == 0) return ""
        val label = englishContext.resources.getText(labelRes)?.toString()?.trim().orEmpty()
        // Only keep labels that add something searchable in Latin letters.
        return if (label.any(KeyboardLayoutUtils::isLatinLetter)) label else ""
    }

    private fun readEnglishCache(context: Context) {
        val file = File(context.filesDir, ENGLISH_CACHE_FILE)
        if (!file.exists()) return
        file.forEachLine { line ->
            val parts = line.split('\t')
            if (parts.size == 3) {
                val version = parts[1].toLongOrNull() ?: return@forEachLine
                englishByComponent[parts[0]] = EnglishLabel(version, parts[2])
            }
        }
    }

    private fun writeEnglishCache(context: Context) {
        try {
            val builder = StringBuilder()
            englishByComponent.forEach { (key, value) ->
                builder.append(key).append('\t').append(value.versionCode).append('\t')
                    .append(value.label.replace('\t', ' ').replace('\n', ' ')).append('\n')
            }
            val target = File(context.filesDir, ENGLISH_CACHE_FILE)
            val temp = File(context.filesDir, "$ENGLISH_CACHE_FILE.tmp")
            temp.writeText(builder.toString())
            temp.renameTo(target)
        } catch (e: Throwable) {
            Log.e(TAG, "[SearchIndex] writing English cache failed: ${e.message}")
        }
    }
}
