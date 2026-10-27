package com.buffetbogo.hunter.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Instant

class FacebookGraphTest {

    private val graph = FacebookGraph("test-token")

    private val feed = """
        {
          "data": [
            {
              "id": "123_456",
              "message": "Buy 1 Get 1 on our buffet dinner at BDT 9,950 net.\nValid till 30 November 2026.",
              "created_time": "2026-10-20T10:00:00+0000",
              "permalink_url": "https://www.facebook.com/SheratonDhaka/posts/456/",
              "full_picture": "https:\/\/scontent.xx.fbcdn.net\/v\/buffet.jpg"
            },
            {
              "id": "123_789",
              "created_time": "2026-10-21T10:00:00+0000"
            }
          ],
          "paging": { "next": "https://graph.facebook.com/next" }
        }
    """.trimIndent()

    @Test
    fun `keeps dated posts with text and links`() {
        val posts = graph.parseFeed(feed)
        assertEquals(1, posts.size)
        val post = posts.single()
        assertEquals("123_456", post.id)
        assertTrue(post.message.startsWith("Buy 1 Get 1"))
        assertEquals("https://www.facebook.com/SheratonDhaka/posts/456/", post.permalink)
        assertEquals("https://scontent.xx.fbcdn.net/v/buffet.jpg", post.image)
        assertEquals(
            Instant.parse("2026-10-20T10:00:00Z").toEpochMilli(),
            post.createdAt,
        )
        assertNull(post.permalink?.takeIf { it.isBlank() })
    }

    @Test
    fun `meta's own error is surfaced, not swallowed`() {
        val body = """
            {"error":{"message":"(#10) Application does not have permission for this call.",
            "type":"OAuthException","code":10,"fbtrace_id":"abc"}}
        """.trimIndent()
        val error = runCatching { graph.parseFeed(body) }.exceptionOrNull()
        assertTrue("expected an IOException, got $error", error is IOException)
        assertTrue(error!!.message!!.contains("#10"))
        assertTrue(error.message!!.contains("OAuthException"))
    }

    @Test
    fun `an empty feed is not an error`() {
        assertTrue(graph.parseFeed("""{"data":[]}""").isEmpty())
    }
}
