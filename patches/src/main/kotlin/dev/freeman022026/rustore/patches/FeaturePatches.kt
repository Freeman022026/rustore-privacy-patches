package dev.freeman022026.rustore.patches

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val ZERO_UUID = "00000000-0000-0000-0000-000000000000"

private const val RETURN_ZERO_UUID = """
    const-string v0, "$ZERO_UUID"
    return-object v0
"""

@Suppress("unused")
val disableAdvertisementsPatch = bytecodePatch(
    name = "Disable advertisements",
    description = "Removes ad providers, sanitizes ad identifiers, returns an empty ad list, and keeps advertising consent disabled.",
    default = true
) {
    compatibleWith(RUSTORE_COMPATIBILITY)
    dependsOn(advertisingManifestPatch)

    execute {
        googleAdvertisingIdFingerprint.method.addInstructions(
            0,
            """
                new-instance v0, Lcom/google/android/gms/ads/identifier/AdvertisingIdClient${'$'}Info;
                const-string v1, "$ZERO_UUID"
                const/4 v2, 0x1
                invoke-direct {v0, v1, v2}, Lcom/google/android/gms/ads/identifier/AdvertisingIdClient${'$'}Info;-><init>(Ljava/lang/String;Z)V
                return-object v0
            """
        )
        sourceMethod("Raw advertisement repository", "RawAdvertisementRepositoryImpl.kt") { method ->
            method.params.size == 9 && method.params[0] == "Ljava/util/List;" &&
                method.params[5] == "Ljava/util/UUID;"
        }.addInstructions(
            0,
            """
                sget-object v0, ${emptyListReference()}
                return-object v0
            """
        )
        advertisementIdsConstructorFingerprint.method.addInstructions(
            0,
            """
                const-string p1, "$ZERO_UUID"
                const-string p2, "$ZERO_UUID"
                const/4 p3, 0x0
                const/4 p4, 0x0
                const/4 p5, 0x0
            """
        )
        settingConstructorFingerprint.method.addInstruction(0, "const/4 p6, 0x0")
        agreementSettingConstructorFingerprint.method.addInstruction(0, "const/4 p3, 0x0")
    }
}

@Suppress("unused")
val excludeGooglePlayAppsFromUpdateChecksPatch = bytecodePatch(
    name = "Exclude Google Play apps from update checks",
    description = "Excludes only apps whose recorded Android installer is Google Play from update requests.",
    default = true
) {
    compatibleWith(RUSTORE_COMPATIBILITY)

    execute {
        methodWhere("Installer filter of update checks") { type, method ->
            method.isStatic && method.returnType == "Ljava/io/Serializable;" &&
                method.params.size == 3 && method.params[0] == type.type &&
                method.params[1] == "Ljava/lang/String;" &&
                method.stringConstants.any { it.contains("installedApp") }
        }.apply {
            val instructions = implementation!!.instructions
            val equalsIndex = instructions.withIndex().single { (_, instruction) ->
                (instruction as? ReferenceInstruction)?.reference?.toString() ==
                    "Ljava/lang/Object;->equals(Ljava/lang/Object;)Z"
            }.index
            val equalsResult = instructions[equalsIndex + 1]
            require(equalsResult.opcode == Opcode.MOVE_RESULT)
            val equalsRegister = (equalsResult as OneRegisterInstruction).registerA

            addInstruction(
                equalsIndex + 2,
                "xor-int/lit8 v$equalsRegister, v$equalsRegister, 0x1"
            )
            addInstruction(0, "const-string p1, \"com.android.vending\"")
        }
    }
}

