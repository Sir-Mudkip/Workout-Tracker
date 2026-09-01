package com.luke.workouttracker.di

import android.content.Context
import androidx.room.Room
import com.luke.workouttracker.data.db.AppDatabase
import com.luke.workouttracker.data.db.dao.PeakDao
import com.luke.workouttracker.data.db.dao.ProgramDao
import com.luke.workouttracker.data.db.dao.SessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "workout.db")
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
            )
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideProgramDao(db: AppDatabase): ProgramDao = db.programDao()

    @Provides
    fun provideSessionDao(db: AppDatabase): SessionDao = db.sessionDao()

    @Provides
    fun providePeakDao(db: AppDatabase): PeakDao = db.peakDao()
}
