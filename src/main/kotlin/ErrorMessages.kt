import java.nio.file.AccessDeniedException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileSystemException
import java.nio.file.NoSuchFileException

object ErrorMessages {
    fun describe(error: Exception): String = when (error) {
        is AccessDeniedException -> "Permission denied: ${error.file ?: "file access"}.\n" +
            "Check file and directory permissions. Saving requires write access to the project directory " +
            "to create a temporary file, a backup, and replace gradle.properties."
        is NoSuchFileException -> "File or directory not found: ${error.file ?: error.message}.\n" +
            "Check the selected project and JSON paths in Settings."
        is AtomicMoveNotSupportedException -> "This filesystem does not support atomic file replacement: ${error.file}.\n" +
            "Use a project location that supports atomic moves."
        is FileSystemException -> "${error.javaClass.simpleName}: ${error.message ?: "Filesystem operation failed."}"
        else -> "${error.javaClass.simpleName}: ${error.message ?: "Operation failed."}"
    }
}
