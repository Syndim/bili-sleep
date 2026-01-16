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
    
    private fun generateBuvid3(): String {
        // Generate a buvid3 cookie similar to Bilibili's format
        val uuid = UUID.randomUUID().toString().replace("-", "")
        val timestamp = System.currentTimeMillis()
        return "${uuid}${timestamp}infoc"
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
                cookieStore[url.host] = cookies.toMutableList()
            }
            
            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                // Add required Bilibili cookies
                val cookies = cookieStore[url.host]?.toMutableList() ?: mutableListOf()
                
                // Add buvid3 if not already present
                if (cookies.none { it.name == "buvid3" }) {
                    cookies.add(
                        Cookie.Builder()
                            .domain("bilibili.com")
                            .name("buvid3")
                            .value(generateBuvid3())
                            .build()
                    )
                }
                
                // Add b_nut timestamp if not already present
                if (cookies.none { it.name == "b_nut" }) {
                    cookies.add(
                        Cookie.Builder()
                            .domain("bilibili.com")
                            .name("b_nut")
                            .value((System.currentTimeMillis() / 1000).toString())
                            .build()
                    )
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
                    .header("Accept-Language", "en-US,en;q=0.9,zh-CN;q=0.8,zh;q=0.7")
                    // Don't set Accept-Encoding manually - let OkHttp handle it for automatic decompression
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