@Suppress("unused")
val disableAnalyticsAndTrackersPatch = bytecodePatch(
    name = "Disable analytics and trackers",
    description = "Disables audited analytics transports and replaces the stable request device identifier.",
    default = true
) {
    compatibleWith(RUSTORE_COMPATIBILITY)
    dependsOn(analyticsManifestPatch)

    execute {
        val returnUnit = returnUnit()
        val workerSuccess = workerSuccess()

        // These paths run directly, outside the blocked WorkManager workers.
        methodsWhere { _, method ->
            method.name == "invoke" && method.params.isEmpty() &&
                method.hasStrings("metrics_event_table", "job_config_version")
        }.also { require(it.isNotEmpty()) { "Metrics event collection not found" } }
            .forEach { it.addInstructions(0, returnUnit) }
        listOf(
            methodWhere("MediaScope launch tracking") { _, method ->
                method.returnType == "Ljava/lang/Object;" && method.hasStrings("mediaScopeCustomLaunch")
            },
            methodWhere("InAppStory initialization") { _, method ->
                method.returnType == "Ljava/lang/Object;" && method.hasStrings("story.initilized", "story_user_id")
            }
        ).forEach { it.addInstructions(0, returnUnit) }
        // Also discard queued events from installations predating this patch.
        methodWhere("Metrics event sending") { _, method ->
            method.returnType == "V" && method.params == listOf("Ljava/util/List;") &&
                method.hasStrings("USER_ID_KEY", "X-Metrics-Request-Time")
        }.addInstruction(0, "return-void")
        sourceMethod("Request device identifier", "DeviceInfoProvider.kt") { method ->
            method.returnType == "Ljava/lang/String;" && method.params.isEmpty() && method.hasStrings("android_id")
        }.addInstructions(0, RETURN_ZERO_UUID)
        listOf(appMetricaActivateFingerprint, myTrackerInitializeFingerprint).forEach { fingerprint ->
            fingerprint.method.addInstruction(0, "return-void")
        }
        // Earlier RuStore releases reported app opens from MainActivity.onNewIntent.
        methodsWhere { type, method ->
            type.type == "Lru/vk/store/MainActivity;" && method.name == "onNewIntent"
        }.forEach { method ->
            method.implementation!!.instructions.withIndex()
                .filter { (_, instruction) ->
                    (instruction as? ReferenceInstruction)?.reference?.toString() == REPORT_APP_OPEN_REFERENCE
                }
                .map { it.index }
                .reversed()
                .forEach { method.removeInstructions(it, 1) }
        }

        sourceMethod("AltCraft event sending", "AltCraftAnalyticsImpl.kt") { method ->
            method.returnType == "V" &&
                method.params.take(4) == listOf("Ljava/lang/String;", "Ljava/util/Map;", "Ljava/lang/String;", "Z") &&
                method.params.size == 5
        }.addInstruction(0, "return-void")

        val workManager = workManager()
        listOf(
            ALTCRAFT_WORKER to "AltCraftFlushEventsWorker",
            RADAR_WORKER to "RadarFlushSnapshotsWorker"
        ).forEach { (worker, uniqueName) ->
            val companion = worker.removeSuffix(";") + "\$a;"
            methodWhere("$uniqueName scheduling") { type, method ->
                type.type == companion && method.isStatic && method.returnType == "V" &&
                    method.params.size == 2 && method.params[1] == "J"
            }.apply {
                require(params[0] == workManager.workManager) { "$uniqueName scheduler takes ${params[0]}" }
                addInstructions(
                    0,
                    """
                        const-string v0, "$uniqueName"
                        invoke-virtual {p0, v0}, ${workManager.cancelUniqueWork}
                        return-void
                    """
                )
            }
            workMethod(worker).addInstructions(0, workerSuccess)
        }
    }
}

@Suppress("unused")
val replaceRuStoreSdkDeviceIdentifierPatch = bytecodePatch(
    name = "Replace RuStore SDK device identifier",
    description = "Replaces the RuStore SDK device identifier sent with payment and session requests with the zero UUID.",
    default = true
) {
    compatibleWith(RUSTORE_COMPATIBILITY)

    execute {
        methodWhere("RuStore SDK device identifier") { type, method ->
            type.sourceFile != "DeviceInfoProvider.kt" && method.returnType == "Ljava/lang/String;" &&
                method.params.isEmpty() && method.hasStrings("android_id", "value")
        }.addInstructions(0, RETURN_ZERO_UUID)
    }
}

@Suppress("unused")
val replaceVkSdkDeviceIdentifierPatch = bytecodePatch(
    name = "Replace VK SDK device identifier",
    description = "Replaces the VK SDK device fingerprint sent by VK ID and VK Pay request paths with the zero UUID.",
    default = true
) {
    compatibleWith(RUSTORE_COMPATIBILITY)

    execute {
        vkSdkDeviceIdFingerprint.method.addInstructions(0, RETURN_ZERO_UUID)
    }
}

