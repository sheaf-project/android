# Moshi
-keepclassmembers class ** {
    @com.squareup.moshi.FromJson *;
    @com.squareup.moshi.ToJson *;
}
-keep class systems.lupine.sheaf.data.model.** { *; }

# Retrofit
-keepattributes Signature
-keepattributes Exceptions
-keep class retrofit2.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Credential Manager loads its Play Services provider by reflection
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** {
  *;
}
