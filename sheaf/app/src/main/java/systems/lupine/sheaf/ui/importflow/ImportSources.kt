package systems.lupine.sheaf.ui.importflow

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.ImportJobSource
import systems.lupine.sheaf.data.model.PKApiPreviewBody
import systems.lupine.sheaf.ui.importcommon.jsonQuote

/** What the user has to supply before a preview can run. */
sealed interface ImportInput {
    /** Pick a file; the preview runs as soon as one is chosen. */
    data class File(val prompt: String, val help: String) : ImportInput

    /** Pick a file and type a passphrase, then preview on demand. */
    data class EncryptedFile(
        val help: String,
        val fieldLabel: String,
        val action: String,
    ) : ImportInput

    /** Type a secret, then preview on demand. */
    data class Token(
        val help: String,
        val fieldLabel: String,
        val footnote: String,
        val action: String,
    ) : ImportInput
}

/** The file part and/or secret a preview call needs. */
class PreviewRequest(
    val file: MultipartBody.Part?,
    val credential: String?,
)

/**
 * Everything that differs between one import source and the next.
 *
 * The flow itself (pick, preview, choose what to bring across, submit, poll,
 * report) is identical for all of them and lives in [ImportViewModel] and
 * [ImportScreen]. Adding a source means adding an entry to [importSources].
 */
data class ImportSource(
    /** Route slug, e.g. "simplyplural". */
    val id: String,
    /** One of the [ImportJobSource] constants. */
    val source: String,
    val label: String,
    val title: String,
    val subtitle: String,
    val input: ImportInput,
    val preview: suspend (SheafApiService, PreviewRequest) -> ImportPreview,
    val defaultFileName: String = "export.json",
    val previewError: String = "Preview failed — check the file and try again",
    /** Fixed options fields, as raw JSON values. */
    val constants: Map<String, String> = emptyMap(),
    /** Write `"member_ids":null` when the selection hasn't been narrowed. */
    val alwaysEmitMemberIds: Boolean = false,
    /** PluralKit's live pull goes to the credential endpoint, not the file one. */
    val credentialApi: Boolean = false,
    val restartLabel: String = "Import another file",
    /** Sheaf's complete-backup zip submits under a different source. */
    val submitSource: (ImportPreview) -> String = { source },
)

/** Onboarding links straight to this one, so it has a name outside the list. */
const val SIMPLY_PLURAL_SOURCE = "simplyplural"

private const val CONFLICT_SKIP = "conflict_strategy"

private val simplyPlural = ImportSource(
    id = SIMPLY_PLURAL_SOURCE,
    source = ImportJobSource.SIMPLYPLURAL_FILE,
    label = "Simply Plural",
    title = "Import from Simply Plural",
    subtitle = "Import members, groups, and history",
    input = ImportInput.File(
        prompt = "Choose your Simply Plural export file to get started.",
        help = "In Simply Plural, go to Settings → Account → Export Data → Request data export. " +
            "You'll receive the export by email — download the JSON file from there, then select it here.",
    ),
    preview = { api, req ->
        val s = api.previewSimplyPluralImport(req.file!!)
        ImportPreview(
            systemName = s.systemName,
            members = s.members.map { PreviewMember(it.id, it.name) },
            memberCount = s.memberCount,
            categories = listOf(
                ImportCategory(
                    key = "system_profile",
                    label = "System profile",
                    visible = s.systemName != null,
                    default = s.systemName != null,
                ),
                counted("groups", "Groups", s.groupCount),
                counted("custom_fronts", "Custom fronts", s.customFrontCount),
                ImportCategory(
                    key = "front_history",
                    label = "Front history (${s.frontHistoryCount} entries)",
                    visible = s.frontHistoryCount > 0,
                    default = s.frontHistoryCount > 0,
                ),
                counted("custom_fields", "Custom fields", s.customFieldCount),
            ),
            skipped = if (s.noteCount > 0) {
                listOf("Notes (${s.noteCount}) — not supported, will be skipped")
            } else {
                emptyList()
            },
        )
    },
)

private val sheaf = ImportSource(
    id = "sheaf",
    source = ImportJobSource.SHEAF_FILE,
    label = "Sheaf export",
    title = "Import from Sheaf Export",
    subtitle = "Restore from a Sheaf JSON backup",
    input = ImportInput.File(
        prompt = "Choose your Sheaf export to get started: the JSON export, or a complete backup zip that includes images.",
        help = "Export your data from Sheaf, then select the downloaded file here. A JSON export brings your data across; " +
            "a complete backup (zip) brings your images too.",
    ),
    defaultFileName = "sheaf_export.json",
    preview = { api, req -> sheafShapedPreview(api.previewSheafImport(req.file!!), lineage = false) },
    submitSource = { if (it.archive) ImportJobSource.SHEAF_ARCHIVE else ImportJobSource.SHEAF_FILE },
)

