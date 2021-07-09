package cn.newinfinideas.plugins

import cn.newinfinideas.proxy.*
import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.websocket.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.websocket.*
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

val server = Server(
    arrayOf("localhost.newinfinideas.cn"),
    listOf(
        listOf(
            SelectorRouteSelectorStage(PrefixRouteSelector("/")),
            SelectorRouteRewriteStage(BaseUrlRedirectRouteRewriter(Url("http://localhost:18080"))),
            SelectorRouteUpstreamStage(DefaultProxyUpstreamTransformer(
                listOf(
                    UpstreamHeaderCopyAndDrop(listOf("^(x-)".toRegex(RegexOption.IGNORE_CASE))),
                    UpstreamXScheme,
                    UpstreamXRealIp,
                    UpstreamXForwardFor,
                    UpstreamXForwardProto,
                )
            )),
            SelectorRouteDownstreamStage(DefaultProxyDownstreamTransformer(
                listOf(
                    DownstreamHeaderCopyAndDrop()
                )
            ))
        )
    ),
    mutableMapOf(
        Keys.CLIENT to HttpClient(CIO) {
            expectSuccess = false
            followRedirects = false
        }
    )
)

fun Application.configureRouting() {
    intercept(ApplicationCallPipeline.Call) {
        val host = call.request.header(HttpHeaders.Host)!!
        if (server.match(host))
            server.run(host, call)
        else
            call.respond(HttpStatusCode.NotFound, "$host${call.request.uri} not found on server")
        this.finish()
    }
}

fun Route.webSocketReverseProxy(path: String, proxied: Url) {
    webSocket(path) {
        val serverSession = this
        val client = HttpClient(CIO) { install(io.ktor.client.features.websocket.WebSockets) {} }
        client.webSocketRaw {  }
        client.webSocket(call.request.httpMethod, proxied.host, 0, proxied.fullPath, request = {
            url.protocol = proxied.protocol
            url.port = proxied.port
            println("Connecting to: ${url.buildString()}")
        }) {
            val clientSession = this
            val serverJob = launch { for (received in serverSession.incoming) { clientSession.send(received) } }
            val clientJob = launch { for (received in clientSession.incoming) { serverSession.send(received) } }
            listOf(serverJob, clientJob).joinAll()
        }
    }
}