@Suppress("unused")
val restrictBackgroundWorkToUpdatesPatch = bytecodePatch(
    name = "Restrict background work to updates",
    description = "Keeps only the workers required for automatic updates and allows update checks to run while RuStore is foreground or background.",
    default = true
) {
    compatibleWith(RUSTORE_COMPATIBILITY)
    dependsOn(analyticsManifestPatch, disablePushServicesPatch)

    execute {
        val unit = unitReference()
        val returnUnit = returnUnit()
        val workerSuccess = workerSuccess()
        val workManager = workManager()

        applicationOnCreateFingerprint.method.apply {
            // WorkManager keeps unique work across APK updates. Its configuration needs the
            // worker factory that Hilt injects in super.onCreate().
            val superOnCreate = implementation!!.instructions.indexOfFirst { instruction ->
                instruction.opcode == Opcode.INVOKE_SUPER &&
                    (instruction as ReferenceInstruction).reference.toString().endsWith("->onCreate()V")
            }
            require(superOnCreate >= 0) { "App.onCreate no longer calls super.onCreate()" }
            addInstructions(
                superOnCreate + 1,
                """
                    invoke-static {p0}, ${workManager.getInstance}
                    move-result-object v0
                """ + persistedUniqueWorkNames.joinToString("\n") { name ->
                    """
                        const-string v1, "$name"
                        invoke-virtual {v0, v1}, ${workManager.cancelUniqueWork}
                    """
                }
            )

            // Android preserves explicit component overrides across APK updates.
            val disablePersistedPushServices = listOf(
                "com.vk.push.authsdk.ipc.AuthService",
                "com.vk.push.pushsdk.ipc.PushService",
                "com.vk.push.pushsdk.masterhost.MasterSelectionService"
            ).joinToString("\n") { service ->
                """
                    new-instance v1, Landroid/content/ComponentName;
                    const-string v2, "$service"
                    invoke-direct {v1, p0, v2}, Landroid/content/ComponentName;-><init>(Landroid/content/Context;Ljava/lang/String;)V
                    const/4 v2, 0x2
                    const/4 v3, 0x1
                    invoke-virtual {v0, v1, v2, v3}, Landroid/content/pm/PackageManager;->setComponentEnabledSetting(Landroid/content/ComponentName;II)V
                """
            }
            addInstructions(
                0,
                """
                    invoke-virtual {p0}, Landroid/content/Context;->getPackageManager()Landroid/content/pm/PackageManager;
                    move-result-object v0
                    $disablePersistedPushServices
                """
            )
        }

        sourceMethod("Automatic update conditions", "AutoUpdateAnyAppController.kt") { method ->
            method.returnType == "Z" && method.params.size == 1 && foregroundReads(method).size == 1
        }.apply {
            val foregroundRead = foregroundReads(this).single()
            val register = (foregroundRead.value as OneRegisterInstruction).registerA
            // Keep the existing update-permission and status checks.
            replaceInstruction(foregroundRead.index, "const/4 v$register, 0x0")
        }

        (blockedCoroutineWorkerClasses + blockedWorkerClasses).forEach { worker ->
            workMethod(worker).addInstructions(0, workerSuccess)
        }
        // The work requests themselves are built by suspend functions that complete with Unit.
        scheduledWorkerClasses.forEach { worker ->
            workSchedulers(worker, unit).forEach { it.addInstructions(0, returnUnit) }
        }

        tracerDiskUsageInitializerFingerprint.method.addInstructions(
            0,
            """
                move-object/from16 v0, p1
                invoke-static {v0}, ${workManager.getInstance}
                move-result-object v0
                const-string v1, "tracer.disk.usage.worker"
                invoke-virtual {v0, v1}, ${workManager.cancelUniqueWork}
                return-void
            """
        )
        tracerSampleUploadFingerprint.method.addInstruction(0, "return-void")

        sourceMethod("Omicron config request", "NetworkDataRetriever.kt") { method ->
            method.params.size == 3 && classDefByOrNull(method.returnType)?.let {
                AccessFlags.ENUM.isSet(it.accessFlags)
            } == true
        }.apply {
            val result = classDefBy(returnType)
            require(result.staticFields.any { it.name == "ERROR" && it.type == returnType }) {
                "Omicron request result has no ERROR"
            }
            addInstructions(
                0,
                """
                    sget-object v0, $returnType->ERROR:$returnType
                    return-object v0
                """
            )
        }
        listOf(
            "Lru/mail/omicron/DefaultWorkManagerExecutor;" to listOf("J", "Z"),
            "Lru/mail/omicron/MultiAccountWorkManagerExecutor;" to listOf("J", "J", "Z")
        ).forEach { (executor, parameters) ->
            methodWhere("Omicron scheduling in $executor") { type, method ->
                type.type == executor && method.returnType == "V" && method.params == parameters
            }.addInstructions(
                0,
                """
                    invoke-virtual/range {p0 .. p0}, $executor->cancel()V
                    return-void
                """
            )
        }

        sourceMethod("Remote analytics dispatch", "AnalyticsSenderImpl.kt") { method ->
            method.returnType == "V" && method.params == listOf("Ljava/lang/String;", "Ljava/util/Map;", "Z")
        }.addInstruction(0, "return-void")
        sourceMethod("Remote analytics user identifier", "AnalyticsSenderImpl.kt") { method ->
            method.returnType == "V" && method.params == listOf("J")
        }.addInstruction(0, "return-void")

        listOf(
            "VK push provider" to "VkpnsPushProviderSdk.kt",
            "VK push authentication" to "VkpnsAuthSdk.kt"
        ).forEach { (feature, sourceFile) ->
            val initializer = allClasses.filter { type ->
                type.sourceFile == sourceFile && setOf("create", "invoke", "invokeSuspend").all { name ->
                    type.methods.any { it.name == name }
                }
            }
            require(initializer.size == 1) { "$feature initializer changed: ${initializer.size} candidates" }
            methodWhere("$feature initializer") { type, method ->
                type.type == initializer.single().type && method.name == "invokeSuspend"
            }.addInstructions(0, returnUnit)
        }
        pushLifecycleMethods.forEach { (name, parameters) ->
            sourceMethod("Push lifecycle $name", "ActivityLifecycleDataSource.kt") { method ->
                method.name == name && method.returnType == "V" && method.params == parameters
            }.addInstruction(0, "return-void")
        }
    }
}

