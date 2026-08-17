package dev.freeman022026.rustore.patches

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import org.w3c.dom.Element

private val permissionsToDisable = linkedMapOf(
    "android.permission.INSTALL_PACKAGES" to "android.permission.XNSTALL_PACKAGES",
    "com.google.android.gms.permission.AD_ID" to "com.google.android.gms.permission.XD_ID",
    "android.permission.PACKAGE_USAGE_STATS" to "android.permission.XACKAGE_USAGE_STATS",
    "android.permission.READ_CALL_LOG" to "android.permission.XEAD_CALL_LOG",
    "android.permission.READ_PHONE_NUMBERS" to "android.permission.XEAD_PHONE_NUMBERS",
    "android.provider.Telephony.SMS_RECEIVED" to "android.provider.Xelephony.SMS_RECEIVED",
    "android.permission.RECEIVE_BOOT_COMPLETED" to "android.permission.XECEIVE_BOOT_COMPLETED",
    "android.permission.CHANGE_WIFI_STATE" to "android.permission.XHANGE_WIFI_STATE",
    "android.permission.CHANGE_NETWORK_STATE" to "android.permission.XHANGE_NETWORK_STATE",
    "com.google.android.c2dm.permission.RECEIVE" to "com.google.android.c2dm.permission.XECEIVE",
    "android.permission.CALL_PHONE" to "android.permission.XALL_PHONE",
    "ru.sb.mobile.sid.BIND_PERSONALIZATION_SERVICE" to
        "ru.sb.mobile.sid.XIND_PERSONALIZATION_SERVICE",
    "android.permission.ACCESS_FINE_LOCATION" to "android.permission.XCCESS_FINE_LOCATION",
    "android.permission.ACCESS_COARSE_LOCATION" to "android.permission.XCCESS_COARSE_LOCATION",
    "android.permission.READ_BASIC_PHONE_STATE" to "android.permission.XEAD_BASIC_PHONE_STATE",
    "com.google.android.providers.gsf.permission.READ_GSERVICES" to
        "com.google.android.providers.gsf.permission.XEAD_GSERVICES",
    "com.android.vending.BILLING" to "com.android.vending.XILLING",
    "android.permission.USB_HOST" to "android.permission.XSB_HOST",
    "com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE" to
        "com.google.android.finsky.permission.XIND_GET_INSTALL_REFERRER_SERVICE",
    "android.permission.READ_EXTERNAL_STORAGE" to "android.permission.XEAD_EXTERNAL_STORAGE",
    "android.permission.WRITE_EXTERNAL_STORAGE" to "android.permission.XRITE_EXTERNAL_STORAGE",
    "com.huawei.appmarket.service.commondata.permission.GET_COMMON_DATA" to
        "com.huawei.appmarket.service.commondata.permission.XET_COMMON_DATA",
    "android.permission.BIND_VPN_SERVICE" to "android.permission.XIND_VPN_SERVICE",
    "android.intent.action.BOOT_COMPLETED" to "android.intent.action.XOOT_COMPLETED",
    "android.net.VpnService" to "android.net.XpnService"
)

private val componentPrefixReplacements = listOf(
    "ru.mail.libverify" to "xu.mail.libverify",
    "ru.mail.verify" to "xu.mail.verify",
    "sid.sdk.global.utils.sms" to "xid.sdk.global.utils.sms",
    "ru.vk.store.feature.connect.session" to "xu.vk.store.feature.connect.session",
    "ru.vk.store.feature.storeapp.install.referrer" to
        "xu.vk.store.feature.storeapp.install.referrer",
    "ru.rustore.sdk.pushclient.provider" to "xu.rustore.sdk.pushclient.provider",
    "ru.rustore.sdk.metrics" to "xu.rustore.sdk.metrics",
    "io.appmetrica" to "xo.appmetrica",
    "com.my.target" to "xom.my.target",
    "com.vk.push" to "xom.vk.push",
    "com.vk.superapp.logs" to "xom.vk.superapp.logs",
    "com.inappstory.sdk" to "xom.inappstory.sdk",
    "com.kavsdk" to "xom.kavsdk",
    "kavsdk." to "xavsdk.",
    "com.google.android.datatransport" to "xom.google.android.datatransport"
)

