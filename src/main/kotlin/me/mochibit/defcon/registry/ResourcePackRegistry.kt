/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2024-2025 mochibit.
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

package me.mochibit.defcon.registry

import kotlinx.coroutines.runBlocking
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.config.MainConfiguration
import me.mochibit.defcon.content.pack.FormatReader
import me.mochibit.defcon.utils.Logger.err
import me.mochibit.defcon.utils.Logger.info
import me.mochibit.defcon.utils.Logger.warn
import me.mochibit.defcon.customassets.fonts.AbstractCustomFont
import me.mochibit.defcon.customassets.items.AbstractCustomItemModel
import me.mochibit.defcon.customassets.items.ModelData
import me.mochibit.defcon.customassets.sounds.AbstractCustomSound
import me.mochibit.defcon.server.ResourcePackServer
import me.mochibit.defcon.utils.versionGreaterOrEqualThan
import net.kyori.adventure.resource.ResourcePackInfo
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import org.reflections.Reflections
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.jar.JarFile
import kotlin.collections.iterator
import kotlin.io.path.pathString

/**
 * Handles registration and generation of the resource pack.
 */
object ResourcePackRegistry : PackRegistry(true) {

    private val pluginResourcePackPath = Defcon.dataFolder.toPath().resolve("resourcepack")

    override val tempPath: Path = Paths.get(pluginResourcePackPath.pathString, "defcon_temp_resourcepack")
    override val destinationPath: Path? = Paths.get(pluginResourcePackPath.pathString, "defcon_resourcepack")

    private val jsonParser = JSONParser()
    private val packageName = Defcon::class.java.`package`.name

    private var resourcePackHash: String? = null
    private var resourcePackInfo: ResourcePackInfo? = null
    private var localResourcePackInfo: ResourcePackInfo? = null


    private fun generateResourcePackHash(path: Path): String {
        return try {
            if (!Files.exists(path) || Files.size(path) == 0L) {
                warn("Cannot generate hash for non-existent or empty file: $path")
                return "0"
            }

            BufferedInputStream(FileInputStream(path.toFile())).use { bis ->
                val digest = MessageDigest.getInstance("SHA-1")
                val buffer = ByteArray(8192)
                var bytesRead: Int

                while (bis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }

                digest.digest().joinToString("") { "%02x".format(it) }
            }
        } catch (e: Exception) {
            err("Failed to generate resource pack hash: ${e.message}")
            "0"
        }
    }

    private val mainConfiguration by lazy {
        runBlocking { MainConfiguration.getSchema() }
    }

    val packInfo: ResourcePackInfo
        get() {
            resourcePackInfo?.let {
                return it
            }

            val hash = resourcePackHash ?: run {
                warn("Resource pack hash is null !")
                "0"
            }

            return ResourcePackInfo.resourcePackInfo()
                .uri(URI.create("http://${Defcon.server.ip}:${mainConfiguration.resourcePackConfig.serverPort}/resourcepack.zip"))
                .hash(hash)
                .build()
                .also {
                    resourcePackInfo = it
                }
        }

    val localPackInfo: ResourcePackInfo
        get() {
            localResourcePackInfo?.let {
                return it
            }


            val hash = resourcePackHash ?: run {
                warn("Resource pack hash is null !")
                "0"
            }

            return ResourcePackInfo.resourcePackInfo()
                .uri(URI.create("http://127.0.0.1:${mainConfiguration.resourcePackConfig.serverPort}/resourcepack.zip"))
                .hash(hash)
                .build()
                .also {
                    localResourcePackInfo = it
                }
        }

    override fun onPackCreated(finalPath: Path) {
        if (!Files.exists(finalPath) || Files.size(finalPath) == 0L) {
            warn("Resource pack creation failed, file does not exist or is empty at $finalPath")
            return
        }
        info("Resource pack created at $finalPath")
        resourcePackHash = generateResourcePackHash(finalPath)
        resourcePackInfo = null
        localResourcePackInfo = null
        ResourcePackServer.updatePackPath(finalPath)
    }

    /**
     * Writes the resource pack content to the temp directory.
     */
    override fun write() {
        info("Creating resource pack for Defcon")

        try {
            // Create necessary directories
            val localAssetsPath = Paths.get("$tempPath/assets")
            val minecraftPath = Paths.get("$localAssetsPath/minecraft")
            val fontPath = Paths.get("$minecraftPath/font")

            Files.createDirectories(localAssetsPath)
            Files.createDirectories(minecraftPath)
            Files.createDirectories(fontPath)
            // Create .mcmeta file
            val mcmetaContent = createMcmetaJson(
                FormatReader.packFormat.resourceVersion,
                "Defcon resource pack - auto generated",
            )
            Files.write(Paths.get("$tempPath/pack.mcmeta"), mcmetaContent.toByteArray())

            // Copy static assets and process dynamic assets
            copyStaticAssets(localAssetsPath, minecraftPath, fontPath)
            processCustomFonts(fontPath)
            processCustomSounds(minecraftPath)
            processCustomItemModels(minecraftPath)
        } catch (e: Exception) {
            warn("Error creating resource pack: ${e.message}")
            e.printStackTrace()
        }

    }


