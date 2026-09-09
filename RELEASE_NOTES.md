# WebTools 2.0 — a browser for the Light Phone, on Firefox's engine

WebTools 1.x was a shelf of pages on the phone's own WebView. That WebView is Chromium 113, and a
sign-in page that runs a bot check (Ticketmaster does) refuses it. The phone has no browser to
fall back to. So 2.0 ships an engine of its own.

## In this release

- **Firefox's engine (GeckoView 148) inside the app.** Sites see a real browser. Nothing here
  pretends to be anything. The APK is about 108 MB now, and it carries its own engine forward.
- **uBlock Origin, built in, always on.** Plus Firefox's own strict tracking protection. There
  is no switch.
- **GO.** One field on the shelf: an address, opened once, not kept. No suggestions, no history,
  no search engine behind the field. The shelf is still the front door.
- **The pull-down menu.** From the top of any page, pull down and a menu draws out of the top
  edge, one row at a time; let go on a lit row. TOOLS, SET AS HOME (KEEP ON SHELF for a GO page),
  READER VIEW, SAVE A COPY.
- **Set as home.** The page you are on becomes the tool's start page.
- **Reader view** per tool: text only, for articles.
- **Saved copies are PDFs** the engine prints, opened in its own viewer with no signal.
- **Bring a login** now sets cookies through a bundled extension. Firefox's engine passes most
  sign-in checks on its own. The code is for the ones it does not.
- **Sign out of this site** on the tool's page clears its cookies.
- The shake report now names the engine and the state of the bridge extension.

## Fixed since 2.0.5

- A page crashed the app the moment it loaded. Gecko's content process shares the app's
  startup code, and the engine was being created twice, once where it must not be. Now the
  engine starts in the main process only. A crash now files its own report on the next launch.
- The download was 202 MB with the engine's libraries stored flat. Compressed, it is 108 MB.

## Known limits

- First launch is slower: the engine starts and the two extensions install, once.
- A page that wants a popup gets nothing. A page that wants location, camera, or notifications
  gets a no.
- Bundled tools (Split, Convert) do not keep their last values between launches on this engine.
