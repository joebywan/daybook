package com.joebywan.daybook.web

import com.joebywan.daybook.data.KeyValueStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// localStorage can throw (Safari private windows, storage switched off); a store that cannot be read
// behaves as an empty one rather than taking the app down with it.
private fun lsGet(key: String): String? = js("(() => { try { return localStorage.getItem(key); } catch (e) { return null; } })()")

private fun lsSet(key: String, value: String): Boolean =
    js("(() => { try { localStorage.setItem(key, value); return true; } catch (e) { return false; } })()")

private fun lsRemove(key: String): Unit = js("(() => { try { localStorage.removeItem(key); } catch (e) {} })()")

/** Every localStorage key, joined by newlines (keys are ours, and never contain one). */
private fun lsKeys(): String =
    js("(() => { try { return Object.keys(localStorage).join('\\n'); } catch (e) { return ''; } })()")

/** Asks the browser not to evict this site's storage under pressure. Best effort, and silent. */
private fun requestPersistence(): Unit =
    js("(() => { try { navigator.storage && navigator.storage.persist && navigator.storage.persist().catch(() => {}); } catch (e) {} })()")

private val StoreJson = Json { ignoreUnknownKeys = true }
private val StringList = ListSerializer(String.serializer())

/**
 * [KeyValueStore] over `localStorage`. Every entry of file `name` lives under the key
 * `name.key`: a set as a JSON array, a string as itself. The page has one thread, so a
 * read-modify-write is already atomic.
 */
class LocalStorageKeyValueStore(private val name: String) : KeyValueStore {

    /** Bumped on every write, which is what makes the flows below re-read. */
    private val version = MutableStateFlow(0)

    private fun storageKey(key: String) = "$name.$key"

    private fun readSet(key: String): Set<String> =
        lsGet(storageKey(key))?.let { raw ->
            runCatching { StoreJson.decodeFromString(StringList, raw).toSet() }.getOrNull()
        }.orEmpty()

    override fun stringSet(key: String): Flow<Set<String>> =
        version.map { readSet(key) }.distinctUntilChanged()

    override suspend fun updateStringSet(key: String, transform: (Set<String>) -> Set<String>) {
        val before = readSet(key)
        val after = transform(before)
        if (after != before || lsGet(storageKey(key)) == null) {
            lsSet(storageKey(key), StoreJson.encodeToString(StringList, after.toList()))
            version.value++
        }
    }

    override fun string(key: String): Flow<String?> =
        version.map { lsGet(storageKey(key)) }.distinctUntilChanged()

    override suspend fun putString(key: String, value: String) {
        write(key, value)
    }

    /** [putString] without the suspension, for start-up code that is not in a coroutine. */
    fun write(key: String, value: String) {
        lsSet(storageKey(key), value)
        version.value++
    }

    /** Re-reads everything, after a backup has been restored underneath the store. */
    fun reload() {
        version.value++
    }
}

/** One store per file for the whole page, so the screens and the backup see the same one. */
object WebStores {
    private val stores = HashMap<String, LocalStorageKeyValueStore>()

    fun get(name: String): LocalStorageKeyValueStore = stores.getOrPut(name) { LocalStorageKeyValueStore(name) }

    fun init() {
        requestPersistence()
    }

    internal fun reloadAll() {
        stores.values.forEach { it.reload() }
    }
}

private fun downloadText(fileName: String, text: String): Unit = js("""(() => {
    const url = URL.createObjectURL(new Blob([text], { type: 'application/json' }));
    const a = document.createElement('a');
    a.href = url;
    a.download = fileName;
    a.style.display = 'none';
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 10000);
})()""")

private fun pickTextFile(onText: (String) -> Unit): Unit = js("""(() => {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = '.json,application/json';
    input.style.display = 'none';
    input.addEventListener('change', () => {
        const file = input.files && input.files[0];
        input.remove();
        if (file) file.text().then(t => onText(t), () => onText(''));
    });
    document.body.appendChild(input);
    input.click();
})()""")

/**
 * Progress as a file and back. The file is every `localStorage` entry the app owns, verbatim, so
 * it round-trips whatever the stores hold without this code knowing their formats.
 */
object Backup {
    private const val FORMAT = "daybook-backup"
    private val Entries = MapSerializer(String.serializer(), String.serializer())

    private fun ownKeys(): List<String> =
        lsKeys().split('\n').filter { key ->
            key.substringBefore('.') in listOf(KeyValueStore.PROGRESS, KeyValueStore.LAUNCH)
        }

    fun export(): String {
        val entries = ownKeys().associateWith { lsGet(it).orEmpty() }
        val payload = JsonObject(
            mapOf(
                "format" to JsonPrimitive(FORMAT),
                "version" to JsonPrimitive(1),
                "entries" to StoreJson.encodeToJsonElement(Entries, entries),
            )
        )
        downloadText("daybook-backup-${com.joebywan.daybook.platform.currentDate()}.json", payload.toString())
        return "Exported ${entries.size} entries."
    }

    /** Replaces this browser's progress with a backup's, once the player has picked the file. */
    fun import(onResult: (String) -> Unit) {
        pickTextFile { text -> onResult(restore(text)) }
    }

    internal fun restore(text: String): String {
        val root = runCatching { StoreJson.parseToJsonElement(text).jsonObject }.getOrNull()
        if (root == null || root["format"]?.jsonPrimitive?.content != FORMAT) {
            return "That file is not a Daybook backup."
        }
        val entries = runCatching {
            StoreJson.decodeFromJsonElement(Entries, root.getValue("entries"))
        }.getOrNull() ?: return "That backup is damaged."
        val wanted = entries.filterKeys { key ->
            key.substringBefore('.') in listOf(KeyValueStore.PROGRESS, KeyValueStore.LAUNCH)
        }
        ownKeys().forEach(::lsRemove)
        val failed = wanted.count { (key, value) -> !lsSet(key, value) }
        WebStores.reloadAll()
        return if (failed == 0) "Restored ${wanted.size} entries." else "Restored, but $failed entries did not fit."
    }
}
