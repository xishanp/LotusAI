<p align="center">
  <img src="assets/banner.png" alt="Lotus AI" width="100%">
</p>

<h1 align="center">Lotus AI</h1>

<h3 align="center">Pure chat · Liquid Glass everywhere · AI with real memory, for Android</h3>

<p align="center">
  <a href="README.md">中文</a> · <b>English</b>
</p>

<p align="center">
  <img alt="Android" src="https://img.shields.io/badge/Android-8.0%2B%20(API%2028)-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.4.20-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Compose" src="https://img.shields.io/badge/Jetpack%20Compose-BOM%202026.09.00-4285F4?logo=jetpackcompose&logoColor=white">
  <img alt="License" src="https://img.shields.io/badge/License-Apache--2.0-blue">
</p>

---

## What it is

**Lotus AI** is an Android AI chat app. It does exactly one thing: **chat**.

The whole interface is one continuous sheet of liquid glass, and the conversation itself is light,
fast and cheap — one question is one model call. The request carries nothing but
*your persona, your memories, and what you actually said*.

It remembers you, and it has a stable personality — because both live in **plain Markdown files you
can open and edit**. The model can write to them, and you can replace them with your own.

## What it is not

- **Not an agent.** No tool loop, no background jobs, no wall of tool descriptions stuffed into every
  turn. One question = one request. That is why it is cheap on tokens and quick to answer.
- **No vendor baked in.** Base URL, API key and model list are yours to fill in. It speaks the
  OpenAI-compatible protocol.
- **No account, no telemetry.** Your keys and conversations stay on your device.

## Highlights

### Light on tokens

- The request body contains three things only: **persona prompts**, **memory**, **history**.
- Memory is **tiered** by default: three must-read files (`IDENTITY` / `SOUL` / `AGENTS`), the
  "hot pointer" summary at the top of `MEMORY.md` (hard-capped at 900 characters), and an
  on-demand archive. The whole archive is *not* pushed into every turn.
- Live context usage, per-conversation **trim / compress**. Compression only rewrites the summary —
  nothing is deleted.

### Fast, with thinking kept intact

- SSE streaming, rendered as it arrives.
- Reasoning tiers are **mapped to what the model actually supports** — as many as it has, from
  `none` all the way to `max` / `ultra`, no more, no fewer.

### Real memory — a complete Markdown framework

- Persona and memory are plain text, editable from the **Files** tab.
- **Automatic recall**: when the model wants to remember something long-term it emits a hidden
  marker, and the client strips it out of the **same** response and appends it to `MEMORY.md` —
  no second request, no extra tokens.
- **Manual memory**: `⋯ → remember this` in a chat compresses the conversation into a few
  long-term notes.
- **Auto-titled conversations**: the first reply of every conversation generates its title.

### A personality you can rewrite

- Name, tone, rules and boundaries are Markdown. Change them and they take effect immediately.
- The model can **tidy its own memory section** — it only ever overwrites the part it wrote; text
  you wrote by hand stays on top, byte for byte.

### Liquid Glass — one visual language

- Every pane uses one recipe: **light blur + edge refraction + chromatic dispersion + inner
  highlight + soft outer shadow**. Pure white canvas, no colour tints.
- **No second sheet of glass inside another sheet**: buttons nested in a pane are invisible at rest
  and only surface their refraction and scale while pressed.
- Every control responds for real — press scale, lean toward the finger, gently nudging its
  neighbours.

### The features a chat app should have

- **Long-press a message** → copy / quote / edit-and-resend / regenerate / retract.
- **Long-press the text** → select / copy / select all (system text selection).
- Attachments: image / video / file. Models with vision can see the image you send.
- Image generation: configure an image endpoint, then just say "draw me …". A glass placeholder
  animates while it works, and the picture eases from blurred to sharp.
- Web search: optional. When enabled the request body gains exactly one entry,
  `tools:[{"type":"web_search"}]` — the search happens upstream, the client still makes **one** call.

## Screenshots

| Home | Chat |
| --- | --- |
| <img src="assets/screenshots/home.png" width="330"> | <img src="assets/screenshots/chat.png" width="330"> |

| Files (persona & memory) | About |
| --- | --- |
| <img src="assets/screenshots/files.png" width="330"> | <img src="assets/screenshots/about.png" width="330"> |

## Memory and persona: a set of Markdown files

```
filesDir/md/
├── IDENTITY.md   who I am        ← injected every turn
├── SOUL.md       how I speak     ← injected every turn
├── AGENTS.md     rules & bounds  ← injected every turn
├── USER.md       about you       ← on demand
├── MEMORY.md     long-term       ← hot pointer every turn, the rest on demand
├── CAPABILITIES.md  what I can and cannot do (for humans, not injected)
└── memory/       archive (not injected, retrieved on demand)
    facts · decisions · lessons · projects · references · tasks · temporary
```

