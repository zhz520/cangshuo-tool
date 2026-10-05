package com.cangshuo.toolbox

import android.content.Context
import androidx.room.Room
import com.cangshuo.toolbox.core.database.MIGRATION_1_2
import com.cangshuo.toolbox.core.database.MIGRATION_2_3
import com.cangshuo.toolbox.core.database.MIGRATION_3_4
import com.cangshuo.toolbox.feature.qr.data.RoomQrHistoryRepository
import com.cangshuo.toolbox.feature.qr.domain.QrHistoryUseCases
import com.cangshuo.toolbox.core.database.ToolboxDatabase
import com.cangshuo.toolbox.core.tool.ToolRegistry
import com.cangshuo.toolbox.core.tool.ToolRegistryStore
import com.cangshuo.toolbox.feature.calculator.CalculatorToolDefinition
import com.cangshuo.toolbox.feature.calculator.data.LocalCalculatorRepository
import com.cangshuo.toolbox.feature.calculator.domain.CalculateExpressionUseCase
import com.cangshuo.toolbox.feature.calculator.ui.CalculatorViewModel
import com.cangshuo.toolbox.feature.converter.UnitConverterToolDefinition
import com.cangshuo.toolbox.feature.converter.data.LocalConverterRepository
import com.cangshuo.toolbox.feature.converter.domain.ConvertUseCase
import com.cangshuo.toolbox.feature.converter.ui.ConverterViewModel
import com.cangshuo.toolbox.feature.timestamp.TimestampToolDefinition
import com.cangshuo.toolbox.feature.timestamp.data.LocalTimestampRepository
import com.cangshuo.toolbox.feature.timestamp.domain.ConvertDateTimeUseCase
import com.cangshuo.toolbox.feature.timestamp.domain.ConvertTimestampUseCase
import com.cangshuo.toolbox.feature.timestamp.domain.GetCurrentTimeUseCase
import com.cangshuo.toolbox.feature.timestamp.ui.TimestampViewModel
import com.cangshuo.toolbox.feature.uuid.UuidToolDefinition
import com.cangshuo.toolbox.feature.uuid.data.LocalUuidRepository
import com.cangshuo.toolbox.feature.uuid.domain.GenerateUuidUseCase
import com.cangshuo.toolbox.feature.uuid.ui.UuidViewModel
import com.cangshuo.toolbox.feature.base64.Base64ToolDefinition
import com.cangshuo.toolbox.feature.base64.data.LocalBase64Repository
import com.cangshuo.toolbox.feature.base64.domain.DecodeBase64UseCase
import com.cangshuo.toolbox.feature.base64.domain.EncodeBase64UseCase
import com.cangshuo.toolbox.feature.base64.ui.Base64ViewModel
import com.cangshuo.toolbox.feature.urlcodec.UrlCodecToolDefinition
import com.cangshuo.toolbox.feature.urlcodec.data.LocalUrlCodecRepository
import com.cangshuo.toolbox.feature.urlcodec.domain.DecodeUrlUseCase
import com.cangshuo.toolbox.feature.urlcodec.domain.EncodeUrlUseCase
import com.cangshuo.toolbox.feature.urlcodec.ui.UrlCodecViewModel
import com.cangshuo.toolbox.feature.hash.HashToolDefinition
import com.cangshuo.toolbox.feature.hash.data.LocalHashRepository
import com.cangshuo.toolbox.feature.hash.domain.ComputeHashesUseCase
import com.cangshuo.toolbox.feature.hash.ui.HashViewModel
import com.cangshuo.toolbox.feature.json.JsonToolDefinition
import com.cangshuo.toolbox.feature.json.data.LocalJsonRepository
import com.cangshuo.toolbox.feature.json.domain.CompressJsonUseCase
import com.cangshuo.toolbox.feature.json.domain.EscapeJsonUseCase
import com.cangshuo.toolbox.feature.json.domain.FormatJsonUseCase
import com.cangshuo.toolbox.feature.json.domain.UnescapeJsonUseCase
import com.cangshuo.toolbox.feature.json.ui.JsonViewModel
import com.cangshuo.toolbox.feature.text.TextToolDefinition
import com.cangshuo.toolbox.feature.text.data.LocalTextRepository
import com.cangshuo.toolbox.feature.text.domain.ComputeTextStatisticsUseCase
import com.cangshuo.toolbox.feature.text.domain.FindReplaceUseCase
import com.cangshuo.toolbox.feature.text.domain.TransformTextUseCase
import com.cangshuo.toolbox.feature.text.ui.TextViewModel
import com.cangshuo.toolbox.feature.qr.QrToolDefinition
import com.cangshuo.toolbox.feature.qr.data.LocalQrRepository
import com.cangshuo.toolbox.feature.qr.data.LocalQrExportRepository
import com.cangshuo.toolbox.feature.qr.domain.SaveQrImageUseCase
import com.cangshuo.toolbox.feature.qr.domain.PrepareQrShareUseCase
import com.cangshuo.toolbox.feature.qr.domain.DecodeQrUseCase
import com.cangshuo.toolbox.feature.qr.domain.GenerateQrUseCase
import com.cangshuo.toolbox.feature.qr.domain.LoadQrPreviewUseCase
import com.cangshuo.toolbox.feature.qr.ui.QrViewModel
import com.cangshuo.toolbox.feature.webtools.QrStudioToolDefinition
import com.cangshuo.toolbox.feature.webtools.domain.RefreshWebToolCatalogUseCase
import com.cangshuo.toolbox.feature.webtools.domain.RestoreWebToolCatalogUseCase
import com.cangshuo.toolbox.feature.webtools.domain.RequestWebToolCatalogSyncUseCase
import com.cangshuo.toolbox.feature.webtools.data.RoomWebToolCatalogCache
import com.cangshuo.toolbox.core.network.ToolCatalogClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import com.cangshuo.toolbox.feature.imagecompress.ImageCompressToolDefinition
import com.cangshuo.toolbox.feature.imagecompress.data.LocalImageCompressRepository
import com.cangshuo.toolbox.feature.imagecompress.domain.CompressImageUseCase
import com.cangshuo.toolbox.feature.imagecompress.domain.DiscardImageUseCase
import com.cangshuo.toolbox.feature.imagecompress.domain.PrepareImageShareUseCase
import com.cangshuo.toolbox.feature.imagecompress.domain.ReadImageMetadataUseCase
import com.cangshuo.toolbox.feature.imagecompress.domain.SaveImageUseCase
import com.cangshuo.toolbox.feature.imagecompress.ui.ImageCompressViewModel
import com.cangshuo.toolbox.feature.home.data.RegistryHomeRepository
import com.cangshuo.toolbox.feature.home.domain.GetHomeUseCase
import com.cangshuo.toolbox.feature.home.domain.OpenHomeToolUseCase
import com.cangshuo.toolbox.feature.home.ui.HomeViewModel
import com.cangshuo.toolbox.feature.search.data.RegistryToolSearchRepository
import com.cangshuo.toolbox.feature.search.domain.SearchToolsUseCase
import com.cangshuo.toolbox.feature.search.ui.SearchViewModel
import com.cangshuo.toolbox.feature.favorites.data.RoomFavoriteRepository
import com.cangshuo.toolbox.feature.favorites.domain.ObserveFavoritesUseCase
import com.cangshuo.toolbox.feature.favorites.domain.SetFavoriteUseCase
import com.cangshuo.toolbox.feature.favorites.ui.FavoritesViewModel
import com.cangshuo.toolbox.feature.recent.data.RoomRecentRepository
import com.cangshuo.toolbox.feature.recent.domain.ClearRecentUseCase
import com.cangshuo.toolbox.feature.recent.domain.ObserveRecentUseCase
import com.cangshuo.toolbox.feature.recent.domain.RecordToolUseUseCase
import com.cangshuo.toolbox.feature.recent.ui.RecentViewModel

