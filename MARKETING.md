# BetterStreamflix Marketing System

This file is the practical growth playbook for BetterStreamflix. It is designed around one goal: turn active development into discoverability, trust, downloads, contributors and GitHub stars without making misleading claims about content availability.

## 1. Core positioning

### One-line position

**BetterStreamflix is a modern, actively maintained Streamflix fork for Android TV and Android mobile, focused on reliability, UX and fast fixes.**

### Short pitch

BetterStreamflix keeps the Streamflix experience moving forward with active maintenance, Android TV/mobile support, provider resilience, smarter caching, playback-state improvements, release automation and a rapidly improving Kotlin codebase.

### What we should NOT say

Avoid claims such as:

- "watch every movie for free"
- "all streaming services in one app"
- "Netflix for free"
- "piracy app"
- guaranteed provider/content availability
- claims that unfinished infrastructure is already a polished end-user feature

The project should be marketed as an **open-source Android streaming interface/aggregator and engineering project**, not as a substitute for paid copyrighted services.

## 2. Audience

### Primary audience: Android TV power users

They care about:

- remote-friendly UI
- fast navigation
- stability
- frequent fixes
- sideloadable APKs
- active maintenance

Message: **A Streamflix fork that is actually being maintained and improved.**

### Secondary audience: Android/Kotlin developers

They care about:

- Kotlin
- MVVM
- Coroutines / Flow
- Room
- Retrofit / OkHttp
- Media3
- CI/CD
- testing
- architecture

Message: **A real Android TV/mobile codebase with active engineering work and lots of places to contribute.**

### Third audience: former Streamflix users/contributors

They care about:

- whether the project is alive
- whether bugs are being fixed
- whether migration is simple
- whether original authors are credited

Message: **The familiar project, continued respectfully and actively.**

## 3. Brand language

### Tagline options

Primary:

> Streamflix, actively maintained.

Alternatives:

> The Streamflix fork that keeps moving.

> Built for Android TV. Maintained for real life.

> Open-source streaming UI, rebuilt for reliability.

### Tone

- technical but accessible
- confident, not hype-driven
- transparent about development status
- respectful toward the original project
- community-first

### Visual direction

Use a dark cinematic UI style that matches the app itself. Screenshots should contain the app UI, not generic movie posters detached from the product. Every visual should answer one of these questions:

1. What does the app look like?
2. What problem did this release fix?
3. What improved since the original fork?
4. Why should a developer contribute?

## 4. GitHub discovery setup

After merging the marketing branch, manually add repository topics from the GitHub repository settings.

Recommended topics:

`android`
`android-tv`
`google-tv`
`kotlin`
`streaming`
`media3`
`exoplayer`
`leanback`
`open-source`
`android-app`
`mvvm`
`tv-app`

Recommended repository description:

> Actively maintained open-source Streamflix fork for Android TV and mobile, built with Kotlin and focused on reliability, UX and fast fixes.

Recommended website field after GitHub Pages is enabled:

> https://dskja.github.io/BetterStreamflix/

### Why topics matter

The repository currently has no topics, so it is missing a large part of GitHub's category/discovery surface. Topics also immediately explain the stack and device targets to developers who land on the repository.

## 5. Conversion funnel

Every channel should push users through this path:

**Discovery → GitHub/landing page → screenshot + value proposition → release page → install/test → star → issue/PR/share**

Do not send cold traffic directly to a raw APK unless there is a specific reason. Let users see the project, disclaimer and release notes first.

Primary CTA:

> Download latest release

Secondary CTA:

> Star on GitHub

Developer CTA:

> Pick an issue and contribute

## 6. Launch sequence

### Phase A — Foundation

Before broad promotion:

- merge the new README
- enable GitHub Pages for `/docs`
- add repository topics
- update repository description
- verify latest release has working APK assets
- verify mobile and TV installation instructions
- pin one clean screenshot plus 2–4 feature screenshots
- ensure issue templates work
- create a clear release with concise user-facing notes

