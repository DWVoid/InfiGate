package cn.newinfinideas.proxy

import io.ktor.http.*

interface IRouteRewriter {
    fun rewrite(path: String): Url
}

class BaseUrlRedirectRouteRewriter(private val base: Url): IRouteRewriter {
    override fun rewrite(path: String) = URLBuilder(base.toString() + path).build()
}


