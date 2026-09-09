# WebTools v1.1 — Chromium for the sites that refuse, ad block, shake to report

## In this release

- **Opens in: Chromium.** Ticketmaster's sign-in sits behind Kasada, and no embedded view passes
  it. Hold a tool, switch it to Chromium, and the page runs in the phone's own browser with its
  own cookies. The Ticketmaster starter comes set that way and starts at the home page.
- **The built-in view names the gate.** When a page title reads "browsing activity paused", the
  status line tells you which switch to flip.
- **Pull down works from anywhere on the page.** The WebView used to claim the stroke first, so
  the gesture only worked from the top edge. Now a pull from the top band of the screen, or
  from a page at its top, goes back to the list.
- **Ad block.** Peter Lowe's list plus the deep-link routers behind "open in the app" banners,
  bundled and refreshed once a week. A stylesheet hides the banners by the class names the common
  vendors use.
- **A site that moved is not a dead end.** tutanota.com redirects to tuta.com. A redirect during the
  first load is the site's own doing, so the tool follows it and remembers the host. Any other
  blocked host shows on the tool's page with an Allow row.
- **Shake to report.** Three shakes raise a sheet. SEND files an issue with the page log, what
  the page sees (user agent, client hints, window.chrome, Kasada present or not), and a
  grayscale picture of the screen. NO sends nothing.

## Known limits

- In Chromium there is no allowlist and no saved copy. The tool row says "in Chromium".
- Pages with heavy scripts may not rebuild well from a saved copy. Static pages do.
