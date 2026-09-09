# WebTools v1.0 — first release

A shelf of web pages for the Light Phone III. Each one opens by itself, stays inside its own
site, and can keep an offline copy. There is no address bar.

## In this release

- **One list.** Tap to open, hold for settings. Two tools ship inside: Split a bill and Convert
  units. Both work with no network.
- **Each tool stays inside its site.** Links to other sites are dead ends, with a note at the
  bottom of the screen.
- **Saved copies.** Turn on *Keeping a copy* and the app saves the page after every visit. With
  no signal, or when a load fails, the saved copy opens.
- **Pull down to get back.** Pull from the top of any page and let go once the line reads TOOLS.
  You are on the list again.
- **The wheel scrolls.** The camera button goes back.
- **Three ways to add:** scan a code from <https://gi-os.github.io/WebTools/>, type an address,
  or take a starter (subway status, weather, Ticketmaster, Wikipedia).
- Sites see a phone running Chrome, not an embedded WebView. Ticketmaster's bot check refused the
  stock WebView with a "browsing activity paused" page.
- A five-page tutorial on first run, again from HELP.

## Known limits

- No shake-to-report yet. It arrives with the `light-common` dependency in a later build.
- Logins live in the WebView cookie jar. There is no vault.
- Pages with heavy scripts may not rebuild well from a saved copy. Static pages and ticket
  views do.
