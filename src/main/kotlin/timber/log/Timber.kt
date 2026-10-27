package timber.log

import com.drxwnify.desktop.data.Log

/** The phone's logging calls from the shared modules land in the desktop log. */
object Timber {
    class Tree(private val tag: String?) {
        fun v(message: String?, vararg args: Any?) = Log.write('V', tag, fmt(message, args), null)
        fun d(message: String?, vararg args: Any?) = Log.write('D', tag, fmt(message, args), null)
        fun i(message: String?, vararg args: Any?) = Log.write('I', tag, fmt(message, args), null)
        fun w(message: String?, vararg args: Any?) = Log.write('W', tag, fmt(message, args), null)
        fun w(t: Throwable?, message: String? = null, vararg args: Any?) = Log.write('W', tag, fmt(message, args), t)
        fun w(t: Throwable?) = Log.write('W', tag, null, t)
        fun e(message: String?, vararg args: Any?) = Log.write('E', tag, fmt(message, args), null)
        fun e(t: Throwable?, message: String? = null, vararg args: Any?) = Log.write('E', tag, fmt(message, args), t)
        fun e(t: Throwable?) = Log.write('E', tag, null, t)
    }

    private val root = Tree(null)

    fun tag(tag: String): Tree = Tree(tag)
    fun v(message: String?, vararg args: Any?) = root.v(message, *args)
    fun d(message: String?, vararg args: Any?) = root.d(message, *args)
    fun i(message: String?, vararg args: Any?) = root.i(message, *args)
    fun w(message: String?, vararg args: Any?) = root.w(message, *args)
    fun w(t: Throwable?, message: String? = null, vararg args: Any?) = root.w(t, message, *args)
    fun w(t: Throwable?) = root.w(t)
    fun e(message: String?, vararg args: Any?) = root.e(message, *args)
    fun e(t: Throwable?, message: String? = null, vararg args: Any?) = root.e(t, message, *args)
    fun e(t: Throwable?) = root.e(t)

    private fun fmt(message: String?, args: Array<out Any?>): String? =
        if (message == null || args.isEmpty()) message else runCatching { message.format(*args) }.getOrDefault(message)
}