/** Composition root. Add completed, trusted tool definitions to this collection. */
class ToolboxAppContainer(context: Context) {
    private val applicationContext = context.applicationContext
    private val resources = applicationContext.resources
    private val database = Room.databaseBuilder(applicationContext, ToolboxDatabase::class.java, "toolbox.db")
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, com.cangshuo.toolbox.core.database.MIGRATION_4_5,
            com.cangshuo.toolbox.core.database.MIGRATION_5_6, com.cangshuo.toolbox.core.database.MIGRATION_6_7)
        .build()
    private val calculatorFactory = CalculatorViewModel.factory(
        CalculateExpressionUseCase(LocalCalculatorRepository()),
    )
    private val converterFactory = ConverterViewModel.factory(
        ConvertUseCase(LocalConverterRepository()),
    )
    private val timestampRepository = LocalTimestampRepository()
    private val timestampFactory = TimestampViewModel.factory(
        getCurrentTimeUseCase = GetCurrentTimeUseCase(timestampRepository),
        convertTimestampUseCase = ConvertTimestampUseCase(timestampRepository),
        convertDateTimeUseCase = ConvertDateTimeUseCase(timestampRepository),
    )
    private val uuidFactory = UuidViewModel.factory(
        GenerateUuidUseCase(LocalUuidRepository()),
    )
    private val base64Repository = LocalBase64Repository()
    private val base64Factory = Base64ViewModel.factory(
        encodeUseCase = EncodeBase64UseCase(base64Repository),
        decodeUseCase = DecodeBase64UseCase(base64Repository),
    )
    private val urlCodecRepository = LocalUrlCodecRepository()
    private val urlCodecFactory = UrlCodecViewModel.factory(
        encodeUseCase = EncodeUrlUseCase(urlCodecRepository),
        decodeUseCase = DecodeUrlUseCase(urlCodecRepository),
    )
    private val hashRepository = LocalHashRepository()
    private val hashFactory = HashViewModel.factory(
        computeHashesUseCase = ComputeHashesUseCase(hashRepository),
    )
    private val jsonRepository = LocalJsonRepository()
    private val jsonFactory = JsonViewModel.factory(
        formatJsonUseCase = FormatJsonUseCase(jsonRepository),
        compressJsonUseCase = CompressJsonUseCase(jsonRepository),
        escapeJsonUseCase = EscapeJsonUseCase(jsonRepository),
        unescapeJsonUseCase = UnescapeJsonUseCase(jsonRepository),
    )
    private val textRepository = LocalTextRepository()
    private val textFactory = TextViewModel.factory(
        computeStatsUseCase = ComputeTextStatisticsUseCase(textRepository),
        transformUseCase = TransformTextUseCase(textRepository),
        findReplaceUseCase = FindReplaceUseCase(textRepository),
    )
    private val qrRepository = LocalQrRepository(applicationContext)
    private val qrExportRepository = LocalQrExportRepository(applicationContext)
    private val qrFactory = QrViewModel.factory(
        generateQrUseCase = GenerateQrUseCase(qrRepository),
        decodeQrUseCase = DecodeQrUseCase(qrRepository),
        saveQrImageUseCase = SaveQrImageUseCase(qrExportRepository),
        prepareQrShareUseCase = PrepareQrShareUseCase(qrExportRepository),
        loadQrPreviewUseCase = LoadQrPreviewUseCase(qrRepository),
        historyUseCases = QrHistoryUseCases(RoomQrHistoryRepository(database.qrHistoryDao())),
    )
    private val imageCompressRepository = LocalImageCompressRepository(applicationContext)
    private val imageCompressFactory = ImageCompressViewModel.factory(
        readMetadataUseCase = ReadImageMetadataUseCase(imageCompressRepository),
        compressImageUseCase = CompressImageUseCase(imageCompressRepository),
        saveImageUseCase = SaveImageUseCase(imageCompressRepository),
        prepareShareUseCase = PrepareImageShareUseCase(imageCompressRepository),
        discardImageUseCase = DiscardImageUseCase(imageCompressRepository),
    )
    private val deviceInfoFactory=com.cangshuo.toolbox.feature.deviceinfo.ui.DeviceInfoViewModel.factory(
        com.cangshuo.toolbox.feature.deviceinfo.domain.ReadDeviceInfoUseCase(
            com.cangshuo.toolbox.feature.deviceinfo.data.AndroidDeviceInfoRepository(applicationContext)))
    private val storageInfoFactory=com.cangshuo.toolbox.feature.storage.ui.StorageInfoViewModel.factory(
        com.cangshuo.toolbox.feature.storage.domain.ReadStorageInfoUseCase(
            com.cangshuo.toolbox.feature.storage.data.AndroidStorageInfoRepository(applicationContext)))
    private val batteryInfoFactory=com.cangshuo.toolbox.feature.battery.ui.BatteryInfoViewModel.factory(
        com.cangshuo.toolbox.feature.battery.domain.ObserveBatteryInfoUseCase(
            com.cangshuo.toolbox.feature.battery.data.AndroidBatteryInfoRepository(applicationContext)))
    private val sensorsFactory=com.cangshuo.toolbox.feature.sensors.ui.SensorsViewModel.factory(
        com.cangshuo.toolbox.feature.sensors.domain.SensorsUseCases(
            com.cangshuo.toolbox.feature.sensors.data.AndroidSensorsRepository(applicationContext)))
    private val compassFactory=com.cangshuo.toolbox.feature.compass.ui.CompassViewModel.factory(
        com.cangshuo.toolbox.feature.compass.domain.ObserveCompassUseCase(
            com.cangshuo.toolbox.feature.compass.data.AndroidCompassRepository(applicationContext)))
    private val levelFactory=com.cangshuo.toolbox.feature.level.ui.LevelViewModel.factory(
        com.cangshuo.toolbox.feature.level.domain.ObserveLevelUseCase(
            com.cangshuo.toolbox.feature.level.data.AndroidLevelRepository(applicationContext)))
    private val pingFactory=com.cangshuo.toolbox.feature.ping.ui.PingViewModel.factory(
        com.cangshuo.toolbox.feature.ping.domain.ExecutePingUseCase(
            com.cangshuo.toolbox.feature.ping.data.NativePingRepository()))
    private val httpStatusFactory=com.cangshuo.toolbox.feature.httpstatus.ui.HttpStatusViewModel.factory(
        com.cangshuo.toolbox.feature.httpstatus.domain.ProbeHttpStatusUseCase(
            com.cangshuo.toolbox.feature.httpstatus.data.OkHttpStatusRepository()))
    private fun bundledRegistry(resources: android.content.res.Resources) = ToolRegistry(
        definitions = listOf(
            CalculatorToolDefinition(resources, calculatorFactory),
            UnitConverterToolDefinition(resources, converterFactory),
            TimestampToolDefinition(resources, timestampFactory),
            UuidToolDefinition(resources, uuidFactory),
            Base64ToolDefinition(resources, base64Factory),
            UrlCodecToolDefinition(resources, urlCodecFactory),
            HashToolDefinition(resources, hashFactory),
            JsonToolDefinition(resources, jsonFactory),
            TextToolDefinition(resources, textFactory),
            QrToolDefinition(resources, qrFactory),
            ImageCompressToolDefinition(resources, imageCompressFactory),
            QrStudioToolDefinition(resources),
            com.cangshuo.toolbox.feature.deviceinfo.DeviceInfoToolDefinition(resources,deviceInfoFactory),
            com.cangshuo.toolbox.feature.storage.StorageInfoToolDefinition(resources,storageInfoFactory),
            com.cangshuo.toolbox.feature.battery.BatteryInfoToolDefinition(resources,batteryInfoFactory),
            com.cangshuo.toolbox.feature.sensors.SensorsToolDefinition(resources,sensorsFactory),
            com.cangshuo.toolbox.feature.compass.CompassToolDefinition(resources,compassFactory),
            com.cangshuo.toolbox.feature.level.LevelToolDefinition(resources,levelFactory),
            com.cangshuo.toolbox.feature.ping.PingToolDefinition(resources,pingFactory),
            com.cangshuo.toolbox.feature.httpstatus.HttpStatusToolDefinition(resources,httpStatusFactory),
        ),
    )
    private val registryStore = ToolRegistryStore(bundledRegistry(resources))
    fun updateLocale(resources: android.content.res.Resources) { registryStore.replaceBundled(bundledRegistry(resources)) }
    private val repository = RegistryHomeRepository(registryStore)

    private val catalogCache = RoomWebToolCatalogCache(database.cachedToolCatalogDao(), BuildConfig.API_BASE_URL)
    private val refreshWebCatalog = RefreshWebToolCatalogUseCase(ToolCatalogClient(BuildConfig.API_BASE_URL), registryStore, catalogCache)

    /** Restore a bounded private snapshot in the background, then prefer a complete network result. */
    private val catalogScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val authRepository = com.cangshuo.toolbox.feature.auth.data.RemoteAuthRepository(BuildConfig.API_BASE_URL, catalogScope, applicationContext)
    private val authUsers = authRepository.account.map { it?.id }.stateIn(catalogScope,
        kotlinx.coroutines.flow.SharingStarted.Eagerly, null)
    private val cloudSync = com.cangshuo.toolbox.feature.sync.domain.CloudSyncRepository(authUsers,
        com.cangshuo.toolbox.feature.sync.data.RoomSyncStore(database),
        com.cangshuo.toolbox.feature.sync.data.RemoteSyncTransport(BuildConfig.API_BASE_URL) { user,force ->
            if (force) authRepository.restore()
            val bearer=authRepository.authorization()
            check(authRepository.account.value?.id==user)
            bearer
        },catalogScope)
    val syncViewModelFactory=com.cangshuo.toolbox.feature.sync.ui.CloudSyncViewModel.factory(
        com.cangshuo.toolbox.feature.sync.domain.CloudSyncUseCases(cloudSync))
    val settings get() = cloudSync.settings
    private val authRestoreFinished=kotlinx.coroutines.flow.MutableStateFlow(false)
    private val startupSettings=combine(authRestoreFinished,authRepository.account,cloudSync.context,settings) { ready,account,context,settings ->
        settings.takeIf { ready && it.ready && account?.id==context.userId && it.ownerScope==(context.settingsScope ?: 0) }
    }.filterNotNull().stateIn(catalogScope,kotlinx.coroutines.flow.SharingStarted.Eagerly,
        com.cangshuo.toolbox.feature.sync.domain.AppSettings())
    val authViewModelFactory = com.cangshuo.toolbox.feature.auth.ui.AuthViewModel.factory(
        com.cangshuo.toolbox.feature.auth.domain.AuthUseCases(authRepository),
    )
    private val catalogSync = RequestWebToolCatalogSyncUseCase(
        applicationScope = catalogScope,
        restore = RestoreWebToolCatalogUseCase(catalogCache, registryStore)::invoke,
        refresh = refreshWebCatalog::invoke,
    )

    init {
        catalogScope.launch {
            try { authRepository.restore() } catch(e: kotlinx.coroutines.CancellationException) { throw e }
            catch(_: Exception) { } finally { authRestoreFinished.value=true }
        }
        catalogScope.launch {
            catalogSync()
        }
    }
    private val favorites = RoomFavoriteRepository(database.favoriteToolDao(), registryStore, cloudSync)
    private val recent = RoomRecentRepository(database.recentToolDao(), registryStore, sync=cloudSync)

    val favoritesViewModelFactory = FavoritesViewModel.factory(ObserveFavoritesUseCase(favorites), SetFavoriteUseCase(favorites))
    val recentViewModelFactory = RecentViewModel.factory(
        observe = ObserveRecentUseCase(recent),
        record = RecordToolUseUseCase(recent),
        clear = ClearRecentUseCase(recent),
    )

    val searchViewModelFactory = SearchViewModel.factory(SearchToolsUseCase(RegistryToolSearchRepository(registryStore, resources)))

    val homeViewModelFactory = HomeViewModel.factory(
        getHome = GetHomeUseCase(repository),
        openTool = OpenHomeToolUseCase(repository, isSignedIn = { authRepository.account.value != null }),
        catalogSync = catalogSync,
        settings = startupSettings,
    )
}