private val inertProviderClasses = listOf(
    "ru.mail.libverify.utils.VerifyInitProvider",
    "ru.rustore.sdk.pushclient.provider.RuStorePushClientInitProvider",
    "com.my.target.common.MyTargetContentProvider",
    "io.appmetrica.analytics.internal.PreloadInfoContentProvider",
    "com.vk.push.core.deviceid.contentprovider.VkpnsDeviceIdContentProvider",
    "com.inappstory.sdk.share.InAppStoryFileProvider",
    "com.vk.superapp.logs.LogsFileProvider"
)

private val inertReceiverClasses = listOf(
    "com.google.android.datatransport.runtime.scheduling.jobscheduling.AlarmManagerSchedulerBroadcastReceiver",
    "com.inappstory.sdk.stories.utils.StoryShareBroadcastReceiver",
    "com.kavsdk.AlarmReceiver",
    "com.kavsdk.StartReceiver",
    "com.vk.push.pushsdk.broadcast.FullyPackageRemovedReceiver",
    "com.vk.push.pushsdk.broadcast.TimeChangedReceiver",
    "com.vk.push.pushsdk.broadcast.VkpnsReceiver",
    "com.vk.superapp.logs.SuperappLogsBroadcastReceiver",
    "ru.mail.libverify.notifications.ChangePushPermissionsReceiver",
    "ru.mail.libverify.platform.firebase.sms.SmsRetrieverReceiver",
    "ru.mail.libverify.sms.IncomingCallReceiver",
    "ru.mail.libverify.sms.IncomingSmsReceiver",
    "ru.mail.libverify.utils.AlarmReceiver",
    "ru.mail.libverify.utils.BatteryLevelReceiver",
    "ru.mail.libverify.utils.network.NetworkStateReceiver",
    "ru.mail.libverify.utils.NetworkStateReceiver",
    "ru.mail.libverify.utils.PackageStateReceiver",
    "ru.mail.libverify.utils.ScreenStateReceiver",
    "ru.mail.libverify.utils.SystemRestartReceiver",
    "ru.mail.verify.core.utils.network.NetworkStateReceiver",
    "sid.sdk.global.utils.sms.SIDSMSBroadcastReceiver"
)

private val knownProviderClasses = setOf(
    "androidx.core.content.FileProvider",
    "ru.vk.store.feature.storeapp.search.system.mobile.impl.presentation.SystemSearchContentProvider",
    "androidx.startup.InitializationProvider",
    "ru.vk.store.feature.storeapp.details.direct.impl.presentation.contentprovider.WarmingContentProvider",
    "com.inappstory.sdk.share.InAppStoryFileProvider",
    "com.vk.superapp.logs.LogsFileProvider",
    "com.vk.superapp.provider.SakFileProvider",
    "com.vk.usersstore.contentprovider.UsersContentProvider",
    "com.vk.id.captcha.init.SdkInitContentProvider",
    "ru.mail.libverify.utils.VerifyInitProvider",
    "ru.rustore.sdk.pushclient.provider.RuStorePushClientInitProvider",
    "com.my.target.common.MyTargetContentProvider",
    "ru.vk.store.lib.logging.impl.data.LogFileProvider",
    "ru.vk.store.lib.zip.ZippedFileProvider",
    "ru.mail.auth.sdk.MailIDInitProvider",
    "io.appmetrica.analytics.internal.PreloadInfoContentProvider",
    "com.vk.push.core.deviceid.contentprovider.VkpnsDeviceIdContentProvider",
    "ru.ok.tracer.startup.InitializationProvider",
    "ru.mail.omicron.util.network.state.NetworkStateListenerProvider",
    "ru.mail.network.NetworkStateListenerProvider",
    "ru.rustore.sdk.imaging.ImageLoaderContentProvider",
    "com.huawei.hms.update.provider.UpdateProvider",
    "com.huawei.updatesdk.fileprovider.UpdateSdkFileProvider",
    "com.huawei.agconnect.core.provider.AGConnectInitializeProvider"
)

