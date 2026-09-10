# WebTools 2.6 — the phone's browser, downloads, Wi-Fi sign-in, the library, 2FA

## In this release

- **SEND TO LIBRARY.** A new row in the pull-down on any page. Mozilla's Readability (the code
  behind Reader View) lifts the article out inside the page; the app writes it as a one-chapter
  EPUB and hands it to BrightLibrary, which opens it like any book. Needs BrightLibrary 1.19 or later;
  the row is only there when the Library is on the phone.
- **2FA CODE.** Another row. The app asks Authenticator for this site's code: its PIN first if one
  is set, then a list with the matching account on top. Tap it and the six digits land in the
  page's code field, one-digit boxes included. No secret leaves Authenticator; one code
  does, once. Needs BrightAuthenticator 1.3 or later.
- **The pull-down fits.** Nine rows at 64dp would run off a 472dp screen and put TOOLS out of
  reach. Rows now shrink when there are many, never under 40dp, so the deepest pull is always
  the exit.

- **Web Tools can be the browser.** Settings › *The phone's browser* asks the system to hand
  over the role. From then on any link another app opens (Movie Tickets, BrightNews, a message)
  lands here. If a shelf tool's wall covers the link, it opens inside that tool, wall and all;
  otherwise it opens the way a typed address does. If the system has no dialog to offer, the
  row says the one ADB line that does the same.
- **Wi-Fi sign-in.** Hotel and café Wi-Fi answers every request with its own page until you fill
  it in, and this phone had nothing to fill it in with. Settings › *Wi-Fi sign-in* (or the
  system's own "sign in to network" flow, which now finds this app) binds the app to that
  network, shows the gate, and asks a known address every four seconds until it answers 204. Then
  it tells the system the network is good. A VPN makes Android refuse the bind for every app but
  its own sign-in app; the row says so rather than spinning, and BrightControl knows how to
  reach that app.
- **A page can hand you a file.** A link to a PDF, a picture served as an attachment, or a
  calendar file used to do nothing. Now it saves to the phone under the server's name while the
  status line counts up.
- **DOWNLOADS**, on the shelf's bar between ADD and SETTINGS. Newest first: kind, size, day. Tap
  a PDF, a picture or a text file and the engine shows it, with the pull-down as on any page, so
  MAKE A TICKET works on a downloaded ticket. Anything else is offered to the phone; if nothing
  takes it, the app says so. Hold a row and the bar asks once before removing.
- **The engine switch left the search field.** It was one tap from the wrong result. Settings is
  the only place it changes.

## Unverified on the phone

Whether Gecko's connections follow `bindProcessToNetwork` (they should: Gecko does its
networking in this process on Android), and whether LightOS shows the browser-role dialog.

## From 2.3

- Settings, the report chip from light-common, BACK and FORWARD at the top of the pull-down,
  MAKE A TICKET from the page's own pixels, OPEN THE PAGE back to the live page.

## Known limits

- First launch is slower: the engine starts and the two extensions install, once.
- A page that wants a popup gets nothing. A page that wants location, camera, or notifications
  gets a no.
- Bundled tools (Split, Convert) do not keep their last values between launches on this engine.