private val openPlural = ImportSource(
    id = "openplural",
    source = ImportJobSource.OPENPLURAL_FILE,
    label = "OpenPlural",
    title = "Import from OpenPlural",
    subtitle = "Use an OpenPlural .json or .openplural.zip export",
    input = ImportInput.File(
        prompt = "Choose an OpenPlural export from another compatible app: the plain .json, or an .openplural.zip backup that also includes images.",
        help = "OpenPlural is an interchange format shared across plural apps. A .json brings your data across; " +
            "an .openplural.zip brings your images too.",
    ),
    preview = { api, req -> sheafShapedPreview(api.previewOpenPluralImport(req.file!!), lineage = true) },
)

/** Sheaf and OpenPlural share a preview shape; only the lineage line differs. */
private fun sheafShapedPreview(
    s: systems.lupine.sheaf.data.model.SheafPreviewSummary,
    lineage: Boolean,
): ImportPreview {
    val locked = buildList {
        if (s.memberCount > 0) add("Members (${s.memberCount})")
        // Images ride with the records they belong to (avatars, banners), so
        // there's no toggle; the count is here so the user knows they're coming.
        if (s.archive && s.imageCount > 0) add("Images (${s.imageCount})")
    }
    return ImportPreview(
        headline = if (lineage && s.lineageLength > 0) {
            "Passed through ${s.lineageLength} prior export${if (s.lineageLength == 1) "" else "s"}."
        } else {
            null
        },
        locked = locked,
        archive = s.archive,
        categories = listOf(
            ImportCategory("system_profile", "System profile", visible = true, default = true),
            ImportCategory(
                key = "fronts",
                label = "Front history (${s.frontCount} entries)",
                visible = s.frontCount > 0,
                default = s.frontCount > 0,
            ),
            counted("groups", "Groups", s.groupCount),
            counted("tags", "Tags", s.tagCount),
            counted("custom_fields", "Custom fields", s.customFieldCount),
        ),
    )
}

private const val PK_HISTORY_NOTE =
    "Large switch logs can take a while to import — the job runs in the background and shows up under Import history while it's working."

private val pluralKitFile = ImportSource(
    id = "pluralkit",
    source = ImportJobSource.PLURALKIT_FILE,
    label = "PluralKit (file)",
    title = "Import from PluralKit (file)",
    subtitle = "Use a PK export JSON from `pk;export`",
    input = ImportInput.File(
        prompt = "Choose your PluralKit export JSON to get started.",
        help = "Export from PluralKit with the `pk;export` command in DM. PK sends you a download link; save the JSON and pick it here. " +
            "Front history can be very large — leave it unchecked unless you specifically want switch logs in Sheaf.",
    ),
    preview = { api, req ->
        val s = api.previewPluralKitFileImport(req.file!!)
        ImportPreview(
            locked = if (s.memberCount > 0) listOf("Members (${s.memberCount})") else emptyList(),
            categories = listOf(
                ImportCategory(
                    key = "system_profile",
                    label = "System profile",
                    visible = true,
                    default = s.systemName != null,
                ),
                counted("groups", "Groups", s.groupCount),
                ImportCategory(
                    key = "front_history",
                    label = "Front history (${s.switchCount} switches)",
                    visible = s.switchCount > 0,
                    // Switch logs can be huge; the user opts in explicitly.
                    default = false,
                    note = PK_HISTORY_NOTE,
                ),
            ),
        )
    },
)

