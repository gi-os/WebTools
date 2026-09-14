# WebTools 3.6 — the first launch after a while no longer crashes

## In this release

- **The crash on the first launch after a while is fixed.** Open the app after leaving it alone
  for an afternoon and it died instantly; open it again and it was fine. Two things caused that,
  and both are changed.

  The app kills its own process a few minutes after the page is parked, to hand Firefox's several
  hundred megabytes back to the phone. It killed only the process you can see. The engine runs its
  pages in processes of their own, and those were left for the system to reap whenever it got
  round to it — so the next launch was racing that cleanup. They are killed first now, and this
  process last.

  The engine is also told to shut down now, and given a moment to do it, rather than being cut
  off mid-sentence. That is what stops the mess below from being made in the first place.

  The other half is on disk. The engine keeps a startup cache, and being killed mid-write is how a
  half-written one is left behind. Reading it takes the launch down; the launch after that works
  because the failed one cleared it on the way out. So the app now writes down that a launch has
  started and clears that mark once it is on screen. A launch that finds the mark still set knows
  the last one never arrived, and throws the startup cache away before the engine can read it.
  Whatever was left behind, the launch that follows is clean.

- **An engine that will not start no longer takes the app with it.** If it fails anyway, the
  shelf, Settings and the report sheet all still open, opening a page says what happened instead
  of closing the app, and the reason travels in the bug report.

## Known limits

- BrightControl needs its ADB connection up for the browser row to work. Without it the row still
  names the command.
- First launch is slower: the engine starts and the two extensions install, once. A launch that
  had to throw away the startup cache is slower again, once.
- A page that wants a popup gets nothing. A page that wants location, camera, or notifications
  gets a no.
- Bundled tools (Split, Convert) do not keep their last values between launches on this engine.
