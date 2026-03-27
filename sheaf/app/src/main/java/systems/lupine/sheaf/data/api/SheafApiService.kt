package systems.lupine.sheaf.data.api

import systems.lupine.sheaf.data.model.*
import okhttp3.MultipartBody
import retrofit2.http.*

interface SheafApiService {

    // ── Auth ──────────────────────────────────────────────────────────────────

    @POST("/v1/auth/register")
    suspend fun register(@Body body: UserRegister): TokenResponse

    @POST("/v1/auth/login")
    suspend fun login(@Body body: UserLogin): TokenResponse

    @POST("/v1/auth/logout")
    suspend fun logout()

    @POST("/v1/auth/totp/setup")
    suspend fun setupTotp(): systems.lupine.sheaf.data.model.TOTPSetupResponse

    @POST("/v1/auth/totp/verify")
    suspend fun verifyTotp(@Body body: systems.lupine.sheaf.data.model.TOTPVerify)

    @POST("/v1/auth/totp/disable")
    suspend fun disableTotp(@Body body: systems.lupine.sheaf.data.model.TOTPDisable)

    @POST("/v1/auth/refresh")
    suspend fun refresh(@Body body: TokenRefresh): TokenResponse

    @GET("/v1/auth/me")
    suspend fun getMe(): UserRead

    // ── System ────────────────────────────────────────────────────────────────

    @GET("/v1/systems/me")
    suspend fun getOwnSystem(): SystemRead

    @PATCH("/v1/systems/me")
    suspend fun updateOwnSystem(@Body body: SystemUpdate): SystemRead

    // ── Members ───────────────────────────────────────────────────────────────

    @GET("/v1/members")
    suspend fun listMembers(): List<MemberRead>

    @POST("/v1/members")
    suspend fun createMember(@Body body: MemberCreate): MemberRead

    @GET("/v1/members/{id}")
    suspend fun getMember(@Path("id") id: String): MemberRead

    @PATCH("/v1/members/{id}")
    suspend fun updateMember(@Path("id") id: String, @Body body: MemberUpdate): MemberRead

    @DELETE("/v1/members/{id}")
    suspend fun deleteMember(@Path("id") id: String)

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

    // ── Custom Fields ─────────────────────────────────────────────────────────────

    @GET("/v1/fields")
    suspend fun listFields(): List<CustomFieldRead>

    @POST("/v1/fields")
    suspend fun createField(@Body body: CustomFieldCreate): CustomFieldRead

    @PATCH("/v1/fields/{id}")
    suspend fun updateField(@Path("id") id: String, @Body body: CustomFieldUpdate): CustomFieldRead

    @DELETE("/v1/fields/{id}")
    suspend fun deleteField(@Path("id") id: String)

    // ── Files ─────────────────────────────────────────────────────────────────────

    @Multipart
    @POST("/v1/files/upload")
    suspend fun uploadFile(@Part file: MultipartBody.Part): FileUploadResponse

    // ── Export ────────────────────────────────────────────────────────────────

    @GET("/v1/export")
    suspend fun exportAll(): Map<String, Any>

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
}