private val pluralKitApi = ImportSource(
    id = "pluralkit-api",
    source = ImportJobSource.PLURALKIT_API,
    label = "PluralKit (API)",
    title = "Import from PluralKit (API)",
    subtitle = "Connect with your PK token to import live",
    input = ImportInput.Token(
        help = "Paste your PluralKit token to fetch your system directly. " +
            "Run `pk;token` in DM with PluralKit on Discord; PK will reply with the token.",
        fieldLabel = "PluralKit token",
        footnote = "The token is sent over HTTPS to Sheaf, used once for the import job, encrypted while the job runs, " +
            "and wiped on completion. It is never logged.",
        action = "Preview",
    ),
    credentialApi = true,
    restartLabel = "Import another system",
    previewError = "Couldn't reach PluralKit. Check your token and try again.",
    preview = { api, req ->
        val s = api.previewPluralKitApiImport(PKApiPreviewBody(req.credential.orEmpty()))
        ImportPreview(
            systemName = s.systemName,
            locked = if (s.memberCount > 0) listOf("Members (${s.memberCount})") else emptyList(),
            categories = listOf(
                ImportCategory(
                    key = "system_profile",
                    label = "System profile",
                    visible = true,
                    default = s.systemName != null,
                ),
                counted("groups", "Groups", s.groupCount),
                // The API preview's switch count is a sampled summary (PK
                // paginates them), so the row can't promise an exact number.
                ImportCategory(
                    key = "front_history",
                    label = "Front history (switch log)",
                    visible = true,
                    default = false,
                    note = "PluralKit will paginate the switch log over multiple requests; the import job continues in the " +
                        "background and shows up under Import history while it's running.",
                ),
            ),
        )
    },
)

private val tupperbox = ImportSource(
    id = "tupperbox",
    source = ImportJobSource.TUPPERBOX_FILE,
    label = "Tupperbox",
    title = "Import from Tupperbox",
    subtitle = "Use a Tupperbox export JSON from `tul!export`",
    defaultFileName = "tuppers.json",
    input = ImportInput.File(
        prompt = "Choose your Tupperbox export JSON to get started.",
        help = "Export from Tupperbox with the `tul!export` command in DM. Tupperbox sends back a JSON file with your tuppers and groups; pick it here. " +
            "Tupperbox exports don't carry fronting history or system-level metadata, so only members and groups land in Sheaf.",
    ),
    preview = { api, req ->
        val s = api.previewTupperboxImport(req.file!!)
        ImportPreview(
            locked = if (s.memberCount > 0) listOf("Tuppers (${s.memberCount})") else emptyList(),
            categories = listOf(counted("groups", "Groups", s.groupCount)),
        )
    },
)

private val pluralSpace = ImportSource(
    id = "pluralspace",
    source = ImportJobSource.PLURALSPACE_FILE,
    label = "PluralSpace",
    title = "Import from PluralSpace",
    subtitle = "Use a PluralSpace .zip data export",
    constants = mapOf(CONFLICT_SKIP to "\"skip\""),
    alwaysEmitMemberIds = true,
    input = ImportInput.File(
        prompt = "Choose your PluralSpace export to get started.",
        help = "In PluralSpace, open Settings → Data export and generate an export. " +
            "Download the resulting .zip, then select it here.",
    ),
    preview = { api, req ->
        val s = api.previewPluralSpaceImport(req.file!!)
        ImportPreview(
            systemName = s.systemName,
            members = s.members.map { PreviewMember(it.id, it.name) },
            memberCount = s.memberCount,
            categories = listOf(
                ImportCategory(
                    key = "system_profile",
                    label = "System profile",
                    visible = s.systemName != null,
                    default = s.systemName != null,
                ),
                ImportCategory("member_avatars", "Member avatars", visible = s.memberCount > 0, default = true),
                ImportCategory("roles_as_tags", "Roles as tags", visible = s.memberCount > 0, default = true),
                counted("groups", "Groups", s.groupCount),
                counted("custom_fronts", "Custom fronts", s.customFrontCount),
                counted("custom_fields", "Custom fields", s.customFieldCount),
                ImportCategory(
                    key = "fronts",
                    label = "Front history (${s.frontCount} entries)",
                    visible = s.frontCount > 0,
                    default = s.frontCount > 0,
                ),
                counted("journal_entries", "Journal entries", s.journalEntryCount),
                ImportCategory(
                    key = "chat_messages",
                    label = "Chat messages (${s.chatMessageCount}, collapsed to system board)",
                    visible = s.chatMessageCount > 0,
                    default = s.chatMessageCount > 0,
                ),
                counted("polls", "Polls", s.pollCount),
            ),
            skipped = if (s.thoughtCount > 0) {
                listOf("Thoughts (${s.thoughtCount}) — no Sheaf equivalent, will be skipped")
            } else {
                emptyList()
            },
        )
    },
)

