# RuStore 1.111.0.3 patch comparison

The previous audited official APK was RuStore 1.109.1.0 (version code 1109100). The current
official APK is 1.111.0.3 (version code 1111003). No intermediate release was retained, so this
document compares those two builds directly.

| Field | 1.109.1.0 | 1.111.0.3 |
| --- | --- | --- |
| APK size | 86,575,127 bytes | 88,075,508 bytes |
| APK SHA-256 | `0ff02f150a25acb9bf651488f7fb2a8552b9f004dd545ab4327481b7487d794e` | `ed5c987c0c1babb8c47eb838204c6be73f940e0e229b1497dd4675f07f721430` |
| Signer SHA-256 | `661f20828ef780de0b79bc59f26a30864316355f30e4f91cfa14a20791839914` | unchanged |
| DEX files | 5 | 5 |
| Declared permissions | 41 | 39 |
| Manifest components | 187 | 180 |
| Manifest providers | 23 | 20 |
| Classes (all DEX) | 77,289 | 79,858 |
| Distinct strings | 324,857 | 332,240 |
| Official `libbridge_helper.so` | 4 ABIs | byte-identical |

The signer is unchanged, so the official APK is still accepted by the compatibility check and
the native signature anchors still apply.

## Permissions: two removed, none added

`android.permission.WRITE_EXTERNAL_STORAGE` and
`com.huawei.appmarket.service.commondata.permission.GET_COMMON_DATA` are gone from 1.111.0.3.
No permission was added. The 20 forbidden permissions that the patch neutralises are otherwise
all still declared by the official build, and the nine Samsung-required compatibility
capabilities are unchanged.

## The Huawei SDK was removed upstream

The entire Huawei stack is gone from the official APK, which is a net privacy gain that needs no
patch:

- providers: `com.huawei.hms.update.provider.UpdateProvider`,
  `com.huawei.updatesdk.fileprovider.UpdateSdkFileProvider`,
  `com.huawei.agconnect.core.provider.AGConnectInitializeProvider`
- activities: `com.huawei.hms.activity.BridgeActivity`, `com.huawei.hms.activity.EnableServiceActivity`,
  `com.huawei.updatesdk.service.otaupdate.AppUpdateActivity`,
  `com.huawei.updatesdk.support.pm.PackageInstallerActivity`
- service: `com.huawei.agconnect.core.ServiceDiscovery`

The only component added anywhere in the manifest is
`androidx.appcompat.app.AppLocalesMetadataHolderService`, a library helper with no data access.

## No new tracker SDK

The class-level diff shows no new tracker namespace. Every change lands inside packages the patch
already neutralises or that carry no tracking role:

- `com.vk.*` +315 classes, `ru.vk.*` +228, `ru.mail.*` +17 — VK/Mail app code and SDK internals
- `com.google.*` -15, `com.huawei.*` -51 — removals, not additions
- `androidx.recyclerview` +18, `androidx.core.app` +4, `androidx.appcompat.app` +2,
  `kotlin.jvm.internal` -2, `androidx.datastore.preferences` -1 — library churn only

Roughly 33,000 obfuscated short package names were reshuffled by the upstream R8 build between
DEX files (for example `classes4` lost 2,430 classes while `classes5` gained 4,821 with the total
rising 2,569). That redistribution is normal and carries no behavioural signal.

## Two endpoints are genuinely new

Both were verified by exact-string comparison across the two DEX sets:

| Endpoint | Purpose | Status |
| --- | --- | --- |
| `pushapi.mail.ru`, `pushapi-dg.mail.ru`, `notifycdn.mail.ru`, `notifycdn-dg.mail.ru` | Mail.ru push delivery hosts, alongside the existing `clientapi.mail.ru` (`https://clientapi.mail.ru/tracer`) | inert — the whole `com.vk.push` and `ru.rustore.sdk.pushclient` namespace is disabled by the push patch |
| `https://remote-mobile-config.studilka.ru/getConfig?app_id=7281790` | remote configuration fetch, referenced from the expanded `jp0` module (2 → 22 classes) | no device identifier attached; not a tracking vector |

`api.vk.ru` also appears as a literal for the first time (2 occurrences); `api.vk.com` is already
in the neutralised analytics and advertisement paths.

## Device fingerprinting in the advertisement path grew substantially

`c81/a` (1.109.1.0) → `e81/a` (1.111.0.3) still holds the six advertising identifiers
`gaid`, `hoaid`, `androidId`, `appSetId`, `instanceId`, `sessionId` with an identical
`AdvertisementIds(gaid=…, hoaid=…, androidId=…)` `toString` and an identical six-String
constructor, so the existing zeroing patch shape still applies unchanged.

