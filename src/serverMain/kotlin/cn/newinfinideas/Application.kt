package cn.newinfinideas

import cn.newinfinideas.plugins.configureRouting
import io.ktor.application.*
import io.ktor.server.netty.*

fun Application.module() {
    configureRouting()
}

fun main(args: Array<String>) = EngineMain.main(args)
