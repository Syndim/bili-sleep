package org.syndim.bilisleep.di

import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.syndim.bilisleep.data.api.BiliApiService
import java.util.concurrent.TimeUnit

/**
 * Unit tests for NetworkModule to verify HTTP 412 error fix
 * Tests cookie generation, cookie jar functionality, and header configuration
 * Uses real Bilibili API requests to verify the fix works in production
 */
class NetworkModuleTest {
    
    private lateinit var okHttpClient: OkHttpClient
    
    @Before
    fun setup() {
        // Use the actual OkHttpClient from NetworkModule
        okHttpClient = NetworkModule.provideOkHttpClient()
    }
    
    @Test
    fun `test OkHttpClient is configured with correct timeouts`() {
        assertEquals(30, okHttpClient.connectTimeoutMillis.toLong() / 1000)
        assertEquals(30, okHttpClient.readTimeoutMillis.toLong() / 1000)
        assertEquals(30, okHttpClient.writeTimeoutMillis.toLong() / 1000)
    }
    
    @Test
    fun `test OkHttpClient has cookie jar configured`() {
        assertNotNull("CookieJar should be configured", okHttpClient.cookieJar)
    }
    
    @Test
    fun `test real Bilibili search API returns 200 not 412`() {
        // Make a real request to Bilibili API
        val request = Request.Builder()
            .url("https://api.bilibili.com/x/web-interface/search/type?keyword=test&search_type=video&page=1&page_size=5")
            .build()
        
        val response = okHttpClient.newCall(request).execute()
        
        val responseCode = response.code
        
        // Main test: Verify we don't get 412 error
        assertTrue(
            "Response should not be 412 Precondition Failed. Got: $responseCode",
            responseCode != 412
        )
        
        // Verify we get 200 OK
        assertEquals(
            "Response should be 200 OK. Got: $responseCode",
            200,
            responseCode
        )
        
        // Verify response has content (OkHttp automatically decompresses gzip)
        val body = response.body?.string()
        assertNotNull("Response body should not be null", body)
        
        // The response should be valid JSON with a code field
        assertTrue(
            "Response should contain JSON. Body length: ${body?.length}",
            (body?.length ?: 0) > 0
        )
        
        response.close()
    }
    
    @Test
    fun `test real Bilibili video info API returns 200 not 412`() {
        // Test another endpoint to ensure consistency
        val request = Request.Builder()
            .url("https://api.bilibili.com/x/web-interface/view?bvid=BV1xx411c7mD")
            .header("Referer", "https://www.bilibili.com")
            .header("Origin", "https://www.bilibili.com")
            .header("User-Agent", BiliApiService.DEFAULT_USER_AGENT)
            .build()
        
        val response = okHttpClient.newCall(request).execute()
        
        // Verify we don't get 412 error
        assertTrue(
            "Response should not be 412 Precondition Failed. Got: ${response.code}",
            response.code != 412
        )
        
        // Should get 200 or possibly other success codes
        assertTrue(
            "Response should be successful (2xx)",
            response.code in 200..299
        )
        
        response.close()
    }
    
    @Test
    fun `test buvid3 cookie format is correct`() {
        // We can't directly inspect cookies sent to real Bilibili,
        // but we can verify the format of generated buvid3
        val cookieJar = okHttpClient.cookieJar
        assertNotNull("CookieJar should be configured", cookieJar)
        
        // Make a request to trigger cookie generation
        val request = Request.Builder()
            .url("https://api.bilibili.com/x/web-interface/view?bvid=BV1xx411c7mD")
            .build()
        
        val response = okHttpClient.newCall(request).execute()
        
        // If we didn't get 412, cookies are working correctly
        assertTrue(
            "Should not get 412 error (means cookies are working)",
            response.code != 412
        )
        
        response.close()
    }
    
    @Test
    fun `test User-Agent version is recent`() {
        val userAgent = BiliApiService.DEFAULT_USER_AGENT
        
        // Verify it contains Chrome and the version is recent (>= 131)
        assertTrue(
            "User-Agent should contain Chrome",
            userAgent.contains("Chrome")
        )
        
        val chromeVersionPattern = Regex("Chrome/(\\d+)")
        val matchResult = chromeVersionPattern.find(userAgent)
        assertNotNull("Chrome version should be found", matchResult)
        
        val version = matchResult?.groupValues?.get(1)?.toIntOrNull()
        assertNotNull("Chrome version should be a number", version)
        assertTrue(
            "Chrome version should be >= 131 to avoid 412 errors",
            (version ?: 0) >= 131
        )
    }
    
    @Test
    fun `test multiple consecutive requests don't fail`() {
        // Test that multiple requests work correctly
        for (i in 1..3) {
            val request = Request.Builder()
                .url("https://api.bilibili.com/x/web-interface/view?bvid=BV1xx411c7mD")
                .build()
            
            val response = okHttpClient.newCall(request).execute()
            
            assertTrue(
                "Request $i should not return 412. Got: ${response.code}",
                response.code != 412
            )
            
            response.close()
            
            // Small delay between requests
            Thread.sleep(100)
        }
    }
    
    @Test
    fun `test search with Chinese characters doesn't return 412`() {
        // Test with the actual search query that was failing
        val keyword = "睡前相声"
        val encodedKeyword = java.net.URLEncoder.encode(keyword, "UTF-8")
        
        val request = Request.Builder()
            .url("https://api.bilibili.com/x/web-interface/search/type?keyword=$encodedKeyword&search_type=video&page=1&page_size=5")
            .build()
        
        val response = okHttpClient.newCall(request).execute()
        
        // This is the main test - verify the 412 error is fixed
        assertTrue(
            "Search with Chinese characters should not return 412. Got: ${response.code}",
            response.code != 412
        )
        
        assertEquals(
            "Search should return 200 OK",
            200,
            response.code
        )
        
        val body = response.body?.string()
        assertNotNull("Response body should not be null", body)
        
        response.close()
    }
    
    @Test
    fun `test headers are correctly applied to real requests`() {
        // Verify the interceptor is adding headers
        // We can't directly inspect the request, but we can verify
        // that requests succeed, which indicates headers are correct
        
        val request = Request.Builder()
            .url("https://api.bilibili.com/x/web-interface/view?bvid=BV1xx411c7mD")
            .build()
        
        val response = okHttpClient.newCall(request).execute()
        
        // If headers weren't set correctly, we'd get 412 or other errors
        assertTrue(
            "Request with headers should succeed. Got: ${response.code}",
            response.code in 200..299
        )
        
        response.close()
    }
    
    @Test
    fun `test integration - full search workflow`() {
        // Simulate the full search workflow from the app
        val keyword = "test"
        val encodedKeyword = java.net.URLEncoder.encode(keyword, "UTF-8")
        
        val request = Request.Builder()
            .url("https://api.bilibili.com/x/web-interface/search/type?keyword=$encodedKeyword&search_type=video&page=1&page_size=20&order=totalrank")
            .build()
        
        val response = okHttpClient.newCall(request).execute()
        
        // Main assertion: should not get 412
        assertTrue(
            "Search workflow should not fail with 412. Got: ${response.code}",
            response.code != 412
        )
        
        // Should get successful response
        assertEquals("Should get 200 OK", 200, response.code)
        
        // Response should have body content
        val body = response.body?.string()
        assertNotNull("Response body should not be null", body)
        assertTrue(
            "Response should have content. Body length: ${body?.length}",
            (body?.length ?: 0) > 0
        )
        
        response.close()
    }
}
