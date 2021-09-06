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

    @HTTP(method = "MKCOL")
    suspend fun putDir(@Url path: String): Response<Unit>

    @DELETE
    suspend fun delete(@Url path: String): Response<Unit>

    @HTTP(method = "MOVE")
    suspend fun move(@Url path: String, @Header("Destination") destination: String): Response<Unit>

    @HTTP(method = "PROPFIND")
    suspend fun propFind(@Url path: String, @Header("Depth") depth: Int): Response<Multistatus>

    @HTTP(method = "OPTIONS")
    suspend fun options(@Url path: String): Response<Unit>
}
