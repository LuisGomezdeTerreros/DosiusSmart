package com.dosius.smart.data.repository

import com.dosius.smart.data.local.dao.TherapyParameterDao
import com.dosius.smart.domain.model.TherapyParameter
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TherapyParameterRepository @Inject constructor(
    private val therapyParameterDao: TherapyParameterDao
){
    suspend fun upsertAll(therapyParameters: List<TherapyParameter>) = therapyParameterDao.upsertAll(therapyParameters)

    fun getAll(): Flow<List<TherapyParameter>> = therapyParameterDao.getAll()

    suspend fun getAllSuspend(): List<TherapyParameter> = therapyParameterDao.getAllSuspend()

    suspend fun getBySlotAndType(slot: Int, type: String): TherapyParameter? =
        therapyParameterDao.getBySlotAndType(slot, type)

    suspend fun upsert(param: TherapyParameter) = therapyParameterDao.upsert(param)

    suspend fun getCount(): Int = therapyParameterDao.getCount()

}
