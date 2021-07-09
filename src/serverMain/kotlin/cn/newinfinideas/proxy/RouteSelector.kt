package cn.newinfinideas.proxy


interface IRouteSelector {
    fun match(path: String): Boolean
}

class FullPathRouteSelector(private val target: String, private val caseSensitive: Boolean = true) : IRouteSelector {
    override fun match(path: String) =
        (path.length == target.length) && path.startsWith(target, ignoreCase = !caseSensitive)
}

class PrefixRouteSelector(private val prefix: String, private val caseSensitive: Boolean = true) : IRouteSelector {
    override fun match(path: String) = path.startsWith(prefix, ignoreCase = !caseSensitive)
}

class PostfixRouteSelector(private val prefix: String, private val caseSensitive: Boolean = true) : IRouteSelector {
    override fun match(path: String) = path.endsWith(prefix, ignoreCase = !caseSensitive)
}

class RegexRouteSelector(regex: String, private val caseSensitive: Boolean = true) : IRouteSelector {
    private val selector = if (caseSensitive) regex.toRegex() else regex.toRegex(RegexOption.IGNORE_CASE)
    override fun match(path: String): Boolean = path.matches(selector)
}
