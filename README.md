# WebTools

A browser for the Light Phone III, built for the Light ethos, on Firefox's engine. It can be
*the* browser: any link another app opens lands here, and a hotel Wi-Fi's sign-in page has
somewhere to appear.

It has two halves. The **shelf** is a short list of web pages you need now and then: a ticket, a
train status, a form you fill twice a year. Each opens full screen and stays inside its own site.
The **field** at the top is the other. Type an address and it opens. Type words and they go to
DuckDuckGo, Ecosia or Kagi. No feed, no suggestions, no history you did not ask for, no tabs.

## What it does

- **One list.** Tap a name and the page opens full screen. Hold a name to see its settings. Each
  row carries its number, so a tool can be named out loud before it is read.
- **Folders.** Tools filed under the same name show as one row — *Tickets · 3 tools* — which opens
  to its own list. A folder is only a name written on its tools: nothing to create, and nothing
  left behind when the last tool leaves. Ticketmaster, AXS and DICE arrive filed together.
- **One field.** Type an address and it opens. Type anything else and the search engine gets it:
  DuckDuckGo, Ecosia or Kagi, chosen in Settings. Links may go anywhere. The app keeps nothing unless you pull down and choose
  *Keep on shelf*.
- **A shelf page stays inside its site.** A tool for `ticketmaster.com` can reach
  `auth.ticketmaster.com` and nothing else. A link elsewhere is a dead end, with a note at the
  bottom of the screen and an *Allow* row on the tool's page.
- **The pull-down menu.** From the top of any page, pull down. A menu unrolls from the top edge,
  the way a Sailfish pulley does, one row at a time, each lit as it comes; let go on a lit row.
  The first rows are BACK and FORWARD, when the page has them. SAVE A COPY keeps the page as a PDF. MAKE A TICKET sends a picture of the page to Movie
  Tickets (BrightPasses) and keeps the page ready for an hour. READER VIEW shows the text only. SET AS HOME makes the
  current page the tool's start page. TOOLS is last, so the longest pull always goes back to the
  list.
- **uBlock Origin and strict tracking protection**, always on. "Open in the app" banners are
  hidden by the class names the vendors use.
- **Saved copies.** Turn on *Keeping a copy* and the engine prints the page to a PDF after every
  visit. With no signal, the copy opens instead. Open a ticket at home and it is on the phone at
  the gate.
- **The wheel scrolls.** Any page, any list. The camera button goes back one page, then to the
  list.
- **Two tools ship inside:** a bill splitter and a unit converter. Both work with no network.
- **Bring a login from a computer.** A few sign-in pages refuse anything but a browser they know.
  Firefox's engine passes most. For the rest: sign in on a computer, paste the page's *Copy as
  cURL* into the companion page, scan the code it draws. The cookies travel compressed, in parts
  if needed, and the tool opens signed in.
- **Shake to report.** A shake raises a chip in the corner; tap it for the sheet, BUG or IDEA,
  SEND. The issue lands in the private tracker with the page log, the engine version and a
  screenshot. The same reporter every Bright app carries (`com.gios:light-common`).
- **Downloads.** A link to a PDF, a picture or a file the page will not show is saved to the
  phone and listed under DOWNLOADS. PDFs, pictures and text open in the engine; anything else
  is offered to the phone. Hold a row to remove it.
- **Send to library.** The article, lifted out by Readability inside the page, written as a
  one-chapter EPUB and handed to BrightLibrary. Long reads belong in a reader.
- **2FA code.** Authenticator is asked for this site's code (its PIN first, if set); the six
  digits are typed into the page's code field, one-digit boxes included. No secret crosses.
- **Passkeys.** Firefox's engine talks to Android 14's Credential Manager (no Google needed).
  The phone needs a provider: Bitwarden, set as `credential_service` by BrightControl's GRANT
  ALL. Bitwarden asks once whether to trust Web Tools as a browser. Settings › Passkeys says
  where things stand.
- **The phone's browser.** Settings asks the system to make Web Tools the browser. A link from
  BrightNews, Movie Tickets or a message opens inside the shelf tool whose wall covers it, or as
  a searched page.
