<p align="center">
  <img src="assets/banner.png" alt="Lotus AI" width="100%">
</p>

<h3 align="center">Liquid Glass · An AI that remembers</h3>

<p align="center">
  <a href="README.md">中文</a> · <b>English</b>
</p>

---

**Lotus AI** is an AI chat app for Android. The whole interface is one sheet of liquid glass, and
the conversation itself is clean, quick, and effortless.

It remembers you, understands a persona, and keeps token usage as low as it can.

## Why it feels light

- **Light.** One question = one model call. The request carries your persona, your memory, and
  what you actually said — nothing else slowing it down. Every token spent is visible.
- **Fast.** Streaming answers, shown as they're written. Reasoning is kept, and the effort levels
  follow the model — as many as it really supports, no more, no less.
- **Cheap.** Live context usage, per-conversation trimming and compression; compression only
  touches the summary, never deletes a single original message.
- **It remembers.** Long-term memory lives in a `MEMORY.md` you can open and edit.
- **It feels like someone.** Persona and tone are plain text. Change them whenever you like.

## The interface: liquid glass throughout

- One recipe everywhere: **light blur + edge refraction + chromatic dispersion + white inner rim
  + soft outer shadow**, on pure white, with no colour.
- No second piece of glass inside a piece of glass: embedded buttons are invisible at rest and
  only surface a refraction while pressed.
- Every control responds — press-scale, edge refraction, neighbouring glass swaying slightly.

## Memory and persona: a set of Markdown files you can edit

Persona and memory are plain text, editable right inside the app:

```
filesDir/md/
├── IDENTITY.md   who I am           ← injected every turn
├── SOUL.md       how I speak        ← injected every turn
├── AGENTS.md     rules and limits   ← injected every turn
├── USER.md       about you          ← injected every turn
├── MEMORY.md     long-term memory   ← injected every turn
└── memory/       archive (not injected)
    facts · decisions · lessons · projects · references · tasks · temporary
```

- **Automatic memory**: the model writes what's worth keeping as one hidden marker, and the client
  strips it out of **the same reply** and appends it to `MEMORY.md` — no second request, no extra
  tokens. If you say "remember this", it is actually saved.
- **Manual memory**: `⋯ → Remember this` in a chat condenses the conversation into a few
  long-term memories.
- **Conversation titles**: auto-generated once, after the first reply.
- **Persona**: rename it, change its tone, change its rules — all in your own Markdown.

## Chatting

- **Long-press any message** → copy / quote / edit & resend / regenerate / withdraw.
- Images: a picked image is really sent with your message; models that can see, see it.
- Image generation: fill in a service under *Default model → Side capabilities*, then just ask.

## Models

- **OpenAI-compatible.** Provider, base URL and key are all yours to fill in; the app ships with
  no vendor baked in.
- Thinking levels map to what the model actually supports (`medium` / `high` / `xhigh` … up to
  `max` / `ultra`).
- Web search is optional: turning it on adds a single `tools:[{"type":"web_search"}]` field and
  lets the upstream do the searching — still one call from the client.

## Screenshots

| Home | Chat |
| --- | --- |
| <img src="assets/screen-home.png" width="330"> | <img src="assets/screen-chat.png" width="330"> |

| Files (persona & memory) | About |
| --- | --- |
| <img src="assets/screen-files.png" width="330"> | <img src="assets/screen-about.png" width="330"> |

## Credits

Liquid glass is not something a single `blur()` gets you. Thanks to:

| Dependency | Role | License |
| --- | --- | --- |
| [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) (`io.github.kyant0:backdrop` / `shapes`) | refraction / dispersion / blur base | Apache-2.0 |
| [chrisbanes/haze](https://github.com/chrisbanes/haze) | blur base | Apache-2.0 |

## Download

Currently a **fast-test build** (debug-signed).

---

**A new build every 7 days.**
