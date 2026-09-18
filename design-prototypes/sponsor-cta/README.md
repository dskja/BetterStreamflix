# BetterStreamflix — Sponsor / Spenden Design-Prototypen

Interactive HTML gallery (no app code wired yet):

```bash
cd design-prototypes/sponsor-cta
python3 -m http.server 8765
# open http://127.0.0.1:8765/
```

## Links (live)

| Channel | URL |
|---------|-----|
| Patreon | https://www.patreon.com/BetterStreamflix |
| Buy Me a Coffee | https://buymeacoffee.com/betterstreamflix |
| GitHub Sponsors | https://github.com/sponsors/dskja |
| Telegram | https://t.me/BetterStreamflix |

## Variants

| ID | Placement | Notes |
|----|-----------|-------|
| **A** Startup Modal | Every cold start | Checkbox **Never show again on start** (`SUPPORT_PROMPT_NEVER_ON_START`) |
| **B** Home Banner | Home top | Soft persistent until goal closed |
| **C** Critical Sheet | Bottom sheet DE | Stronger urgency copy |
| **D** Settings Embed | Settings / Support | Full URL list |
| **E** About Block | About | Large card + progress |
| **F** Android TV | Leanback focus | D-Pad tiles + never-again |
| **G** Player Tip | After playback | Soft dismissible chip |

## Recommended ship order

1. **A** (startup) + **D** (settings) — highest reach, opt-out respected  
2. **F** for TV users who never open mobile Settings  
3. **B** or **G** as secondary surfaces  

## Mock images

Generated under `/opt/cursor/artifacts/sponsor-prototypes/`:

- `sponsor-startup-modal.png`
- `sponsor-home-banner.png`
- `sponsor-critical-sheet.png`
- `sponsor-settings-embed.png`
- `sponsor-tv-card.png`
- `sponsor-prototype-gallery-full.png`
