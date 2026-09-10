# WebTools 2.1 — a field that searches

The shelf holds what you come back to. The field above it is for everything else.

## In this release

- **One field at the top.** Type an address and it opens. Type words and they go to a search
  engine. The app keeps nothing you type there unless you pull down and choose KEEP ON SHELF.
- **Three engines, none of them Google.** DuckDuckGo (its plain HTML results, which suit a
  small black-and-white panel), Ecosia, and Kagi. The engine's name sits at the right end of the
  field. Tap it and it is the next one. The choice sticks.
- **Kagi wants an account.** Sign in once on the phone, or bring the login over by code from
  gi-os.github.io/WebTools, like any other site.
- **GO is gone.** The field does what GO did, and more. The action bar is ADD and HELP.

## What counts as an address

A scheme (`https://`) settles it. So does one word with a dot and a real ending: `mta.info`,
`en.wikipedia.org/wiki/Cat`, `localhost`. Anything with a space is a search, so "what is 3.5
inches in cm" goes to the engine.

## Still true from 2.0

- Firefox's engine (GeckoView 148), uBlock Origin and strict tracking protection, always on.
- The pull-down menu: SAVE A COPY, READER VIEW or FULL PAGE, SET AS HOME or KEEP ON SHELF, and
  TOOLS at the deepest pull.
- Shake the phone to file a report.

## Known limits

- First launch is slower: the engine starts and the two extensions install, once.
- A page that wants a popup gets nothing. A page that wants location, camera, or notifications
  gets a no.
- Bundled tools (Split, Convert) do not keep their last values between launches on this engine.
