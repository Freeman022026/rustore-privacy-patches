package dev.freeman022026.rustore.patches

import app.morphe.patcher.Fingerprint

internal fun methodFingerprint(
    definingClass: String,
    name: String,
    returnType: String,
    parameters: List<String> = emptyList(),
    strings: List<String> = emptyList()
) = Fingerprint(
    definingClass = definingClass,
    name = name,
    returnType = returnType,
    parameters = parameters,
    strings = strings
)

internal val appMetricaActivateFingerprint = Fingerprint(
    definingClass = "Lio/appmetrica/analytics/AppMetrica;",
    name = "activate",
    returnType = "V",
    custom = { method, _ -> method.implementation != null }
)

internal val myTrackerInitializeFingerprint = Fingerprint(
    definingClass = "Lcom/my/tracker/MyTracker;",
    name = "initTracker",
    returnType = "V",
    custom = { method, _ -> method.implementation != null }
)

internal val mainActivityOnNewIntentFingerprint = methodFingerprint(
    "Lru/vk/store/app/MainActivity;",
    "onNewIntent",
    "V",
    listOf("Landroid/content/Intent;")
)

private val settingClassFingerprint = Fingerprint(
    strings = listOf("Setting(settingId=", ", iconUrl=", ", value=")
)

private val agreementSettingClassFingerprint = Fingerprint(
    strings = listOf("AgreementSetting(id=", ", value=")
)

internal val settingConstructorFingerprint = Fingerprint(
    classFingerprint = settingClassFingerprint,
    name = "<init>",
    returnType = "V",
    parameters = listOf(
        "J",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Z"
    )
)

internal val agreementSettingConstructorFingerprint = Fingerprint(
    classFingerprint = agreementSettingClassFingerprint,
    name = "<init>",
    returnType = "V",
    parameters = listOf("J", "Z")
)

internal val rawAdvertisementRepositoryGetFingerprint = methodFingerprint(
    "Li81/u0;",
    "a",
    "Ljava/lang/Object;",
    listOf(
        "Ljava/util/List;",
        "Ld81/c;",
        "Ljava/util/List;",
        "La81/d;",
        "Ljava/util/Set;",
        "Ljava/util/UUID;",
        "Z",
        "Z",
        "Lau0/d;"
    )
)

private val advertisementIdsClassFingerprint = Fingerprint(
    strings = listOf("AdvertisementIds(gaid=", ", hoaid=", ", androidId=")
)

internal val advertisementIdsConstructorFingerprint = Fingerprint(
    classFingerprint = advertisementIdsClassFingerprint,
    name = "<init>",
    returnType = "V",
    parameters = List(6) { "Ljava/lang/String;" }
)

internal val appVersionInfoListFingerprint = methodFingerprint(
    "Lfb2/l;",
    "c",
    "Ljava/io/Serializable;",
    listOf("Lfb2/l;", "Ljava/lang/String;", "Lau0/d;")
)

internal val requestDeviceIdFingerprint = methodFingerprint(
    "Liq2/l;",
    "a",
    "Ljava/lang/String;",
    strings = listOf("android_id")
)

internal val altCraftSendFingerprint = methodFingerprint(
    "Lxo2/b;",
    "b",
    "V",
    listOf(
        "Ljava/lang/String;",
        "Ljava/util/Map;",
        "Ljava/lang/String;",
        "Z",
        "Lro2/f;"
    )
)

internal val altCraftScheduleFingerprint = methodFingerprint(
    "Lru/vk/store/lib/analytics/system/altcraft/presentation/AltCraftFlushEventsWorker\$a;",
    "a",
    "V",
    listOf("Ltb/j0;", "J"),
    listOf("AltCraftFlushEventsWorker")
)

internal val altCraftWorkerFingerprint = methodFingerprint(
    "Lru/vk/store/lib/analytics/system/altcraft/presentation/AltCraftFlushEventsWorker;",
    "b",
    "Ljava/lang/Object;",
    listOf("Lyt0/e;")
)

internal val radarScheduleFingerprint = methodFingerprint(
    "Lru/vk/store/lib/analytics/system/radar/presentation/RadarFlushSnapshotWorker\$a;",
    "a",
    "V",
    listOf("Ltb/j0;", "J"),
    listOf("RadarFlushSnapshotsWorker")
)

internal val radarWorkerFingerprint = methodFingerprint(
    "Lru/vk/store/lib/analytics/system/radar/presentation/RadarFlushSnapshotWorker;",
    "b",
    "Ljava/lang/Object;",
    listOf("Lyt0/e;")
)

private val kasperskyScannerDtoClassFingerprint = Fingerprint(
    strings = listOf("KasperskyScannerDto(isScanResultViewed=")
)

internal val kasperskyPeriodicEnabledFingerprint = Fingerprint(
    classFingerprint = kasperskyScannerDtoClassFingerprint,
    name = "isPeriodicScanEnabled",
    returnType = "Z",
    parameters = emptyList()
)

internal val kasperskyScheduleFingerprint = methodFingerprint(
    "Lru/vk/store/feature/kaspersky/impl/presentation/KasperskyScannerWorker\$a;",
    "a",
    "Ljava/lang/Object;",
    listOf("Ltb/j0;", "Lau0/d;"),
    listOf("PeriodicKasperskyScanner")
)

internal val kasperskyWorkerFingerprint = methodFingerprint(
    "Lru/vk/store/feature/kaspersky/impl/presentation/KasperskyScannerWorker;",
    "b",
    "Ljava/lang/Object;",
    listOf("Lyt0/e;")
)

internal val mineViewModelOpenGameCenterFingerprint = methodFingerprint(
    "Llo1/z8;",
    "C0",
    "V",
    strings = listOf("gameProfile.click")
)

internal val gameCenterV2ButtonFingerprint = methodFingerprint(
    "Ldh1/t0;",
    "d",
    "V",
    listOf(
        "Lkotlin/jvm/functions/Function0;",
        "Lk2/j;",
        "Ldh1/w;",
        "Landroidx/compose/runtime/a;",
        "I"
    ),
    listOf("GAME_CENTER_BUTTON_V2_KEY")
)

internal val gameCenterV1ButtonFingerprint = methodFingerprint(
    "Ldh1/u;",
    "e",
    "V",
    listOf(
        "Lkotlin/jvm/functions/Function0;",
        "Lk2/j;",
        "Ldh1/w;",
        "Landroidx/compose/runtime/a;",
        "I"
    ),
    listOf("GAME_CENTER_BUTTON_KEY")
)

internal val updateAuthSuggestFingerprint = methodFingerprint(
    "Laa1/e;",
    "a",
    "Ljava/lang/Object;",
    listOf("Lau0/d;")
)

internal const val REPORT_APP_OPEN_REFERENCE =
    "Lio/appmetrica/analytics/AppMetrica;->reportAppOpen(Ljava/lang/String;)V"
