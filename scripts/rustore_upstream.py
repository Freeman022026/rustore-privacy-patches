#!/usr/bin/env python3
"""Cheaply probe RuStore and validate changed APKs for the patch pipeline."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import sys
import urllib.request
from datetime import datetime, timezone
from pathlib import Path


PACKAGE_NAME = "ru.vk.store"
OFFICIAL_SIGNER_SHA256 = (
    "661f20828ef780de0b79bc59f26a30864316355f30e4f91cfa14a20791839914"
)
REQUIRED_PERMISSIONS = {
    "android.permission.QUERY_ALL_PACKAGES",
    "com.android.permission.GET_INSTALLED_APPS",
    "android.permission.REQUEST_INSTALL_PACKAGES",
    "android.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION",
    "android.permission.ENFORCE_UPDATE_OWNERSHIP",
    "android.permission.REQUEST_DELETE_PACKAGES",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
}
FORBIDDEN_PERMISSIONS = {
    "android.permission.INSTALL_PACKAGES",
    "com.google.android.gms.permission.AD_ID",
    "android.permission.PACKAGE_USAGE_STATS",
    "android.permission.READ_CALL_LOG",
    "android.permission.READ_PHONE_NUMBERS",
    "android.permission.RECEIVE_BOOT_COMPLETED",
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.ACCESS_COARSE_LOCATION",
    "android.permission.BIND_VPN_SERVICE",
}


def run(*command: str) -> str:
    result = subprocess.run(command, check=True, text=True, capture_output=True)
    return result.stdout + result.stderr


def write_json(path: Path, value: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def github_output(path: Path | None, values: dict[str, object]) -> None:
    if path is None:
        return
    with path.open("a", encoding="utf-8") as output:
        for key, value in values.items():
            if isinstance(value, bool):
                value = str(value).lower()
            output.write(f"{key}={value}\n")


def probe(args: argparse.Namespace) -> None:
    request = urllib.request.Request(
        args.url,
        method="HEAD",
        headers={"User-Agent": "rustore-privacy-patches/1.0"},
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        metadata = {
            "url": response.geturl(),
            "content_length": int(response.headers.get("Content-Length", "0")),
            "etag": response.headers.get("ETag", ""),
            "last_modified": response.headers.get("Last-Modified", ""),
            "checked_at": datetime.now(timezone.utc).isoformat(),
        }

    state = json.loads(args.state.read_text(encoding="utf-8"))
    previous = state.get("http", {})
    compared_fields = ("content_length", "etag", "last_modified")
    changed = args.force or any(
        metadata[field] != previous.get(field) for field in compared_fields
    )
    write_json(args.metadata, metadata)
    github_output(
        args.github_output,
        {
            "changed": changed,
            "content_length": metadata["content_length"],
            "etag": metadata["etag"],
            "last_modified": metadata["last_modified"],
        },
    )
    print(json.dumps({"changed": changed, **metadata}, indent=2))


def inspect_apk(args: argparse.Namespace) -> None:
    badging = run(str(args.aapt), "dump", "badging", str(args.apk))
    package_match = re.search(
        r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'",
        badging,
        re.MULTILINE,
    )
    if package_match is None:
        raise RuntimeError("aapt did not report APK package metadata")
    package_name, version_code, version_name = package_match.groups()
    if package_name != PACKAGE_NAME:
        raise RuntimeError(f"Unexpected package: {package_name}")

    signer_output = run(
        str(args.apksigner), "verify", "--print-certs", str(args.apk)
    )
    signer_match = re.search(
        r"Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]+)", signer_output
    )
    if signer_match is None:
        raise RuntimeError("apksigner did not report a SHA-256 certificate digest")
    signer = signer_match.group(1).lower()
    if args.require_official_signer and signer != OFFICIAL_SIGNER_SHA256:
        raise RuntimeError(f"Unexpected RuStore signer: {signer}")

    digest = hashlib.sha256(args.apk.read_bytes()).hexdigest()
    inspection = {
        "package_name": package_name,
        "version_code": version_code,
        "version_name": version_name,
        "sha256": digest,
        "size": args.apk.stat().st_size,
        "signer_sha256": signer,
    }
    write_json(args.output, inspection)
    github_output(
        args.github_output,
        {"version_name": version_name, "version_code": version_code, "sha256": digest},
    )
    print(json.dumps(inspection, indent=2))


def audit_patched(args: argparse.Namespace) -> None:
    badging = run(str(args.aapt), "dump", "badging", str(args.apk))
    permissions = set(re.findall(r"uses-permission: name='([^']+)'", badging))
    missing = sorted(REQUIRED_PERMISSIONS - permissions)
    forbidden = sorted(FORBIDDEN_PERMISSIONS & permissions)
    if missing or forbidden:
        raise RuntimeError(
            f"Patched manifest permission audit failed; missing={missing}, forbidden={forbidden}"
        )
    print(
        json.dumps(
            {
                "required_permissions_present": sorted(REQUIRED_PERMISSIONS),
                "forbidden_permissions_absent": sorted(FORBIDDEN_PERMISSIONS),
            },
            indent=2,
        )
    )


def promote(args: argparse.Namespace) -> None:
    metadata = json.loads(args.metadata.read_text(encoding="utf-8"))
    inspection = json.loads(args.inspection.read_text(encoding="utf-8"))
    state = json.loads(args.state.read_text(encoding="utf-8"))
    constants = args.constants.read_text(encoding="utf-8")

    current_match = re.search(r'const val AUDITED_VERSION = "([^"]+)"', constants)
    previous_match = re.search(
        r'const val PREVIOUS_AUDITED_VERSION = "([^"]+)"', constants
    )
    if current_match is None or previous_match is None:
        raise RuntimeError("Could not find audited version constants")

    old_current = current_match.group(1)
    new_current = inspection["version_name"]
    version_changed = old_current != new_current
    if version_changed:
        constants = constants.replace(
            f'const val PREVIOUS_AUDITED_VERSION = "{previous_match.group(1)}"',
            f'const val PREVIOUS_AUDITED_VERSION = "{old_current}"',
        )
        constants = constants.replace(
            f'const val AUDITED_VERSION = "{old_current}"',
            f'const val AUDITED_VERSION = "{new_current}"',
        )
        args.constants.write_text(constants, encoding="utf-8")

    state["http"] = {
        key: metadata[key]
        for key in ("url", "content_length", "etag", "last_modified")
    }
    state["apk"] = inspection
    state["audited_at"] = datetime.now(timezone.utc).isoformat()
    write_json(args.state, state)
    github_output(args.github_output, {"version_changed": version_changed})
    print(json.dumps({"version_changed": version_changed, **state}, indent=2))


def parser() -> argparse.ArgumentParser:
    root = argparse.ArgumentParser()
    commands = root.add_subparsers(dest="command", required=True)

    probe_parser = commands.add_parser("probe")
    probe_parser.add_argument("--url", required=True)
    probe_parser.add_argument("--state", type=Path, required=True)
    probe_parser.add_argument("--metadata", type=Path, required=True)
    probe_parser.add_argument("--github-output", type=Path)
    probe_parser.add_argument("--force", action="store_true")
    probe_parser.set_defaults(handler=probe)

    inspect_parser = commands.add_parser("inspect")
    inspect_parser.add_argument("--apk", type=Path, required=True)
    inspect_parser.add_argument("--aapt", type=Path, required=True)
    inspect_parser.add_argument("--apksigner", type=Path, required=True)
    inspect_parser.add_argument("--output", type=Path, required=True)
    inspect_parser.add_argument("--github-output", type=Path)
    inspect_parser.add_argument("--require-official-signer", action="store_true")
    inspect_parser.set_defaults(handler=inspect_apk)

    audit_parser = commands.add_parser("audit-patched")
    audit_parser.add_argument("--apk", type=Path, required=True)
    audit_parser.add_argument("--aapt", type=Path, required=True)
    audit_parser.set_defaults(handler=audit_patched)

    promote_parser = commands.add_parser("promote")
    promote_parser.add_argument("--metadata", type=Path, required=True)
    promote_parser.add_argument("--inspection", type=Path, required=True)
    promote_parser.add_argument("--state", type=Path, required=True)
    promote_parser.add_argument("--constants", type=Path, required=True)
    promote_parser.add_argument("--github-output", type=Path)
    promote_parser.set_defaults(handler=promote)
    return root


def main() -> int:
    args = parser().parse_args()
    try:
        args.handler(args)
    except Exception as error:
        print(f"error: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
