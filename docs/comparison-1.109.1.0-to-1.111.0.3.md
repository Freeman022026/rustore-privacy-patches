# RuStore 1.111.0.3 patch comparison

This comparison uses the retained official RuStore 1.109.1.0 APK and the official
1.111.0.3 APK. The latter matches the release CDN byte for byte and passes the
official signing-certificate check. No intermediate APK was retained.

| Field | 1.109.1.0 | 1.111.0.3 |
| --- | --- | --- |
| Version code | 1109100 | 1111003 |
| APK size | 86,575,127 bytes | 88,075,508 bytes |
| APK SHA-256 | `0ff02f150a25acb9bf651488f7fb2a8552b9f004dd545ab4327481b7487d794e` | `ed5c987c0c1babb8c47eb838204c6be73f940e0e229b1497dd4675f07f721430` |
| Signer SHA-256 | `661f20828ef780de0b79bc59f26a30864316355f30e4f91cfa14a20791839914` | unchanged |
| DEX files | 5 | 5 |
| Uncompressed DEX size | 55,235,288 bytes | 56,970,876 bytes |
| Defined classes | 77,289 | 79,858 |
| Unique strings across all DEX files | 271,762 | 278,825 |
| Declared permissions | 41 | 39 |
| Manifest components | 187 | 180 |
| Manifest providers | 23 | 20 |
| Native libraries | 48 | 48 |
| Official `libbridge_helper.so` | 4 ABIs | byte-identical |

## Permissions and startup components

The official APK removed `android.permission.WRITE_EXTERNAL_STORAGE` and
`com.huawei.appmarket.service.commondata.permission.GET_COMMON_DATA`. It added no
permissions. The declarations needed for package discovery, installation, and
automatic updates remain available to the patch bundle.

Eight Huawei manifest components disappeared: the AGConnect initialization and
service-discovery components, the HMS update provider and two HMS activities,
and the Huawei Update SDK provider and two activities. Huawei classes fell from
68 to 17. Advertising-related Huawei code remains bundled, so the removal of
those startup components does not establish that every Huawei path is gone.
The only added manifest component is
`androidx.appcompat.app.AppLocalesMetadataHolderService`.

## SDKs, endpoints, and internal features

The readable-package inventory contains no newly identified tracker SDK vendor.
It includes new VK SSL-pinning classes and changes inside existing advertising,
verification, and RuStore feature packages. Obfuscated class names changed
throughout the APK; a renamed class alone does not establish new behavior.

The new readable CDN-probe, statistics, download-monitor, and VPN-detector
configuration classes have corresponding configuration strings in 1.109.1.0.
For example, `cdnProbeTargetsUrl` and `cdnProbeReportUrl` existed in the old
`va1.f` configuration provider. CDN probe reporting is an existing telemetry
path. These package additions must not be described as proof that every added
class has no tracking role.

Exact comparison of DEX strings found two new literal URL hosts, `api.vk.ru`
and `remote-mobile-config.studilka.ru`. The latter is the network-policy source
examined below. Four additional push host literals are new: `pushapi.mail.ru`,
`pushapi-dg.mail.ru`, `notifycdn.mail.ru`, and `notifycdn-dg.mail.ru`. The bundle
blocks the reviewed VK and RuStore push initialization, services, and workers.

No native library was added or removed. `libverify.so` and `libvkqrcode.so`
changed in all four ABIs. The native secure-session patch checks its original
byte anchors before changing `libbridge_helper.so`; those anchors remain valid.
The inventory records every native-library hash, but this review does not claim
a complete disassembly of both changed native libraries.

## Remote network policy

