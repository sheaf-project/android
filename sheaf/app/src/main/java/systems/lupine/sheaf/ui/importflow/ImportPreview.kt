package systems.lupine.sheaf.ui.importflow

/** A member the preview found, offered for per-member selection. */
data class PreviewMember(val id: String, val name: String)

/**
 * One options field the user can turn on or off.
 *
 * [key] is the backend field name. Every category is written into the submit
 * body whether or not it was shown: the backend forbids unknown fields but
 * still expects the known ones, and a hidden category's [default] is the value
 * it should carry. [visible] and [default] are independent, because PluralKit
 * shows front history unchecked while Simply Plural hides what its export
 * doesn't carry.
 */
data class ImportCategory(
    val key: String,
    val label: String,
    val visible: Boolean,
    val default: Boolean,
    /** Shown under the row while it is checked. */
    val note: String? = null,
)

/** "Groups" + 4 -> a visible "Groups (4)" row, checked by default. */
internal fun counted(
    key: String,
    label: String,
    count: Int,
    default: Boolean = count > 0,
    note: String? = null,
) = ImportCategory(
    key = key,
    label = "$label ($count)",
    visible = count > 0,
    default = default,
    note = note,
)

/**
 * Source-agnostic preview. Each [ImportSource] maps its own preview response
 * into this shape; the screen and the options body are written against it, so
 * a new import source is a descriptor rather than another screen.
 */
data class ImportPreview(
    val systemName: String? = null,
    /** A line above the options, e.g. OpenPlural's export lineage. */
    val headline: String? = null,
    /** Empty when the source doesn't offer picking individual members. */
    val members: List<PreviewMember> = emptyList(),
    /** What the export holds, which a truncated [members] list may undercount. */
    val memberCount: Int = members.size,
    /** Rows shown checked and disabled: these always import. */
    val locked: List<String> = emptyList(),
    val categories: List<ImportCategory> = emptyList(),
    /** Records with no Sheaf equivalent, e.g. "Notes (3), will be skipped". */
    val skipped: List<String> = emptyList(),
    /** Sheaf only: a complete-backup zip submits under a different source. */
    val archive: Boolean = false,
) {
    /** Initial checkbox state, including the categories that stay hidden. */
    fun defaults(): Map<String, Boolean> = categories.associate { it.key to it.default }
}
