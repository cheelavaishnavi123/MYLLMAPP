import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object NoteUtil {

    // Create a new note as .txt file in Downloads with the specified title
    fun createNote(context: Context, title: String, content: String, date: String): Boolean {
        // Sanitize title to avoid invalid filename characters
        val safeTitle = title.replace("[^a-zA-Z0-9_\\-]".toRegex(), "_")
        val finalTitle = if (safeTitle.endsWith(".txt", ignoreCase = true)) safeTitle else "$safeTitle.txt"
        val noteFile = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            finalTitle
        )

        return try {
            FileOutputStream(noteFile).use { fos ->
                val fullContent = "Date: $date\n\n$content"
                fos.write(fullContent.toByteArray())
            }
            true
        } catch (e: IOException) {
            Log.e("NoteUtil", "Failed to create note '$finalTitle': ${e.message}")
            false
        }
    }

    // Read all note files from Downloads
    fun readNotes(context: Context): MutableList<Note> {
        val notes = mutableListOf<Note>()
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val files = dir.listFiles { file -> file.name.endsWith(".txt") }
            ?: return notes

        for (file in files) {
            try {
                val content = file.readText()
                val parts = content.split("\n\n", limit = 2)
                val dateLine = parts.getOrNull(0)?.removePrefix("Date: ") ?: "Unknown"
                val body = parts.getOrNull(1) ?: ""
                val title = file.name.removeSuffix(".txt")
                notes.add(Note(title, body, dateLine))
            } catch (e: Exception) {
                Log.e("NoteUtil", "Failed to read note '${file.name}': ${e.message}")
            }
        }
        return notes
    }

    // Update note content by rewriting the file
    fun updateNote(context: Context, title: String, newContent: String, newDate: String): Boolean {
        val safeTitle = title.replace("[^a-zA-Z0-9_\\-]".toRegex(), "_")
        val finalTitle = if (safeTitle.endsWith(".txt", ignoreCase = true)) safeTitle else "$safeTitle.txt"
        val noteFile = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            finalTitle
        )
        if (!noteFile.exists()) return false

        return try {
            FileOutputStream(noteFile).use { fos ->
                val updatedContent = "Date: $newDate\n\n$newContent"
                fos.write(updatedContent.toByteArray())
            }
            true
        } catch (e: IOException) {
            Log.e("NoteUtil", "Failed to update note '$finalTitle': ${e.message}")
            false
        }
    }

    // Delete the note file
    fun deleteNote(context: Context, title: String): Boolean {
        val safeTitle = title.replace("[^a-zA-Z0-9_\\-]".toRegex(), "_")
        val finalTitle = if (safeTitle.endsWith(".txt", ignoreCase = true)) safeTitle else "$safeTitle.txt"
        val noteFile = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            finalTitle
        )
        return noteFile.delete()
    }
}

// Plain Note data model
data class Note(val title: String, val content: String, val date: String)









































//import android.content.Context
//import android.os.Environment
//import android.util.Log
//import java.io.File
//import java.io.FileOutputStream
//import java.io.IOException
//import java.util.*
//
//object NoteUtil {
//
//    // Create a new note as .txt file in Downloads
//    fun createNote(context: Context, content: String, date: String): Boolean {
//        val id = UUID.randomUUID().toString()
//        val noteFile = File(
//            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
//            "note_$id.txt"
//        )
//
//        return try {
//            FileOutputStream(noteFile).use { fos ->
//                val fullContent = "Date: $date\n\n$content"
//                fos.write(fullContent.toByteArray())
//            }
//            true
//        } catch (e: IOException) {
//            Log.e("NoteUtil", "Failed to create note: ${e.message}")
//            false
//        }
//    }
//
//    // Read all note files (starting with "note_") from Downloads
//    fun readNotes(context: Context): MutableList<Note> {
//        val notes = mutableListOf<Note>()
//        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
//        val files = dir.listFiles { file -> file.name.startsWith("note_") && file.name.endsWith(".txt") }
//            ?: return notes
//
//        for (file in files) {
//            try {
//                val content = file.readText()
//                val parts = content.split("\n\n", limit = 2)
//                val dateLine = parts.getOrNull(0)?.removePrefix("Date: ") ?: "Unknown"
//                val body = parts.getOrNull(1) ?: ""
//                val id = file.name.removePrefix("note_").removeSuffix(".txt")
//                notes.add(Note(id, body, dateLine))
//            } catch (e: Exception) {
//                Log.e("NoteUtil", "Failed to read note '${file.name}': ${e.message}")
//            }
//        }
//        return notes
//    }
//
//    // Update note content by rewriting the file
//    fun updateNote(context: Context, id: String, newContent: String, newDate: String): Boolean {
//        val noteFile = File(
//            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
//            "note_$id.txt"
//        )
//        if (!noteFile.exists()) return false
//
//        return try {
//            FileOutputStream(noteFile).use { fos ->
//                val updatedContent = "Date: $newDate\n\n$newContent"
//                fos.write(updatedContent.toByteArray())
//            }
//            true
//        } catch (e: IOException) {
//            Log.e("NoteUtil", "Failed to update note: ${e.message}")
//            false
//        }
//    }
//
//    // Delete the note file
//    fun deleteNote(context: Context, id: String): Boolean {
//        val noteFile = File(
//            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
//            "note_$id.txt"
//        )
//        return noteFile.delete()
//    }
//}
//
//// Plain Note data model
//data class Note(val id: String, val content: String, val date: String)