private val prism = ImportSource(
    id = "prism",
    source = ImportJobSource.PRISM_FILE,
    label = "Prism",
    title = "Import from Prism",
    subtitle = "Use an encrypted .prism export and its passphrase",
    defaultFileName = "export.prism",
    constants = mapOf(CONFLICT_SKIP to "\"skip\""),
    alwaysEmitMemberIds = true,
    previewError = "Couldn't decrypt the export — check the passphrase and try again",
    input = ImportInput.EncryptedFile(
        help = "In Prism, go to Settings → Data → Export, choose a passphrase, and save the " +
            ".prism file. Select it here together with the same passphrase. The passphrase " +
            "is encrypted at rest while the import runs and wiped when it finishes.",
        fieldLabel = "Decryption passphrase",
        action = "Decrypt + preview",
    ),
    preview = { api, req ->
        val s = api.previewPrismImport(req.file!!, req.credential!!.toFormPart())
        val skippedCount = s.sleepSessionCount + s.habitCount + s.reminderCount
        ImportPreview(
            systemName = s.systemName,
            members = s.members.map { PreviewMember(it.id, it.name) },
            memberCount = s.memberCount,
            categories = listOf(
                ImportCategory(
                    key = "system_profile",
                    label = "System profile",
                    visible = s.systemName != null,
                    default = s.systemName != null,
                ),
                ImportCategory("member_avatars", "Member avatars", visible = s.memberCount > 0, default = true),
                counted("member_groups", "Member groups", s.groupCount),
                counted("custom_fields", "Custom fields", s.customFieldCount),
                ImportCategory(
                    key = "front_sessions",
                    label = "Front history (${s.frontSessionCount} sessions)",
                    visible = s.frontSessionCount > 0,
                    default = s.frontSessionCount > 0,
                ),
                ImportCategory(
                    key = "notes",
                    label = "Notes (${s.noteCount}, as journal entries)",
                    visible = s.noteCount > 0,
                    default = s.noteCount > 0,
                ),
                counted("polls", "Polls", s.pollCount),
                ImportCategory(
                    key = "conversations",
                    label = "Chat messages (${s.messageCount}, collapsed to system board)",
                    visible = s.conversationCount > 0,
                    default = s.conversationCount > 0,
                ),
                counted("member_board_posts", "Member board posts", s.memberBoardPostCount),
                counted("media_attachments", "Media attachments", s.mediaAttachmentCount),
            ),
            skipped = if (skippedCount > 0) {
                listOf(
                    "Skipped on import: ${s.sleepSessionCount} sleep, ${s.habitCount} habits, " +
                        "${s.reminderCount} reminders — no Sheaf surface for these yet.",
                )
            } else {
                emptyList()
            },
        )
    },
)

private val ampersand = ImportSource(
    id = "ampersand",
    source = ImportJobSource.AMPERSAND_FILE,
    label = "Ampersand",
    title = "Import from Ampersand",
    subtitle = "Use an Ampersand .json data export",
    constants = mapOf(CONFLICT_SKIP to "\"skip\""),
    alwaysEmitMemberIds = true,
    input = ImportInput.File(
        prompt = "Choose your Ampersand export to get started.",
        help = "In Ampersand, open Settings → Import & export → Export your data to a JSON file (note: this is a different option to 'Export your data', which " +
            "produces an incompatible file format), and save the .json file. Then select it here.",
    ),
    preview = { api, req ->
        val s = api.previewAmpersandImport(req.file!!)
        val boardLabel = buildString {
            append("Board messages")
            if (s.boardMessageCount > 0) append(" (${s.boardMessageCount})")
            if (s.pollCount > 0) append(" & polls (${s.pollCount})")
        }
        ImportPreview(
            locked = if (s.memberCount > 0) listOf("Members (${s.memberCount})") else emptyList(),
            categories = listOf(
                counted("groups", "Systems as groups", s.systemCount),
                counted("custom_fronts", "Custom fronts", s.customFrontCount),
                counted("tags", "Tags", s.tagCount),
                counted("custom_fields", "Custom fields", s.customFieldCount),
                ImportCategory(
                    key = "front_history",
                    label = "Front history (${s.frontHistoryCount} entries)",
                    visible = s.frontHistoryCount > 0,
                    default = s.frontHistoryCount > 0,
                ),
                counted("journals", "Journal entries", s.journalCount),
                counted("notes", "Notes", s.noteCount),
                ImportCategory(
                    key = "board_messages",
                    label = boardLabel,
                    visible = s.boardMessageCount > 0 || s.pollCount > 0,
                    default = s.boardMessageCount > 0 || s.pollCount > 0,
                ),
                counted("reminders", "Reminders", s.reminderCount),
                counted("images", "Images", s.assetCount),
            ),
            skipped = s.limitWarnings,
        )
    },
)

