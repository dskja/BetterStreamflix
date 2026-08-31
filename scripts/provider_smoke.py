#!/usr/bin/env python3
"""Nightly smoke: DNS + HTTP reachability for failover provider domains and sample home URLs."""

from __future__ import annotations

import socket
import ssl
import sys
import urllib.error
import urllib.request
from dataclasses import dataclass
from typing import Iterable

# Mirrors ProviderDomainManager.domainPatterns (app/src/main/java/.../ProviderDomainManager.kt)
FAILOVER_PROVIDERS: dict[str, list[str]] = {
    "SerienStream": ["186.2.175.5", "serienstream.to", "s.to", "serienstream.sx"],
    "AniWorld": ["aniworld.to", "aniworld.sx"],
    "Frembed": ["frembed.xyz", "frembed.cc"],
    "StreamingCommunity": [
        "streamingunity.cc",
        "streamingcommunity.cz",
        "streamingcommunity.xyz",
    ],
    "Cuevana": ["cuevana.gs", "cuevana3.eu", "cuevana3.ch"],
    "Moflix": ["moflix-stream.xyz", "moflix.to"],
    "PoseidonHD2": ["www.poseidonhd2.co", "poseidonhd2.co"],
}

# Representative registered providers — home page should respond (GET/HEAD).
HOME_SMOKE_URLS: dict[str, str] = {
    "SerienStream": "https://serienstream.to/",
    "AniWorld": "https://aniworld.to/",
    "FilmPalast": "https://filmpalast.to/",
    "Zaluknij": "https://zaluknij.cc/",
    "IptvOrg": "https://iptv-org.github.io/iptv/index.m3u",
    "MStream": "https://mstream.io/",
}

HTTP_TIMEOUT_SEC = 20.0
# Home probes can be flaky; fail only when too many are down at once.
MIN_HOME_PROBES_OK = 4


@dataclass
class ProbeResult:
    label: str
    detail: str
    ok: bool


def dns_resolves(host: str) -> bool:
    host = host.removeprefix("https://").removeprefix("http://").split("/")[0]
    if host.replace(".", "").isdigit():
        return True
    try:
        socket.getaddrinfo(host, 443, type=socket.SOCK_STREAM)
        return True
    except socket.gaierror:
        return False


def http_reachable(url: str, timeout: float = HTTP_TIMEOUT_SEC) -> tuple[bool, str]:
    req = urllib.request.Request(
        url,
        method="HEAD",
        headers={"User-Agent": "BetterStreamflix-NightlySmoke/1.0"},
    )
    ctx = ssl.create_default_context()
    try:
        with urllib.request.urlopen(req, timeout=timeout, context=ctx) as resp:
            code = resp.getcode()
            if code < 400 or code in (401, 403):
                return True, f"HTTP {code}"
            return False, f"HTTP {code}"
    except urllib.error.HTTPError as exc:
        if exc.code < 500 or exc.code in (401, 403):
            return True, f"HTTP {exc.code}"
        return False, f"HTTP {exc.code}"
    except Exception as exc:  # noqa: BLE001 — smoke script aggregates failures
        # Some hosts reject HEAD; retry with GET (range).
        get_req = urllib.request.Request(
            url,
            method="GET",
            headers={
                "User-Agent": "BetterStreamflix-NightlySmoke/1.0",
                "Range": "bytes=0-0",
            },
        )
        try:
            with urllib.request.urlopen(get_req, timeout=timeout, context=ctx) as resp:
                return True, f"GET HTTP {resp.getcode()}"
        except Exception as get_exc:  # noqa: BLE001
            return False, f"{type(exc).__name__}: {exc}; GET {type(get_exc).__name__}: {get_exc}"


def probe_failover_provider(name: str, domains: Iterable[str]) -> ProbeResult:
    reachable: list[str] = []
    errors: list[str] = []
    for domain in domains:
        host = domain.removeprefix("https://").removeprefix("http://").split("/")[0]
        if not dns_resolves(host):
            errors.append(f"{host}: DNS failed")
            continue
        base = domain if domain.startswith("http") else f"https://{domain}"
        ok, detail = http_reachable(base)
        if ok:
            reachable.append(f"{host} ({detail})")
        else:
            errors.append(f"{host}: {detail}")
    if reachable:
        return ProbeResult(name, f"reachable via {reachable[0]}", True)
    return ProbeResult(name, "; ".join(errors) or "no domains", False)


def probe_home(name: str, url: str) -> ProbeResult:
    host = url.split("/")[2]
    if not dns_resolves(host):
        return ProbeResult(f"home:{name}", f"{host}: DNS failed", False)
    ok, detail = http_reachable(url)
    return ProbeResult(f"home:{name}", detail if ok else f"{url}: {detail}", ok)


def main() -> int:
    failover_results: list[ProbeResult] = []
    home_results: list[ProbeResult] = []

    for provider, domains in FAILOVER_PROVIDERS.items():
        failover_results.append(probe_failover_provider(provider, domains))
    for provider, url in HOME_SMOKE_URLS.items():
        home_results.append(probe_home(provider, url))

    results = failover_results + home_results
    for result in results:
        status = "OK" if result.ok else "FAIL"
        print(f"[{status}] {result.label}: {result.detail}")

    failover_failures = [r for r in failover_results if not r.ok]
    home_ok = sum(1 for r in home_results if r.ok)
    home_failures = [r for r in home_results if not r.ok]

    if failover_failures:
        print(f"\n{len(failover_failures)} failover probe(s) failed.", file=sys.stderr)
        return 1
    if home_ok < MIN_HOME_PROBES_OK:
        print(
            f"\nOnly {home_ok}/{len(home_results)} home probes passed "
            f"(need {MIN_HOME_PROBES_OK}). Failed: "
            + ", ".join(r.label for r in home_failures),
            file=sys.stderr,
        )
        return 1

    if home_failures:
        print(
            f"\nWarning: {len(home_failures)} home probe(s) failed but "
            f"{home_ok}/{len(home_results)} passed (threshold {MIN_HOME_PROBES_OK}).",
        )
    print(f"\nAll {len(failover_results)} failover probes passed; home {home_ok}/{len(home_results)} OK.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