private val knownSourcePermissions = setOf(
    "android.permission.INSTALL_PACKAGES",
    "android.permission.REQUEST_INSTALL_PACKAGES",
    "android.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION",
    "android.permission.ENFORCE_UPDATE_OWNERSHIP",
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.ACCESS_WIFI_STATE",
    "com.google.android.gms.permission.AD_ID",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.WAKE_LOCK",
    "com.android.permission.GET_INSTALLED_APPS",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
    "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
    "android.permission.QUERY_ALL_PACKAGES",
    "android.permission.PACKAGE_USAGE_STATS",
    "android.permission.READ_PHONE_STATE",
    "android.permission.READ_CALL_LOG",
    "android.permission.DETECT_SCREEN_CAPTURE",
    "android.permission.READ_PHONE_NUMBERS",
    "android.provider.Telephony.SMS_RECEIVED",
    "android.permission.RECEIVE_BOOT_COMPLETED",
    "android.permission.CHANGE_WIFI_STATE",
    "android.permission.CHANGE_NETWORK_STATE",
    "com.google.android.c2dm.permission.RECEIVE",
    "android.permission.CALL_PHONE",
    "ru.sb.mobile.sid.BIND_PERSONALIZATION_SERVICE",
    "android.permission.REQUEST_DELETE_PACKAGES",
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.ACCESS_COARSE_LOCATION",
    "android.permission.FOREGROUND_SERVICE_DATA_SYNC",
    "android.permission.READ_BASIC_PHONE_STATE",
    "com.google.android.providers.gsf.permission.READ_GSERVICES",
    "com.android.vending.BILLING",
    "ru.vk.store.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
    "android.permission.USB_HOST",
    "com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE",
    "android.permission.WRITE_EXTERNAL_STORAGE",
    "com.huawei.appmarket.service.commondata.permission.GET_COMMON_DATA"
)

private val requiredCapabilities = listOf(
    "android.permission.REQUEST_INSTALL_PACKAGES",
    "android.permission.QUERY_ALL_PACKAGES",
    "com.android.permission.GET_INSTALLED_APPS",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
    "android.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION",
    "android.permission.ENFORCE_UPDATE_OWNERSHIP",
    "android.permission.REQUEST_DELETE_PACKAGES"
)

private val criticalManifestAnchors = listOf(
    "android.permission.INSTALL_PACKAGES",
    "com.google.android.gms.permission.AD_ID",
    "android.permission.PACKAGE_USAGE_STATS",
    "android.permission.READ_CALL_LOG",
    "android.permission.READ_PHONE_NUMBERS",
    "android.provider.Telephony.SMS_RECEIVED",
    "android.permission.RECEIVE_BOOT_COMPLETED",
    "android.intent.action.BOOT_COMPLETED",
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.ACCESS_COARSE_LOCATION",
    "android.permission.BIND_VPN_SERVICE",
    "android.net.VpnService",
    "ru.vk.store.feature.connect.session"
)

private val forbiddenManifestValues = listOf(
    "android.permission.PACKAGE_USAGE_STATS",
    "com.google.android.gms.permission.AD_ID",
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.ACCESS_COARSE_LOCATION",
    "android.permission.READ_PHONE_NUMBERS",
    "android.permission.READ_CALL_LOG",
    "android.provider.Telephony.SMS_RECEIVED",
    "android.intent.action.BOOT_COMPLETED",
    "android.permission.BIND_VPN_SERVICE",
    "android.net.VpnService",
    "ru.mail.libverify.sms.SmsHandlingService",
    "ru.mail.verify.core.utils.VerificationService",
    "ru.vk.store.feature.connect.session",
    "com.vk.push.pushsdk.ipc.PushService"
)

private fun String.replacedBy(pairs: Iterable<Pair<String, String>>) =
    pairs.fold(this) { value, (old, new) -> value.replace(old, new) }

