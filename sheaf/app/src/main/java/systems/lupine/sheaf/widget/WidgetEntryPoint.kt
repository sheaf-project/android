package systems.lupine.sheaf.widget

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.repository.PreferencesRepository

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun sheafApiService(): SheafApiService
    fun okHttpClient(): OkHttpClient
    fun preferencesRepository(): PreferencesRepository
}
