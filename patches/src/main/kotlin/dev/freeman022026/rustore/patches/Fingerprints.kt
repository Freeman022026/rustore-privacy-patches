package dev.freeman022026.rustore.patches

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import java.util.WeakHashMap

// R8 renames RuStore classes in every release. Anchors below rely on what survives it: string
// constants, Kotlin source file names, named library and worker classes, and method shapes.

internal val Method.params get() = parameterTypes.map(CharSequence::toString)

internal val Method.instructionList get() = implementation?.instructions?.toList().orEmpty()

internal val Method.isStatic get() = AccessFlags.STATIC.isSet(accessFlags)

internal val Method.stringConstants
    get() = instructionList.mapNotNull {
        ((it as? ReferenceInstruction)?.reference as? StringReference)?.string
    }

internal fun Method.hasStrings(vararg strings: String) = stringConstants.containsAll(strings.toList())

internal fun Method.constClasses() = instructionList
    .filter { it.opcode == Opcode.CONST_CLASS }
    .map { ((it as ReferenceInstruction).reference as TypeReference).type }

private fun Method.sameSignature(other: Method) =
    name == other.name && returnType == other.returnType && params == other.params

private val classCache = WeakHashMap<BytecodePatchContext, List<ClassDef>>()

internal val BytecodePatchContext.allClasses: List<ClassDef>
    get() = classCache.getOrPut(this) { buildList { classDefForEach { add(it) } } }

internal fun BytecodePatchContext.methodsWhere(filter: (ClassDef, Method) -> Boolean): List<MutableMethod> =
    allClasses.flatMap { type -> type.methods.filter { filter(type, it) }.map { type to it } }
        .map { (type, method) -> mutableClassDefBy(type.type).methods.first { it.sameSignature(method) } }

internal fun BytecodePatchContext.methodWhere(
    target: String,
    filter: (ClassDef, Method) -> Boolean
): MutableMethod {
    val found = methodsWhere(filter)
    require(found.size == 1) { "$target: expected one method, found ${found.size}" }
    return found.single()
}

internal fun BytecodePatchContext.sourceMethod(
    target: String,
    sourceFile: String,
    filter: (Method) -> Boolean
) = methodWhere(target) { type, method ->
    type.sourceFile == sourceFile && method.implementation != null && filter(method)
}

/** Field reference of kotlin.Unit.INSTANCE. */
internal fun BytecodePatchContext.unitReference(): String {
    val unit = allClasses.single { type ->
        type.methods.any { it.name == "toString" && it.stringConstants == listOf("kotlin.Unit") }
    }
    val field = unit.staticFields.single { it.type == unit.type }
    return "${unit.type}->${field.name}:${unit.type}"
}

/** Field reference of kotlin.collections.EmptyList.INSTANCE. */
internal fun BytecodePatchContext.emptyListReference(): String {
    val emptyList = allClasses.single { type ->
        "Ljava/util/List;" in type.interfaces && "Ljava/util/RandomAccess;" in type.interfaces &&
            type.staticFields.any { it.type == type.type } &&
            type.methods.any { it.name == "toString" && it.stringConstants == listOf("[]") }
    }
    val field = emptyList.staticFields.single { it.type == emptyList.type }
    return "${emptyList.type}->${field.name}:${emptyList.type}"
}

/** Returns the Unit singleton; for suspend functions that complete with Unit. */
internal fun BytecodePatchContext.returnUnit() = """
    sget-object v0, ${unitReference()}
    return-object v0
"""

/** True when the method already completes with the Unit singleton somewhere. */
internal fun Method.returnsUnit(unit: String) = instructionList.zipWithNext().any { (load, ret) ->
    load.opcode == Opcode.SGET_OBJECT && ret.opcode == Opcode.RETURN_OBJECT &&
        ((load as ReferenceInstruction).reference as FieldReference).toString() == unit
}

