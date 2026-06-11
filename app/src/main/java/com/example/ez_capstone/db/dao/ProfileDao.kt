package com.example.ez_capstone.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.ez_capstone.db.entity.ProfileEntity

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun getProfile(): ProfileEntity?

    @Upsert
    suspend fun upsertProfile(profile: ProfileEntity)

    @Query("UPDATE profile SET homeAddress = :address, homeLat = :lat, homeLng = :lng WHERE id = 1")
    suspend fun updateHome(address: String, lat: Double, lng: Double)

    @Query("UPDATE profile SET workAddress = :address, workLat = :lat, workLng = :lng WHERE id = 1")
    suspend fun updateWork(address: String, lat: Double, lng: Double)

    @Query("UPDATE profile SET vehicleType = :type WHERE id = 1")
    suspend fun updateVehicleType(type: String)

    @Query("UPDATE profile SET fuelType = :type WHERE id = 1")
    suspend fun updateFuelType(type: String)

    @Query("UPDATE profile SET ttsStyle = :style WHERE id = 1")
    suspend fun updateTtsStyle(style: String)
}
