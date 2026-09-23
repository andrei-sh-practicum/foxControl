package com.andrew.foxcontrol.di

import com.andrew.foxcontrol.core.security.KeystoreSecretCipher
import com.andrew.foxcontrol.core.security.SecretCipher
import com.andrew.foxcontrol.data.repository.UsageStatsRepositoryImpl
import com.andrew.foxcontrol.domain.repository.UsageStatsRepository
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
    abstract fun bindUsageStatsRepository(impl: UsageStatsRepositoryImpl): UsageStatsRepository

    @Binds
    @Singleton
    abstract fun bindSecretCipher(impl: KeystoreSecretCipher): SecretCipher
}
