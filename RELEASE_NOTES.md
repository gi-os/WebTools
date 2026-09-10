# WebTools 2.2 — a page that knows when to sleep

## In this release

- **The battery.** A page left open in the background kept Firefox's engine running its scripts
  all night. Now a page that leaves the screen parks after two minutes: its session closes, the app
  keeps its address, and coming back re-opens it where it was. Five minutes after that, once
  nothing is still writing, the app leaves memory. The next launch is a cold one, a second or
  two. One process cannot stop and restart the engine, so leaving is the only way to put it down.
- **MAKE A TICKET.** A new row in the pull-down on any site. It takes a picture of the page and
  hands it to Movie Tickets (BrightPasses 1.19 or later), which reads it the way it reads a
  photographed stub. The pass keeps a `webtools://` address back to this page, and OPEN THE
  PAGE on the pass returns here.
- **An hour of warmth.** A page that made a ticket stays open in the background for an hour
  instead of two minutes. Ticketmaster's barcode rotates every fifteen seconds, so the picture is
  a reminder and the live page is what the gate scans. Coming back within the hour keeps the
  rest of it.
- The row is only there when Movie Tickets is on the phone.

## From 2.1

- The search field at the top of the shelf: addresses open, words go to DuckDuckGo, Ecosia or
  Kagi. Tap the engine's name to switch.

## Known limits

- First launch is slower: the engine starts and the two extensions install, once.
- A page that wants a popup gets nothing. A page that wants location, camera, or notifications
  gets a no.
- Bundled tools (Split, Convert) do not keep their last values between launches on this engine.