- **Wi-Fi sign-in.** Hotel and café Wi-Fi shows its page here. The app binds to that network,
  loads the gate, and probes a 204 endpoint until it opens; if the system asked, it is told. A
  VPN blocks this for every app but the platform's own; BrightControl knows that route.
- **Settings.** The search engine, how long a page stays warm after you leave, the tutorial,
  Send feedback, the shake readout, and the install id.

## Why an engine of its own

The phone's WebView is Chromium 113 and Light decides when that changes. Bot checks refuse it,
it cannot rewrite its client hints, and there is no browser on the phone to hand a page to.
GeckoView is Firefox's engine as a library: current, independent of the phone, and a real browser
to every site. It costs about 100 MB of download and a slower first launch. It buys a page that
works.

## Adding a tool

Three ways, all from ADD:

1. **Scan a code.** Open <https://gi-os.github.io/WebTools/> on a computer. Type a name and an
   address, choose whether to keep a copy, and point the phone at the code. The page draws the
   code in your browser and sends nothing anywhere.
2. **Type an address.** `mta.info` is enough.
3. **Take a starter.** Subway status, weather, Ticketmaster, Wikipedia.

A code from the companion page is JSON:

```json
{"wt":1,"n":"Tickets","u":"https://www.ticketmaster.com/","o":["ticketmaster.com","livenation.com"],"keep":true}
```

`o` lists the sites the tool may visit. `keep: true` saves a copy after each visit. A bare
`https://` address in a code works too.

A login code adds `"k":"login"`, a cookie domain `d`, and `c`, which holds a `Cookie:` header
compressed with deflate-raw and base64url-encoded. A code too big for one image is split:
`{"wt":1,"k":"part","id":"x7","i":1,"n":3,"p":"…"}`, and the phone joins the parts in order.

## Three kinds of tool

| Kind | Where it lives | Works with no signal |
|---|---|---|
| Bundle | HTML in the app, at `resource://android/assets/builtin/<id>/` | Always |
| Site | A web address, opened inside its allowlist | No |
| Site with a saved copy | The same address, plus a PDF the engine printed | Yes |
| Searched page | A typed address or a search result, no allowlist, not kept | No |

Every bundle loads `light.css`, the house stylesheet: black ground, white type, one gray, no
color, no motion.

## The bridge extension

GeckoView has no `CookieManager` and no `evaluateJavascript`. `assets/bridge/` is a small
WebExtension the app talks to over a native-messaging port: it sets a carried-over login
(`browser.cookies.set`), can answer a probe from inside the page, and hides app banners with a
content script. uBlock Origin sits beside it in `assets/ublock/`, the Firefox build
unpacked, installed with `ensureBuiltIn`.

## Install

Take the APK from [Releases](https://github.com/gi-os/WebTools/releases), or install it through
BrightMarket or Obtainium. The app asks for the camera once, the first time you scan a code.

## Versions

Versioning is `v1.x.x`. CI stamps the patch number from the build.

### v2.0.7

- Engine created in the main process only. Gecko's child processes run the same
  `Application.onCreate`, and a runtime inside one killed the page on load.
- `CrashLog`: an uncaught exception files itself on the next launch.
- Native libraries compressed in the APK: 108 MB, not 202.

### v3.0.0

- Two typefaces: the phone's own for content, monospace for every label, numeral and bar word.
- The pull-down marks the picked row (a short white bar at the left edge, LET GO on the right)
  instead of filling it white; rows are 44dp with the deadzone at 40dp.
- Loading is the page, dimmed, with a 2px line along the top edge. The page does not move.
- Folders (`Tool.folder`, `data/Shelf.kt`), an index down every list, label–value grids on Info,
  fact rows with their state on the right.
- The companion page's Bring-a-login section is gone; it has a Folder field instead.

### v2.7.0

- Passkeys: `CREDENTIAL_MANAGER_SET_ORIGIN` and `CREDENTIAL_MANAGER_QUERY_CANDIDATE_CREDENTIALS`
  declared (both normal). Settings › Passkeys reads `credential_service` and says what to do.

### v2.6.0

- SEND TO LIBRARY: Readability.js rides in the bridge extension, the article comes back as XHTML,
  `web/Epub.kt` writes the book, `ACTION_SEND application/epub+zip` to `com.lightfastread`
  (BrightLibrary 1.19+).
- 2FA CODE: `com.gios.lightauth.PICK_CODE` for a result (BrightAuthenticator 1.3+), the code
  typed by the bridge into the page's code field.
- The pull-down's rows shrink when there are many, so TOOLS is always within reach (never under
  40dp a row).

