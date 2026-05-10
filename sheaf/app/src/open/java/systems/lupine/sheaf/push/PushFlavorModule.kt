package systems.lupine.sheaf.push

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class PushFlavorModule {
    @Binds
    abstract fun bindPushTokenProvider(impl: NoopTokenProvider): PushTokenProvider
}
