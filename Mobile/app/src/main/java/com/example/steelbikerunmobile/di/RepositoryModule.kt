package com.example.steelbikerunmobile.di

import com.example.steelbikerunmobile.data.repository.AuthRepositoryImpl
import com.example.steelbikerunmobile.data.repository.DriverRepositoryImpl
import com.example.steelbikerunmobile.data.repository.TripRepositoryImpl
import com.example.steelbikerunmobile.data.repository.UserRepositoryImpl
import com.example.steelbikerunmobile.domain.repository.AuthRepository
import com.example.steelbikerunmobile.domain.repository.DriverRepository
import com.example.steelbikerunmobile.domain.repository.TripRepository
import com.example.steelbikerunmobile.domain.repository.UserRepository
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
    abstract fun bindAuthRepository(authRepositoryImpl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindDriverRepository(driverRepositoryImpl: DriverRepositoryImpl): DriverRepository

    @Binds
    @Singleton
    abstract fun bindTripRepository(tripRepositoryImpl: TripRepositoryImpl): TripRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(userRepositoryImpl: UserRepositoryImpl): UserRepository
}
