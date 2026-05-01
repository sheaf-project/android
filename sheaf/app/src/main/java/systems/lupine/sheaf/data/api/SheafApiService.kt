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

    // ── System ────────────────────────────────────────────────────────────────

    @GET("/v1/systems/me")
    suspend fun getOwnSystem(): SystemRead

    @PATCH("/v1/systems/me")
    suspend fun updateOwnSystem(@Body body: SystemUpdate): SystemRead

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

    // ── Members ───────────────────────────────────────────────────────────────

    @GET("/v1/members")
    suspend fun listMembers(): List<MemberRead>

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

    @GET("/v1/members/{id}/revisions")
    suspend fun listMemberBioRevisions(@Path("id") id: String): List<ContentRevisionRead>

    @POST("/v1/members/{id}/restore-revision")
    suspend fun restoreMemberBioRevision(
        @Path("id") id: String,
        @Body body: RestoreRevisionRequest,
    ): MemberRead

    // ── Fronts ────────────────────────────────────────────────────────────────

    @GET("/v1/fronts")
    suspend fun listFronts(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): List<FrontRead>

    @POST("/v1/fronts")
    suspend fun createFront(@Body body: FrontCreate): FrontRead

    @GET("/v1/fronts/current")
    suspend fun getCurrentFronts(): List<FrontRead>

    @PATCH("/v1/fronts/{id}")
    suspend fun updateFront(@Path("id") id: String, @Body body: FrontUpdate): FrontRead

    @DELETE("/v1/fronts/{id}")
    suspend fun deleteFront(@Path("id") id: String)

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

    // ── Tags ──────────────────────────────────────────────────────────────────

    @GET("/v1/tags")
    suspend fun listTags(): List<TagRead>

    @POST("/v1/tags")
    suspend fun createTag(@Body body: TagCreate): TagRead

    @GET("/v1/tags/{id}")
    suspend fun getTag(@Path("id") id: String): TagRead

    @PATCH("/v1/tags/{id}")
    suspend fun updateTag(@Path("id") id: String, @Body body: TagUpdate): TagRead

    @DELETE("/v1/tags/{id}")
    suspend fun deleteTag(@Path("id") id: String)

    // ── Custom Fields ─────────────────────────────────────────────────────────

    @GET("/v1/fields")
    suspend fun listFields(): List<CustomFieldRead>

    @POST("/v1/fields")
    suspend fun createField(@Body body: CustomFieldCreate): CustomFieldRead

    @PATCH("/v1/fields/{id}")
    suspend fun updateField(@Path("id") id: String, @Body body: CustomFieldUpdate): CustomFieldRead

    @DELETE("/v1/fields/{id}")
    suspend fun deleteField(@Path("id") id: String)

    // ── Files ─────────────────────────────────────────────────────────────────

    @Multipart
    @POST("/v1/files/upload")
    suspend fun uploadFile(@Part file: MultipartBody.Part): FileUploadResponse

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

    @DELETE("/v1/settings/client/{clientId}")
    suspend fun deleteClientSettings(@Path("clientId") clientId: String)

    // ── Export ────────────────────────────────────────────────────────────────

    @GET("/v1/export")
    suspend fun exportAll(): okhttp3.ResponseBody

    // ── Simply Plural import ──────────────────────────────────────────────────

    @Multipart
    @POST("/v1/import/simplyplural/preview")
    suspend fun previewSimplyPluralImport(
        @Part file: MultipartBody.Part,
    ): SPPreviewSummary

    @Multipart
    @POST("/v1/import/simplyplural")
    suspend fun runSimplyPluralImport(
        @Query("system_profile") systemProfile: Boolean,
        @Query("custom_fronts") customFronts: Boolean,
        @Query("custom_fields") customFields: Boolean,
        @Query("groups") groups: Boolean,
        @Query("front_history") frontHistory: Boolean,
        @Query("member_ids") memberIds: String?,
        @Part file: MultipartBody.Part,
    ): SPImportResult

    // ── Sheaf import ──────────────────────────────────────────────────────────

    @Multipart
    @POST("/v1/import/sheaf/preview")
    suspend fun previewSheafImport(
        @Part file: MultipartBody.Part,
    ): SheafPreviewSummary

    @Multipart
    @POST("/v1/import/sheaf")
    suspend fun runSheafImport(
        @Query("system_profile") systemProfile: Boolean,
        @Query("fronts") fronts: Boolean,
        @Query("groups") groups: Boolean,
        @Query("tags") tags: Boolean,
        @Query("custom_fields") customFields: Boolean,
        @Query("member_ids") memberIds: String?,
        @Part file: MultipartBody.Part,
    ): SheafImportResult

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
    suspend fun adminDisableTotp(@Path("id") id: String)

    @POST("/v1/admin/users/{id}/verify-email")
    suspend fun adminVerifyEmail(@Path("id") id: String)

    @POST("/v1/admin/users/{id}/cancel-deletion")
    suspend fun adminCancelDeletion(@Path("id") id: String)
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
