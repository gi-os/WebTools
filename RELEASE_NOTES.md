# WebTools 3.5 — a code read by the camera, and a browser row that does something

## In this release

- **Roll can send a code straight here.** The camera is the scanner on this phone: it is already
  pointed at things, and a code on a computer screen is read from across a desk rather than from
  inside the app that will use it. Scan a tool code in Roll's QR mode and the row says ADD TO WEB
  TOOLS. The payload arrives whole on `webtools://code` and goes down the same road as ADD's own
  scanner — same parser, same words when it is malformed, same behavior for a login and for one
  part of a split code. Roll does not read it; this app does, as it always did.
- **A scanned link opens here by name.** Roll asks for this app rather than for whatever handles
  `https`, which on a phone with no browser was nothing at all.
- **Set as default browser now asks BrightControl to do it.** The role dialog does not exist on
  LightOS and neither does the Default apps screen, so the row's last resort was a sentence telling
  you to go and find GRANT ALL somewhere else. It now hands BrightControl the one line that sets
  the role. BrightControl rebuilds that line against the app that sent it, shows it, runs it over
  its own shell, and reads the role holder back — so the row either makes this the browser or says
  why not.
- **The row was also lying.** LightOS answers "this phone has no browser role" to the role manager
  whatever the truth is, so the row read `not yet` even after `cmd role` had handed the role over
  and every link on the phone was already opening here. When the role manager says it does not
  know, the package manager is asked what actually answers a web address instead.

## Known limits

- BrightControl needs its ADB connection up for the browser row to work. Without it the row still
  names the command.
- First launch is slower: the engine starts and the two extensions install, once.
- A page that wants a popup gets nothing. A page that wants location, camera, or notifications
  gets a no.
- Bundled tools (Split, Convert) do not keep their last values between launches on this engine.
