package cn.newinfinideas.proxy

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.utils.io.*

interface IRequestTransformer {
    suspend fun HttpRequestBuilder.transform(request: ApplicationRequest, env: MutableMap<Any, Any>)
}

interface IResponseTransformer {
    suspend fun transform(response: HttpResponse, env: MutableMap<Any, Any>): OutgoingContent
}

interface IUpstreamHeadersTransformer {
    fun HeadersBuilder.transform(headers: Headers, routing: RoutingInfo, env: Map<Any, Any>)
}

interface IDownstreamHeadersTransformer {
    fun HeadersBuilder.transform(headers: Headers, env: Map<Any, Any>)
}

val autoHeaders = arrayOf(
    HttpHeaders.ContentLength.toLowerCase(),
    HttpHeaders.ContentType.toLowerCase(),
    HttpHeaders.TransferEncoding.toLowerCase()
)

// DO NOT RELY ON THIS FOR SECURITY
class RoutingInfo(
    val url: Url, // original request url
    val hop: Url, // the determined upstream url
    val route: List<String>, // X-Forward-For + last hop
    val realIp: String // X-Real-Ip or inferred remote host
) {
    val scheme get() = url.protocol.name
}

private fun getRoute(request: ApplicationRequest, lastIp: String): List<String> {
    val xf = request.headers.getAll(HttpHeaders.XForwardedFor) ?: return listOf(lastIp)
    return xf.toMutableList().apply { add(lastIp) }
}

private fun makeRoutingInfo(request: ApplicationRequest, env: MutableMap<Any, Any>): RoutingInfo {
    val lastIp = request.local.remoteHost
    val realIp = request.headers["X-Real-IP"] ?: lastIp
    return RoutingInfo(env[Keys.URL] as Url, env[Keys.URL_UP] as Url, getRoute(request, lastIp), realIp)
}

object UpstreamXScheme : IUpstreamHeadersTransformer {
    override fun HeadersBuilder.transform(headers: Headers, routing: RoutingInfo, env: Map<Any, Any>) {
        append("X-Scheme", routing.scheme)
    }
}

object UpstreamXRealIp : IUpstreamHeadersTransformer {
    override fun HeadersBuilder.transform(headers: Headers, routing: RoutingInfo, env: Map<Any, Any>) {
        append("X-Real-IP", routing.realIp)
    }
}

object UpstreamXForwardFor : IUpstreamHeadersTransformer {
    override fun HeadersBuilder.transform(headers: Headers, routing: RoutingInfo, env: Map<Any, Any>) {
        appendAll(HttpHeaders.XForwardedFor, routing.route)
    }
}

object UpstreamXForwardProto : IUpstreamHeadersTransformer {
    override fun HeadersBuilder.transform(headers: Headers, routing: RoutingInfo, env: Map<Any, Any>) {
        append(HttpHeaders.XForwardedProto, routing.scheme)
    }
}

open class HeaderCopyAndDrop(private val conditions: List<Regex>) {
    fun HeadersBuilder.transform(headers: Headers) {
        for ((k, v) in headers.entries()) {
            val match = k.toLowerCase()
            if (match in autoHeaders || checkConditions(match)) continue
            appendAll(k, v)
        }
    }

    private fun checkConditions(k: String) = conditions.foldRight(false) { r, l -> l || r.matches(k) }
}

class UpstreamHeaderCopyAndDrop(conditions: List<Regex> = listOf()) :
    HeaderCopyAndDrop(conditions), IUpstreamHeadersTransformer {
    override fun HeadersBuilder.transform(headers: Headers, routing: RoutingInfo, env: Map<Any, Any>) {
        transform(headers)
    }
}

class DownstreamHeaderCopyAndDrop(conditions: List<Regex> = listOf()) :
    HeaderCopyAndDrop(conditions), IDownstreamHeadersTransformer {
    override fun HeadersBuilder.transform(headers: Headers, env: Map<Any, Any>) = transform(headers)
}

class DefaultProxyUpstreamTransformer(
    private val headerTransformers: List<IUpstreamHeadersTransformer> = listOf()
) : IRequestTransformer {
    override suspend fun HttpRequestBuilder.transform(request: ApplicationRequest, env: MutableMap<Any, Any>) {
        method = request.httpMethod
        contentType(request.contentType())
        val routes = makeRoutingInfo(request, env)
        headers { for (x in headerTransformers) x.apply { transform(request.headers, routes, env) } }
        body = request.receiveChannel()
    }
}

class DefaultProxyDownstreamTransformer(
    private val headerTransformers: List<IDownstreamHeadersTransformer> = listOf()
) : IResponseTransformer {
    override suspend fun transform(response: HttpResponse, env: MutableMap<Any, Any>): OutgoingContent {
        val headers = response.headers
        return object : OutgoingContent.WriteChannelContent() {
            override val contentLength = response.contentLength()
            override val contentType = response.contentType()
            override val status = response.status
            override val headers = Headers.build { for (x in headerTransformers) x.apply { transform(headers, env) } }
            override suspend fun writeTo(channel: ByteWriteChannel) {
                response.content.copyAndClose(channel)
            }
        }
    }
}