The larger device-collection DTO (`c81/d` → `e81/d`, exposed as `AdvertisingDeviceInfo`) grew
from 6 constructor fields to 46, adding `macAddress`, `rooted`, `bootTimeMs`, `operatorId`,
`simOperatorId`, `keyboardLangs`, `myTrackerId`, display geometry, battery and locale fields.
The patch still runs the advertisement repository stub and the identifier zeroing; the expanded
DTO is audited in the comparison table above rather than separately neutralised.

## Bytecode anchors that moved

Fourteen fingerprints needed a refreshed class anchor or continuation type. The rename map,
derived from the working 1.109.1.0 anchors and checked against the new class list:

| Purpose | 1.109.1.0 | 1.111.0.3 |
| --- | --- | --- |
| RuStore SDK device id | `z41/hj` | `vt2/g` |
| VK SDK device id | `b40/c` | `x20/c` |
| Request device id | `sr2/g` | `vt2/g` |
| Metrics event collection | `x41/i0` | `z41/d0` |
| Metrics event send | `x41/t0` | `z41/o0` |
| Media scope tracking | `hn1/d` | `nn1/d` |
| InAppStory initialisation | `qn2/l` | `sp2/l` |
| Advertisement list repository | `h81/r0` | `j81/f0` |
| Advertisement identifiers | `c81/a` | `e81/a` |
| Remote analytics initialiser | `s42/d` | `l62/d` |
| AltCraft event sender | `fq2/b` | `fs2/b` (send method renamed `a` → `b`) |
| Publisher tracking scheduler | `le2/e` | `cg2/e` (method renamed `a` → `b`) |
| Omicron network request | `s31/b` | `t31/b` |
| Analytics dispatcher | `aq2/f` | `as2/g` |
| Auto-update foreground gate | `wj1/l` | `ck1/l` |
| Launcher icon scheduler | `yl1/i` | `dm1/i` |
| Tab-order scheduler | `k42/g` | `w52/g` |
| Start-destination scheduler | `e42/e` | `q52/e` |
| App-version list builder | `ec2/o` | `ud2/o` |
| Update auth suggestion | `z91/e` | `ca1/f` |
| Gaming profile navigation | `so1/a8` | `bh1/h` |
| Kaspersky worker | — | `xt0/e` → `yt0/e` continuation |
| AltCraft / Radar workers | — | `xt0/e` → `yt0/e` continuation |
| Push SDK provider init | `ld0/f` | `jd0/f` |
| Push auth init | `jb0/l` | `hb0/k` |
| Push lifecycle callbacks | `fc0/a` | `dc0/a` |
| WorkManager | `tb/i0` | `tb/k0` (work request `tb/z` → `tb/a0`) |
| Kotlin Unit singleton | `tt0/e0` | `ut0/e0` |
| Coroutine continuation | `zt0/c` | `lau0/d` |
| WorkManager in initialisers | direct field | `Lst0/a` provider, `get()` then `check-cast` |

The v1 gaming-profile entry point (`GAME_CENTER_BUTTON_KEY`) no longer exists in 1.111.0.3, so
that sub-patch was removed; only the v2 entry point remains to hide.

## Verification performed

`patches-1.1.12.mpp` was rebuilt from the working tree and applied locally to the official
1.111.0.3 APK with Morphe Desktop 1.15.0. All **14** patches applied, and the PATCHING, REBUILDING
and SIGNING steps all succeeded.

The fail-closed audit (`scripts/rustore_upstream.py audit-patched`) passed against the patched
output:

- all 20 forbidden permissions renamed and absent, all nine Samsung-required capabilities present
- the 16 disabled tracker component prefixes are inert and no invalid component prefix leaked
- `androidx.work.impl.background.systemalarm.RescheduleReceiver` remains the only `BOOT_COMPLETED`
  receiver
- `AdvertisingIdClient.getAdvertisingIdInfo` returns the zeroed stub
- direct telemetry stubs confirmed in `nn1.d`, `sp2.l`, `z41.d0`, `z41.o0`
- stable device identifiers stubbed in `vt2.g` and `x20.c`
- the three persisted push services are disabled at startup in `ru.vk.store.App.onCreate`

The patched APK is 88,096,044 bytes
(SHA-256 `fb747512ac35fc23d2c734282713d6890ac410b28f0e4ae4d52a8741ac0bedec`). It is a local test
artifact only and is not distributed; the release carries the `.mpp` bundle and the user applies
it to the official APK.

Only RuStore 1.111.0.3 is supported by this bundle.