private fun String.occurrencesOf(needle: String): Int {
    var count = 0
    var offset = 0
    while (true) {
        val index = indexOf(needle, offset)
        if (index < 0) return count
        count++
        offset = index + needle.length
    }
}

private val manifestHardeningPatch = resourcePatch {
    compatibleWith(RUSTORE_COMPATIBILITY)

    execute {
        document("AndroidManifest.xml").use { document ->
            val sourcePermissions = listOf("uses-permission", "uses-permission-sdk-23")
                .flatMap { tagName ->
                    val nodes = document.getElementsByTagName(tagName)
                    (0 until nodes.length)
                        .map { nodes.item(it) as Element }
                        .map { it.getAttribute("android:name") }
                }
                .toSet()
            require(sourcePermissions == knownSourcePermissions) {
                val added = sourcePermissions - knownSourcePermissions
                val removed = knownSourcePermissions - sourcePermissions
                "RuStore permission inventory changed. Added: ${added.sorted()}; removed: ${removed.sorted()}"
            }

            val providers = document.getElementsByTagName("provider")
            val providerNames = (0 until providers.length)
                .map { providers.item(it) as Element }
                .map { it.getAttribute("android:name") }
                .toSet()

            val unknownProviders = providerNames - knownProviderClasses
            require(providerNames.size == 23 && unknownProviders.isEmpty()) {
                "RuStore provider inventory changed. Unknown providers: ${unknownProviders.sorted()}"
            }

            val replacements = linkedMapOf<String, String>().apply {
                putAll(permissionsToDisable)
                putAll(componentPrefixReplacements)
                inertProviderClasses.forEach { provider ->
                    put(provider.replacedBy(componentPrefixReplacements), provider)
                }
                inertReceiverClasses.forEach { receiver ->
                    put(receiver.replacedBy(componentPrefixReplacements), receiver)
                }
            }
            val counts = replacements.keys.associateWith { 0 }.toMutableMap()

            val elements = document.getElementsByTagName("*")
            for (elementIndex in 0 until elements.length) {
                val element = elements.item(elementIndex) as Element
                val attributes = element.attributes
                for (attributeIndex in 0 until attributes.length) {
                    val attribute = attributes.item(attributeIndex)
                    var value = attribute.nodeValue
                    replacements.forEach { (old, new) ->
                        val count = value.occurrencesOf(old)
                        if (count > 0) {
                            counts[old] = counts.getValue(old) + count
                            value = value.replace(old, new)
                        }
                    }
                    attribute.nodeValue = value
                }
            }

            criticalManifestAnchors.forEach { anchor ->
                require(counts.getOrDefault(anchor, 0) > 0) {
                    "Critical RuStore manifest anchor is missing: $anchor"
                }
            }

            val manifestValues = buildList {
                val allElements = document.getElementsByTagName("*")
                for (elementIndex in 0 until allElements.length) {
                    val attributes = (allElements.item(elementIndex) as Element).attributes
                    for (attributeIndex in 0 until attributes.length) {
                        add(attributes.item(attributeIndex).nodeValue)
                    }
                }
            }

            forbiddenManifestValues.forEach { forbidden ->
                require(manifestValues.none { forbidden in it }) {
                    "Forbidden RuStore manifest capability remains: $forbidden"
                }
            }
            requiredCapabilities.forEach { required ->
                require(manifestValues.any { required in it }) {
                    "Required RuStore capability is missing: $required"
                }
            }
            (inertProviderClasses + inertReceiverClasses).forEach { requiredClass ->
                require(manifestValues.any { requiredClass in it }) {
                    "Required inert RuStore component is missing: $requiredClass"
                }
            }
        }
    }
}

private fun methodFingerprint(
    definingClass: String,
    name: String,
    returnType: String,
    parameters: List<String> = emptyList()
) = Fingerprint(
    definingClass = definingClass,
    name = name,
    returnType = returnType,
    parameters = parameters
)

