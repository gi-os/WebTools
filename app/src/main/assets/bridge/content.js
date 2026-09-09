// WebTools bridge — content script, every http(s) page, at document_start.
//
// Two jobs: hide "open in the app" banners by the class and id names the common vendors use,
// and answer the shake report's probe with what a bot check would see from in here.

(function () {
  var CSS = [
    ".smartbanner, #smartbanner, .smart-banner, .smartbanner-show,",
    "#branch-banner-iframe, .branch-banner, #branch-banner, .branch-journeys-top, .branch-animation,",
    ".af-banner, #af-smart-banner, .af-smart-banner,",
    "[class*='app-banner' i], [id*='app-banner' i], [class*='appbanner' i], [id*='appbanner' i],",
    "[class*='download-app' i], [class*='open-in-app' i], [class*='openinapp' i], [class*='get-the-app' i],",
    "[class*='install-app' i], [data-testid*='app-banner' i], [data-testid*='smart-banner' i],",
    ".js-app-banner, .mobile-app-banner, .app-download-banner, .app-install-banner,",
    "a[href^='intent://'], a[href*='play.google.com/store/apps'], a[href*='apps.apple.com']",
    "{ display: none !important; visibility: hidden !important; height: 0 !important; }",
    "body.smartbanner-show { margin-top: 0 !important; }"
  ].join("\n");

  function add() {
    try {
      if (document.getElementById("__wt_banner_css")) return;
      var st = document.createElement("style");
      st.id = "__wt_banner_css";
      st.textContent = CSS;
      (document.head || document.documentElement).appendChild(st);
    } catch (e) {}
  }
  add();
  document.addEventListener("DOMContentLoaded", add);
  try { new MutationObserver(add).observe(document.documentElement, { childList: true }); } catch (e) {}

  function t(x) { try { return typeof x; } catch (e) { return "err"; } }
  browser.runtime.onMessage.addListener(function (m) {
    if (!m || m.type !== "probe") return;
    var o = {
      title: document.title, url: location.href,
      ua: navigator.userAgent,
      webdriver: navigator.webdriver, plugins: navigator.plugins ? navigator.plugins.length : null,
      languages: navigator.languages, cookies: navigator.cookieEnabled, dpr: window.devicePixelRatio,
      inner: [window.innerWidth, window.innerHeight], screen: [screen.width, screen.height],
      touch: navigator.maxTouchPoints,
      kasada: t(window.wrappedJSObject ? window.wrappedJSObject.KPSDK : undefined),
      recaptcha: t(window.wrappedJSObject ? window.wrappedJSObject.grecaptcha : undefined),
      abuse: !!document.querySelector("abuse-component"),
      text: (document.body && document.body.innerText || "").replace(/\s+/g, " ").slice(0, 600)
    };
    return Promise.resolve(o);
  });
})();
