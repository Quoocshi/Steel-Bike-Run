package com.example.steelbikerunmobile.di

import com.example.steelbikerunmobile.BuildConfig
import com.example.steelbikerunmobile.data.remote.AuthTokenInterceptor
import com.example.steelbikerunmobile.data.remote.api.AuthApiService
import com.example.steelbikerunmobile.data.remote.api.DriverApiService
import com.example.steelbikerunmobile.data.remote.api.TripApiService
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(authTokenInterceptor: AuthTokenInterceptor): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .addInterceptor(authTokenInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthApiService(retrofit: Retrofit): AuthApiService {
        return retrofit.create(AuthApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideDriverApiService(retrofit: Retrofit): DriverApiService {
        return retrofit.create(DriverApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideTripApiService(retrofit: Retrofit): TripApiService {
        return retrofit.create(TripApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()
}