### Phase B — Soft launch

Publish first to communities where feedback matters more than reach:

- GitHub followers/network
- Android development communities
- Kotlin communities
- Android TV enthusiast communities
- relevant open-source forums

Goal: find broken install steps, unclear messaging, crashes and obvious UX problems before a large push.

### Phase C — Public launch

Publish the same product story in channel-native formats across:

- Reddit
- Hacker News / Show HN
- X
- Mastodon
- Bluesky
- Lemmy
- Android/Kotlin Discord communities where project promotion is allowed
- GitHub Discussions in relevant projects only when genuinely relevant

Do not spam identical text everywhere. Adapt the framing.

### Phase D — Release loop

Every meaningful release becomes a mini-launch:

1. ship release
2. write 3–5 user-facing changes
3. create one screenshot/GIF
4. publish one developer-oriented update
5. publish one user-oriented update
6. answer comments quickly
7. collect bugs into issues

The most effective long-term marketing for this project is visible momentum.

## 7. Ready-to-post launch copy

### X / Bluesky / Mastodon — main launch

> I’ve been rebuilding and actively maintaining BetterStreamflix — an open-source Streamflix fork for Android TV and mobile.
>
> The focus is simple: faster fixes, better reliability, cleaner UX and continuous Android/Kotlin improvements.
>
> Recent work includes playback-state fixes, provider resilience, caching, CI/release automation and a much larger architecture/QA pass.
>
> GitHub: https://github.com/dskja/BetterStreamflix
>
> If you use Android TV or build Android apps, feedback and contributions are welcome.

### X — short version

> BetterStreamflix: an actively maintained Streamflix fork for Android TV + mobile.
>
> Kotlin. Open source. Frequent fixes. Better reliability. No ad layer added by the app.
>
> https://github.com/dskja/BetterStreamflix

### Reddit — Android developer angle

**Title:** I’m actively rebuilding a Streamflix fork for Android TV/mobile — Kotlin, MVVM, Media3, Room, CI/CD

**Body:**

> I’ve been working on BetterStreamflix, an actively maintained open-source Streamflix fork targeting Android TV and Android mobile.
>
> The project started as a maintenance effort, but it has grown into a much larger modernization pass. Recent work includes playback-state fixes, provider resilience, smarter caching, network retry/error handling, database/sync infrastructure, image caching, release automation, QA helpers and ongoing download/cast/widget infrastructure.
>
> Stack includes Kotlin, MVVM, Coroutines/Flow, Room, Retrofit/OkHttp, Media3/ExoPlayer and Leanback.
>
> I’m especially interested in feedback on Android TV UX, code quality, testing and reliability. Contributions are welcome.
>
> Repo: https://github.com/dskja/BetterStreamflix
>
> The project does not host media; it is an interface/aggregator for third-party sources and is intended for educational/personal use.

### Reddit — Android TV user angle

**Title:** BetterStreamflix — an actively maintained Streamflix fork for Android TV and mobile

**Body:**

> I’ve started actively maintaining and improving BetterStreamflix for Android TV and mobile.
>
> The main goal is reliability: fixing broken behavior quickly, improving Continue Watching/playback state, strengthening provider fallbacks, making updates easier and polishing the TV/mobile experience.
>
> If you used Streamflix before and want to test the maintained fork, the project and releases are here:
>
> https://github.com/dskja/BetterStreamflix
>
> Bug reports with device model + Android version are especially useful.

### Show HN

**Title:** Show HN: BetterStreamflix – actively maintained Android TV/mobile Streamflix fork

**Text:**

> BetterStreamflix is an open-source Android TV/mobile project I’m actively maintaining and modernizing in Kotlin.
>
> Recent work goes beyond provider fixes: caching, playback state, network resilience, CI/release automation, QA infrastructure, database/sync helpers, image caching and ongoing cast/download/widget work.
>
> I’m sharing it mainly as an Android/Kotlin open-source project and would appreciate feedback on architecture, TV UX, testing and maintainability.
>
> Source: https://github.com/dskja/BetterStreamflix

