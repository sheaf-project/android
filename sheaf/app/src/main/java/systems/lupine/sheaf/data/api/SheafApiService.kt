package systems.lupine.sheaf.data.api

import systems.lupine.sheaf.data.model.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface SheafApiService {

    // ── Auth config ───────────────────────────────────────────────────────────

    @GET("/v1/auth/config")
    suspend fun getAuthConfig(): AuthConfig

    @GET("/v1/auth/captcha/challenge")
    suspend fun getCaptchaChallenge(): CaptchaChallenge

    // ── Auth ──────────────────────────────────────────────────────────────────

    @POST("/v1/auth/register")
    suspend fun register(@Body body: UserRegister): TokenResponse

    @POST("/v1/auth/login")
    suspend fun login(@Body body: UserLogin): TokenResponse

    @POST("/v1/auth/logout")
    suspend fun logout()

    @POST("/v1/auth/totp/setup")
    suspend fun setupTotp(): TOTPSetupResponse

    @POST("/v1/auth/totp/verify")
    suspend fun verifyTotp(@Body body: TOTPVerify)

    @POST("/v1/auth/totp/disable")
    suspend fun disableTotp(@Body body: TOTPDisable)

    @POST("/v1/auth/totp/regenerate-recovery-codes")
    suspend fun regenerateTotpRecoveryCodes(@Body body: TOTPVerify): TOTPRecoveryCodes

    @POST("/v1/auth/refresh")
    suspend fun refresh(@Body body: TokenRefresh): TokenResponse

    @GET("/v1/auth/me")
    suspend fun getMe(): UserRead

    @POST("/v1/auth/request-password-reset")
    suspend fun requestPasswordReset(@Body body: PasswordResetRequest)

    @POST("/v1/auth/reset-password")
    suspend fun resetPassword(@Body body: PasswordReset)

    @GET("/v1/auth/verify-email")
    suspend fun verifyEmail(@Query("token") token: String)

    @POST("/v1/auth/resend-verification")
    suspend fun resendVerification()

    @POST("/v1/auth/delete-account")
    suspend fun deleteAccount(@Body body: DeleteAccountRequest)

    @POST("/v1/auth/cancel-deletion")
    suspend fun cancelAccountDeletion()

    // ── API Keys ──────────────────────────────────────────────────────────────

    @GET("/v1/auth/keys")
    suspend fun listApiKeys(): List<ApiKeyRead>

    @POST("/v1/auth/keys")
    suspend fun createApiKey(@Body body: ApiKeyCreate): ApiKeyCreated

    @DELETE("/v1/auth/keys/{id}")
    suspend fun revokeApiKey(@Path("id") id: String)

    // ── Sessions ──────────────────────────────────────────────────────────────

    @GET("/v1/auth/sessions")
    suspend fun listSessions(): List<SessionRead>

    @PATCH("/v1/auth/sessions/{id}")
    suspend fun renameSession(@Path("id") id: String, @Body body: SessionUpdate)

    @DELETE("/v1/auth/sessions/{id}")
    suspend fun revokeSession(@Path("id") id: String)

    @POST("/v1/auth/sessions/revoke-others")
    suspend fun revokeOtherSessions(@Body body: TokenRefresh)

    /**
     * Mint a child session + independent refresh token for a paired
     * companion device (the wear app). Lets the watch rotate its own
     * one-shot refresh JWT without colliding with the phone's rotation.
     * On parent revocation (logout, /sessions DELETE, change-password)
     * the child is cascaded automatically server-side.
     */
    @POST("/v1/auth/sessions/secondary")
    suspend fun createSecondarySession(@Body body: SecondarySessionRequest): SecondarySessionResponse

    // ── System ────────────────────────────────────────────────────────────────

    @GET("/v1/systems/me")
    suspend fun getOwnSystem(): SystemRead

    @PATCH("/v1/systems/me")
    suspend fun updateOwnSystem(@Body body: SystemUpdate): SystemRead

    // Timezone-only update. Takes a pre-serialized body (built with
    // serializeNulls) so "automatic" is sent as an explicit null rather than
    // being dropped by the codegen adapter. See [SystemTimezoneBody].
    @PATCH("/v1/systems/me")
    suspend fun updateOwnSystemTimezone(@Body body: RequestBody): SystemRead

    @PUT("/v1/systems/me/delete-confirmation")
    suspend fun updateDeleteConfirmation(@Body body: DeleteConfirmationUpdate): SystemRead

    // ── System Safety ─────────────────────────────────────────────────────────

    @GET("/v1/system/safety")
    suspend fun getSystemSafety(): SystemSafetyResponse

    @PATCH("/v1/system/safety")
    suspend fun updateSystemSafety(@Body body: SystemSafetyUpdate): SystemSafetyUpdateResponse

    @DELETE("/v1/system/safety/pending-actions/{id}")
    suspend fun cancelPendingAction(@Path("id") id: String)

    @DELETE("/v1/system/safety/pending-changes/{id}")
    suspend fun cancelPendingSafetyChange(@Path("id") id: String)

    // ── Revision retention ─────────────────────────────────────────────────────

    @GET("/v1/retention")
    suspend fun getRetention(): RetentionResponse

    @PATCH("/v1/retention")
    suspend fun updateRetention(@Body body: RetentionUpdate): RetentionResponse

    @DELETE("/v1/retention/trim-notice/{id}")
    suspend fun cancelTrimNotice(@Path("id") id: String)

    // ── Analytics ─────────────────────────────────────────────────────────────

    /**
     * Per-member fronting summary over [since]..[until], plus an hour-of-
     * day distribution in [tz]. Defaults if omitted: until=now,
     * since=until-30d, tz=UTC. The server clamps windows to 5 years.
     * Co-fronting double-counts so "Alice fronted X" reads naturally
     * regardless of who else was on at the same time.
     */
    @GET("/v1/analytics/fronting")
    suspend fun getFrontingAnalytics(
        @Query("since") since: String? = null,
        @Query("until") until: String? = null,
        @Query("tz") tz: String? = null,
    ): FrontingAnalytics

    // ── Members ───────────────────────────────────────────────────────────────

    @GET("/v1/members")
    suspend fun listMembers(): List<MemberRead>

    /**
     * Members ordered for a quick-switch UI: `quick_switch_pin`-pinned
     * members first (in pin order), then the rest by a recency-weighted
     * fronting score (30-day half life). Backs the home-screen carousel
     * so it surfaces the people the user actually switches to often.
     */
    @GET("/v1/members/top-fronters")
    suspend fun getTopFronters(@Query("limit") limit: Int = 8): List<MemberRead>

    @POST("/v1/members")
    suspend fun createMember(@Body body: MemberCreate): MemberRead

    @GET("/v1/members/{id}")
    suspend fun getMember(@Path("id") id: String): MemberRead

    @PATCH("/v1/members/{id}")
    suspend fun updateMember(@Path("id") id: String, @Body body: MemberUpdate): MemberRead

    @PATCH("/v1/members/{id}")
    suspend fun patchMemberRaw(@Path("id") id: String, @Body body: RequestBody): MemberRead

    @HTTP(method = "DELETE", path = "/v1/members/{id}", hasBody = true)
    suspend fun deleteMember(
        @Path("id") id: String,
        @Body body: MemberDeleteConfirm = MemberDeleteConfirm(),
    ): Response<MemberDeletePending>

    /** Archive (reversible soft-hide). [body] carries step-up credentials only
     *  when the system's archive safety category is on; an empty body is fine
     *  otherwise (the server then 4xxs and the caller retries with creds). */
    @POST("/v1/members/{id}/archive")
    suspend fun archiveMember(
        @Path("id") id: String,
        @Body body: MemberArchiveBody = MemberArchiveBody(),
    ): MemberRead

    @POST("/v1/members/{id}/unarchive")
    suspend fun unarchiveMember(@Path("id") id: String): MemberRead

    @GET("/v1/members/{id}/revisions")
    suspend fun listMemberBioRevisions(@Path("id") id: String): List<ContentRevisionRead>

    @POST("/v1/members/{id}/restore-revision")
    suspend fun restoreMemberBioRevision(
        @Path("id") id: String,
        @Body body: RestoreRevisionRequest,
    ): MemberRead

    @POST("/v1/members/{id}/pin-revision")
    suspend fun pinMemberBioRevision(
        @Path("id") id: String,
        @Body body: PinRevisionRequest,
    ): ContentRevisionRead

    @POST("/v1/members/{id}/unpin-revision")
    suspend fun unpinMemberBioRevision(
        @Path("id") id: String,
        @Body body: UnpinRevisionRequest,
    ): UnpinRevisionResponse

    // ── Fronts ────────────────────────────────────────────────────────────────

    @GET("/v1/fronts")
    suspend fun listFronts(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): List<FrontRead>

    // Pagination-aware variant. Returns a Retrofit Response so callers can
    // read the X-Sheaf-Has-More, X-Sheaf-Next-Cursor, and (when include_total
    // is true) X-Sheaf-Total-Count headers the backend exposes for cursor
    // and numbered-page UIs respectively.
    @GET("/v1/fronts")
    suspend fun listFrontsPaginated(
        @Query("limit") limit: Int,
        @Query("offset") offset: Int? = null,
        @Query("cursor") cursor: String? = null,
        @Query("include_total") includeTotal: Boolean? = null,
    ): Response<List<FrontRead>>

    @POST("/v1/fronts")
    suspend fun createFront(@Body body: FrontCreate): FrontRead

    @GET("/v1/fronts/current")
    suspend fun getCurrentFronts(): List<FrontRead>

    @PATCH("/v1/fronts/{id}")
    suspend fun updateFront(@Path("id") id: String, @Body body: FrontUpdate): FrontRead

    @DELETE("/v1/fronts/{id}")
    suspend fun deleteFront(@Path("id") id: String)

    /**
     * End one open front and start its replacement in a single transaction,
     * without touching any other open front. Use this whenever the change is
     * "these people are fronting instead of those" within one front: it keeps
     * per-member history entries intact and emits one aggregated notification
     * rather than a stop followed by a start.
     */
    @POST("/v1/fronts/{id}/replace")
    suspend fun replaceFront(@Path("id") id: String, @Body body: FrontReplace): FrontRead

    // ── Groups ────────────────────────────────────────────────────────────────

    @GET("/v1/groups")
    suspend fun listGroups(): List<GroupRead>

    @POST("/v1/groups")
    suspend fun createGroup(@Body body: GroupCreate): GroupRead

    @GET("/v1/groups/{id}")
    suspend fun getGroup(@Path("id") id: String): GroupRead

    @PATCH("/v1/groups/{id}")
    suspend fun updateGroup(@Path("id") id: String, @Body body: GroupUpdate): GroupRead

    @DELETE("/v1/groups/{id}")
    suspend fun deleteGroup(@Path("id") id: String)

    @GET("/v1/groups/{id}/members")
    suspend fun getGroupMembers(@Path("id") id: String): List<MemberRead>

    @PUT("/v1/groups/{id}/members")
    suspend fun setGroupMembers(
        @Path("id") id: String,
        @Body body: GroupMemberUpdate,
    ): List<MemberRead>

    // ── Journals ──────────────────────────────────────────────────────────────

    @GET("/v1/journals")
    suspend fun listJournals(
        @Query("member_id") memberId: String? = null,
        @Query("system_only") systemOnly: Boolean? = null,
        @Query("before") before: String? = null,
        @Query("limit") limit: Int = 50,
    ): JournalListResponse

    @POST("/v1/journals")
    suspend fun createJournal(@Body body: JournalEntryCreate): JournalEntryRead

    @GET("/v1/journals/{id}")
    suspend fun getJournal(@Path("id") id: String): JournalEntryReadWithCount

    @PATCH("/v1/journals/{id}")
    suspend fun updateJournal(
        @Path("id") id: String,
        @Body body: JournalEntryUpdate,
    ): JournalEntryRead

    @HTTP(method = "DELETE", path = "/v1/journals/{id}", hasBody = true)
    suspend fun deleteJournal(
        @Path("id") id: String,
        @Body body: JournalEntryDeleteConfirm = JournalEntryDeleteConfirm(),
    ): Response<JournalEntryDeletePending>

    @GET("/v1/journals/{id}/revisions")
    suspend fun listJournalRevisions(@Path("id") id: String): List<ContentRevisionRead>

    @POST("/v1/journals/{id}/restore-revision")
    suspend fun restoreJournalRevision(
        @Path("id") id: String,
        @Body body: RestoreRevisionRequest,
    ): JournalEntryRead

    @POST("/v1/journals/{id}/pin-revision")
    suspend fun pinJournalRevision(
        @Path("id") id: String,
        @Body body: PinRevisionRequest,
    ): ContentRevisionRead

    @POST("/v1/journals/{id}/unpin-revision")
    suspend fun unpinJournalRevision(
        @Path("id") id: String,
        @Body body: UnpinRevisionRequest,
    ): UnpinRevisionResponse

    // ── Tags ──────────────────────────────────────────────────────────────────

    @GET("/v1/tags")
    suspend fun listTags(): List<TagRead>

    @POST("/v1/tags")
    suspend fun createTag(@Body body: TagCreate): TagRead

    @GET("/v1/tags/{id}")
    suspend fun getTag(@Path("id") id: String): TagRead

    @PATCH("/v1/tags/{id}")
    suspend fun updateTag(@Path("id") id: String, @Body body: TagUpdate): TagRead

    @HTTP(method = "DELETE", path = "/v1/tags/{id}", hasBody = true)
    suspend fun deleteTag(
        @Path("id") id: String,
        @Body body: MemberDeleteConfirm = MemberDeleteConfirm(),
    ): Response<TagDeletePending>

    // ── Custom Fields ─────────────────────────────────────────────────────────

    @GET("/v1/fields")
    suspend fun listFields(): List<CustomFieldRead>

    @POST("/v1/fields")
    suspend fun createField(@Body body: CustomFieldCreate): CustomFieldRead

    @PATCH("/v1/fields/{id}")
    suspend fun updateField(@Path("id") id: String, @Body body: CustomFieldUpdate): CustomFieldRead

    @DELETE("/v1/fields/{id}")
    suspend fun deleteField(@Path("id") id: String)

    /**
     * Per-member custom field values. Server returns the decrypted
     * plaintext list, gated by each field's privacy level — fields the
     * viewer isn't allowed to see are omitted server-side. Values are
     * type-erased on the wire; the matching field definition (via
     * [listFields]) tells the client how to render each one.
     */
    @GET("/v1/members/{memberId}/fields")
    suspend fun getMemberFieldValues(
        @Path("memberId") memberId: String,
    ): List<CustomFieldValueRead>

    /**
     * Bulk set / clear field values for one member. Server validates
     * each value against the field's type + choices, encrypts at rest,
     * and returns the full updated list (mirrors what GET would yield).
     * Send `value = null` to clear a previously-set value.
     */
    @PUT("/v1/members/{memberId}/fields")
    suspend fun setMemberFieldValues(
        @Path("memberId") memberId: String,
        @Body body: List<CustomFieldValueSet>,
    ): List<CustomFieldValueRead>

    // ── Files ─────────────────────────────────────────────────────────────────

    @Multipart
    @POST("/v1/files/upload")
    suspend fun uploadFile(
        @Part file: MultipartBody.Part,
        // Server stores under a per-purpose prefix (avatars/bios/banners)
        // and applies per-purpose size caps. Defaults to avatar so existing
        // callers are unaffected.
        @Query("purpose") purpose: String = "avatar",
    ): FileUploadResponse

    @GET("/v1/files/usage")
    suspend fun getFileUsage(): FileUsage

    @GET("/v1/files/list")
    suspend fun listFiles(): List<FileRead>

    @HTTP(method = "DELETE", path = "/v1/files/{id}", hasBody = true)
    suspend fun deleteFile(
        @Path("id") id: String,
        @Body body: MemberDeleteConfirm = MemberDeleteConfirm(),
    ): Response<FileDeletePending>

    // ── Client Settings ───────────────────────────────────────────────────────

    @GET("/v1/settings/client/{clientId}")
    suspend fun getClientSettings(@Path("clientId") clientId: String): ClientSettingsResponse

    @PUT("/v1/settings/client/{clientId}")
    suspend fun saveClientSettings(
        @Path("clientId") clientId: String,
        @Body body: ClientSettingsBody,
    ): ClientSettingsResponse

    /**
     * Atomic top-level key merge — the only safe option when independent
     * features (theme, dismissed announcements, etc.) each write their
     * own subset of the blob. Using PUT here would clobber whichever key
     * wasn't included in the latest write. Backend implements this as a
     * single JSONB `||` UPDATE so concurrent callers don't race.
     */
    @PATCH("/v1/settings/client/{clientId}")
    suspend fun patchClientSettings(
        @Path("clientId") clientId: String,
        @Body body: ClientSettingsBody,
    ): ClientSettingsResponse

    @DELETE("/v1/settings/client/{clientId}")
    suspend fun deleteClientSettings(@Path("clientId") clientId: String)

    // ── Export ────────────────────────────────────────────────────────────────

    /**
     * Synchronous JSON export. [format] is "sheaf" (native, full-fidelity
     * re-import) or "openplural" (v0.1 interchange, uri-only assets). No
     * step-up; this is metadata only, no image bytes.
     *
     * @Streaming so a large system's export goes socket -> file. Without it
     * Retrofit buffers the entire body into a byte array before the caller sees
     * it, so the caller's byteStream().copyTo() was copying from memory and a
     * big enough export could exhaust the heap.
     */
    @Streaming
    @GET("/v1/export")
    suspend fun exportAll(@Query("format") format: String = "sheaf"): okhttp3.ResponseBody

    /**
     * Enqueue an async full-backup job (JSON + image bytes, zipped). Body
     * carries the format ("sheaf_native" or "openplural") and step-up
     * credentials (password, plus totp_code when the account has 2FA). The
     * server refuses API-key auth and allows only one in-flight job per user.
     * Returns 202 + the pending [ExportJobRead]; poll [getExportJob] or
     * refresh [listExportJobs] until status is "done", then [downloadExportJob].
     */
    @POST("/v1/export/jobs")
    suspend fun createExportJob(@Body body: ExportJobRequest): ExportJobRead

    @GET("/v1/export/jobs")
    suspend fun listExportJobs(): List<ExportJobRead>

    @GET("/v1/export/jobs/{id}")
    suspend fun getExportJob(@Path("id") id: String): ExportJobRead

    /** Stream the finished backup zip. @Streaming so the zip isn't buffered. */
    @Streaming
    @GET("/v1/export/jobs/{id}/download")
    suspend fun downloadExportJob(@Path("id") id: String): okhttp3.ResponseBody

    // ── Imports (preview synchronous, submit async) ──────────────────────────
    //
    // Preview endpoints under `/v1/import/<source>/preview` are still
    // synchronous: they parse + sniff the file and return a summary
    // without committing anything. Submit endpoints moved to the
    // unified async runner at `/v1/imports/file` (multipart) and
    // `/v1/imports/api` (JSON, credential-based — PK only); the runner
    // walks the file out-of-band and clients poll `/v1/imports/{id}`
    // for status / counts / events.

    @Multipart
    @POST("/v1/import/simplyplural/preview")
    suspend fun previewSimplyPluralImport(
        @Part file: MultipartBody.Part,
    ): SPPreviewSummary

    @Multipart
    @POST("/v1/import/sheaf/preview")
    suspend fun previewSheafImport(
        @Part file: MultipartBody.Part,
    ): SheafPreviewSummary

    @Multipart
    @POST("/v1/import/pluralkit/preview")
    suspend fun previewPluralKitFileImport(
        @Part file: MultipartBody.Part,
    ): PKPreviewSummary

    /**
     * Live-API preview for PluralKit. Hits the user's PK system via the
     * supplied token for a single round-trip; the token is request-scoped
     * server-side and never persisted. Submit goes through [createApiImport].
     */
    @POST("/v1/import/pluralkit-api/preview")
    suspend fun previewPluralKitApiImport(
        @Body body: PKApiPreviewBody,
    ): PKPreviewSummary

    @Multipart
    @POST("/v1/import/tupperbox/preview")
    suspend fun previewTupperboxImport(
        @Part file: MultipartBody.Part,
    ): TBPreviewSummary

    @Multipart
    @POST("/v1/import/pluralspace/preview")
    suspend fun previewPluralSpaceImport(
        @Part file: MultipartBody.Part,
    ): PluralSpacePreviewSummary

    @Multipart
    @POST("/v1/import/ampersand/preview")
    suspend fun previewAmpersandImport(
        @Part file: MultipartBody.Part,
    ): AmpersandPreviewSummary

    /**
     * Preview an OpenPlural v0.1 import. Accepts a bare `.json` export or an
     * `.openplural.zip` bundle (the endpoint sniffs the zip magic). Reuses the
     * Sheaf preview shape plus a `lineage_length`; submit via [createFileImport]
     * with source [ImportJobSource.OPENPLURAL_FILE].
     */
    @Multipart
    @POST("/v1/import/openplural/preview")
    suspend fun previewOpenPluralImport(
        @Part file: MultipartBody.Part,
    ): SheafPreviewSummary

    /**
     * Preview a Prism (.prism) export. The PRISM1 envelope is decrypted
     * server-side with [passphrase]; nothing is persisted. Submit goes
     * through [createFileImport] with the same passphrase as `credential`.
     */
    @Multipart
    @POST("/v1/import/prism/preview")
    suspend fun previewPrismImport(
        @Part file: MultipartBody.Part,
        @Part("passphrase") passphrase: RequestBody,
    ): PrismPreviewSummary

    /**
     * Enqueue a file-based import. [source] is one of the
     * [ImportJobSource] constants; [options] is a JSON-encoded
     * source-specific options dict (or omit for backend defaults).
     * [credential] is a per-source secret (currently only Prism's PRISM1
     * passphrase); omit it for sources that don't need one. The server
     * encrypts it at rest until the runner finalises the job.
     *
     * Returns 202 + the freshly-minted [ImportJobRead] with
     * `status = pending`. Poll [getImportJob] until status is in
     * [ImportJobStatus.terminal] to see the result.
     */
    @Multipart
    @POST("/v1/imports/file")
    suspend fun createFileImport(
        @Part file: MultipartBody.Part,
        @Part("source") source: RequestBody,
        @Part("idempotency_key") idempotencyKey: RequestBody,
        @Part("options") options: RequestBody?,
        @Part("credential") credential: RequestBody? = null,
    ): ImportJobRead

    @GET("/v1/imports/{jobId}")
    suspend fun getImportJob(@Path("jobId") jobId: String): ImportJobRead

    /**
     * Enqueue a credential-based import (PluralKit API today; only PK uses
     * this path). The body is a hand-built JSON object — see ImportApiCreate
     * field-name docs in Models.kt for the shape. Returns 202 + ImportJobRead.
     */
    @POST("/v1/imports/api")
    suspend fun createApiImport(@Body body: RequestBody): ImportJobRead

    /**
     * Paginated list of the current user's import jobs, most recent first.
     * Pass [cursor] back from a prior response's `nextCursor` for the next
     * page; null on the first call. [includeArchived] defaults false on the
     * server, matching the "show only active" UI default.
     */
    @GET("/v1/imports")
    suspend fun listImports(
        @Query("limit") limit: Int = 25,
        @Query("include_archived") includeArchived: Boolean = false,
        @Query("cursor") cursor: String? = null,
    ): ImportJobList

    /**
     * Pending jobs: cancel (204). Terminal jobs: archive — drops the
     * row from the default history listing (204). Running jobs return
     * 409 since v1 has no cooperative mid-flight cancel.
     */
    @DELETE("/v1/imports/{jobId}")
    suspend fun cancelOrArchiveImport(@Path("jobId") jobId: String): Response<Unit>

    // ── Announcements ────────────────────────────────────────────────────────

    @GET("/v1/announcements")
    suspend fun getAnnouncements(): List<AnnouncementPublic>

    @GET("/v1/admin/announcements")
    suspend fun listAllAnnouncements(): List<AnnouncementRead>

    @POST("/v1/admin/announcements")
    suspend fun createAnnouncement(@Body body: AnnouncementCreate): AnnouncementRead

    @PATCH("/v1/admin/announcements/{id}")
    suspend fun updateAnnouncement(@Path("id") id: String, @Body body: AnnouncementUpdate): AnnouncementRead

    @DELETE("/v1/admin/announcements/{id}")
    suspend fun deleteAnnouncement(@Path("id") id: String)

    // ── Invite codes ─────────────────────────────────────────────────────────

    @GET("/v1/admin/invites")
    suspend fun listInvites(): List<InviteCodeRead>

    @POST("/v1/admin/invites")
    suspend fun createInvite(@Body body: InviteCodeCreate): InviteCodeRead

    @DELETE("/v1/admin/invites/{invite_id}")
    suspend fun deleteInvite(@Path("invite_id") inviteId: String)

    // ── Admin ─────────────────────────────────────────────────────────────────

    @GET("/v1/admin/auth")
    suspend fun getAdminAuthStatus(): AdminAuthStatus

    @POST("/v1/admin/auth")
    suspend fun adminStepUp(@Body body: AdminStepUpVerify)

    @GET("/v1/admin/stats")
    suspend fun getAdminStats(): AdminStats

    @GET("/v1/admin/users")
    suspend fun getAdminUsers(@Query("search") search: String? = null): List<AdminUserRead>

    @PATCH("/v1/admin/users/{id}")
    suspend fun updateAdminUser(@Path("id") id: String, @Body body: AdminUserUpdate): AdminUserRead

    @GET("/v1/admin/approvals")
    suspend fun getApprovals(): List<PendingUserRead>

    @POST("/v1/admin/users/{id}/approve")
    suspend fun approveUser(@Path("id") id: String)

    @POST("/v1/admin/users/{id}/reject")
    suspend fun rejectUser(@Path("id") id: String)

    @POST("/v1/admin/retention/run")
    suspend fun runRetention()

    @POST("/v1/admin/cleanup/run")
    suspend fun runCleanup()

    @GET("/v1/admin/storage/stats")
    suspend fun getStorageStats(): Map<String, Any>

    // ── Admin account recovery ────────────────────────────────────────────────

    @POST("/v1/admin/users/{id}/reset-password")
    suspend fun adminResetPassword(@Path("id") id: String, @Body body: AdminResetPasswordRequest)

    @POST("/v1/admin/users/{id}/change-email")
    suspend fun adminChangeEmail(@Path("id") id: String, @Body body: AdminChangeEmailRequest)

    @POST("/v1/admin/users/{id}/disable-totp")
    suspend fun adminDisableTotp(@Path("id") id: String, @Body body: AdminReasonBody)

    @POST("/v1/admin/users/{id}/verify-email")
    suspend fun adminVerifyEmail(@Path("id") id: String, @Body body: AdminReasonBody)

    @POST("/v1/admin/users/{id}/cancel-deletion")
    suspend fun adminCancelDeletion(@Path("id") id: String, @Body body: AdminReasonBody)

    // ── Admin account moderation ──────────────────────────────────────────────
    // Suspend = reversible soft-ban (optional expiry); ban = permanent. Both
    // revoke the target's sessions server-side. Responses carry status detail
    // we don't currently consume; we reload the affected row instead.

    @POST("/v1/admin/users/{id}/suspend")
    suspend fun adminSuspendUser(@Path("id") id: String, @Body body: AdminSuspendRequest)

    @POST("/v1/admin/users/{id}/unsuspend")
    suspend fun adminUnsuspendUser(@Path("id") id: String, @Body body: AdminReasonBody)

    @POST("/v1/admin/users/{id}/ban")
    suspend fun adminBanUser(@Path("id") id: String, @Body body: AdminReasonBody)

    @POST("/v1/admin/users/{id}/unban")
    suspend fun adminUnbanUser(@Path("id") id: String, @Body body: AdminReasonBody)

    // ── Admin user diagnostics ────────────────────────────────────────────────

    @GET("/v1/admin/users/{id}/explain")
    suspend fun getAdminUserExplain(@Path("id") id: String): AdminExplainResponse

    @GET("/v1/admin/users/{id}/sessions")
    suspend fun getAdminUserSessions(@Path("id") id: String): List<AdminSessionRow>

    @POST("/v1/admin/users/{id}/sessions/{sessionId}/terminate")
    suspend fun terminateAdminUserSession(
        @Path("id") id: String,
        @Path("sessionId") sessionId: String,
        @Body body: AdminReasonBody,
    )

    @POST("/v1/admin/users/{id}/api-keys/rotate-all")
    suspend fun rotateAllAdminUserApiKeys(
        @Path("id") id: String,
        @Body body: AdminReasonBody,
    ): AdminRotateAllResponse

    // ── Admin maintenance jobs + ops ──────────────────────────────────────────

    @GET("/v1/admin/jobs")
    suspend fun getAdminJobs(): List<AdminJobRead>

    @POST("/v1/admin/jobs/{jobName}/run")
    suspend fun runAdminJob(@Path("jobName") jobName: String): AdminJobRunResponse

    @GET("/v1/admin/pushover-usage")
    suspend fun getAdminPushoverUsage(): AdminPushoverUsage

    @POST("/v1/admin/approvals/bulk-approve")
    suspend fun bulkApprove(@Body body: BulkApproveRequest): BulkApproveResponse

    @POST("/v1/admin/users/{id}/reset-safety")
    suspend fun adminResetSafety(@Path("id") id: String, @Body body: AdminReasonBody): AdminResetSafetyResponse

    @POST("/v1/admin/users/{id}/bypass-pending")
    suspend fun adminBypassPending(@Path("id") id: String, @Body body: AdminReasonBody): AdminBypassPendingResponse

    // Import-job inspection. The list is browse-only; the per-job detail
    // (with events) is a privacy-sensitive read, hence POST + reason.
    @GET("/v1/admin/users/{id}/import-jobs")
    suspend fun getAdminUserImportJobs(@Path("id") id: String): List<AdminImportJobSummary>

    @POST("/v1/admin/import-jobs/{jobId}")
    suspend fun getAdminImportJobDetail(
        @Path("jobId") jobId: String,
        @Body body: AdminReasonBody,
    ): AdminImportJobDetail

    // GDPR Article 15 metadata export; returns a downloadable JSON document.
    @Streaming
    @POST("/v1/admin/users/{id}/dossier")
    suspend fun exportUserDossier(
        @Path("id") id: String,
        @Body body: AdminReasonBody,
    ): okhttp3.ResponseBody

    // ── Admin audit log ─────────────────────────────────────────────────────

    @GET("/v1/admin/audit-events")
    suspend fun getAdminAuditEvents(
        @Query("target_user_id") targetUserId: String? = null,
        @Query("admin_user_id") adminUserId: String? = null,
        @Query("action") action: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50,
    ): List<AdminAuditEventRead>

    @GET("/v1/admin/audit-events/{eventId}")
    suspend fun getAdminAuditEvent(@Path("eventId") eventId: String): AdminAuditEventRead

    // User-facing transparency: admin actions taken against the caller's own
    // account. No admin gate; every user can read their own.
    @GET("/v1/auth/admin-activity")
    suspend fun getMyAdminActivity(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50,
    ): List<UserAdminActivityRead>

    // ── Mobile push device registration ────────────────────────────────────
    // Phase A: client-side stubs only. Endpoints aren't live until the
    // backend mobile-push work lands; calls will 404 until then. Design
    // doc: mobile-push-architecture.md in sheaf-design-docs.

    @POST("/v1/devices/push")
    suspend fun registerPushDevice(@Body body: PushDeviceRegistration)

    @HTTP(method = "DELETE", path = "/v1/devices/push", hasBody = true)
    suspend fun unregisterPushDevice(@Body body: PushDeviceUnregister)

    @GET("/v1/devices/push")
    suspend fun listPushDevices(): List<PushDeviceListEntry>

    // ── Notification subscription redemption ──────────────────────────────
    // Public endpoint: anonymous redemption is allowed for web push, but
    // mobile-push channels (FCM/APNS_*) require a logged-in session.

    @POST("/v1/notifications/redeem")
    suspend fun redeemActivationCode(@Body body: RedeemRequest): RedeemResponse

    @GET("/v1/notifications/receiving")
    suspend fun listReceivingChannels(): List<ReceivingChannelView>

    @POST("/v1/notifications/receiving/{channelId}/unsubscribe")
    suspend fun unsubscribeReceiving(@Path("channelId") channelId: String)

    // ── Watch tokens (owner-side) ──────────────────────────────────────────

    @POST("/v1/systems/{systemId}/watch-tokens")
    suspend fun createWatchToken(
        @Path("systemId") systemId: String,
        @Body body: WatchTokenCreate,
    ): WatchTokenRead

    @GET("/v1/systems/{systemId}/watch-tokens")
    suspend fun listWatchTokens(@Path("systemId") systemId: String): List<WatchTokenRead>

    @DELETE("/v1/watch-tokens/{tokenId}")
    suspend fun revokeWatchToken(@Path("tokenId") tokenId: String)

    // ── Notification channels (owner-side) ─────────────────────────────────

    @POST("/v1/watch-tokens/{tokenId}/channels")
    suspend fun createChannel(
        @Path("tokenId") tokenId: String,
        @Body body: NotificationChannelCreate,
    ): NotificationChannelCreateResponse

    @GET("/v1/channels")
    suspend fun listOwnedChannels(): List<NotificationChannelRead>

    @DELETE("/v1/channels/{channelId}")
    suspend fun deleteChannel(@Path("channelId") channelId: String)

    @POST("/v1/channels/{channelId}/disable")
    suspend fun disableChannel(@Path("channelId") channelId: String)

    @POST("/v1/channels/{channelId}/enable")
    suspend fun enableChannel(@Path("channelId") channelId: String)

    @POST("/v1/channels/{channelId}/reissue-activation")
    suspend fun reissueChannelActivation(@Path("channelId") channelId: String): ReissueActivationResponse

    @PATCH("/v1/channels/{channelId}")
    suspend fun updateChannel(
        @Path("channelId") channelId: String,
        @Body body: NotificationChannelUpdate,
    ): NotificationChannelRead

    @POST("/v1/channels/{channelId}/duplicate")
    suspend fun duplicateChannel(@Path("channelId") channelId: String): NotificationChannelCreateResponse

    @POST("/v1/channels/{channelId}/test")
    suspend fun sendTestChannel(@Path("channelId") channelId: String): TestDispatchResponse

    // ── Reminders ──────────────────────────────────────────────────────────

    @GET("/v1/reminders")
    suspend fun listReminders(): List<ReminderRead>

    @POST("/v1/reminders")
    suspend fun createReminder(@Body body: ReminderWrite): ReminderRead

    @GET("/v1/reminders/{id}")
    suspend fun getReminder(@Path("id") id: String): ReminderRead

    @PATCH("/v1/reminders/{id}")
    suspend fun updateReminder(@Path("id") id: String, @Body body: ReminderWrite): ReminderRead

    @DELETE("/v1/reminders/{id}")
    suspend fun deleteReminder(@Path("id") id: String)

    // ── Relationships ────────────────────────────────────────────────────────

    @GET("/v1/relationship-types")
    suspend fun listRelationshipTypes(): List<RelationshipTypeRead>

    @POST("/v1/relationship-types")
    suspend fun createRelationshipType(@Body body: RelationshipTypeCreate): RelationshipTypeRead

    @PATCH("/v1/relationship-types/{id}")
    suspend fun updateRelationshipType(
        @Path("id") id: String,
        @Body body: RelationshipTypeUpdate,
    ): RelationshipTypeRead

    // Cascades: deleting a type also removes every edge that uses it.
    @DELETE("/v1/relationship-types/{id}")
    suspend fun deleteRelationshipType(@Path("id") id: String)

    @GET("/v1/members/{memberId}/relationships")
    suspend fun getMemberRelationships(@Path("memberId") memberId: String): List<RelationshipFromViewpoint>

    @POST("/v1/member-relationships")
    suspend fun createMemberRelationship(@Body body: RelationshipEdgeCreate): RelationshipEdgeRead

    @DELETE("/v1/member-relationships/{edgeId}")
    suspend fun deleteMemberRelationship(@Path("edgeId") edgeId: String)

    @GET("/v1/groups/{groupId}/relationships")
    suspend fun getGroupRelationships(@Path("groupId") groupId: String): List<RelationshipFromViewpoint>

    @POST("/v1/group-relationships")
    suspend fun createGroupRelationship(@Body body: RelationshipEdgeCreate): RelationshipEdgeRead

    @DELETE("/v1/group-relationships/{edgeId}")
    suspend fun deleteGroupRelationship(@Path("edgeId") edgeId: String)

    // Whole-system relationship graph. scope = "members" | "groups".
    @GET("/v1/relationships/graph")
    suspend fun getRelationshipGraph(@Query("scope") scope: String = "members"): RelationshipGraph

    // ── Polls ──────────────────────────────────────────────────────────────

    @GET("/v1/polls")
    suspend fun listPolls(): List<PollRead>

    @POST("/v1/polls")
    suspend fun createPoll(@Body body: PollCreate): PollRead

    @GET("/v1/polls/{id}")
    suspend fun getPoll(@Path("id") id: String): PollRead

    @DELETE("/v1/polls/{id}")
    suspend fun deletePoll(@Path("id") id: String)

    @POST("/v1/polls/{pollId}/votes")
    suspend fun castVote(@Path("pollId") pollId: String, @Body body: VoteCast): PollVoteRead

    @DELETE("/v1/polls/{pollId}/votes/{memberId}")
    suspend fun withdrawVote(
        @Path("pollId") pollId: String,
        @Path("memberId") memberId: String,
    )

    // ── Board messages ─────────────────────────────────────────────────────

    @GET("/v1/messages/boards")
    suspend fun listBoards(
        @Query("caller_member_id") callerMemberId: String? = null,
    ): List<BoardSummary>

    @GET("/v1/messages")
    suspend fun getBoardMessages(
        @Query("board_kind") boardKind: String,
        @Query("board_member_id") boardMemberId: String? = null,
        @Query("caller_member_id") callerMemberId: String? = null,
        @Query("limit") limit: Int = 100,
        @Query("before") before: String? = null,
    ): MessagesPage

    @POST("/v1/messages")
    suspend fun createMessage(@Body body: MessageCreate): MessageRead

    @PATCH("/v1/messages/{id}")
    suspend fun updateMessage(@Path("id") id: String, @Body body: MessageUpdate): MessageRead

    @DELETE("/v1/messages/{id}")
    suspend fun deleteMessage(@Path("id") id: String)

    @POST("/v1/messages/mark-seen")
    suspend fun markBoardSeen(@Body body: MarkSeenRequest)
}

