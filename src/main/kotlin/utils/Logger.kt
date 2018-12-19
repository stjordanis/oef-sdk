package fetch.oef.sdk.kotlin

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger


fun <T: Any>unWrapCompanionClass(ofClass: Class<T>): Class<*>{
    return ofClass.enclosingClass?.takeIf {
        ofClass.kotlin.isCompanion
    } ?: ofClass
}

fun <R: Any> R.logger(): Lazy<Logger>{
    return lazy { LogManager.getLogger(unWrapCompanionClass(this.javaClass).name) }
}