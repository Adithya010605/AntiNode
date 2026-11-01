package dev.antinode.core.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
    val selectedSetupId: Long? = null,
)

@Entity(tableName = "setups")
data class SetupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val name: String,
    val deviceKey: String,
    val deviceKind: String,
    val deviceName: String,
    /** Comma-separated [dev.antinode.core.model.Art] names. */
    val art: String,
    val bypass: Boolean,
    val presetId: Long?,
    val chainJson: String,
    val modified: Boolean = false,
    val sortOrder: Int = 0,
    val lastUsedAt: Long = 0,
)

@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val name: String,
    val description: String,
    val chainJson: String,
    val sortOrder: Int = 0,
)

@Dao
interface EqDao {
    @Query("SELECT * FROM profiles ORDER BY sortOrder, id") fun profiles(): Flow<List<ProfileEntity>>
    @Query("SELECT * FROM profiles ORDER BY sortOrder, id") suspend fun profilesNow(): List<ProfileEntity>
    @Query("SELECT * FROM profiles WHERE id = :id") suspend fun profile(id: Long): ProfileEntity?
    @Insert suspend fun insert(p: ProfileEntity): Long
    @Update suspend fun update(p: ProfileEntity)
    @Query("DELETE FROM profiles WHERE id = :id") suspend fun deleteProfile(id: Long)

    @Query("SELECT * FROM setups ORDER BY sortOrder, id") fun setups(): Flow<List<SetupEntity>>
    @Query("SELECT * FROM setups WHERE profileId = :profileId ORDER BY sortOrder, id") suspend fun setupsNow(profileId: Long): List<SetupEntity>
    @Query("SELECT * FROM setups WHERE id = :id") suspend fun setup(id: Long): SetupEntity?
    @Insert suspend fun insert(s: SetupEntity): Long
    @Update suspend fun update(s: SetupEntity)
    @Query("DELETE FROM setups WHERE id = :id") suspend fun deleteSetup(id: Long)
    @Query("DELETE FROM setups WHERE profileId = :profileId") suspend fun deleteSetupsOf(profileId: Long)

    @Query("SELECT * FROM presets ORDER BY sortOrder, id") fun presets(): Flow<List<PresetEntity>>
    @Query("SELECT * FROM presets WHERE profileId = :profileId ORDER BY sortOrder, id") suspend fun presetsNow(profileId: Long): List<PresetEntity>
    @Query("SELECT * FROM presets WHERE id = :id") suspend fun preset(id: Long): PresetEntity?
    @Insert suspend fun insert(p: PresetEntity): Long
    @Update suspend fun update(p: PresetEntity)
    @Query("DELETE FROM presets WHERE id = :id") suspend fun deletePreset(id: Long)
    @Query("DELETE FROM presets WHERE profileId = :profileId") suspend fun deletePresetsOf(profileId: Long)
}

@Database(entities = [ProfileEntity::class, SetupEntity::class, PresetEntity::class], version = 1, exportSchema = false)
abstract class EqDatabase : RoomDatabase() {
    abstract fun dao(): EqDao
}
