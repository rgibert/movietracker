package ca.gibert.movietracker.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Small JSON-file cache. It is deliberately simpler than a database: these are
 * read-mostly snapshots that can be re-fetched at any time.
 */
class CacheStore(
    context: Context,
    private val json: Json,
) {

    private val directory = File(context.filesDir, "snapshots")
    private val mutex = Mutex()

    suspend fun readWatchlist(): CachedWatchlist? =
        read(WATCHLIST_FILE, CachedWatchlist.serializer())

    suspend fun writeWatchlist(value: CachedWatchlist) =
        write(WATCHLIST_FILE, CachedWatchlist.serializer(), value)

    suspend fun readLookups(): CachedLookups? =
        read(LOOKUPS_FILE, CachedLookups.serializer())

    suspend fun writeLookups(value: CachedLookups) =
        write(LOOKUPS_FILE, CachedLookups.serializer(), value)

    suspend fun readDiary(): CachedDiary? =
        read(DIARY_FILE, CachedDiary.serializer())

    suspend fun writeDiary(value: CachedDiary) =
        write(DIARY_FILE, CachedDiary.serializer(), value)

    suspend fun readMovieDetail(movieId: Int): MovieDetail? =
        read(detailFile(movieId), MovieDetail.serializer())

    suspend fun writeMovieDetail(movieId: Int, value: MovieDetail) =
        write(detailFile(movieId), MovieDetail.serializer(), value)

    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            directory.deleteRecursively()
            Unit
        }
    }

    private suspend fun <T> read(name: String, serializer: KSerializer<T>): T? =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val file = File(directory, name)
                if (!file.isFile) return@withLock null
                runCatching {
                    json.decodeFromString(serializer, file.readText())
                }.getOrNull()
            }
        }

    private suspend fun <T> write(name: String, serializer: KSerializer<T>, value: T) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                directory.mkdirs()
                val target = File(directory, name)
                val temp = File(directory, "$name.tmp")
                temp.writeText(json.encodeToString(serializer, value))
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }
            }
        }

    private fun detailFile(movieId: Int) = "movie_$movieId.json"

    private companion object {
        const val WATCHLIST_FILE = "watchlist.json"
        const val LOOKUPS_FILE = "lookups.json"
        const val DIARY_FILE = "diary.json"
    }
}
