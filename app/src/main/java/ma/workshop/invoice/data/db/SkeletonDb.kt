package ma.workshop.invoice.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity(tableName = "skeleton")
data class SkeletonEntity(@PrimaryKey val id: Long, val name: String)

@Dao
interface SkeletonDao {
    @Query("SELECT * FROM skeleton") suspend fun all(): List<SkeletonEntity>
}

@Database(entities = [SkeletonEntity::class], version = 1, exportSchema = true)
abstract class SkeletonDb : RoomDatabase() {
    abstract fun dao(): SkeletonDao
}