private val berryTree = ImportSource(
    id = "berrytree",
    source = ImportJobSource.BERRYTREE_FILE,
    label = "BerryTree",
    title = "Import from BerryTree",
    subtitle = "Experimental: use a BerryTree .json export",
    constants = mapOf(CONFLICT_SKIP to "\"skip\""),
    alwaysEmitMemberIds = true,
    input = ImportInput.File(
        prompt = "Choose your BerryTree export to get started.",
        help = "Export your data from BerryTree as JSON, then select the file here. This importer is " +
            "experimental: the preview lists anything in your file it can't read yet before " +
            "anything is imported.",
    ),
    preview = { api, req ->
        val s = api.previewBerryTreeImport(req.file!!)
        val x = mutableListOf<String>()
        s.unsupportedSections.forEach { x += "${it.name} (${it.count}) - not supported yet, will be skipped" }
        if (s.exportErrors.isNotEmpty()) {
            x += "BerryTree reported problems writing this export, so it was incomplete before it got here:"
            x += s.exportErrors
        }
        x += s.limitWarnings
        ImportPreview(
            systemName = s.systemName,
            headline = "Experimental. If something you need is listed as not supported, " +
                "get in touch and bring your export.",
            members = s.members.map { PreviewMember(it.id, it.name) },
            memberCount = s.memberCount,
            categories = listOf(
                ImportCategory(
                    key = "system_profile",
                    label = "System profile",
                    visible = s.systemName != null,
                    default = s.systemName != null,
                ),
                counted(
                    "templates", "Template members", s.templateCount, default = false,
                    note = "These count against your member limit. Only brought across when all members are selected.",
                ),
                counted("custom_fronts", "Custom statuses, as custom fronts", s.customFrontCount),
                counted("folders", "Folders, as groups", s.folderCount),
                counted("tags", "Tags", s.tagCount),
                counted("custom_fields", "Custom fields", s.customFieldCount),
                ImportCategory(
                    key = "front_history",
                    label = "Front history (${s.frontHistoryCount} entries)",
                    visible = s.frontHistoryCount > 0,
                    default = s.frontHistoryCount > 0,
                    note = if (s.frontingTypeCount > 0) "Fronting types are kept on each entry's status text." else null,
                ),
            ),
            skipped = x,
        )
    },
)

/** Every source the Import screen can run, in the order the picker lists them. */
val importSources: List<ImportSource> = listOf(
    simplyPlural,
    sheaf,
    pluralKitFile,
    pluralKitApi,
    tupperbox,
    pluralSpace,
    prism,
    openPlural,
    ampersand,
    berryTree,
)

fun importSourceById(id: String?): ImportSource? = importSources.firstOrNull { it.id == id }

/**
 * Hand-build the options JSON. The backend uses `extra="forbid"`, so the field
 * names have to match its options model exactly; small enough that a Moshi
 * adapter for it would be indirection for its own sake, and the body is
 * embedded raw into the API-import body below rather than encoded twice.
 */
internal fun buildOptionsJson(
    source: ImportSource,
    values: Map<String, Boolean>,
    memberIds: List<String>?,
): String {
    val parts = mutableListOf<String>()
    source.constants.forEach { (k, v) -> parts += "${jsonQuote(k)}:$v" }
    values.forEach { (k, v) -> parts += "${jsonQuote(k)}:$v" }
    if (memberIds != null) {
        parts += "\"member_ids\":[${memberIds.joinToString(",") { jsonQuote(it) }}]"
    } else if (source.alwaysEmitMemberIds) {
        parts += "\"member_ids\":null"
    }
    return parts.joinToString(",", prefix = "{", postfix = "}")
}

/**
 * Hand-build the credential-API submit body. The options field is embedded as
 * a raw JSON object (already encoded). Mirrors the server-side
 * `ImportApiCreateRequest` shape: `source`, `idempotency_key`, `pk_token`,
 * `options`.
 *
 * The token is a value the user pasted in from somewhere else, so it goes
 * through [jsonQuote] rather than straight interpolation.
 */
internal fun buildApiImportBodyJson(
    token: String,
    idempotencyKey: String,
    options: String,
): String =
    """{"source":"${ImportJobSource.PLURALKIT_API}",""" +
        """"idempotency_key":"$idempotencyKey",""" +
        """"pk_token":${jsonQuote(token)},""" +
        """"options":$options}"""

internal fun String.toFormPart(): RequestBody = toRequestBody("text/plain".toMediaType())

internal fun String.toJsonPart(): RequestBody = toRequestBody("application/json".toMediaType())