    /**
     * Copies static assets from the plugin JAR to the resource pack.
     */
    private fun copyStaticAssets(localAssetsPath: Path, minecraftPath: Path, fontPath: Path) {
        // Copy assets excluding the defcon folder
        copyFoldersFromResource(
            "assets/",
            localAssetsPath,
            setOf("assets/defcon")
        )

        // Copy default.json font file
        try {
            val jarFile = javaClass.protectionDomain.codeSource.location.toURI().path
            JarFile(jarFile).use { jar ->
                val fontEntry = jar.getEntry("assets/defcon/fonts/default.json")
                val packEntry = jar.getEntry("assets/pack.png")
                fontEntry?.let {
                    jar.getInputStream(it).use { input ->
                        Files.copy(input, fontPath.resolve("default.json"), StandardCopyOption.REPLACE_EXISTING)
                    }
                }

                packEntry?.let {
                    jar.getInputStream(it).use { input ->
                        Files.copy(input, Paths.get("$tempPath/pack.png"), StandardCopyOption.REPLACE_EXISTING)
                    }
                }
            }
        } catch (e: Exception) {
            info("Error copying default.json: ${e.message}")
        }

        // Copy textures, models, and optifine folders
        copyFoldersFromResource("assets/defcon/textures/", Paths.get("$minecraftPath/textures"))
        copyFoldersFromResource("assets/defcon/models/", Paths.get("$minecraftPath/models"))
        copyFoldersFromResource("assets/defcon/optifine/", Paths.get("$minecraftPath/optifine"))
        copyFoldersFromResource("assets/defcon/sounds/", Paths.get("$minecraftPath/sounds"))
    }

    /**
     * Processes custom fonts and adds them to default.json.
     */
    private fun processCustomFonts(fontPath: Path) {
        try {
            val defaultJsonPath = fontPath.resolve("default.json")

            // Read existing default.json
            val jsonReader = Files.newBufferedReader(defaultJsonPath)
            val defaultJson = jsonParser.parse(jsonReader) as JSONObject
            val providers = defaultJson["providers"] as JSONArray
            jsonReader.close()

            // Add custom fonts
            val customFonts = Reflections("$packageName.customassets.fonts.definitions")
                .getSubTypesOf(AbstractCustomFont::class.java)

            for (fontClass in customFonts) {
                try {
                    val fontInstance = fontClass.getDeclaredConstructor().newInstance()
                    val fontData = fontInstance.fontData

                    val newFont = JSONObject()
                    newFont["file"] = fontData.file
                    newFont["type"] = fontData.type
                    newFont["ascent"] = fontData.ascent
                    newFont["height"] = fontData.height

                    val chars = JSONArray()
                    chars.addAll(fontData.chars)
                    newFont["chars"] = chars

                    newFont["advances"] = fontData.advances
                    providers.add(newFont)
                } catch (e: Exception) {
                    info("Error processing font ${fontClass.simpleName}: ${e.message}")
                }
            }

            // Write updated default.json
            Files.write(defaultJsonPath, defaultJson.toJSONString().toByteArray())
        } catch (e: Exception) {
            info("Error processing custom fonts: ${e.message}")
        }
    }

    /**
     * Processes custom sounds and creates sounds.json.
     */
    private fun processCustomSounds(minecraftPath: Path) {
        try {
            val soundsJson = JSONObject()

            val customSounds = Reflections("$packageName.customassets.sounds.definitions")
                .getSubTypesOf(AbstractCustomSound::class.java)

            for (soundClass in customSounds) {
                try {
                    val soundInstance = soundClass.getDeclaredConstructor().newInstance()
                    val soundInfo = soundInstance.soundInfo
                    val soundData = soundInstance.soundData

                    val soundObject = JSONObject()
                    val soundsArray = JSONArray()
                    soundsArray.addAll(soundData.sounds)
                    soundObject["sounds"] = soundsArray

                    val soundKey = if (soundInfo.directory.isNotEmpty()) {
                        "${soundInfo.directory}.${soundInfo.name}"
                    } else {
                        soundInfo.name
                    }

                    soundsJson[soundKey] = soundObject
                } catch (e: Exception) {
                    info("Error processing sound ${soundClass.simpleName}: ${e.message}")
                }
            }

            Files.write(minecraftPath.resolve("sounds.json"), soundsJson.toJSONString().toByteArray())
        } catch (e: Exception) {
            info("Error processing custom sounds: ${e.message}")
        }
    }

