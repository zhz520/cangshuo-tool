package com.cangshuo.toolbox.feature.worldclock.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.feature.worldclock.domain.WorldClockHourFormat
import com.cangshuo.toolbox.feature.worldclock.domain.WorldClockPolicy
import com.cangshuo.toolbox.feature.worldclock.domain.WorldClockUseCases
import com.cangshuo.toolbox.feature.worldclock.domain.WorldClockZone
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WorldClockViewModel(
    private val useCases: WorldClockUseCases,
    private val savedStateHandle: SavedStateHandle,
    private val localeProvider: () -> Locale,
) : ViewModel() {
    private val systemZoneId = WorldClockPolicy.validatedId(useCases.systemZoneId()) ?: "UTC"
    private var extraZones: List<String>
    private var hourFormat: WorldClockHourFormat
    private var query: String
    private var currentError: WorldClockError? = null
    private var foreground = false
    private var ticker: Job? = null
    private var currentLocale: Locale = localeProvider()
    private var zones: List<WorldClockZone> = useCases.zoneOptions(currentLocale)
    private var searchResults: List<WorldClockZone>
    private val mutable = MutableStateFlow(WorldClockUiState())
    val state: StateFlow<WorldClockUiState> = mutable.asStateFlow()

    init {
        val hasSavedZones = savedStateHandle.contains(KEY_ZONES)
        extraZones = WorldClockPolicy.sanitize(
            if (hasSavedZones) {
                WorldClockPolicy.decode(
                    savedStateHandle.get<ArrayList<String>>(KEY_ZONES).orEmpty(),
                )
            } else {
                WorldClockPolicy.DEFAULT_ZONE_IDS
            },
            systemZoneId,
        )
        hourFormat = savedStateHandle.get<String>(KEY_FORMAT)
            ?.let { name -> WorldClockHourFormat.entries.firstOrNull { it.name == name } }
            ?: WorldClockHourFormat.HOUR_24
        query = savedStateHandle.get<String>(KEY_QUERY)
            .orEmpty()
            .take(WorldClockPolicy.MAX_QUERY_LENGTH)
        searchResults = useCases.filterZones(zones, query)
        persistConfiguration()
        rebuild()
    }

    fun start() {
        foreground = true
        rebuild()
        runTicker()
    }

    fun stop() {
        foreground = false
        ticker?.cancel()
        ticker = null
    }

    fun addZone(zoneId: String) {
        val id = WorldClockPolicy.validatedId(zoneId)
        if (id == null) {
            currentError = WorldClockError.INVALID_ZONE
            rebuild()
            return
        }
        if (id == systemZoneId || id in extraZones) {
            currentError = WorldClockError.DUPLICATE_ZONE
            rebuild()
            return
        }
        if (extraZones.size >= WorldClockPolicy.MAX_TOTAL_ZONES - 1) {
            currentError = WorldClockError.ZONE_LIMIT
            rebuild()
            return
        }
        extraZones = (extraZones + id).distinct()
        currentError = null
        persistConfiguration()
        rebuild()
    }

    fun removeZone(zoneId: String) {
        val id = WorldClockPolicy.validatedId(zoneId) ?: return
        if (id == systemZoneId) return
        val next = extraZones.filterNot { it == id }
        if (next.size == extraZones.size) return
        extraZones = next
        currentError = null
        persistConfiguration()
        rebuild()
    }

    fun setHourFormat(format: WorldClockHourFormat) {
        if (hourFormat == format) return
        hourFormat = format
        currentError = null
        persistConfiguration()
        rebuild()
    }

    fun updateQuery(value: String) {
        if (value.length > WorldClockPolicy.MAX_QUERY_LENGTH) {
            currentError = WorldClockError.QUERY_TOO_LONG
            rebuild()
            return
        }
        query = value
        currentError = null
        searchResults = useCases.filterZones(zones, query)
        persistConfiguration()
        rebuild()
    }

    fun clearQuery() {
        if (query.isEmpty()) return
        query = ""
        currentError = null
        searchResults = zones
        persistConfiguration()
        rebuild()
    }

    fun dismissError() {
        if (currentError == null) return
        currentError = null
        rebuild()
    }

    fun updateLocale(locale: Locale) {
        if (locale == currentLocale) return
        currentLocale = locale
        zones = useCases.zoneOptions(locale)
        searchResults = useCases.filterZones(zones, query)
        rebuild()
    }

    private fun rebuild() {
        val selected = listOf(systemZoneId) + extraZones
        val readings = useCases.readings(
            zoneIds = selected,
            instant = useCases.now(),
            referenceZoneId = systemZoneId,
            locale = currentLocale,
            hourFormat = hourFormat,
        )
        if (readings.size != selected.size && currentError == null) {
            currentError = WorldClockError.READ_FAILED
        }
        val systemReading = readings.firstOrNull { it.zoneId == systemZoneId }
        mutable.value = WorldClockUiState(
            systemZoneId = systemZoneId,
            systemReading = systemReading,
            readings = readings.filterNot { it.zoneId == systemZoneId },
            addedZoneIds = extraZones,
            hourFormat = hourFormat,
            query = query,
            searchResults = searchResults,
            allZoneCount = zones.size,
            error = currentError,
        )
    }

    private fun persistConfiguration() {
        savedStateHandle[KEY_ZONES] = WorldClockPolicy.encode(extraZones)
        savedStateHandle[KEY_FORMAT] = hourFormat.name
        savedStateHandle[KEY_QUERY] = query
    }

    private fun runTicker() {
        ticker?.cancel()
        ticker = null
        if (!foreground) return
        ticker = viewModelScope.launch {
            while (isActive && foreground) {
                val millis = useCases.now().toEpochMilli()
                val untilBoundary = Math.floorMod(millis, WorldClockPolicy.TICK_INTERVAL_MS)
                    .let { remainder ->
                        if (remainder == 0L) WorldClockPolicy.TICK_INTERVAL_MS
                        else WorldClockPolicy.TICK_INTERVAL_MS - remainder
                    }
                delay(untilBoundary)
                rebuild()
            }
        }
    }

    companion object {
        private const val KEY_ZONES = "worldClockZones"
        private const val KEY_FORMAT = "worldClockFormat"
        private const val KEY_QUERY = "worldClockQuery"

        fun factory(
            useCases: WorldClockUseCases,
            localeProvider: () -> Locale,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                WorldClockViewModel(useCases, extras.createSavedStateHandle(), localeProvider) as T
        }
    }
}
