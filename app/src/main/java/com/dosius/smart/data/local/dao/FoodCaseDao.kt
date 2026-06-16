package com.dosius.smart.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dosius.smart.domain.model.FoodCase
import com.dosius.smart.domain.model.TherapyParameter

@Dao
interface FoodCaseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(foodCase: FoodCase)

    @Query("SELECT * FROM food_cases WHERE foodId = :foodId  ORDER BY id DESC ")
    suspend fun getByFoodId(foodId: String) :  List<FoodCase>

    @Query("SELECT * FROM food_cases WHERE foodId IN (:foodIds) ORDER BY id DESC")
    suspend fun getBySiblingFoodIds(foodIds: List<String>) : List<FoodCase>

    @Query("SELECT * FROM food_cases")
    suspend fun getAll(): List<FoodCase>

    @Query("SELECT COUNT(*) FROM food_cases")
    suspend fun getCount(): Int
}