### Developer community post

> Looking for Android/Kotlin contributors: BetterStreamflix is an actively maintained Android TV + mobile project using Kotlin, MVVM, Coroutines/Flow, Room, Retrofit/OkHttp, Media3/ExoPlayer and Leanback.
>
> Useful contribution areas: Android TV focus/navigation, provider resilience, tests, accessibility, error handling, documentation and performance.
>
> Repo: https://github.com/dskja/BetterStreamflix

## 8. Content engine

Do not rely on one launch post. Turn development into recurring content.

### Content pillar 1 — Fixes

Format:

> Problem → what users experienced → what changed → link to release/commit

Example:

> Continue Watching was getting out of sync in a few flows. The latest BetterStreamflix work routes those actions through the same watch-state path so clearing/marking behaves consistently. Small fix, much less annoying TV experience.

### Content pillar 2 — Before/after UX

Show:

- provider screen redesign
- TV focus improvements
- loading states
- update flow
- error states
- animation/polish

### Content pillar 3 — Engineering depth

Show one technical improvement at a time:

- retry interceptor
- cache policy
- database migration
- state machine
- test scenario runner
- release pipeline

Each post should be understandable without reading the entire commit.

### Content pillar 4 — Open roadmap

Monthly post:

> What shipped / what broke / what is next / where contributors can help.

Transparency converts developers better than generic promotion.

### Content pillar 5 — Release day

Every release post should contain:

- version
- 3 strongest changes
- one screenshot or short GIF
- release link
- one feedback request

## 9. 30-day content calendar

### Week 1 — Establish identity

Day 1: main launch post

Day 2: screenshot + "why this fork exists"

Day 3: technical thread on architecture modernization

Day 4: Android TV UX post

Day 5: contributor call

Day 6: bug-fix story

Day 7: weekly changelog recap

### Week 2 — Build credibility

- provider resilience explanation
- Continue Watching fix explanation
- CI/release automation screenshot
- code snippet from a clean subsystem
- issue spotlight
- user feedback repost/quote if permission exists
- weekly recap

### Week 3 — Community participation

- poll: most important next improvement
- "good first issue" post
- Android TV device testing request
- documentation contribution request
- roadmap update
- performance/caching post
- weekly recap

### Week 4 — Second launch wave

- release announcement
- before/after visual
- 30-day numbers
- lessons learned
- contributor thank-you
- next milestone teaser
- refreshed Reddit/HN/community post only where self-promotion rules allow it

## 10. Community distribution rules

### Reddit

Before posting:

- read each subreddit’s self-promotion rules
- use a native, useful post rather than link-only spam
- disclose that you maintain the project
- stay in the comments and answer technical questions

Potential community categories to research:

- Android TV
- Android apps
- Kotlin
- Android development
- open source
- self-hosting/media UI communities where relevant and permitted

Do not mass-post the exact same copy to many subreddits in one hour.

### Hacker News

HN responds better to technical substance than marketing language. Lead with:

- what you built
- what was technically difficult
- what changed
- what feedback you want

Avoid grandiose claims.

### Discord / Matrix / forums

Join only communities where the project is relevant. Post in designated showcase/project channels and ask for targeted feedback instead of dropping a link.

## 11. Contributor marketing

Contributors create compounding distribution. Make contribution status visible.

Recommended labels:

- `good first issue`
- `help wanted`
- `android-tv`
- `mobile`
- `provider`
- `ui/ux`
- `bug`
- `testing`
- `documentation`
- `performance`

Every week, select 1–3 realistic issues that a new contributor can finish without understanding the entire codebase.

Contributor CTA:

