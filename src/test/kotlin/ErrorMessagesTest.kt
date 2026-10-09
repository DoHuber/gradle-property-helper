import kotlin.test.*
import org.junit.jupiter.api.Test
import java.nio.file.*

class ErrorMessagesTest {
    @Test fun `permission failures identify required directory access`() {
        val message = ErrorMessages.describe(AccessDeniedException("/project/.properties-helper-123.tmp"))
        assertTrue(message.contains("Permission denied"))
        assertTrue(message.contains("/project/.properties-helper-123.tmp"))
        assertTrue(message.contains("write access to the project directory"))
    }
    @Test fun `missing paths and unsupported atomic moves have distinct messages`() {
        assertTrue(ErrorMessages.describe(NoSuchFileException("/missing/file")).contains("File or directory not found"))
        assertTrue(ErrorMessages.describe(AtomicMoveNotSupportedException("temp", "target", "unsupported")).contains("does not support atomic"))
        assertEquals("IllegalStateException: changed", ErrorMessages.describe(IllegalStateException("changed")))
    }
}
