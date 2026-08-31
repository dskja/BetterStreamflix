#!/usr/bin/env python3
"""Fail if translated locales miss keys present in values/strings.xml (cloud_sync_* and base criticals)."""
import re, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "app/src/main/res"
base = ROOT / "values/strings.xml"
key_re = re.compile(r'<string\s+name="([^"]+)"')

def keys(path):
    return set(key_re.findall(path.read_text(encoding="utf-8")))

base_keys = keys(base)
missing_report = []
for locale_dir in sorted(ROOT.glob("values-*")):
    strings = locale_dir / "strings.xml"
    if not strings.exists():
        continue
    loc_keys = keys(strings)
    missing = sorted(base_keys - loc_keys)
    # Focus gate: cloud_sync + legal + search empty (expand later)
    critical = [k for k in missing if k.startswith("cloud_sync_") or k in {
        "legal_disclaimer_title", "legal_disclaimer_message", "legal_disclaimer_accept",
        "search_no_results", "loading_error_generic", "feature_not_supported",
        "home_recommended_for_you", "home_cached_content_banner", "offline_banner_message",
        "downloads_title", "settings_search_title", "profile_picker_title",
    }]
    if critical:
        missing_report.append(f"{locale_dir.name}: {', '.join(critical)}")

if missing_report:
    print("Missing critical string keys:")
    print("\n".join(missing_report))
    sys.exit(1)
print("Critical string parity OK")
