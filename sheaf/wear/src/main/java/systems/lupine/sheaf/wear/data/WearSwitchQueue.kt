package systems.lupine.sheaf.wear.data

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * A switch the user committed on the watch while we couldn't reach the
 * server directly. The persisted form is line-delimited in tile_data
 * SharedPreferences as `uuid|createdAt|replaceFronts|memberIds_csv`.
 * Member ids are UUIDs (no pipes or commas), so a hand-rolled format
 * is fine and dodges dragging Moshi into yet another tiny structure.
 */
internal data class WearQueuedSwitch(
    val uuid: String,
    val memberIds: List<String>,
    val replaceFronts: Boolean,
    val createdAt: Long,
) {
    companion object {
        fun create(memberIds: List<String>, replaceFronts: Boolean): WearQueuedSwitch =
            WearQueuedSwitch(
                uuid = UUID.randomUUID().toString(),
                memberIds = memberIds,
                replaceFronts = replaceFronts,
                createdAt = System.currentTimeMillis(),
            )
    }
}

/**
 * Watch-side offline queue for front switches plus a phone-fallback
 * delivery path. The flow when [WearStore.switchFront] is invoked is:
 *
 *  1. Try the API directly. The common case; succeeds when the watch
 *     has its own network.
 *  2. If that throws (most often because the watch is offline), hand
 *     the switch to the phone via a DataLayer MessageEvent. The phone
 *     has a persistent PendingFrontSwitch queue + SyncWorker and is
 *     more likely than the watch to actually reach the server. The
 *     MessageEvent only succeeds if a connected node is reachable
 *     (BLE link up) — we treat send success as "the phone owns this
 *     now" and do not also queue locally.
 *  3. If the phone can't be reached either (BLE down, unpaired,
 *     companion process not running), persist locally on the watch
 *     and retry direct from the next [WearStore.refreshNow].
 *
 * Race avoidance: only one side ever owns a given switch. If the
 * MessageEvent send Task reports success we trust the phone; if it
 * reports failure the switch is local-only. So we never double-create
 * the same front from two replay paths.
 */
internal object WearSwitchQueue {
    private const val TAG = "SheafWearQueue"
    private const val PREFS = "tile_data"
    private const val KEY_QUEUE = "switch_queue"

    /** Mirror of `PhoneDataLayerService.PATH_QUEUE_SWITCH` on the phone side. */
    const val PATH_QUEUE_SWITCH = "/sheaf/queue-switch"

    fun snapshot(context: Context): List<WearQueuedSwitch> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_QUEUE, null)
            ?: return emptyList()
        return raw.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull(::parseLine)
            .toList()
    }

    fun enqueue(context: Context, switch: WearQueuedSwitch) {
        write(context, snapshot(context) + switch)
    }

    fun remove(context: Context, uuid: String) {
        write(context, snapshot(context).filter { it.uuid != uuid })
    }

    /**
     * Best-effort handoff to the phone via a DataLayer MessageEvent.
     * Returns true if the send Task succeeded (a connected node
     * accepted the message); we trust the phone's
     * `PhoneDataLayerService.onMessageReceived` from there. Returns
     * false on any error including no-connected-nodes, in which case
     * the caller falls back to [enqueue] for a local replay later.
     */
    suspend fun sendToPhone(context: Context, switch: WearQueuedSwitch): Boolean =
        suspendCoroutine { cont ->
            Wearable.getNodeClient(context).connectedNodes
                .addOnSuccessListener { nodes ->
                    val nodeId = nodes.firstOrNull()?.id
                    if (nodeId == null) {
                        Log.d(TAG, "sendToPhone: no connected nodes; queuing locally")
                        cont.resume(false)
                        return@addOnSuccessListener
                    }
                    val payload = encode(switch).toByteArray(Charsets.UTF_8)
                    Wearable.getMessageClient(context)
                        .sendMessage(nodeId, PATH_QUEUE_SWITCH, payload)
                        .addOnSuccessListener {
                            Log.i(TAG, "sendToPhone: delegated switch ${switch.uuid}")
                            cont.resume(true)
                        }
                        .addOnFailureListener { e ->
                            Log.w(TAG, "sendToPhone: send failed", e)
                            cont.resume(false)
                        }
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "sendToPhone: connectedNodes failed", e)
                    cont.resume(false)
                }
        }

    private fun write(context: Context, queue: List<WearQueuedSwitch>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_QUEUE, queue.joinToString("\n", transform = ::encode))
            .apply()
    }

    internal fun encode(s: WearQueuedSwitch): String =
        "${s.uuid}|${s.createdAt}|${if (s.replaceFronts) 1 else 0}|${s.memberIds.joinToString(",")}"

    // internal so the encode/decode round trip can be unit-tested: a bad parse
    // here silently drops a queued offline switch, or replays it with the wrong
    // member set / replace flag / createdAt (which becomes the front's
    // started_at on drain).
    internal fun parseLine(line: String): WearQueuedSwitch? {
        val parts = line.split('|', limit = 4)
        if (parts.size != 4) return null
        val uuid = parts[0]
        val createdAt = parts[1].toLongOrNull() ?: return null
        val replaceFronts = parts[2] == "1"
        val memberIds = parts[3].split(',').filter { it.isNotBlank() }
        if (uuid.isBlank() || memberIds.isEmpty()) return null
        return WearQueuedSwitch(uuid, memberIds, replaceFronts, createdAt)
    }
}
