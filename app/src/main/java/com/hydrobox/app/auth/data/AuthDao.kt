package com.hydrobox.app.auth.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AuthDao {

    @Query("SELECT * FROM users_local WHERE email = :email COLLATE NOCASE LIMIT 1")
    suspend fun findByEmail(email: String): UserEntity?

    @Query("SELECT * FROM users_local WHERE principalUuid = :principalUuid LIMIT 1")
    suspend fun findByPrincipalUuid(principalUuid: String): UserEntity?

    @Query("SELECT * FROM users_local WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<UserEntity?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(vararg users: UserEntity)

    @Query("SELECT * FROM users_local WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): UserEntity?

    @androidx.room.Update
    suspend fun update(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(user: UserEntity): Long
}

