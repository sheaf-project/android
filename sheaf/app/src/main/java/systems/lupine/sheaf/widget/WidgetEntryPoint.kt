package systems.lupine.sheaf.widget

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import systems.lupine.sheaf.data.api.SheafApiService

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun sheafApiService(): SheafApiService
}
