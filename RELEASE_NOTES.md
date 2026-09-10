# WebTools 3.2 — a login that is not a white page, and a pull you can catch

## In this release

- **Strict tracking protection was blanking sign-ins.** AXS hands you to `login.axs.com` and back
  to `www.axs.com/login-redirect`. Strict mode partitions the cookies that round trip needs. The
  page arrived, said "Login to AXS" in its title, and rendered nothing. Blocking is on
  standard now. uBlock Origin still blocks the ads.
- **Block ads and trackers, per tool.** A row on the tool's page. Blocking is the commonest reason
  a page comes up blank, and this is the difference between a site that does not work and one
  that works once you know.
- **The pull-down was too small to catch.** The strip along the top was 28dp, and a thumb aiming
  at 28dp misses. It is 56dp now.
- **A way back out of CONVERT.** Its first row is BACK TO THE MENU.
- **The camera button is gone from the app.** It never worked here. The two stages arrive in no
  reliable order and the system claims the press first, so the app stops swallowing it. BACK in
  the pull-down goes back a page.
- **Set as default browser and Passkeys open BrightControl** when the phone has no screen for
  them, which on LightOS is always. Only the shell can set either, and BrightControl
  has one: GRANT ALL does it.
- A report now says whether uBlock and the bridge installed, and what blocking was on.

## Known limits

- First launch is slower: the engine starts and the two extensions install, once.
- A page that wants a popup gets nothing. A page that wants location, camera, or notifications
  gets a no.
- Bundled tools (Split, Convert) do not keep their last values between launches on this engine.