> Want to help without touching provider code? TV focus/navigation, tests, accessibility, documentation and error-state UX are all useful contribution areas.

## 12. Release marketing template

Use this for every meaningful release.

### Title

> BetterStreamflix vX.Y.Z — [strongest user-facing improvement]

### Release post

> BetterStreamflix vX.Y.Z is out.
>
> Highlights:
> - [change 1]
> - [change 2]
> - [change 3]
>
> Download / release notes:
> https://github.com/dskja/BetterStreamflix/releases/latest
>
> If something breaks, please include your device model, Android version and reproduction steps in the issue.

## 13. Screenshot/GIF checklist

Create at least these visual assets:

1. Home screen on TV layout
2. Content detail screen
3. Continue Watching
4. Provider/source selection
5. Search
6. Mobile layout
7. Settings/update flow
8. One 8–15 second navigation GIF

Rules:

- crop cleanly
- remove notifications/private information
- use the same device resolution/aspect ratio per set
- do not overfill images with text
- include one short headline when used on social media

## 14. Landing page

A starter static landing page is included in `docs/index.html` on the marketing branch.

After merge:

1. Open GitHub repository Settings
2. Open Pages
3. Deploy from branch
4. Select `main` and `/docs`
5. Save
6. Add `https://dskja.github.io/BetterStreamflix/` to the repository Website field

The landing page intentionally sends traffic back to GitHub Releases and the source repository so trust remains centralized.

## 15. Metrics

Track weekly, not obsessively every hour.

### Discovery

- GitHub unique visitors
- repository views
- referring sites
- landing-page visits if analytics is later added

### Conversion

- stars gained
- release downloads
- clones
- issue creation
- contributor PRs

### Quality

- crash/bug reports per release
- time to first maintainer response
- time from confirmed bug to fix
- percentage of issues with reproducible steps

### Suggested launch targets

The repository is brand new, so use directional milestones rather than promises.

Milestone 1:

- 25 stars
- first external contributor PR
- 10 useful tester reports

Milestone 2:

- 100 stars
- 5 external contributors
- stable repeatable release cadence

Milestone 3:

- 250+ stars
- healthy issue triage
- regular community-driven fixes

## 16. Weekly operating rhythm

### Monday

Pick the strongest user-facing improvement shipping this week.

### Tuesday–Thursday

Publish one useful technical or UX update. Respond to issues/comments.

### Friday

Ship or summarize progress. Post screenshots/release notes.

### Weekend

Triage feedback, tag contributor-friendly issues and prepare the next cycle.

## 17. Anti-spam rule

Marketing should never become noise.

A post is worth publishing when it contains at least one of:

- a real new release
- a visible UX improvement
- a meaningful bug fix
- a useful technical explanation
- a concrete request for community feedback
- a contributor opportunity

If none of these exist, build first and post later.

## 18. Immediate launch checklist

- [ ] Merge `marketing/launch-system`
- [ ] Add GitHub topics
- [ ] Update repository description
- [ ] Enable GitHub Pages from `/docs`
- [ ] Verify latest release APK assets install correctly
- [ ] Capture 6–8 clean screenshots
- [ ] Record one short TV-navigation GIF
- [ ] Publish main launch post
- [ ] Publish Android/Kotlin developer post
- [ ] Publish Android TV user post where rules allow
- [ ] Reply to every useful launch comment
- [ ] Convert bug feedback into GitHub issues
- [ ] Label 3 contributor-friendly issues
- [ ] Post one release/progress update each week
- [ ] Review GitHub traffic and release downloads after 7 days

## 19. The core growth loop

The growth loop is intentionally simple:

**Ship → explain → show → invite feedback → fix → release → thank contributors → repeat.**

BetterStreamflix does not need fake hype. It already has the raw material for strong open-source marketing: visible development speed, a known upstream project, Android TV/mobile utility and a deep technical roadmap. The job of marketing is to make that work legible to people outside the commit history.
