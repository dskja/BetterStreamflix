package com.betterstreamflix.architecture

import com.betterstreamflix.utils.FileLogger

/**
 * Dependency container — lightweight service locator for providing
 * singletons and factory-created instances without a full DI framework.
 */
object DependencyContainer {

    private val singletons = mutableMapOf<Class<*>, Any>()
    private val factories = mutableMapOf<Class<*>, () -> Any>()

    /**
     * Register a singleton instance.
     */
    fun <T : Any> registerSingleton(type: Class<T>, instance: T) {
        FileLogger.i("DependencyContainer", "registerSingleton: ${type.name}")
        singletons[type] = instance
    }

    /**
     * Register a factory for lazy creation.
     */
    fun <T : Any> registerFactory(type: Class<T>, factory: () -> T) {
        FileLogger.i("DependencyContainer", "registerFactory: ${type.name}")
        factories[type] = factory
    }

    /**
     * Get a registered instance. Throws if not found.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(type: Class<T>): T {
        FileLogger.d("DependencyContainer", "get: requesting ${type.name}")
        singletons[type]?.let { return it as T }
        factories[type]?.let {
            FileLogger.d("DependencyContainer", "get: creating via factory for ${type.name}")
            val instance = it() as T
            singletons[type] = instance
            return instance
        }
        FileLogger.e("DependencyContainer", "get: ✗ No registration for ${type.name}")
        throw IllegalStateException("No registration for ${type.name}")
    }

    /**
     * Get a registered instance or null.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getOrNull(type: Class<T>): T? {
        singletons[type]?.let { return it as T }
        factories[type]?.let {
            val instance = it() as T
            singletons[type] = instance
            return instance
        }
        return null
    }

    /**
     * Check if a type is registered.
     */
    fun isRegistered(type: Class<*>): Boolean {
        return singletons.containsKey(type) || factories.containsKey(type)
    }

    /**
     * Clear all registrations (for testing).
     */
    fun clear() {
        singletons.clear()
        factories.clear()
    }

    /**
     * Remove a specific registration.
     */
    fun <T : Any> unregister(type: Class<T>) {
        singletons.remove(type)
        factories.remove(type)
    }
}

/**
 * Convenience inline function to get a dependency.
 */
inline fun <reified T : Any> inject(): T = DependencyContainer.get(T::class.java)

/**
 * Convenience inline function to get a dependency or null.
 */
inline fun <reified T : Any> injectOrNull(): T? = DependencyContainer.getOrNull(T::class.java)