/** Returns null when deletion was immediate (204) or queued payload when safeguarded (202). */
suspend fun SheafApiService.deleteMemberOrQueue(
    id: String,
    password: String? = null,
    totpCode: String? = null,
): MemberDeletePending? {
    val resp = deleteMember(id, MemberDeleteConfirm(password?.ifBlank { null }, totpCode?.ifBlank { null }))
    if (!resp.isSuccessful) throw retrofit2.HttpException(resp)
    return if (resp.code() == 202) resp.body() else null
}

/** Returns null when deletion was immediate (204) or queued payload when safeguarded (202). */
suspend fun SheafApiService.deleteTagOrQueue(
    id: String,
    password: String? = null,
    totpCode: String? = null,
): TagDeletePending? {
    val resp = deleteTag(id, MemberDeleteConfirm(password?.ifBlank { null }, totpCode?.ifBlank { null }))
    if (!resp.isSuccessful) throw retrofit2.HttpException(resp)
    return if (resp.code() == 202) resp.body() else null
}

/** Returns null when deletion was immediate (200) or queued payload when image-safeguarded (202). */
suspend fun SheafApiService.deleteFileOrQueue(
    id: String,
    password: String? = null,
    totpCode: String? = null,
): FileDeletePending? {
    val resp = deleteFile(id, MemberDeleteConfirm(password?.ifBlank { null }, totpCode?.ifBlank { null }))
    if (!resp.isSuccessful) throw retrofit2.HttpException(resp)
    return if (resp.code() == 202) resp.body() else null
}

/** Returns null when deletion was immediate (204) or queued payload when safeguarded (202). */
suspend fun SheafApiService.deleteJournalOrQueue(
    id: String,
    password: String? = null,
    totpCode: String? = null,
): JournalEntryDeletePending? {
    val resp = deleteJournal(
        id,
        JournalEntryDeleteConfirm(password?.ifBlank { null }, totpCode?.ifBlank { null }),
    )
    if (!resp.isSuccessful) throw retrofit2.HttpException(resp)
    return if (resp.code() == 202) resp.body() else null
}
