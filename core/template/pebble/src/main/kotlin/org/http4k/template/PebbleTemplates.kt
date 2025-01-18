package org.http4k.template

import io.pebbletemplates.pebble.PebbleEngine
import io.pebbletemplates.pebble.error.LoaderException
import io.pebbletemplates.pebble.loader.ClasspathLoader
import io.pebbletemplates.pebble.loader.FileLoader
import io.pebbletemplates.pebble.loader.Loader
import java.io.File
import java.io.Reader
import java.io.StringWriter

class PebbleTemplates(private val configure: (PebbleEngine.Builder) -> PebbleEngine.Builder = { it },
                      private val classLoader: ClassLoader = ClassLoader.getSystemClassLoader()) : Templates {

    private class PebbleTemplateRenderer(private val engine: PebbleEngine) : TemplateRenderer {
        override fun invoke(viewModel: ViewModel): String = try {
            val writer = StringWriter()
            engine.getTemplate(viewModel.template() + ".peb").evaluate(writer, mapOf("model" to viewModel))
            writer.toString()
        } catch (e: LoaderException) {
            throw ViewNotFound(viewModel)
        }
    }

    override fun CachingClasspath(baseClasspathPackage: String): TemplateRenderer {
        val loader = ClasspathLoader(classLoader)

        val wrapper = object : Loader<String> by loader {
            override fun getReader(cacheKey: String?): Reader {
                return loader.getReader(cacheKey?.replace(File.separatorChar, '/'))
            }

            override fun resourceExists(templateName: String?): Boolean {
                return loader.resourceExists(templateName?.replace(File.separatorChar, '/'))
            }


        }
        loader.prefix = if (baseClasspathPackage.isEmpty()) null else baseClasspathPackage.replace('.', '/')
        return PebbleTemplateRenderer(configure(PebbleEngine.Builder().loader(wrapper)).build())
    }

    override fun Caching(baseTemplateDir: String): TemplateRenderer {
        val loader = FileLoader()
        loader.prefix = baseTemplateDir
        return PebbleTemplateRenderer(configure(PebbleEngine.Builder().cacheActive(true).loader(loader)).build())
    }

    override fun HotReload(baseTemplateDir: String): TemplateRenderer {
        val loader = FileLoader()
        loader.prefix = baseTemplateDir
        return PebbleTemplateRenderer(configure(PebbleEngine.Builder().cacheActive(false).loader(loader)).build())
    }
}
