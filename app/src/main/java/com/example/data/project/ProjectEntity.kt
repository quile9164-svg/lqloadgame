package com.example.data.project

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val projectJson: String,
    val lastUpdated: Long,
    val thumbnailPath: String?
)
