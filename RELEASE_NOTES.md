# WebTools v1.2 — bring a login from a computer

## What was wrong with Ticketmaster

Ticketmaster's sign-in page (identity.ticketmaster.com) runs reCAPTCHA Enterprise and a
FingerprintJS check before it shows a form. The WebView on this phone (Chromium 113) fails that
check, and the page turns into the "browsing activity paused" notice. The rest of the site
works. Kasada guards www and auth, and it let the built-in view through.

The Light Phone has no browser, so "open in Chromium" had nowhere to go. Ticketmaster's own
Android SDK needs a browser for its login and Google Play Integrity for its barcodes. That road
ends at the same wall.

## In this release

- **Bring a login.** Sign in on a computer. On the companion page, under *Bring a login*, paste
  the page's *Copy as cURL* from DevTools. The page packs the cookies into one code or several.
  Scan them in order and the tool opens signed in. Sessions expire the way they do in a browser;
  make a new code when the site asks you to sign in again.
- **No browser, no pretending.** The *Opens in* switch only shows when the phone has a browser.
  Without one, the tool's page says to sign in on a computer.
- **The WebView tells one story.** On a WebView too old to rewrite its client hints, the user
  agent stays stock too. A Chrome user agent beside "Android WebView" hints reads as a lie, and
  bot checks read it that way.
- The shake report now lists the phone's browsers and what the WebView supports.

## Known limits

- A login code carries the cookies you had at that moment. A site that re-checks the device
  can still ask again.
- Pages with heavy scripts may not rebuild well from a saved copy. Static pages do.
