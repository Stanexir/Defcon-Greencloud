/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2025 mochibit.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.mochibit.defcon.config

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import me.mochibit.defcon.Defcon
import me.mochibit.defcon.utils.Logger.err
import me.mochibit.defcon.utils.Logger.info
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

abstract class PluginConfiguration<out T>(private val configName: String) {
    private val resourcePath = "$configName.yml"
    private val dataFolderFile = File(Defcon.instance.dataFolder, resourcePath)

    val config: YamlConfiguration by lazy { YamlConfiguration.loadConfiguration(dataFolderFile) }

    protected val mutex = Mutex()

    @Volatile
    private var _loaded = false
    val isLoaded: Boolean
        get() = _loaded

    @Volatile
    private var _schema: T? = null


    protected abstract suspend fun loadSchema(): T
    protected abstract suspend fun cleanupSchema()

    suspend fun getSchema(): T {
        _schema?.let { return it }

        return mutex.withLock {
            _schema?.let { return@withLock it }
            try {
                val schema = loadSchema()
                _schema = schema
                _loaded = true
                schema
            } catch (e: Exception) {
                err("Failed to load schema for $configName: ${e.message}")
                throw e
            }
        }
    }

    suspend fun cleanup() {
        mutex.withLock {
            try {
                cleanupSchema()
                _schema = null
                _loaded = false
            } catch (e: Exception) {
                err("Error during cleanup of configuration $configName: ${e.message}")
            }
        }
    }

    suspend fun reloadConfiguration() {
        mutex.withLock {
            try {
                cleanupSchema()
                config.load(dataFolderFile)

                val schema = loadSchema()
                _schema = schema
                _loaded = true
            } catch (e: Exception) {
                err("Failed to reload configuration $configName: ${e.message}")
                _loaded = false
                _schema = null
                throw e
            }
        }
        info("Configuration $configName reloaded successfully")
    }

    suspend fun initialize(preload: Boolean = true) = coroutineScope {
        try {
            saveDefaultConfig()

            if (preload) {
                getSchema()
            } else {
                info("Configuration $configName initialized (lazy loading enabled)")
            }
        } catch (e: Exception) {
            err("Failed to initialize configuration $configName: ${e.message}")
            throw e
        }
    }

    private fun saveDefaultConfig() {
        if (dataFolderFile.exists()) {
            info("Configuration file $configName already exists, skipping default save.")
            return
        }


        try {
            if (Defcon.Companion.instance.getResource(resourcePath) == null) {
                info("Resource $resourcePath not found in the jar resources, assuming it's handled by the sub-configuration.")
                return
            }

            Defcon.Companion.instance.saveResource(resourcePath, false)
            info("Default configuration saved for $configName")
        } catch (e: Exception) {
            err("Could not save default configuration for $configName: ${e.message}")
        }
    }

    companion object {
        private val configurations = mutableSetOf<PluginConfiguration<*>>()

        suspend fun loadAll() = coroutineScope {
            // Registra le configurazioni
            configurations.add(MainConfiguration)
            configurations.add(ItemsConfiguration)
            configurations.add(BlocksConfiguration)
            configurations.add(StructuresConfiguration)

            // Inizializza tutte le configurazioni in parallelo
            for (config in configurations) {
                launch {
                    try {
                        config.initialize()
                    } catch (e: Exception) {
                        err("Failed to initialize configuration ${config.configName}: ${e.message}")
                    }
                }
            }
        }

        suspend fun cleanupAll() = coroutineScope {
            for (config in configurations) {
                launch {
                    try {
                        config.cleanup()
                    } catch (e: Exception) {
                        err("Failed to cleanup configuration ${config.configName}: ${e.message}")
                    }
                }
            }
        }
    }
}