package systems.lupine.sheaf.wear.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class WearApiClient(private val auth: WearAuthManager) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // Codegen-only Moshi: every data class we round-trip is annotated
    // @JsonClass(generateAdapter = true) and its adapter is generated at
    // compile-time by KSP. No reflection required, so this survives R8.
    private val moshi = Moshi.Builder().build()

    // Refresh tokens are one-shot server-side: a second /refresh with the same
    // jti trips reuse detection and kills the session. Serialize refreshes so
    // concurrent 401s collapse into a single rotation.
    private val refreshMutex = Mutex()

    private fun url(path: String) = "${auth.baseUrl.trimEnd('/')}$path"

    // Executes the built request, auto-retries once after a 401 by refreshing the token.
    private suspend fun execute(buildRequest: () -> Request): String {
        var response = withContext(Dispatchers.IO) { http.newCall(buildRequest()).execute() }
        if (response.code == 401) {
            tryRefreshToken()
            response = withContext(Dispatchers.IO) { http.newCall(buildRequest()).execute() }
        }
        if (!response.isSuccessful) throw WearApiException(response.code)
        return response.body!!.string()
    }

    private suspend fun tryRefreshToken() {
        val refreshAtEntry = auth.refreshToken ?: run {
            auth.clearCredentials()
            throw WearApiException(401)
        }
        refreshMutex.withLock {
            // If another coroutine rotated while we waited for the lock, our
            // 401 is stale — skip the refresh and let the caller retry with
            // the access token that's already been stored.
            val current = auth.refreshToken ?: run {
                auth.clearCredentials()
                throw WearApiException(401)
            }
            if (current != refreshAtEntry) return
            try {
                val body = """{"refresh_token":"$current"}"""
                val request = Request.Builder()
                    .url(url("/v1/auth/refresh"))
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()
                val response = withContext(Dispatchers.IO) { http.newCall(request).execute() }
                if (!response.isSuccessful) {
                    auth.clearCredentials()
                    throw WearApiException(401)
                }
                val pair = moshi.adapter(TokenPair::class.java).fromJson(response.body!!.string())!!
                auth.saveCredentials(auth.baseUrl, pair.accessToken, pair.refreshToken)
            } catch (e: WearApiException) {
                throw e
            } catch (_: Exception) {
                auth.clearCredentials()
                throw WearApiException(401)
            }
        }
    }

    /**
     * Login with email + password against [baseUrl] and persist the returned tokens.
     * Does not use [auth.baseUrl] so it can be called before credentials are saved.
     */
    suspend fun login(baseUrl: String, email: String, password: String) {
        val cleanUrl = baseUrl.trimEnd('/')
        val bodyJson = moshi.adapter(LoginBody::class.java).toJson(LoginBody(email, password))
        val response = withContext(Dispatchers.IO) {
            http.newCall(
                Request.Builder()
                    .url("$cleanUrl/v1/auth/login")
                    .post(bodyJson.toRequestBody("application/json".toMediaType()))
                    .build()
            ).execute()
        }
        if (!response.isSuccessful) {
            val errorBody = runCatching { response.body?.string() }.getOrNull()
            throw WearApiException(response.code, errorBody)
        }
        val pair = moshi.adapter(TokenPair::class.java).fromJson(response.body!!.string())!!
        auth.saveCredentials(cleanUrl, pair.accessToken, pair.refreshToken)
    }

    suspend fun getMembers(): List<WearMember> {
        val body = execute {
            Request.Builder()
                .url(url("/v1/members"))
                .header("Authorization", "Bearer ${auth.accessToken}")
                .build()
        }
        val type = Types.newParameterizedType(List::class.java, WearMember::class.java)
        return moshi.adapter<List<WearMember>>(type).fromJson(body)!!
    }

    suspend fun getCurrentFronts(): List<WearFront> {
        val body = execute {
            Request.Builder()
                .url(url("/v1/fronts/current"))
                .header("Authorization", "Bearer ${auth.accessToken}")
                .build()
        }
        val type = Types.newParameterizedType(List::class.java, WearFront::class.java)
        return moshi.adapter<List<WearFront>>(type).fromJson(body)!!
    }

    suspend fun createFront(memberIds: List<String>, replaceFronts: Boolean? = null) {
        val ids = memberIds.joinToString(",") { "\"$it\"" }
        val replaceField = replaceFronts?.let { ""","replace_fronts":$it""" } ?: ""
        val json = """{"member_ids":[$ids]$replaceField}"""
        execute {
            Request.Builder()
                .url(url("/v1/fronts"))
                .header("Authorization", "Bearer ${auth.accessToken}")
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()
        }
    }

    suspend fun getGroups(): List<WearGroup> {
        val body = execute {
            Request.Builder()
                .url(url("/v1/groups"))
                .header("Authorization", "Bearer ${auth.accessToken}")
                .build()
        }
        val type = Types.newParameterizedType(List::class.java, WearGroup::class.java)
        return moshi.adapter<List<WearGroup>>(type).fromJson(body)!!
    }

    suspend fun getGroupMembers(groupId: String): List<WearMember> {
        val body = execute {
            Request.Builder()
                .url(url("/v1/groups/$groupId/members"))
                .header("Authorization", "Bearer ${auth.accessToken}")
                .build()
        }
        val type = Types.newParameterizedType(List::class.java, WearMember::class.java)
        return moshi.adapter<List<WearMember>>(type).fromJson(body)!!
    }

    suspend fun setGroupMembers(groupId: String, memberIds: List<String>) {
        val json = moshi.adapter(GroupMembersBody::class.java).toJson(GroupMembersBody(memberIds))
        execute {
            Request.Builder()
                .url(url("/v1/groups/$groupId/members"))
                .header("Authorization", "Bearer ${auth.accessToken}")
                .put(json.toRequestBody("application/json".toMediaType()))
                .build()
        }
    }

    suspend fun createMember(name: String, displayName: String?, pronouns: String?): WearMember {
        val json = moshi.adapter(MemberCreateBody::class.java)
            .toJson(MemberCreateBody(name, displayName?.ifBlank { null }, pronouns?.ifBlank { null }))
        val body = execute {
            Request.Builder()
                .url(url("/v1/members"))
                .header("Authorization", "Bearer ${auth.accessToken}")
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()
        }
        return moshi.adapter(WearMember::class.java).fromJson(body)!!
    }

    suspend fun deleteFront(frontId: String) {
        execute {
            Request.Builder()
                .url(url("/v1/fronts/$frontId"))
                .header("Authorization", "Bearer ${auth.accessToken}")
                .delete()
                .build()
        }
    }
}