/** ListenableWorker.Result.success() without calling into the obfuscated factory. */
internal fun BytecodePatchContext.workerSuccess(): String {
    val success = allClasses.single { type ->
        type.methods.any { it.name == "toString" && it.stringConstants.any { s -> s.startsWith("Success {") } }
    }
    require(success.methods.any { it.name == "<init>" && it.params.isEmpty() }) {
        "Worker success result constructor changed"
    }
    return """
        new-instance v0, ${success.type}
        invoke-direct {v0}, ${success.type}-><init>()V
        return-object v0
    """
}

internal class WorkManagerReferences(val workManager: String, val getInstance: String, val cancelUniqueWork: String)

internal fun BytecodePatchContext.workManager(): WorkManagerReferences {
    val impl = allClasses.single { type ->
        type.methods.any { method -> method.stringConstants.any { it.startsWith("WorkManager is not initialized properly.") } }
    }
    val getInstance = impl.methods.single {
        it.isStatic && it.params == listOf("Landroid/content/Context;") && it.returnType == impl.type
    }
    val cancel = impl.methods.single { method ->
        method.params == listOf("Ljava/lang/String;") && method.stringConstants.any { it.startsWith("CancelWorkByName_") }
    }
    val workManager = impl.superclass!!
    require(classDefBy(workManager).methods.any { it.sameSignature(cancel) }) { "WorkManager.cancelUniqueWork changed" }
    return WorkManagerReferences(
        workManager,
        "${impl.type}->${getInstance.name}(Landroid/content/Context;)${impl.type}",
        "$workManager->${cancel.name}(Ljava/lang/String;)${cancel.returnType}"
    )
}

/** Reads of the foreground flag kept by the app lifecycle observer. */
internal fun BytecodePatchContext.foregroundReads(method: Method) =
    method.instructionList.withIndex().filter { (_, instruction) ->
        instruction.opcode == Opcode.IGET_BOOLEAN &&
            classDefByOrNull(((instruction as ReferenceInstruction).reference as FieldReference).definingClass)
                ?.sourceFile == "AppLifecycleObserver.kt"
    }

/** The worker's implementation of the abstract work method of its androidx.work base class. */
internal fun BytecodePatchContext.workMethod(workerType: String): MutableMethod {
    val worker = classDefByOrNull(workerType) ?: throw IllegalArgumentException("Worker class not found: $workerType")
    val abstractMethods = generateSequence(worker.superclass?.let(::classDefByOrNull)) { base ->
        base.superclass?.let(::classDefByOrNull)
    }.flatMap { base -> base.methods.filter { AccessFlags.ABSTRACT.isSet(it.accessFlags) } }.toList()
    val implemented = worker.methods.filter { method ->
        method.implementation != null && abstractMethods.any { it.sameSignature(method) }
    }
    require(implemented.size == 1) { "Worker method changed: $workerType" }
    return mutableClassDefBy(workerType).methods.first { it.sameSignature(implemented.single()) }
}

/** Methods that build a WorkRequest for the named worker and complete with Unit. */
internal fun BytecodePatchContext.workSchedulers(workerType: String, unit: String) = methodsWhere { type, method ->
    type.type != workerType && method.returnType == "Ljava/lang/Object;" &&
        workerType in method.constClasses() && method.returnsUnit(unit)
}.also { require(it.isNotEmpty()) { "Worker scheduler not found: $workerType" } }

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
    parameters = listOf("J", "Ljava/lang/String;", "Ljava/lang/String;", "Ljava/lang/String;", "Z")
)

