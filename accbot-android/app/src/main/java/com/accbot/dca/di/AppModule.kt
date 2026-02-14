package com.accbot.dca.di

import android.content.Context
import androidx.work.WorkManager
import com.accbot.dca.data.local.DcaDatabase
import com.accbot.dca.data.local.DcaPlanDao
import com.accbot.dca.data.local.CredentialsStore
import com.accbot.dca.data.local.OnboardingPreferences
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.data.local.DailyPriceDao
import com.accbot.dca.data.local.ExchangeBalanceDao
import com.accbot.dca.data.local.MonthlySummaryDao
import com.accbot.dca.data.local.TransactionDao
import com.accbot.dca.data.local.WithdrawalDao
import com.accbot.dca.exchange.ExchangeApiFactory
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        userPreferences: UserPreferences
    ): DcaDatabase {
        // Database is selected based on sandbox mode at app startup
        // Switching modes requires app restart to use correct database
        return DcaDatabase.getInstance(context, userPreferences.isSandboxMode())
    }

    @Provides
    @Singleton
    fun provideDcaPlanDao(database: DcaDatabase): DcaPlanDao {
        return database.dcaPlanDao()
    }

    @Provides
    @Singleton
    fun provideTransactionDao(database: DcaDatabase): TransactionDao {
        return database.transactionDao()
    }

    @Provides
    @Singleton
    fun provideWithdrawalDao(database: DcaDatabase): WithdrawalDao {
        return database.withdrawalDao()
    }

    @Provides
    @Singleton
    fun provideExchangeBalanceDao(database: DcaDatabase): ExchangeBalanceDao {
        return database.exchangeBalanceDao()
    }

    @Provides
    @Singleton
    fun provideMonthlySummaryDao(database: DcaDatabase): MonthlySummaryDao {
        return database.monthlySummaryDao()
    }

    @Provides
    @Singleton
    fun provideDailyPriceDao(database: DcaDatabase): DailyPriceDao {
        return database.dailyPriceDao()
    }

    @Provides
    @Singleton
    fun provideCredentialsStore(@ApplicationContext context: Context): CredentialsStore {
        return CredentialsStore(context)
    }

    @Provides
    @Singleton
    fun provideOnboardingPreferences(@ApplicationContext context: Context): OnboardingPreferences {
        return OnboardingPreferences(context)
    }

    @Provides
    @Singleton
    fun provideUserPreferences(@ApplicationContext context: Context): UserPreferences {
        return UserPreferences(context)
    }

    @Provides
    @Singleton
    fun provideExchangeApiFactory(userPreferences: UserPreferences, okHttpClient: OkHttpClient): ExchangeApiFactory {
        return ExchangeApiFactory(userPreferences, okHttpClient)
    }

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return Gson()
    }
}
