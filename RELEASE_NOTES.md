# WebTools 3.4 — the extensions, and reports that arrive

## In this release

- **A report too long for the tracker was thrown away.** GitHub refuses an issue body over 65,536
  characters, and the queue read that refusal as "this can never be sent" and deleted the file.
  A page log full of 700-character sign-in URLs plus a screenshot clears that on a bad day, so
  reports left the phone and appeared nowhere. Addresses in the log are now cut at the query
  (`login.axs.com/Account/Login?[612 chars]`), the log is capped, and light-common 1.10 trims a
  body that is still too long instead of losing it.
- **uBlock Origin was not installed at all**, on any 3.x build, and nothing said so. The bundled
  copy still carried the signed xpi's `META-INF`, which cannot verify once unpacked. It is gone.
  A report now names the extension that failed and what the engine said about it.
- **The bridge connected only if the app happened to be ready first.** Its background page opens
  the native port the moment the engine starts, which on every launch after the first is before
  the app has a delegate for it — the port was then lost for the life of the app, and every
  feature that needs it (a carried-over login, the article for the Library, a 2FA code, the
  request trail) silently did nothing. It retries now, and reconnects if the app goes away.

## Known limits

- First launch is slower: the engine starts and the two extensions install, once.
- A page that wants a popup gets nothing. A page that wants location, camera, or notifications
  gets a no.
- Bundled tools (Split, Convert) do not keep their last values between launches on this engine.