Three-tier reading, which is where the token savings come from:

1. **Must read** — `IDENTITY` / `SOUL` / `AGENTS`: persona and rules are always present.
2. **Hot pointer** — the summary block at the top of `MEMORY.md`, hard-capped at 900 characters.
3. **On demand** — `USER.md`, the rest of `MEMORY.md`, and the `memory/` archive. The client first
   matches your own words character-wise; when the model needs deeper recall it emits a retrieval
   marker and the archive opens.

## Model setup

- **Protocol**: OpenAI-compatible (`/chat/completions` streaming, `/models`, `/images/generations`).
- **Providers**: as many as you like; each stores its own base URL, key and model list, with a
  "test connection" button.
- **Default model / chat settings**: model, reasoning tier, usage display, user avatar, per-message
  timing and token counts.
- **Side capabilities**: web search, image generation, video, OCR — all **optional** and separately
  configured; leave them empty and they cost nothing per turn.

## Tech stack

| Layer | Choice |
| --- | --- |
| Language / UI | Kotlin 2.4.20 · Jetpack Compose (BOM 2026.09.00) |
| Build | AGP 9.4.1 · Gradle 9.7.1 · JDK 17 |
| Minimum | Android 8.0 (API 28); `compileSdk 37` / `targetSdk 35` |
| Network / serialization | `HttpURLConnection` + hand-written SSE parsing · kotlinx.serialization |
| Liquid Glass | `io.github.kyant0:backdrop` + `io.github.kyant0:shapes` + `dev.chrisbanes.haze` |
| Storage | local JSON (providers / conversations) + plain-text Markdown (persona & memory) |

## Project layout

```
app/src/main/java/ai/lotus/app/
├── MainActivity.kt              entry point
├── data/                        data layer, UI-free
│   ├── LlmApi.kt                OpenAI-compatible client (SSE)
│   ├── Provider.kt              providers / model list / connection test
│   ├── Conversation.kt          conversations and messages
│   ├── Prompts.kt               prompt injection · tiered memory · recall
│   ├── ImageGen.kt              /images/generations
│   ├── Capabilities.kt          search / image / video / OCR config
│   ├── ThinkingTiers.kt         real reasoning-tier mapping
│   └── UpdateChecker.kt         GitHub Releases update check
└── ui/
    ├── LotusApp.kt              shell / routing / fixed layers
    ├── glass/                   ★ glass infrastructure (Glass.kt is the single source of truth)
    ├── home/                    home · composer · overlays
    ├── chat/                    chat · bubbles · generation placeholder
    ├── nav/                     bottom bar (chat / files / settings)
    ├── settings/                settings
    ├── md/                      Markdown workspace (persona & memory)
    └── model/                   model catalogue
```

## Build and run

```bash
# JDK 17 and an Android SDK with compileSdk 37
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On first launch: **Settings → Providers → Add**, enter the base URL (up to `/v1`) and your API key,
tap **Test connection** to pull the model list, then pick a model on the home screen and chat.

## Versions and updates

- **Settings → About → Check for updates** reads the `latest` GitHub Release tag and compares it with
  the installed version. If there is something newer you get a "Go download" button that opens the
  release page — the app never installs an APK behind your back.
- To ship: keep `versionName` in `app/build.gradle.kts` (e.g. `2026.10.18`) in step with the release
  tag (`v2026.10.18`). The app picks it up automatically.

## Artwork

Every illustration and design file we use lives in one place:

| Mascot | Thinking | Reading MD |
| --- | --- | --- |
| <img src="assets/gallery/01-mascot.png" width="230"> | <img src="assets/gallery/02-thinking.png" width="230"> | <img src="assets/gallery/03-md-reading.png" width="230"> |

| App icon | Home design sheet |
| --- | --- |
| <img src="assets/gallery/04-app-icon.png" width="230"> | <img src="assets/gallery/05-home-approved.png" width="330"> |

## License

Released under the **Apache License 2.0** — see [LICENSE](LICENSE).
Third-party dependencies and assets are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Credits

Liquid Glass is not something a single `blur()` call can produce. The refraction, dispersion and
blur all stand on these upstreams:

| Project | Used for | License |
| --- | --- | --- |
| [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) (`io.github.kyant0:backdrop` / `shapes`) | refraction / dispersion / blur / highlight / shadow | Apache-2.0 |
| [chrisbanes/haze](https://github.com/chrisbanes/haze) | blur foundation | Apache-2.0 |

---

**A new build every 7 days.**