    /**
     * Processes custom item models.
     */
    private fun processCustomItemModels(minecraftPath: Path) {
        try {
            val targetModelsPath = Paths.get("$minecraftPath/models")

            // Get all custom item models
            val customItemModels = Reflections("$packageName.customassets.items.definitions")
                .getSubTypesOf(AbstractCustomItemModel::class.java)


            if (versionGreaterOrEqualThan("1.21.3")) {
                for (itemModelClass in customItemModels) {
                    try {
                        val itemModelInstance = itemModelClass.getDeclaredConstructor().newInstance()
                        createItemModelFile(minecraftPath, itemModelInstance.modelData)
                    } catch (e: Exception) {
                        err("Error processing item model ${itemModelClass.simpleName}: ${e.message}")
                        err("Stack trace:" + e.stackTraceToString())
                    }
                }
            } else {
                createItemCustomModelDataFile(
                    minecraftPath,
                    targetModelsPath,
                    customItemModels.map { it.getDeclaredConstructor().newInstance() })
            }
        } catch (e: Exception) {
            err("Error processing custom item models: ${e.message}")
            e.printStackTrace()
        }
    }

    //TODO: Replace with kotlinx.serialization

    /**
     * Creates an item model JSON file for a group of custom items.
     */
    private fun createItemModelFile(
        targetMinecraftPath: Path,
        modelData: ModelData,
    ) {
        val modelOuter = JSONObject()
        modelOuter["model"] = JSONObject().apply {
            put("type", modelData.type)
            put("model", modelData.model)
        }

        val itemsDirectory = targetMinecraftPath.resolve("items")
        Files.createDirectories(itemsDirectory)

        val filePath = itemsDirectory.resolve("${modelData.name}.json")
        Files.write(
            filePath,
            modelOuter.toJSONString().toByteArray()
        )
    }


    private fun createItemCustomModelDataFile(
        targetMinecraftPath: Path,
        targetModelPath: Path,
        model: List<AbstractCustomItemModel>,
    ) {
        // Group by original item
        val groupedModels = model.groupBy(keySelector = { it.legacyModelData.originalItem })

        val itemsDirectory = targetMinecraftPath.resolve("items")
        Files.createDirectories(itemsDirectory)

        for ((originalItem, itemModels) in groupedModels) {
            // Compatible with newer version data
            val itemModelJson = JSONObject()
            itemModelJson["model"] = JSONObject().apply {
                put("type", "minecraft:range_dispatch")
                put("property", "minecraft:custom_model_data")
                put("entries", JSONArray().apply {
                    for (itemModel in itemModels) {
                        add(JSONObject().apply {
                            put("threshold", itemModel.legacyModelData.customModelData)
                            put("model", JSONObject().apply {
                                put("type", "minecraft:model")
                                put("model", itemModel.legacyModelData.model)
                            })
                        })
                    }
                })
                put("fallback", JSONObject().apply {
                    put("type", "minecraft:model")
                    put("model", "minecraft:item/${originalItem.name.lowercase()}")
                })
            }
            info("Writing model data (COMPAT for >= 1.21.4) for ${originalItem.name.lowercase()}")
            Files.write(
                itemsDirectory.resolve("${originalItem.name.lowercase()}.json"),
                itemModelJson.toJSONString().toByteArray()
            )

            // Legacy item model data
            val legacyModelJson = JSONObject()
            legacyModelJson["parent"] = "item/generated"
            legacyModelJson["textures"] = JSONObject().apply {
                for (itemModel in itemModels) {
                    for ((layer, texture) in itemModel.legacyModelData.textures) {
                        this[layer] = texture
                    }
                }
            }

            legacyModelJson["overrides"] = JSONArray().apply {
                for (baseItemOverrides in itemModels.first().legacyModelData.overrides) {
                    val defaultModelOverride = JSONObject()
                    defaultModelOverride["predicate"] = JSONObject().apply {
                        put(baseItemOverrides.predicate.key, baseItemOverrides.predicate.value)
                    }
                    defaultModelOverride["model"] = baseItemOverrides.model

                    this.add(defaultModelOverride)
                }

                for (itemModel in itemModels) {
                    val override = JSONObject()
                    override["predicate"] = JSONObject().apply {
                        put("custom_model_data", itemModel.legacyModelData.customModelData)
                    }

                    override["model"] = itemModel.legacyModelData.model
                    this.add(override)
                }
            }
            info("Writing legacy model data for ${originalItem.name.lowercase()}")
            Files.write(
                targetModelPath.resolve("item/${originalItem.name.lowercase()}.json"),
                legacyModelJson.toJSONString().toByteArray()
            )
        }
    }
}