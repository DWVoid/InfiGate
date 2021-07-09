package cn.newinfinideas.proxy

import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*

class SelectorPipelineContext(val call: ApplicationCall, url: Url, pEnv: MutableMap<Any, Any>) {
    val environment = pEnv.toMutableMap().also { it[Keys.URL] = url }
}

enum class SelectorStageResult {
    Completed, Proceed, Mismatch
}

object Keys {
    val CLIENT = object {}
    val URL = object {}
    val URL_UP = object {}
    val REQUEST = object {}
}

interface ISelectorStage {
    suspend fun SelectorPipelineContext.stage(): SelectorStageResult
}

class SelectorRouteSelectorStage(private val selector: IRouteSelector) : ISelectorStage {
    override suspend fun SelectorPipelineContext.stage(): SelectorStageResult {
        val url = (environment[Keys.URL] as Url).fullPath
        return if (selector.match(url)) SelectorStageResult.Proceed else SelectorStageResult.Mismatch
    }
}

class SelectorRouteRewriteStage(private val rewriter: IRouteRewriter) : ISelectorStage {
    override suspend fun SelectorPipelineContext.stage(): SelectorStageResult {
        val url = (environment[Keys.URL] as Url).fullPath
        environment[Keys.URL_UP] = rewriter.rewrite(url)
        return SelectorStageResult.Proceed
    }
}

class SelectorRouteUpstreamStage(private val transformer: IRequestTransformer) : ISelectorStage {
    override suspend fun SelectorPipelineContext.stage(): SelectorStageResult {
        val client = environment[Keys.CLIENT] as HttpClient
        val upstream = environment[Keys.URL_UP] as Url
        environment[Keys.REQUEST] = client.request<HttpStatement>(upstream) {
            transformer.apply { transform(call.request, environment) }
        }
        return SelectorStageResult.Proceed
    }
}

class SelectorRouteDownstreamStage(private val transformer: IResponseTransformer) : ISelectorStage {
    override suspend fun SelectorPipelineContext.stage(): SelectorStageResult {
        val request = environment[Keys.REQUEST] as HttpStatement
        request.execute { call.respond(transformer.transform(it, environment)) }
        return SelectorStageResult.Completed
    }
}

class Server(
    private val hosts: Array<String>,
    private val pipelines: List<List<ISelectorStage>>,
    private val environment: MutableMap<Any, Any>
) {
    fun match(host: String) = host in hosts

    suspend fun run(host: String, call: ApplicationCall) {
        val url = URLBuilder("${call.request.local.scheme}://$host${call.request.uri}").build()
        for (pipeline in pipelines) {
            var index = 0
            val length = pipeline.size
            var result = SelectorStageResult.Proceed
            val context = SelectorPipelineContext(call, url, environment)
            while (result == SelectorStageResult.Proceed && index < length) {
                result = pipeline[index++].run { context.stage() }
            }
            if (result == SelectorStageResult.Completed) break
        }
    }
}