private val voidInitializers = listOf(
    methodFingerprint(
        "Lio/appmetrica/analytics/AppMetrica;",
        "activate",
        "V",
        listOf("Landroid/content/Context;", "Lio/appmetrica/analytics/AppMetricaConfig;")
    ),
    methodFingerprint(
        "Lcom/my/tracker/MyTracker;",
        "initTracker",
        "V",
        listOf("Ljava/lang/String;", "Landroid/app/Application;")
    )
)

private val falseInitializers = listOf(
    "Lru/rustore/sdk/pushclient/provider/RuStorePushClientInitProvider;",
    "Lcom/my/target/common/MyTargetContentProvider;",
    "Lio/appmetrica/analytics/internal/PreloadInfoContentProvider;",
    "Lru/mail/libverify/utils/VerifyInitProvider;",
    "Lcom/vk/push/core/deviceid/contentprovider/VkpnsDeviceIdContentProvider;",
    "Lru/ok/tracer/startup/InitializationProvider;"
).map { methodFingerprint(it, "onCreate", "Z") }

private val oldNetworkProvider = methodFingerprint(
    "Lru/mail/omicron/util/network/state/NetworkStateListenerProvider;",
    "onCreate",
    "Z"
)
private val currentNetworkProvider = methodFingerprint(
    "Lru/mail/network/NetworkStateListenerProvider;",
    "onCreate",
    "Z"
)

private val receiverInitializers = inertReceiverClasses.map { className ->
    methodFingerprint(
        "L${className.replace('.', '/')};",
        "onReceive",
        "V",
        listOf("Landroid/content/Context;", "Landroid/content/Intent;")
    )
}

private val mainActivityOnNewIntent = methodFingerprint(
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

private val settingConstructor = Fingerprint(
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

private val agreementSettingConstructor = Fingerprint(
    classFingerprint = agreementSettingClassFingerprint,
    name = "<init>",
    returnType = "V",
    parameters = listOf("J", "Z")
)

private const val REPORT_APP_OPEN_REFERENCE =
    "Lio/appmetrica/analytics/AppMetrica;->reportAppOpen(Ljava/lang/String;)V"

@Suppress("unused")
val rustorePrivacyHardeningPatch = bytecodePatch(
    name = "RuStore privacy hardening",
    description = "Disables audited tracking, advertising consent, push, SMS, VPN, boot, and background hooks while keeping app browsing and user-driven installs working.",
    default = true
) {
    compatibleWith(RUSTORE_COMPATIBILITY)
    dependsOn(manifestHardeningPatch)

    execute {
        voidInitializers.forEach { fingerprint ->
            fingerprint.method.addInstruction(0, "return-void")
        }
        falseInitializers.forEach { fingerprint ->
            fingerprint.method.addInstructions(
                0,
                """
                    const/4 v0, 0x0
                    return v0
                """
            )
        }

        val networkProviderMethods = listOfNotNull(
            oldNetworkProvider.methodOrNull,
            currentNetworkProvider.methodOrNull
        )
        require(networkProviderMethods.size == 1) {
            "Expected one known RuStore network listener provider, found ${networkProviderMethods.size}"
        }
        networkProviderMethods.single().addInstructions(
            0,
            """
                const/4 v0, 0x0
                return v0
            """
        )

        receiverInitializers.forEach { fingerprint ->
            fingerprint.method.addInstruction(0, "return-void")
        }

        mainActivityOnNewIntent.method.apply {
            val matchingIndexes = implementation!!.instructions.withIndex()
                .filter { (_, instruction) ->
                    (instruction as? ReferenceInstruction)?.reference?.toString() ==
                        REPORT_APP_OPEN_REFERENCE
                }
                .map { it.index }
            require(matchingIndexes.size == 1) {
                "Expected one AppMetrica reportAppOpen call, found ${matchingIndexes.size}"
            }
            removeInstructions(matchingIndexes.single(), 1)
        }

        settingConstructor.method.addInstruction(0, "const/4 p6, 0x0")
        agreementSettingConstructor.method.addInstruction(0, "const/4 p3, 0x0")
    }
}
