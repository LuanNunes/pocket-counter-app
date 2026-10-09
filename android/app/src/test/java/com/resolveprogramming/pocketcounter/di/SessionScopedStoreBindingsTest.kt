package com.resolveprogramming.pocketcounter.di

import com.resolveprogramming.pocketcounter.data.session.SessionScopedStore
import dagger.multibindings.IntoSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

/**
 * A store that implements [SessionScopedStore] but is never bound `@IntoSet` is cleared by nobody, so
 * the leak the set exists to close would quietly come back. This fails the build instead.
 */
class SessionScopedStoreBindingsTest {

    @Test
    fun `every SessionScopedStore implementation is bound into the set`() {
        val implementations = implementationsOnClasspath()

        assertTrue("found no implementations to check", implementations.isNotEmpty())
        assertEquals(names(implementations), names(boundInDataModule()))
    }

    private fun names(types: Set<Class<*>>): List<String> = types.map { it.simpleName }.sorted()

    private fun boundInDataModule(): Set<Class<*>> = DataModule::class.java.declaredMethods
        .filter { it.returnType == SessionScopedStore::class.java }
        .filter { it.isAnnotationPresent(IntoSet::class.java) }
        .map { it.parameterTypes.single() }
        .toSet()

    /** The app's own classes, which the unit-test runtime sees as a jar or an exploded directory. */
    private fun implementationsOnClasspath(): Set<Class<*>> {
        val location = SessionScopedStore::class.java.protectionDomain?.codeSource?.location
            ?: error("cannot locate the app's own classes")
        val root = File(location.toURI())
        if (root.isDirectory) {
            return root.walkTopDown()
                .filter { it.isFile && it.extension == "class" }
                .mapNotNull { implementationOrNull(binaryName(it.relativeTo(root).path)) }
                .toSet()
        }
        return ZipFile(root).use { jar ->
            jar.entries().asSequence()
                .filter { it.name.endsWith(".class") }
                .mapNotNull { implementationOrNull(binaryName(it.name)) }
                .toSet()
        }
    }

    private fun binaryName(path: String): String = path
        .removeSuffix(".class")
        .replace(File.separatorChar, '.')
        .replace('/', '.')

    // A class whose supertypes are absent from the unit-test classpath cannot be an implementation.
    private fun implementationOrNull(binaryName: String): Class<*>? = runCatching {
        val loader = SessionScopedStore::class.java.classLoader
        Class.forName(binaryName, false, loader)
            .takeIf { SessionScopedStore::class.java.isAssignableFrom(it) && !it.isInterface }
    }.getOrNull()
}
