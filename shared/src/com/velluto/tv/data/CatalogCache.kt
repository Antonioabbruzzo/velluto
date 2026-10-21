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

    fun load(account: Account): Catalog? {
        val f = fileFor(account)
        if (!f.exists()) return null
        return try {
            DataInputStream(BufferedInputStream(GZIPInputStream(f.inputStream()), 256 * 1024)).use { input ->
                if (input.readInt() != VERSION) return null
                input.readLong() // data di salvataggio
                Catalog(
                    liveCategories = input.readList { Category(readStr(), readStr()) },
                    live = input.readList { LiveChannel(readInt(), readInt(), readStr(), readStr(), readStr()) },
                    movieCategories = input.readList { Category(readStr(), readStr()) },
                    movies = input.readList {
                        Movie(
                            id = readInt(), name = readStr(), image = readStr(), categoryId = readStr(),
                            rating = readStr(), ext = readStr(), added = readLong(),
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
            }
        } catch (e: Exception) {
            f.delete() // file rovinato: verrà riscaricato
            null
        }
    }

    @Synchronized
    fun save(account: Account, c: Catalog) {
        dir.mkdirs()
        val target = fileFor(account)
        val tmp = File(dir, target.name + ".tmp")
        DataOutputStream(BufferedOutputStream(GZIPOutputStream(tmp.outputStream()), 256 * 1024)).use { out ->
            out.writeInt(VERSION)
            out.writeLong(System.currentTimeMillis())
            out.writeList(c.liveCategories) { writeStr(it.id); writeStr(it.name) }
            out.writeList(c.live) { writeInt(it.id); writeInt(it.num); writeStr(it.name); writeStr(it.icon); writeStr(it.categoryId) }
            out.writeList(c.movieCategories) { writeStr(it.id); writeStr(it.name) }
            out.writeList(c.movies) {
                writeInt(it.id); writeStr(it.name); writeStr(it.image); writeStr(it.categoryId)
                writeStr(it.rating); writeStr(it.ext); writeLong(it.added)
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

    private companion object {
        const val VERSION = 1

        fun DataOutputStream.writeStr(s: String) {
            val b = s.toByteArray(Charsets.UTF_8)
            writeInt(b.size)
            write(b)
        }

        fun DataInputStream.readStr(): String {
            val n = readInt()
            val b = ByteArray(n)
            readFully(b)
            return String(b, Charsets.UTF_8)
        }

        inline fun <T> DataOutputStream.writeList(list: List<T>, write: DataOutputStream.(T) -> Unit) {
            writeInt(list.size)
            list.forEach { write(it) }
        }

        inline fun <T> DataInputStream.readList(read: DataInputStream.() -> T): List<T> {
            val n = readInt()
            val out = ArrayList<T>(n)
            repeat(n) { out.add(read()) }
            return out
        }
    }
}
