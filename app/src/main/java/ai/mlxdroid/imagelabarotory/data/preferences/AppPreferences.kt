package ai.mlxdroid.imagelabarotory.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

class AppPreferences(private val context: Context) {

    companion object {
        private val SELECTED_MODEL_ID = stringPreferencesKey("selected_model_id")
        private val QUALITY_PRESET = stringPreferencesKey("quality_preset")
        private val USE_GPU_ACCELERATION = booleanPreferencesKey("use_gpu_acceleration")
        private val AUTO_DELETE_OLD_GENERATIONS = booleanPreferencesKey("auto_delete_old_generations")
        private val MAX_CACHED_IMAGES = intPreferencesKey("max_cached_images")
        private val DEFAULT_IMAGE_SIZE = intPreferencesKey("default_image_size")
        private val GENERATION_STEPS = intPreferencesKey("generation_steps")
        private val GUIDANCE_SCALE = intPreferencesKey("guidance_scale")
        private val WIFI_ONLY_DOWNLOADS = booleanPreferencesKey("wifi_only_downloads")
    }

    val selectedModelId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[SELECTED_MODEL_ID]
    }

    val qualityPreset: Flow<QualityPreset> = context.dataStore.data.map { preferences ->
        val presetName = preferences[QUALITY_PRESET] ?: QualityPreset.BALANCED.name
        QualityPreset.valueOf(presetName)
    }

    val useGpuAcceleration: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[USE_GPU_ACCELERATION] ?: true
    }

    val autoDeleteOldGenerations: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUTO_DELETE_OLD_GENERATIONS] ?: false
    }

    val maxCachedImages: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[MAX_CACHED_IMAGES] ?: 50
    }

    val defaultImageSize: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[DEFAULT_IMAGE_SIZE] ?: 512
    }

    val generationSteps: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[GENERATION_STEPS] ?: 20
    }

    val guidanceScale: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[GUIDANCE_SCALE] ?: 75
    }

    val wifiOnlyDownloads: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[WIFI_ONLY_DOWNLOADS] ?: true
    }

    suspend fun setSelectedModelId(modelId: String) {
        context.dataStore.edit { preferences ->
            preferences[SELECTED_MODEL_ID] = modelId
        }
    }

    suspend fun setQualityPreset(preset: QualityPreset) {
        context.dataStore.edit { preferences ->
            preferences[QUALITY_PRESET] = preset.name
        }
    }

    suspend fun setUseGpuAcceleration(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[USE_GPU_ACCELERATION] = enabled
        }
    }

    suspend fun setAutoDeleteOldGenerations(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_DELETE_OLD_GENERATIONS] = enabled
        }
    }

    suspend fun setMaxCachedImages(count: Int) {
        context.dataStore.edit { preferences ->
            preferences[MAX_CACHED_IMAGES] = count
        }
    }

    suspend fun setDefaultImageSize(size: Int) {
        context.dataStore.edit { preferences ->
            preferences[DEFAULT_IMAGE_SIZE] = size
        }
    }

    suspend fun setGenerationSteps(steps: Int) {
        context.dataStore.edit { preferences ->
            preferences[GENERATION_STEPS] = steps
        }
    }

    suspend fun setGuidanceScale(scale: Int) {
        context.dataStore.edit { preferences ->
            preferences[GUIDANCE_SCALE] = scale
        }
    }

    suspend fun setWifiOnlyDownloads(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[WIFI_ONLY_DOWNLOADS] = enabled
        }
    }
}

enum class QualityPreset {
    FAST,
    BALANCED,
    QUALITY
}
