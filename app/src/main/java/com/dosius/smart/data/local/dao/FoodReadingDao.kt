package com.dosius.smart.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dosius.smart.domain.model.Food
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodReadingDao {

    @Query("SELECT * FROM foods")
    fun getAllFoods(): Flow<List<Food>>

    @Query("SELECT * FROM foods WHERE id = :id")
    fun getFoodById(id: String): Flow<Food?>

    @Query("SELECT COUNT(*) FROM foods")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFood(food: Food)

    @Delete
    suspend fun deleteFood(food: Food)

    @Query("SELECT DISTINCT idCategory FROM foods ORDER BY idCategory ASC")
    fun getDistinctCategories(): Flow<List<String>>

    @Query("SELECT * FROM foods WHERE name LIKE '%' || :query || '%' OR idCategory LIKE '%' || :query || '%' ORDER BY entries DESC LIMIT 30")
    suspend fun searchFoods(query: String): List<Food>
}
