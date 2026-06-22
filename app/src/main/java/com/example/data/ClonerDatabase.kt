package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Update
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.models.ClonedApp
import com.example.models.SpoofProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface ClonerDao {
    @Query("SELECT * FROM spoof_profiles ORDER BY id DESC")
    fun getAllProfilesFlow(): Flow<List<SpoofProfile>>

    @Query("SELECT * FROM spoof_profiles WHERE id = :id")
    suspend fun getProfileById(id: Long): SpoofProfile?

    @Insert
    suspend fun insertProfile(profile: SpoofProfile): Long

    @Update
    suspend fun updateProfile(profile: SpoofProfile)

    @Delete
    suspend fun deleteProfile(profile: SpoofProfile)

    @Query("SELECT * FROM cloned_apps ORDER BY creationTimeMs DESC")
    fun getAllClonedAppsFlow(): Flow<List<ClonedApp>>

    @Insert
    suspend fun insertClonedApp(app: ClonedApp): Long

    @Update
    suspend fun updateClonedApp(app: ClonedApp)

    @Delete
    suspend fun deleteClonedApp(app: ClonedApp)

    @Query("DELETE FROM cloned_apps")
    suspend fun deleteAllClones()
}

@Database(entities = [SpoofProfile::class, ClonedApp::class], version = 1, exportSchema = false)
abstract class ClonerDatabase : RoomDatabase() {
    abstract fun clonerDao(): ClonerDao

    companion object {
        @Volatile
        private var INSTANCE: ClonerDatabase? = null

        fun getDatabase(context: Context): ClonerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ClonerDatabase::class.java,
                    "cloner_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                @Suppress("UpdateOfToItself")
                INSTANCE = instance
                instance
            }
        }
    }
}
