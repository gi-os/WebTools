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
  and the `X-Requested-With` header, and gets a `window.chrome` when it has none.
- **Bring a login from a computer.** Some sign-in pages refuse every embedded view. Ticketmaster's
  runs reCAPTCHA Enterprise and FingerprintJS before it shows a form, and the Light Phone has no
  browser to fall back to. So sign in on a computer, paste the page's *Copy as cURL* into the
  companion page, and scan the code it draws. The cookies travel compressed, in parts if needed,
  and the tool opens signed in.
- **Or, on a phone with a browser, the site opens there.** Hold the tool and set *Opens in*. A
  Custom Tab in the phone's browser, with its cookies. No allowlist and no saved copy there. The
  switch does not show on a phone without a browser.
- **No ads, no "open in the app" banners.** Peter Lowe's list plus the deep-link routers
  (branch.io, app.link, onelink.me, AppsFlyer, Adjust), bundled, refreshed weekly. A stylesheet
  hides the banners by the class names the vendors use.
- **A site that moved is not a dead end.** A server redirect during the first load (tutanota.com to
  tuta.com) adds the new host to the tool. Any other blocked host appears on the tool's page with
  an *Allow* row.
- **Shake to report.** Three shakes, a sheet, SEND. The issue lands in the private tracker with
  the page log, a probe of what the page sees, and a grayscale screenshot.

## Adding a tool

Three ways, all from ADD:

1. **Scan a code.** Open <https://gi-os.github.io/WebTools/> on a computer. Type a name and an
   address, choose whether to keep a copy, and point the phone at the code. The page draws the
   code in your browser and sends nothing anywhere.
2. **Type an address.** `mta.info` is enough.
3. **Take a starter.** Subway status, weather, Ticketmaster, Wikipedia.

A code from the companion page is JSON:

```json
{"wt":1,"n":"Tickets","u":"https://www.ticketmaster.com/","o":["ticketmaster.com","livenation.com"],"e":"browser"}
```

`o` lists the sites the tool may visit. `keep: true` saves a copy after each visit. `e: "browser"`
opens the tool in the phone's browser. A bare `https://` address in a code works too.

A login code adds `"k":"login"`, a cookie domain `d`, and `c`, which holds a `Cookie:` header
compressed with deflate-raw and base64url-encoded. A code too big for one image is split:
`{"wt":1,"k":"part","id":"x7","i":1,"n":3,"p":"…"}`, and the phone joins the parts in order.

## Three kinds of tool

| Kind | Where it lives | Works with no signal |
|---|---|---|
| Bundle | HTML in the app, served from `https://<id>.webtools.internal/` | Always |
| Site | A web address, opened inside its allowlist | No |
| Site with a saved copy | The same address, plus an MHTML archive on the phone | Yes |
| Site in Chromium | The same address, opened as a Custom Tab in the phone's browser | No |

Bundles get their own https origin through `WebViewAssetLoader`, so each one has its own
`localStorage` and none can read another's. Every bundle can load `/_light/light.css`, the house
stylesheet: black ground, white type, one gray, no color, no motion.

## Install

Take the APK from [Releases](https://github.com/gi-os/WebTools/releases), or install it through
BrightMarket or Obtainium. The app asks for the camera once, the first time you scan a code.

## Versions

Versioning is `v1.x.x`. CI stamps the patch number from the build.

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

This is a plain sideloaded APK, not a Light SDK tool. The SDK sandbox bans
`android.content.Context`. A WebView cannot exist without one.

## Not here

- No credential vault. Logins live in the WebView's cookie jar, which persists across launches. A
  login code is a one-time transfer, not a stored password.
- No per-tool launcher icons. One icon, one list.
- Shake-to-report is this app's own small copy, not `light-common`. GitHub Packages has no
  anonymous read, and a new repo has no secrets for it.

License: MIT.
