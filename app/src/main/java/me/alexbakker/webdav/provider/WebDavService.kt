package me.alexbakker.webdav.provider

import com.thegrizzlylabs.sardineandroid.model.Multistatus
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface WebDavService {
    @GET
    @Streaming
    suspend fun get(@Url path: String, @Header("Range") range: String?): Response<ResponseBody>

    @PUT
    suspend fun putFile(@Url path: String, @Body body: RequestBody): Response<Unit>

    @PUT
    suspend fun putFileEmpty(@Url path: String): Response<Unit>

    @HTTP(method = "MKCOL")
    suspend fun putDir(@Url path: String): Response<Unit>

    @DELETE
    suspend fun delete(@Url path: String): Response<Unit>

    @HTTP(method = "PROPFIND")
    @Headers("Depth: 1")
    suspend fun propFind(@Url path: String): Response<Multistatus>
}
