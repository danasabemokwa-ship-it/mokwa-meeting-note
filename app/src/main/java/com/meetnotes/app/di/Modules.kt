package com.meetnotes.app.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.meetnotes.app.BuildConfig
import com.meetnotes.app.data.local.ActionItemDao
import com.meetnotes.app.data.local.AppDatabase
import com.meetnotes.app.data.local.DocumentDao
import com.meetnotes.app.data.local.MeetingDao
import com.meetnotes.app.data.remote.AnthropicApi
import com.meetnotes.app.data.remote.GeminiApi
import com.meetnotes.app.data.remote.OpenAiApi
import com.meetnotes.app.data.repository.MeetingRepositoryImpl
import com.meetnotes.app.domain.repository.MeetingRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "meetnotes.db")
            // Version 1 only ever held test builds; later versions must add real Migrations.
            .fallbackToDestructiveMigrationFrom(1)
            .build()

    @Provides fun provideMeetingDao(db: AppDatabase): MeetingDao = db.meetingDao()
    @Provides fun provideActionItemDao(db: AppDatabase): ActionItemDao = db.actionItemDao()
    @Provides fun provideDocumentDao(db: AppDatabase): DocumentDao = db.documentDao()

    @Provides @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Provides @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        // Transcribing an hour of audio can take a few minutes server-side.
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.MINUTES)
        .readTimeout(10, TimeUnit.MINUTES)
        .apply {
            if (BuildConfig.DEBUG) {
                // BASIC never logs headers, so API keys don't end up in logcat.
                addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
            }
        }
        .build()

    private fun retrofit(baseUrl: String, client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides @Singleton
    fun provideOpenAi(client: OkHttpClient, json: Json): OpenAiApi =
        retrofit("https://api.openai.com/", client, json).create(OpenAiApi::class.java)

    @Provides @Singleton
    fun provideGemini(client: OkHttpClient, json: Json): GeminiApi =
        retrofit("https://generativelanguage.googleapis.com/", client, json).create(GeminiApi::class.java)

    @Provides @Singleton
    fun provideAnthropic(client: OkHttpClient, json: Json): AnthropicApi =
        retrofit("https://api.anthropic.com/", client, json).create(AnthropicApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton
    abstract fun bindMeetingRepository(impl: MeetingRepositoryImpl): MeetingRepository
}
