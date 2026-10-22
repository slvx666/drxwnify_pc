package android.util

/** The two calls the shared phone files make, backed by java.util.Base64. */
object Base64 {
    const val DEFAULT = 0
    const val NO_WRAP = 2

    @JvmStatic
    fun encodeToString(input: ByteArray, flags: Int): String = java.util.Base64.getEncoder().encodeToString(input)

    @JvmStatic
    fun decode(str: String, flags: Int): ByteArray = java.util.Base64.getMimeDecoder().decode(str)
}