internal val agreementSettingConstructorFingerprint = Fingerprint(
    classFingerprint = agreementSettingClassFingerprint,
    name = "<init>",
    returnType = "V",
    parameters = listOf("J", "Z")
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

private val kasperskyScannerDtoClassFingerprint = Fingerprint(
    strings = listOf("KasperskyScannerDto(isScanResultViewed=")
)

internal val kasperskyPeriodicEnabledFingerprint = Fingerprint(
    classFingerprint = kasperskyScannerDtoClassFingerprint,
    name = "isPeriodicScanEnabled",
    returnType = "Z",
    parameters = emptyList()
)

internal val applicationOnCreateFingerprint = Fingerprint(
    definingClass = "Lru/vk/store/App;",
    name = "onCreate",
    returnType = "V",
    strings = listOf("App process started: version=")
)

internal val googleAdvertisingIdFingerprint = Fingerprint(
    definingClass = "Lcom/google/android/gms/ads/identifier/AdvertisingIdClient;",
    name = "getAdvertisingIdInfo",
    returnType = "Lcom/google/android/gms/ads/identifier/AdvertisingIdClient\$Info;",
    parameters = listOf("Landroid/content/Context;"),
    strings = listOf("Creating AdvertisingIdClient")
)

internal val vkSdkDeviceIdFingerprint = Fingerprint(
    returnType = "Ljava/lang/String;",
    parameters = listOf("Landroid/content/Context;"),
    strings = listOf("__vk_device_id__", "next_device_id is null or empty: ")
)

internal val tracerDiskUsageInitializerFingerprint = Fingerprint(
    definingClass = "Lru/ok/tracer/disk/usage/DiskUsage;",
    name = "initialize\$tracer_disk_usage_release",
    returnType = "V",
    parameters = listOf("Landroid/content/Context;")
)

internal val tracerSampleUploadFingerprint = Fingerprint(
    definingClass = "Lru/ok/tracer/upload/SampleUploader;",
    name = "upload",
    returnType = "V",
    parameters = listOf(
        "Landroid/content/Context;",
        "Lru/ok/tracer/TracerFeature;",
        "Ljava/io/File;",
        "J",
        "Ljava/lang/String;",
        "Z",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Ljava/lang/Long;",
        "Ljava/lang/Long;",
        "Ljava/util/Map;",
        "Lru/ok/tracer/opentelemetry/util/TraceParent;"
    )
)

internal const val REPORT_APP_OPEN_REFERENCE =
    "Lio/appmetrica/analytics/AppMetrica;->reportAppOpen(Ljava/lang/String;)V"

internal const val ALTCRAFT_WORKER = "Lru/vk/store/lib/analytics/system/altcraft/presentation/AltCraftFlushEventsWorker;"
internal const val RADAR_WORKER = "Lru/vk/store/lib/analytics/system/radar/presentation/RadarFlushSnapshotWorker;"
internal const val KASPERSKY_WORKER = "Lru/vk/store/feature/kaspersky/impl/presentation/KasperskyScannerWorker;"

internal val blockedCoroutineWorkerClasses = listOf(
    "Lcom/vk/push/pushsdk/work/CheckThatDeletedAppIsHostWorker;",
    "Lcom/vk/push/pushsdk/work/InitiateMasterElectionsWorker;",
    "Lcom/vk/push/pushsdk/work/NotifyOldMasterWorker;",
    "Lcom/vk/push/pushsdk/work/OneTimePushReceiveWorker;",
    "Lcom/vk/push/pushsdk/work/scheduler/DeleteTokensFromServerWorker;",
    "Lcom/vk/push/pushsdk/work/StopDeliverToRemovedAppWorker;",
    "Lcom/vk/push/pushsdk/work/StopDeliverToUninstalledWork;",
    "Lcom/vk/push/pushsdk/work/TokensHealthCheckWorker;",
    "Lru/mail/omicron/MultiAccountWorkManagerExecutor\$MultiAccountOmicronSyncWorker;",
    "Lru/rustore/sdk/pushclient/internal/work/ArbiterUpdateFallbackWorker;",
    "Lru/rustore/sdk/pushclient/internal/work/DeletePushTokenIfNoHostsWorker;",
    "Lru/vk/store/feature/connect/session/impl/presentation/ConnectDownloadWorker;",
    "Lru/vk/store/feature/install/identifier/impl/presentation/InstallIdentifierSyncWorker;",
    "Lru/vk/store/feature/launchericon/impl/presentation/LauncherIconUpdateWorker;",
    "Lru/vk/store/feature/payments/subscription/update/impl/presentation/CancelSubscriptionSyncWorker;",
    "Lru/vk/store/feature/showcase/startDestination/impl/presentation/UpdateStartDestinationWorker;",
    "Lru/vk/store/feature/showcase/tabsOrder/impl/presentation/ShowcaseTabsOrderUpdateWorker;",
    "Lru/vk/store/feature/storeapp/analytics/remote/impl/presentation/SendAnalyticsEventWorker;",
    "Lru/vk/store/feature/storeapp/install/notice/impl/presentation/InstallNotificationReminderWorker;",
    "Lru/vk/store/feature/storeapp/recommendation/start/trackingUrl/impl/data/PublisherTrackingWorker;",
    "Lru/vk/store/feature/storeapp/review/remote/impl/presentation/AddAppReviewWorker;",
    "Lru/vk/store/feature/usagestats/impl/presentation/UsageStatsCollectorWorker;"
)

internal val blockedWorkerClasses = listOf(
    "Lru/mail/omicron/DefaultWorkManagerExecutor\$PeriodicWorker;",
    "Lru/ok/tracer/disk/usage/DiskUsageWorker;",
    "Lru/ok/tracer/heap/dumps/exceptions/ShrinkDumpWorker;",
    "Lru/ok/tracer/upload/SampleUploadWorker;"
)

/** Workers whose scheduling is skipped; their doWork is also blocked above. */
internal val scheduledWorkerClasses = listOf(
    "Lru/vk/store/feature/install/identifier/impl/presentation/InstallIdentifierSyncWorker;",
    "Lru/vk/store/feature/launchericon/impl/presentation/LauncherIconUpdateWorker;",
    "Lru/vk/store/feature/payments/subscription/update/impl/presentation/CancelSubscriptionSyncWorker;",
    "Lru/vk/store/feature/showcase/startDestination/impl/presentation/UpdateStartDestinationWorker;",
    "Lru/vk/store/feature/showcase/tabsOrder/impl/presentation/ShowcaseTabsOrderUpdateWorker;",
    "Lru/vk/store/feature/storeapp/analytics/remote/impl/presentation/SendAnalyticsEventWorker;",
    "Lru/vk/store/feature/storeapp/recommendation/start/trackingUrl/impl/data/PublisherTrackingWorker;",
    "Lru/vk/store/feature/usagestats/impl/presentation/UsageStatsCollectorWorker;"
)

/** Unique work names that earlier RuStore runs may have persisted. */
internal val persistedUniqueWorkNames = listOf(
    "AltCraftFlushEventsWorker",
    "RadarFlushSnapshotsWorker",
    "tracer.disk.usage.worker",
    "InstallIdentifierSyncWorker",
    "UsageStatsCollectorWorker",
    "CancelSubscriptionSyncWorker",
    "SendAnalyticsEventPeriodicWorker",
    "SendAnalyticsEventWorker",
    "LauncherIconUpdate",
    "PeriodicUpdateStartDestination",
    "NavigationTabsOrderUpdate"
)

internal val pushLifecycleMethods = mapOf(
    "onActivityCreated" to listOf("Landroid/app/Activity;", "Landroid/os/Bundle;"),
    "onActivityDestroyed" to listOf("Landroid/app/Activity;"),
    "onActivityPaused" to listOf("Landroid/app/Activity;"),
    "onActivityResumed" to listOf("Landroid/app/Activity;"),
    "onActivitySaveInstanceState" to listOf("Landroid/app/Activity;", "Landroid/os/Bundle;"),
    "onActivityStarted" to listOf("Landroid/app/Activity;"),
    "onActivityStopped" to listOf("Landroid/app/Activity;")
)
