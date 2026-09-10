# WebTools 2.3 — Settings, and the family's report chip

## In this release

- **SETTINGS.** On the shelf's bar, next to ADD. Rows, each a fact with the tap that changes it:
  the search engine (DuckDuckGo, Ecosia, Kagi), how long a page stays warm after you leave (1, 2,
  5, 15 or 30 minutes; a ticket page keeps its hour), Send feedback, a live shake readout, the
  tutorial, and the install id under the version. HELP moved in here.
- **The report chip.** Shake-to-report is now the copy every Bright app carries,
  `com.gios:light-common`. A shake raises a small chip in the corner; tap it for the sheet, BUG
  or IDEA, a note, a screenshot you can refuse, SEND. A crash offers itself on the next launch.
  The library gained a hook for this app, so the page log, the wall, the engine version and the
  bridge's state still ride in the issue.
- **Shake readout.** "I shook it and nothing happened" now has a number: Settings shows the
  force in g, the peak, and how many turns of the gesture it counted.
- **Pictures of the page are no longer black.** The page draws into a TextureView, so the chip's
  screenshot and MAKE A TICKET's picture show the page and not the hole where it was.

## From 2.2

- A page left in the background parks after the grace and re-opens where it was. The process
  leaves five minutes after that. MAKE A TICKET sends the page to Movie Tickets and keeps it warm
  for an hour.

## Known limits

- First launch is slower: the engine starts and the two extensions install, once.
- A page that wants a popup gets nothing. A page that wants location, camera, or notifications
  gets a no.
- Bundled tools (Split, Convert) do not keep their last values between launches on this engine.
