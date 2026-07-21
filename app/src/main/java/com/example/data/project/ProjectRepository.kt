package com.example.data.project

import com.example.domain.model.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ProjectRepository(private val projectDao: ProjectDao) {

    val allProjects: Flow<List<Project>> = projectDao.getAllProjects().map { entityList ->
        entityList.map { entity ->
            val project = ProjectSerialization.deserialize(entity.projectJson)
            // Ensure id and name and thumbnail match what's stored in Room columns
            project.copy(
                id = entity.id,
                name = entity.name,
                lastUpdated = entity.lastUpdated,
                thumbnailPath = entity.thumbnailPath
            )
        }
    }

    suspend fun insertProject(project: Project): Int {
        val serializedJson = ProjectSerialization.serialize(project)
        val entity = ProjectEntity(
            id = project.id,
            name = project.name,
            projectJson = serializedJson,
            lastUpdated = System.currentTimeMillis(),
            thumbnailPath = project.thumbnailPath
        )
        val newId = projectDao.insertProject(entity)
        return newId.toInt()
    }

    suspend fun deleteProjectById(id: Int) {
        projectDao.deleteProjectById(id)
    }

    suspend fun getProjectById(id: Int): Project? {
        val entity = projectDao.getProjectById(id) ?: return null
        val project = ProjectSerialization.deserialize(entity.projectJson)
        return project.copy(
            id = entity.id,
            name = entity.name,
            lastUpdated = entity.lastUpdated,
            thumbnailPath = entity.thumbnailPath
        )
    }
}
