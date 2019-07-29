/**
 * Copyright 2018 Fetch.AI Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.fetch.oef

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