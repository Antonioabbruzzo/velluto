package com.velluto.tv.data

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Copia del catalogo salvata sul dispositivo (un file per account, compresso).
 * All'avvio l'app la mostra subito e intanto scarica la versione aggiornata dal provider:
 * con provider lenti la differenza è tra pochi secondi e diversi minuti di attesa.
 */
class CatalogCache(private val dir: File) {

    private fun fileFor(account: Account): File {
        val id = MessageDigest.getInstance("SHA-256")
            .digest("${account.baseUrl}|${account.username}".toByteArray())
            .take(8).joinToString("") { "%02x".format(it) }
        return File(dir, "catalog-$id.bin")
    }

    /** Il catalogo salvato e, per ogni elenco ("Canali", "Serie", "Film"), quando è stato scaricato. */
    data class Cached(val catalog: Catalog, val updatedAt: Map<String, Long>)

    fun load(account: Account): Cached? {
        val f = fileFor(account)
        if (!f.exists()) return null
        return try {
            DataInputStream(BufferedInputStream(GZIPInputStream(f.inputStream()), 256 * 1024)).use { input ->
                val version = input.readInt()
                if (version !in 1..VERSION) return null
                val savedAt = input.readLong()
                // La versione 1 non aveva le date per elenco: usiamo la data di salvataggio per tutti.
                val times = if (version >= 2) {
                    PARTS.associateWith { input.readLong() }
                } else {
                    PARTS.associateWith { savedAt }
                }
                val catalog = Catalog(
                    liveCategories = input.readList { Category(readStr(), readStr()) },
                    live = input.readList { LiveChannel(readInt(), readInt(), readStr(), readStr(), readStr()) },
                    movieCategories = input.readList { Category(readStr(), readStr()) },
                    movies = input.readList {
                        Movie(
                            id = readInt(), name = readStr(), image = readStr(), categoryId = readStr(),
                            rating = readStr(), ext = readStr(), added = readLong(),
                            backdrop = readStr(), plot = readStr(), year = readStr(),
                        )
                    },
                    seriesCategories = input.readList { Category(readStr(), readStr()) },
                    series = input.readList {
                        Series(
                            id = readInt(), name = readStr(), image = readStr(), categoryId = readStr(),
                            rating = readStr(), plot = readStr(), year = readStr(), backdrop = readStr(),
                            lastModified = readLong(), genre = readStr(),
                        )
                    },
                )
                Cached(catalog, times)
            }
        } catch (e: Exception) {
            f.delete() // file rovinato: verrà riscaricato
            null
        }
    }

    @Synchronized
    fun save(account: Account, c: Catalog, updatedAt: Map<String, Long>) {
        dir.mkdirs()
        val target = fileFor(account)
        val tmp = File(dir, target.name + ".tmp")
        DataOutputStream(BufferedOutputStream(GZIPOutputStream(tmp.outputStream()), 256 * 1024)).use { out ->
            out.writeInt(VERSION)
            out.writeLong(System.currentTimeMillis())
            PARTS.forEach { out.writeLong(updatedAt[it] ?: 0L) }
            out.writeList(c.liveCategories) { writeStr(it.id); writeStr(it.name) }
            out.writeList(c.live) { writeInt(it.id); writeInt(it.num); writeStr(it.name); writeStr(it.icon); writeStr(it.categoryId) }
            out.writeList(c.movieCategories) { writeStr(it.id); writeStr(it.name) }
            out.writeList(c.movies) {
                writeInt(it.id); writeStr(it.name); writeStr(it.image); writeStr(it.categoryId)
                writeStr(it.rating); writeStr(it.ext); writeLong(it.added)
                writeStr(it.backdrop); writeStr(it.plot); writeStr(it.year)
            }
            out.writeList(c.seriesCategories) { writeStr(it.id); writeStr(it.name) }
            out.writeList(c.series) {
                writeInt(it.id); writeStr(it.name); writeStr(it.image); writeStr(it.categoryId)
                writeStr(it.rating); writeStr(it.plot); writeStr(it.year); writeStr(it.backdrop)
                writeLong(it.lastModified); writeStr(it.genre)
            }
        }
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    fun clear(account: Account) {
        fileFor(account).delete()
    }

    companion object {
        /** Gli elenchi del catalogo, ognuno con la sua data di aggiornamento. */
        val PARTS = listOf("Canali", "Serie", "Film")

        private const val VERSION = 2

        private fun DataOutputStream.writeStr(s: String) {
            val b = s.toByteArray(Charsets.UTF_8)
            writeInt(b.size)
            write(b)
        }

        private fun DataInputStream.readStr(): String {
            val n = readInt()
            val b = ByteArray(n)
            readFully(b)
            return String(b, Charsets.UTF_8)
        }

        private inline fun <T> DataOutputStream.writeList(list: List<T>, writeItem: DataOutputStream.(T) -> Unit) {
            writeInt(list.size)
            for (element in list) writeItem(this, element)
        }

        private inline fun <T> DataInputStream.readList(readItem: DataInputStream.() -> T): List<T> {
            val n = readInt()
            val out = ArrayList<T>(n)
            repeat(n) { out.add(readItem(this)) }
            return out
        }
    }
}
