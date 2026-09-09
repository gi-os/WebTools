# WebTools

A shelf of web pages for the Light Phone III. Each page opens on its own, stays inside its own
site, and can keep an offline copy. There is no address bar. It is not a browser.

It exists for the pages you need four times a year. A ticket, a train status, a form, a check-in
page. None of those deserved an APK.

## What it does

- **One list.** Tap a name and the page opens full screen. Hold a name to see its settings.
- **Each tool stays inside its site.** A tool for `ticketmaster.com` can reach
  `auth.ticketmaster.com` and nothing else. A link to any other site is a dead end, with a note at
  the bottom of the screen. This is the rule that keeps the app from becoming a browser.
- **Offline copies.** Turn on *Keeping a copy* and the app saves the page after every visit. With
  no signal, the saved copy opens instead. Open a ticket at home and it is on the phone at the gate.
- **Pull down to get back.** From the top of any page, pull down and let go. A line grows as you
  pull. When it reads TOOLS, lifting your thumb returns to the list. Pull sideways and nothing
  happens.
- **The wheel scrolls.** Any page, any list. The camera button goes back one page. Press it again
  for the list.
- **Two tools ship inside:** a bill splitter and a unit converter. Both work with no network.
- **Sites see Chrome.** The WebView drops its `; wv` user agent, its `Android WebView` client hint
  and the `X-Requested-With` header. Ticketmaster's bot check refuses all three.

## Adding a tool

Three ways, all from ADD:

1. **Scan a code.** Open <https://gi-os.github.io/WebTools/> on a computer. Type a name and an
   address, choose whether to keep a copy, and point the phone at the code. The page draws the
   code in your browser and sends nothing anywhere.
2. **Type an address.** `mta.info` is enough.
3. **Take a starter.** Subway status, weather, Ticketmaster, Wikipedia.

A code from the companion page is JSON:

```json
{"wt":1,"n":"Tickets","u":"https://www.ticketmaster.com/member/tickets","o":["ticketmaster.com","livenation.com"],"keep":true}
```

`o` lists the sites the tool may visit. A bare `https://` address in a code works too.

## Three kinds of tool

| Kind | Where it lives | Works with no signal |
|---|---|---|
| Bundle | HTML in the app, served from `https://<id>.webtools.internal/` | Always |
| Site | A web address, opened inside its allowlist | No |
| Site with a saved copy | The same address, plus an MHTML archive on the phone | Yes |

Bundles get their own https origin through `WebViewAssetLoader`, so each one has its own
`localStorage` and none can read another's. Every bundle can load `/_light/light.css`, the house
stylesheet: black ground, white type, one gray, no color, no motion.

## Install

Take the APK from [Releases](https://github.com/gi-os/WebTools/releases), or install it through
BrightMarket or Obtainium. The app asks for the camera once, the first time you scan a code.

## Versions

Versioning is `v1.x.x`. CI stamps the patch number from the build.

### v1.0.0

- The list, the tool page, the pull-down back to the list, wheel scrolling, camera button back.
- Site tools with a per-tool allowlist and a blocked-link notice.
- Saved copies (MHTML) with refresh on every live visit and fallback when a load fails or the
  phone is offline.
- Add by QR code, typed address, or starter. Companion page for making codes.
- Two bundled tools: Split a bill, Convert units. House stylesheet at `/_light/light.css`.
- Five-page tutorial on first run, again from HELP.
- `webtools://open/<id>` opens one tool.

## Build

CI builds and releases on every push to `main`. Unit tests (origin rule, code parser, exit
gesture) gate the build. The signing key sits in the repo, on purpose, and counts as public.
Android identifies the app by package plus certificate. The fingerprint check in CI is the
protection, not a hidden key.

This is a plain sideloaded APK, not a Light SDK tool. The SDK sandbox bans
`android.content.Context`. A WebView cannot exist without one.

## Not in v1

- No shake-to-report. The rest of the family pulls `light-common` from GitHub Packages, which a
  new repo cannot resolve without secrets. It comes back once the repo has them.
- No credential vault. Logins live in the WebView's cookie jar, which persists across launches.
- No per-tool launcher icons. One icon, one list.

License: MIT.
