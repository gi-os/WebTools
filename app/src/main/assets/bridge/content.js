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

  // The article, lifted out with Mozilla's Readability (the same code behind Reader View), on a
  // clone so the page is untouched, then re-serialised as XHTML so an EPUB reader can parse it.
  function article() {
    try {
      var clone = document.cloneNode(true);
      var r = new Readability(clone, { keepClasses: false }).parse();
      if (!r || !r.content) return { article: null, why: "nothing readable on this page" };
      var d = new DOMParser().parseFromString("<div>" + r.content + "</div>", "text/html");
      var body = d.body.firstChild;
      var x = new XMLSerializer().serializeToString(body).replace(/ xmlns="[^"]*"/g, "");
      return {
        article: {
          title: r.title || document.title, byline: r.byline || "", site: r.siteName || location.hostname,
          excerpt: r.excerpt || "", length: r.length || 0, lang: r.lang || document.documentElement.lang || "",
          xhtml: x, url: location.href
        }
      };
    } catch (e) { return { article: null, why: String(e && e.message || e) }; }
  }

  // Type a code where the page wants it: the focused field, else the first field that looks like
  // a one-time-code box. Six single-digit boxes get one digit each. Input events fire so a
  // framework watching the field sees the keystrokes it expects.
  function fire(el) {
    ["input", "change"].forEach(function (n) { try { el.dispatchEvent(new Event(n, { bubbles: true })); } catch (e) {} });
  }
  function isField(el) {
    return el && (el.tagName === "INPUT" || el.tagName === "TEXTAREA") && !el.disabled && !el.readOnly &&
      !/^(hidden|submit|button|checkbox|radio|file|image|reset)$/i.test(el.type || "");
  }
  function codeField() {
    var a = document.activeElement;
    if (isField(a)) return a;
    var sel = [
      'input[autocomplete="one-time-code"]', 'input[inputmode="numeric"]',
      'input[name*="code" i]', 'input[id*="code" i]', 'input[name*="otp" i]', 'input[id*="otp" i]',
      'input[name*="token" i]', 'input[name*="totp" i]', 'input[name*="mfa" i]', 'input[name*="2fa" i]',
      'input[type="tel"]', 'input[type="number"]', 'input[type="text"]'
    ];
    for (var i = 0; i < sel.length; i++) {
      var els = document.querySelectorAll(sel[i]);
      for (var j = 0; j < els.length; j++) if (isField(els[j]) && els[j].offsetParent !== null) return els[j];
    }
    return null;
  }
  function typeCode(text) {
    var el = codeField();
    if (!el) return { typed: false, why: "no field on this page looks like a code box" };
    var boxes = null;
    if (el.maxLength === 1 && el.form) {
      boxes = Array.prototype.filter.call(el.form.querySelectorAll("input"), function (b) { return isField(b) && b.maxLength === 1; });
    }
    if (boxes && boxes.length >= text.length) {
      var start = Math.max(0, boxes.indexOf(el));
      for (var k = 0; k < text.length && start + k < boxes.length; k++) {
        boxes[start + k].focus(); boxes[start + k].value = text[k]; fire(boxes[start + k]);
      }
      return { typed: true, where: "boxes:" + boxes.length };
    }
    el.focus(); el.value = text; fire(el);
    return { typed: true, where: (el.name || el.id || el.type) };
  }

  function t(x) { try { return typeof x; } catch (e) { return "err"; } }
  browser.runtime.onMessage.addListener(function (m) {
    if (!m) return;
    if (m.type === "article") return Promise.resolve(article());
    if (m.type === "type") return Promise.resolve(typeCode(String(m.text || "")));
    if (m.type !== "probe") return;
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
