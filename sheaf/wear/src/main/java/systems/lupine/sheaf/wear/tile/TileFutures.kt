package systems.lupine.sheaf.wear.tile

import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/**
 * ListenableFuture wrapping a value that's already known. Tiles use these
 * to satisfy the synchronous-friendly TileService contract without pulling
 * in Guava's full Futures.immediateFuture, which would land in the
 * shared-process tile classpath.
 */
internal class ImmediateTileFuture<T>(private val value: T) : ListenableFuture<T> {
    override fun addListener(r: Runnable, e: Executor) = e.execute(r)
    override fun isDone() = true
    override fun isCancelled() = false
    override fun cancel(b: Boolean) = false
    override fun get(): T = value
    override fun get(t: Long, u: TimeUnit): T = value
}

internal fun <T> immediateTileFuture(value: T): ListenableFuture<T> = ImmediateTileFuture(value)
