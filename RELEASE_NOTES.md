# WebTools 2.4 — downloads

## In this release

- **A page can hand you a file now.** Until this release a link to a PDF, a picture served as an
  attachment, or a calendar file did nothing at all: the engine asked the app to take it and
  the app had no hands. Now it saves to the phone, with the name the server gave it, and the
  status line counts up while it does.
- **DOWNLOADS**, on the shelf's bar between ADD and SETTINGS. Newest first, with the kind, the
  size and the day. Tap a PDF, a picture or a text file and the engine shows it, full screen,
  with the pull-down as on any page: MAKE A TICKET works on a downloaded ticket. Tap anything
  else and the phone is offered it; if nothing on the phone takes it, the app says so. Hold a
  row and the bar asks once before removing.
- **The engine switch left the search field.** It was one tap from the wrong result. Settings is
  the only place the engine changes.

## From 2.3

- Settings, the report chip from light-common, BACK and FORWARD at the top of the pull-down,
  MAKE A TICKET from the page's own pixels, OPEN THE PAGE back to the live page.

## Known limits

- First launch is slower: the engine starts and the two extensions install, once.
- A page that wants a popup gets nothing. A page that wants location, camera, or notifications
  gets a no.
- Bundled tools (Split, Convert) do not keep their last values between launches on this engine.
