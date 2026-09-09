package com.gios.webtools

import com.gios.webtools.data.Tool
import com.gios.webtools.data.ToolKind
import com.gios.webtools.web.QrPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrPayloadTest {

    private fun ok(r: QrPayload.Result): Tool = (r as QrPayload.Result.Ok).tool

    @Test fun `a bare address becomes a tool named after its host`() {
        val t = ok(QrPayload.parse("https://new.mta.info/status", now = 5L))
        assertEquals("New", t.name)
        assertEquals(ToolKind.SITE, t.kind)
        assertEquals(listOf("new.mta.info"), t.origins)
        assertEquals(5L, t.added)
        assertTrue(t.id.startsWith("new-"))
    }

    @Test fun `companion json carries name origins and keep`() {
        val t = ok(
            QrPayload.parse(
                """{"wt":1,"n":"Tickets","u":"https://www.ticketmaster.com/member/tickets","o":["ticketmaster.com","livenation.com"],"keep":true}""",
            ),
        )
        assertEquals("Tickets", t.name)
        assertEquals("https://www.ticketmaster.com/member/tickets", t.url)
        assertEquals(listOf("ticketmaster.com", "livenation.com"), t.origins)
        assertTrue(t.keep)
        assertTrue(t.id.startsWith("tickets-"))
    }

    @Test fun `json with no origins derives the page's own host`() {
        // The whole host, not the registrable domain: a derived allowlist should be the narrow one.
        val t = ok(QrPayload.parse("""{"n":"Weather","u":"https://forecast.weather.gov/"}"""))
        assertEquals(listOf("forecast.weather.gov"), t.origins)
    }

    @Test fun `origins that omit the page's own host get it added`() {
        val t = ok(QrPayload.parse("""{"n":"X","u":"https://app.example.com/","o":["cdn.example.net"]}"""))
        assertEquals(listOf("cdn.example.net", "app.example.com"), t.origins)
    }

    @Test fun `www is stripped from origins`() {
        val t = ok(QrPayload.parse("""{"u":"https://www.ticketmaster.com/","o":["www.ticketmaster.com"]}"""))
        assertEquals(listOf("ticketmaster.com"), t.origins)
    }

    @Test fun `json without a name is named after the host`() {
        val t = ok(QrPayload.parse("""{"u":"https://www.ticketmaster.com/"}"""))
        assertEquals("Ticketmaster", t.name)
    }

    @Test fun `not a web address`() {
        val r = QrPayload.parse("WIFI:S:home;P:secret;;")
        assertTrue(r is QrPayload.Result.Bad)
    }

    @Test fun `empty is bad`() {
        assertTrue(QrPayload.parse("") is QrPayload.Result.Bad)
        assertTrue(QrPayload.parse(null) is QrPayload.Result.Bad)
    }

    @Test fun `broken json is bad not a crash`() {
        assertTrue(QrPayload.parse("{not json") is QrPayload.Result.Bad)
    }

    @Test fun `json with a non-http address is bad`() {
        assertTrue(QrPayload.parse("""{"u":"ftp://x.example/"}""") is QrPayload.Result.Bad)
    }

    @Test fun `same address gives the same id`() {
        val a = ok(QrPayload.parse("""{"n":"A","u":"https://x.example/"}"""))
        val b = ok(QrPayload.parse("""{"n":"A","u":"https://x.example/"}"""))
        assertEquals(a.id, b.id)
    }

    @Test fun `hostOf handles ports userinfo and paths`() {
        assertEquals("example.com", Tool.hostOf("https://user:pw@www.example.com:8443/p?q#f"))
        assertEquals("", Tool.hostOf("not a url"))
    }

    @Test fun `slug is safe for a directory name and an origin label`() {
        assertEquals("split-a-bill", Tool.slug("Split a bill!"))
        assertEquals("tool", Tool.slug("***"))
        assertTrue(Tool.slug("x".repeat(80)).length <= 24)
    }

    @Test fun `e browser selects the Chromium engine`() {
        val t = ok(QrPayload.parse("""{"n":"Tickets","u":"https://www.ticketmaster.com/","e":"browser"}"""))
        assertEquals(com.gios.webtools.data.Engine.BROWSER, t.engine)
        val d = ok(QrPayload.parse("""{"n":"Tickets","u":"https://www.ticketmaster.com/"}"""))
        assertEquals(com.gios.webtools.data.Engine.BUILTIN, d.engine)
    }

    @Test fun `round trips through json`() {
        val t = ok(QrPayload.parse("""{"n":"Tickets","u":"https://t.example/","o":["t.example"],"keep":true}""", now = 9L))
            .copy(snapshotAt = 3L, lastUsed = 4L, builtIn = false, engine = com.gios.webtools.data.Engine.BROWSER)
        assertEquals(t, Tool.fromJson(t.toJson()))
    }
}
