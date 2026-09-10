// WebTools bridge — background.
//
// One native port to the app ("webtools"). The app sends {id, type, ...}; we answer {id, ok, ...}.
//   setCookies {domain, cookies:[{name,value}]}  -> cookies set for https://<domain>/ with Domain=.<domain>
//   probe      {}                                 -> what the current page sees (asked of the content script)
//   clear      {domain}                           -> cookies for the domain removed
//   article    {}                                 -> the page's article as XHTML (Readability, in the page)
//   type       {text}                             -> the text typed into the page's code field
// Nothing here runs unless the app asks.

var port = browser.runtime.connectNative("webtools");

function reply(id, extra) {
  var m = Object.assign({ id: id }, extra);
  try { port.postMessage(m); } catch (e) { /* app is gone */ }
}

async function setCookies(domain, cookies) {
  var url = "https://" + domain + "/";
  var set = 0, failed = [];
  for (var i = 0; i < cookies.length; i++) {
    var c = cookies[i];
    try {
      await browser.cookies.set({
        url: url, name: c.name, value: c.value,
        domain: "." + domain, path: "/", secure: true, sameSite: "no_restriction",
        expirationDate: Math.floor(Date.now() / 1000) + 60 * 60 * 24 * 90
      });
      set++;
    } catch (e) {
      // Host-only names (__Host-) refuse a Domain attribute; try again without one.
      try {
        await browser.cookies.set({ url: url, name: c.name, value: c.value, path: "/", secure: true });
        set++;
      } catch (e2) { failed.push(c.name); }
    }
  }
  return { set: set, failed: failed };
}

async function clearCookies(domain) {
  var all = await browser.cookies.getAll({ domain: domain });
  for (var i = 0; i < all.length; i++) {
    try { await browser.cookies.remove({ url: "https://" + all[i].domain.replace(/^\./, "") + all[i].path, name: all[i].name }); } catch (e) {}
  }
  return { removed: all.length };
}

async function ask(msg) {
  var tabs = await browser.tabs.query({ active: true });
  if (!tabs.length) return { ok: false, why: "no active tab" };
  try {
    var r = await browser.tabs.sendMessage(tabs[0].id, msg);
    return Object.assign({ ok: true }, r || {});
  } catch (e) {
    return { ok: false, why: String(e && e.message || e) };
  }
}

async function probe() {
  var tabs = await browser.tabs.query({ active: true });
  if (!tabs.length) return { probe: null, why: "no active tab" };
  try {
    var r = await browser.tabs.sendMessage(tabs[0].id, { type: "probe" });
    return { probe: r };
  } catch (e) {
    return { probe: null, why: String(e && e.message || e), url: tabs[0].url, title: tabs[0].title };
  }
}

// What a sign-in actually did. A form that posts and comes back "incorrect password" leaves a
// trail — a 400 on the POST, a Set-Cookie the browser refused, a request that never completed —
// and none of it is visible from the app's side. Kept in a ring here and asked for by the report.
var trail = [];
function note(line) {
  trail.push(line);
  if (trail.length > 60) trail.shift();
}
try {
  browser.webRequest.onCompleted.addListener(function (d) {
    if (d.statusCode >= 400 || d.method === "POST") {
      note(d.statusCode + " " + d.method + " " + String(d.url).slice(0, 160));
    }
  }, { urls: ["<all_urls>"] });
  browser.webRequest.onErrorOccurred.addListener(function (d) {
    note("ERR " + (d.error || "?") + " " + String(d.url).slice(0, 160));
  }, { urls: ["<all_urls>"] });
} catch (e) { note("webRequest unavailable: " + e); }

port.onMessage.addListener(async function (m) {
  if (!m || typeof m !== "object") return;
  try {
    if (m.type === "setCookies") reply(m.id, Object.assign({ ok: true }, await setCookies(m.domain, m.cookies || [])));
    else if (m.type === "clear") reply(m.id, Object.assign({ ok: true }, await clearCookies(m.domain)));
    else if (m.type === "probe") reply(m.id, Object.assign({ ok: true }, await probe()));
    else if (m.type === "article") reply(m.id, await ask({ type: "article" }));
    else if (m.type === "type") reply(m.id, await ask({ type: "type", text: m.text }));
    else if (m.type === "trail") reply(m.id, { ok: true, trail: trail.join("\n") });
    else if (m.type === "ping") reply(m.id, { ok: true, pong: true });
    else reply(m.id, { ok: false, why: "unknown type " + m.type });
  } catch (e) {
    reply(m.id, { ok: false, why: String(e && e.message || e) });
  }
});

port.onDisconnect.addListener(function () { /* the app decides when to reconnect */ });
