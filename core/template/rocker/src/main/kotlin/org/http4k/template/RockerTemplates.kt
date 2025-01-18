package org.http4k.template

import com.fizzed.rocker.Rocker
import com.fizzed.rocker.RockerModel
import com.fizzed.rocker.runtime.DefaultRockerModel
import com.fizzed.rocker.runtime.RockerRuntime
import java.io.File
import java.nio.file.Files

/**
 * Use this class as the extension class for all Template classes in the Rocker generation step
 */
abstract class RockerViewModel : DefaultRockerModel(), ViewModel {
    override fun template() = super.template() + ".rocker.html"

    // DefaultRockerModel throws an `UnsupportedOperationException` if the `toString()` method is called.
    override fun toString(): String = javaClass.name
}

@Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
class RockerTemplates : Templates {

    override fun CachingClasspath(unused: String) = object : TemplateRenderer {
        override fun invoke(p1: ViewModel): String = renderIt(p1)
    }

    override fun Caching(unused: String) = object : TemplateRenderer {
        override fun invoke(p1: ViewModel): String = renderIt(p1)
    }

    override fun HotReload(unused: String) = object : TemplateRenderer {
        init {
            RockerRuntime.getInstance().isReloading = true
        }

        override fun invoke(p1: ViewModel) = kotlin.runCatching {
            // Rocker expects the template path to use `/` even on Windows.
            // https://github.com/fizzed/rocker/blob/master/rocker-runtime/src/main/java/com/fizzed/rocker/runtime/DefaultRockerBootstrap.java#L75
            Rocker.template(p1.template().replace(File.separatorChar, '/'))
        }.onFailure { throw ViewNotFound(p1) }
            .map {
                it
                    .bind(
                        p1.javaClass.declaredFields.mapNotNull {
                            try {
                                it.name to p1.javaClass.getDeclaredMethod(it.name)(p1)
                            } catch (e: NoSuchMethodException) {
                                null
                            }
                        }.toMap()
                    ).render().toString()
            }.getOrThrow()
    }

    private fun renderIt(p1: ViewModel) = if (p1 is RockerModel) p1.render().toString()
    else throw ViewNotFound(p1)
}
