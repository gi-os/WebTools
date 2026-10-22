# WebTools 3.6 — a menu you can tap, and the way back

## In this release

- **The pull-down menu can be opened with a tap.** The very top of the screen belongs to the
  system, and a pull that started there never reached the app — so the menu that unrolls from
  the top edge was a gesture you could not always make. The short line just below the top edge
  is the app's. Tap it and the same menu comes down and stays: every row is a target, the lit
  row says TAP, and tapping the line again or tapping outside puts it away. Pulling still works
  the same way, and a pull still ends on the row under the thumb.

- **The menu has a way back and a way forward.** BACK is the first row on a page: it walks one
  page back, or goes to the shelf when there is no page behind. FORWARD comes next when there
  is a page ahead — after you have walked back, the page you came from is still there, and the
  menu now says so. The rows a menu opens with are the rows it keeps, so they never move under
  a thumb that is on its way to one.

- **The launcher icon is called Tools.** It was called WebTools. The app is a shelf of pages, not
  a browser, and the name on the home screen now says what it is.

- **Settings can take the colour out of the screen.** A COLOUR row in Settings turns the phone
  grey and back. It asks for the shell first, because it is a system setting:
  `adb shell pm grant com.gios.webtools android.permission.WRITE_SECURE_SETTINGS`.

- **A pull that drifts sideways is still a pull.** The pull follows the thumb as it moves; it
  used to give up when the thumb crossed the screen rather than coming down it.

- **The crash on the first launch after a while stays fixed.** The engine's pages are shut down
  before the app's own process, and a launch that finds a half-written startup cache throws it
  away instead of reading it.

## Known limits

- A drag that starts in the topmost strip of the screen still belongs to the system. A tap just
  below it does not, which is why the menu answers to a tap as well as a pull.
- The COLOUR row needs the shell grant before it will do anything.
- BrightControl needs its ADB connection up for the browser row to work. Without it the row still
  names the command.
- First launch is slower: the engine starts and the two extensions install, once. A launch that
  had to throw away the startup cache is slower again, once.
- A page that wants a popup gets nothing. A page that wants location, camera, or notifications
  gets a no.
- Bundled tools (Split, Convert) do not keep their last values between launches on this engine.