`jp0.l.a(Context)` downloads
[`getConfig?app_id=7281790`](https://remote-mobile-config.studilka.ru/getConfig?app_id=7281790).
The downstream code parses API and static-content host overrides and PEM
certificates. `jp0.f.a` adds the certificates to the app's TLS key store under
`network_policy_certificate_<index>` aliases and rebuilds its HTTP client when
SSL pinning is enabled. `jp0.e` validates HTTPS host syntax and rejects older
policy revisions. The revision counter is stored locally; it is not a remote
JSON field in the inspected response.

A direct fetch on 2026-10-05 returned an empty `override_domain` object and three
certificates. Their subjects were `Russian Trusted Sub CA`, `YR2`, and `YR1`.
The first is issued by `Russian Trusted Root CA` and expires on 2027-03-06; the
other two are Let's Encrypt intermediates issued by `Root YR` and expire on
2028-09-02. The response contained no static-host override. Future responses
can differ.

This is a remotely controlled TLS-trust and routing mechanism. The response
can change the app's trust anchors and destinations, even though the fixed GET
request carries no explicit device identifier. This finding is not evidence
that an interception occurred.

The default `Block remote network policy` patch stops the download and forces
`jp0.n` to initialize from `jp0.o.a()`'s embedded fallback. A non-null fallback
bypasses restoration of both the cached `payload` and the legacy `api_endpoint`
preference. `jp0.e` treats that fallback as having no active remote policy.
The app's built-in TLS verification and certificate pinning remain in place.

## Advertising identifiers

`AdvertisementIds` moved from `c81.a` to `e81.a`. Its six-string constructor and
identifier fields are unchanged. `AdvertisingDeviceInfo` moved from `c81.d`
to `e81.d`; both versions have 48 constructor parameters and the same field
names in `toString()`. MAC address, root status, boot time, operator identifiers,
and display and battery information were already present in 1.109.1.0.

The advertisements patch still returns an empty advertisement list, sanitizes
its identifiers, and forces advertising consent off. Identifier collection code
remains in the APK. The phone log still contains an attempted AppSet-ID service
lookup, which failed because that service was unavailable. Static blocking and
a short traffic capture do not establish that all identifier-collection code
has stopped executing.

## Bytecode changes

The refreshed anchors include:

| Purpose | 1.109.1.0 | 1.111.0.3 |
| --- | --- | --- |
| RuStore SDK device identifier | `z41.hj` | `b51.ol` |
| Request device identifier | `sr2.g` | `vt2.g` |
| VK SDK device identifier | `b40.c` | `x20.c` |
| Metrics collection / sending | `x41.i0` / `x41.t0` | `z41.d0` / `z41.o0` |
| Mediascope / InAppStory | `hn1.d` / `qn2.l` | `nn1.d` / `sp2.l` |
| Advertisement repository | `h81.r0` | `j81.f0` |
| AltCraft event sender | `fq2.b.a` | `fs2.b.b` |
| Publisher tracking scheduler | `le2.e.a` | `cg2.e.b` |
| Omicron request / error enum | `s31.b` / `s31.e` | `t31.b` / `t31.e` |
| Analytics dispatcher | `aq2.f` | `as2.g` |
| Auto-update eligibility | `wj1.l` | `ck1.l.b` |
| Launcher icon scheduler | `yl1.i` | `dm1.i` |
| Start destination / tab order | `e42.e` / `k42.g` | `q52.e` / `w52.g` |
| Update request filtering | `ec2.o` | `ud2.o` |
| Update authentication suggestion | `z91.e` | `ca1.f` |
| Mine gaming-profile navigation | `so1.a8` | `yo1.t5.z0` |
| Push provider / authentication init | `ld0.f` / `jb0.l` | `jd0.f` / `hb0.k` |
| Push lifecycle callbacks | `fc0.a` | `dc0.a` |
| Kotlin Unit / empty list | `tt0.e0` / `ut0.x` | `ut0.e0` / `vt0.x` |
| WorkManager cancellation | `tb.i0` / `tb.z` | `tb.k0` / `tb.a0` |
| WorkManager implementation lookup | `ub.u0.l` | `ub.r0.m` |
| Coroutine continuation | `zt0.c` | `au0.d` |
| Coroutine-worker continuation | `xt0.e` | `yt0.e` |

Several initializer fields now hold `Lst0/a` providers. Cancellation stubs call
`get()` and cast to WorkManager before cancelling the named jobs.

`ck1.l.b` is a positive eligibility check. Returning false from that method
would suppress automatic updates. The patch replaces only the lifecycle
foreground flag read with false and retains the existing permission and status
checks. The RuStore SDK identifier and request identifier are separate methods;
each has its own audited stub.

The old `GAME_CENTER_BUTTON_KEY` marker disappeared. The remaining gaming
widget entry point is `kh1.j.d`, with its rendering variants called internally.
The patch hides that entry point and blocks `yo1.t5.z0`, which navigates to
`GameCenterStatsDestination`. `bh1.h.A3` only emits a click event and is not the
navigation method.

## Verification

A local review bundle built with Java 21 applies all 15 default patches to the
official 1.111.0.3 APK using Morphe Desktop 1.15.0, without forcing compatibility.
The expanded audit verifies the actual entry-point instructions, all three
stable identifier getters, the embedded-policy startup override, and the
foreground bypass with its original update-status checks intact. Regression
checks reject a loader whose only null return is on its original failure path
and an eligibility method forced to return false.

The same bundle was applied through Morphe Manager and installed in place on a
Motorola running Android 16. The installed version is 1111003, its signing
certificate matches the preceding installation, and its original installation
record remains intact. The APK pulled back from the phone passes the expanded
audit. Store browsing, installed-app inventory, update discovery, search, and
app details load. The Mine screen has no gaming-profile widget. Automatic
updates remain enabled.

A RuStore-only PCAPdroid capture contains 2,712 IPv4 packets spanning 416.76
seconds of traffic during startup, browsing, installed-app checks, search,
app details, and a background interval. DNS and TLS host extraction with
Wireshark found `api-m.rustore.ru`, `backapi-m.rustore.ru`, `static-m.rustore.ru`,
`static.rustore.ru`, and `api.vk.ru`. The network-policy and new push hosts were
not observed. The process log contains no fatal exception, unresolved-member
error, or TLS-handshake error during these checks. TLS payloads were not
decrypted, so this capture cannot establish what every API request contained
or prove that every tracking path is inactive. PCAPdroid was stopped afterward.

WorkManager diagnostics show the two retained periodic update jobs. A forced
scheduler run reaches WorkManager, which defers execution until the existing
daily deadline. This test does not establish that a complete version-111
automatic download and installation cycle has finished. Payment and account
login flows were not exercised.

The bundle supports only RuStore 1.111.0.3. Patched APKs are local test artifacts;
the repository distributes only the `.mpp` patch bundle.
