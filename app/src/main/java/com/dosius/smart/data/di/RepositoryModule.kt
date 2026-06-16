package com.dosius.smart.data.di

import com.dosius.smart.data.repository.LibreLinkUpRepository
import com.dosius.smart.domain.repository.GlucoseRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindGlucoseRepository(
        impl: LibreLinkUpRepository
    ): GlucoseRepository


}