### v2.5.0

- Browser role: `http`/`https` `BROWSABLE` filter; Settings row asks `RoleManager` for
  `ROLE_BROWSER`. A link opens in the tool whose wall covers it, else as a searched page.
- Wi-Fi sign-in (`web/Portal.kt`): `CAPTIVE_PORTAL` action, `bindProcessToNetwork`, 204 probe
  every 4 s, `reportCaptivePortalDismissed`. Explains a VPN refusal instead of spinning.

### v2.4.0

- Downloads: `onExternalResponse` saves to `files/downloads/`, a DOWNLOADS screen on the shelf
  bar lists them, PDFs and pictures open in the engine, other types by content URI.
- The engine switch left the search field; Settings is the only place it changes.

### v2.3.0

- BACK, then FORWARD, lead the pull-down when the page has them: the shortest pull goes one page back.
- SETTINGS on the shelf's bar: search engine, how long a page stays warm (1 to 30 minutes),
  Send feedback, the shake readout, the tutorial, the install id.
- Shake-to-report is the family's shared one (`light-common` 1.9.1): a chip, then the sheet.
  The page log still rides along.
- MAKE A TICKET's picture comes from the engine (`capturePixels`), so it is the page and not a
  black window. OPEN THE PAGE on the pass returns to that page, live, via `webtools://open/<id>?u=`.

### v2.2.0

- A page left in the background parks after two minutes and re-opens where it was when you
  come back. The process leaves five minutes later. This is where the battery went.
- MAKE A TICKET in the pull-down: a screenshot of the page goes to Movie Tickets, with a
  `webtools://` address back to the page. That page stays ready for an hour.

### v2.1.0

- A search field at the top of the shelf replaces GO. Addresses open. Words go to DuckDuckGo,
  Ecosia or Kagi. Tap the engine's name to switch, and the choice sticks.

### v2.0.0

- GeckoView 148 replaces the WebView. uBlock Origin and strict tracking protection built in.
- GO: one address, once. Pull-down pulley menu: Tools, Set as home / Keep on shelf, Reader view,
  Save a copy.
- Saved copies are PDFs (`saveAsPdf`). Bring-a-login and the probe go through the bridge extension.
- Custom Tabs, the `; wv` user-agent work and the host block list are gone with the WebView.

### v1.2.0

- Bring a login: cookies from a computer's *Copy as cURL*, packed into one or more codes.
- The *Opens in* switch shows only when the phone has a browser. Ticketmaster starter is a plain
  site again.
- Stock user agent when the WebView cannot rewrite its client hints (Chromium 113 on the LP3).
- Report lists browsers and WebView features.

### v1.1.0

- Opens in: Chromium (Custom Tab) per tool. Ticketmaster starter defaults to it.
- Gate detection: a "browsing paused" title names the switch to flip.
- Pull-down fixed: the frame overrides `requestDisallowInterceptTouchEvent`, and a stroke from the
  top band of the screen may pull regardless of scroll.
- Ad block and app-banner hiding. `window.chrome` stub at document start.
- The tool follows and remembers a first-load redirect to another host. Allow row for a blocked host.
- Shake to report, into `gi-os/light-reports`, with the page log and a JS probe.

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

This is a plain sideloaded APK, not a Light SDK tool. The SDK sandbox allows none of the
dependencies an engine needs.

## Not here

- No credential vault. Logins live in the engine's cookie jar, which persists across launches. A
  login code is a one-time transfer, not a stored password.
- No per-tool launcher icons. One icon, one list.
- Shake-to-report comes from `com.gios:light-common` 1.9.0 (GitHub Packages; CI reads it with
  `GITHUB_TOKEN` and `packages: read`). The page log rides in the issue through
  `LightReport.details`, a hook added to the library for this app.

License: MIT.