@Suppress("unused")
val disableKasperskyBackgroundScanPatch = bytecodePatch(
    name = "Disable Kaspersky background scan",
    description = "Disables periodic Kaspersky scheduling and reports disabled workers as successfully completed.",
    default = true
) {
    compatibleWith(RUSTORE_COMPATIBILITY)
    dependsOn(kasperskyManifestPatch)

    execute {
        kasperskyPeriodicEnabledFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x0
                return v0
            """
        )
        workMethod(KASPERSKY_WORKER).addInstructions(0, workerSuccess())
    }
}

@Suppress("unused")
val hideGamingProfilePatch = bytecodePatch(
    name = "Hide gaming profile",
    description = "Removes the gaming profile permission, hides the gaming profile widget, and blocks navigation to the gaming profile.",
    default = true
) {
    compatibleWith(RUSTORE_COMPATIBILITY)
    dependsOn(gamingManifestPatch)

    execute {
        methodsWhere { _, method ->
            method.returnType == "V" && method.params.isEmpty() && method.hasStrings("gameProfile.click")
        }.also { require(it.isNotEmpty()) { "Gaming profile navigation not found" } }
            .forEach { it.addInstruction(0, "return-void") }
        methodWhere("Game center button") { _, method ->
            method.returnType == "V" && method.hasStrings("GAME_CENTER_BUTTON_V2_KEY")
        }.addInstruction(0, "return-void")
    }
}

@Suppress("unused")
val skipUpdateAuthenticationPatch = bytecodePatch(
    name = "Skip update authentication",
    description = "Skips the update authentication suggestion and returns a valid completed result.",
    default = true
) {
    compatibleWith(RUSTORE_COMPATIBILITY)
    dependsOn(validatedManifestPatch)

    execute {
        val authSuggest = methodWhere("Update authentication suggestion") { type, method ->
            type.sourceFile == "AuthSuggestDelegateImpl.kt" && type.methods.none { it.name == "invokeSuspend" } &&
                !method.isStatic && method.returnType == "Ljava/lang/Object;" && method.params.size == 1
        }
        val unitReferences = authSuggest.implementation!!.instructions
            .zipWithNext()
            .mapNotNull { (fieldInstruction, returnInstruction) ->
                if (
                    fieldInstruction.opcode != Opcode.SGET_OBJECT ||
                    returnInstruction.opcode != Opcode.RETURN_OBJECT
                ) return@mapNotNull null

                ((fieldInstruction as? ReferenceInstruction)?.reference as? FieldReference)
                    ?.takeIf { it.definingClass == it.type }
            }
            .distinctBy { it.toString() }
        require(unitReferences.size == 1) {
            "Expected one completed-result singleton reference, found ${unitReferences.size}"
        }

        authSuggest.addInstructions(
            0,
            """
                sget-object v0, ${unitReferences.single()}
                return-object v0
            """
        )
    }
}

@Suppress("unused")
val blockRemoteNetworkPolicyPatch = bytecodePatch(
    name = "Block remote network policy",
    description = "Blocks remote and cached network policies that add TLS trust anchors or override API and static-content hosts.",
    default = true
) {
    compatibleWith(RUSTORE_COMPATIBILITY)

    execute {
        val constructor = methodWhere("Network policy constructor") { _, method ->
            method.name == "<init>" && method.params.size == 3 &&
                method.params[0] == "Landroid/content/Context;" && method.params[2] == "Z" &&
                method.hasStrings("superappkit_network_policy", "payload", "api_endpoint")
        }
        val policy = constructor.params[1]
        val embeddedSnapshot = methodWhere("Embedded network policy") { _, method ->
            method.isStatic && method.params.isEmpty() && method.returnType == policy
        }
        // A non-null embedded snapshot bypasses both cached payload and legacy
        // api_endpoint restoration. The existing certificate pinning stays active.
        constructor.addInstructions(
            0,
            """
                invoke-static {}, ${embeddedSnapshot.definingClass}->${embeddedSnapshot.name}()$policy
                move-result-object p2
            """
        )
        methodWhere("Remote network policy load") { _, method ->
            method.returnType != "V" && method.hasStrings("[Network Policy]: load remote config")
        }.addInstructions(
            0,
            """
                const/4 v0, 0x0
                return-object v0
            """
        )
    }
}
