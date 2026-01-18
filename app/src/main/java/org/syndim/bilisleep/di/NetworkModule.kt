package org.syndim.bilisleep.di

import org.syndim.bilisleep.data.api.BiliApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    // Generate buvid cookies once and reuse them
    private val buvid3: String by lazy {
        // Format: XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXXXXXXXinfoc
        val uuid = UUID.randomUUID().toString().uppercase()
        "${uuid}infoc"
    }
    
    private val buvid4: String by lazy {
        // Format: XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX
        UUID.randomUUID().toString().uppercase()
    }
    
    private val b_nut: String by lazy {
        (System.currentTimeMillis() / 1000).toString()
    }
    
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        
        // Cookie jar to manage Bilibili cookies
        val cookieJar = object : CookieJar {
            private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()
            
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                val host = url.host
                val existing = cookieStore.getOrPut(host) { mutableListOf() }
                // Update existing cookies or add new ones
                cookies.forEach { newCookie ->
                    existing.removeAll { it.name == newCookie.name }
                    existing.add(newCookie)
                }
            }
            
            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                val cookies = cookieStore.getOrPut(url.host) { mutableListOf() }
                
                // Ensure required Bilibili cookies are present
                val requiredCookies = listOf(
                    Triple("buvid3", buvid3, "bilibili.com"),
                    Triple("buvid4", buvid4, "bilibili.com"),
                    Triple("b_nut", b_nut, "bilibili.com"),
                )
                
                requiredCookies.forEach { (name, value, domain) ->
                    if (cookies.none { it.name == name }) {
                        cookies.add(
                            Cookie.Builder()
                                .domain(domain)
                                .name(name)
                                .value(value)
                                .path("/")
                                .build()
                        )
                    }
                }
                
                return cookies
            }
        }
        
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .cookieJar(cookieJar)
            .addInterceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder()
                    .header("Referer", "https://www.bilibili.com")
                    .header("Origin", "https://www.bilibili.com")
                    .header("User-Agent", BiliApiService.DEFAULT_USER_AGENT)
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .header("sec-ch-ua", "\"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"")
                    .header("sec-ch-ua-mobile", "?0")
                    .header("sec-ch-ua-platform", "\"Windows\"")
                    .header("Sec-Fetch-Dest", "empty")
                    .header("Sec-Fetch-Mode", "cors")
                    .header("Sec-Fetch-Site", "same-site")
                    .method(original.method, original.body)
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BiliApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    @Provides
    @Singleton
    fun provideBiliApiService(retrofit: Retrofit): BiliApiService {
        return retrofit.create(BiliApiService::class.java)
    }
}
