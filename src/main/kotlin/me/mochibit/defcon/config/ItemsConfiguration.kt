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

import me.mochibit.defcon.content.element.ElementDefinition
import me.mochibit.defcon.content.items.ItemBehaviour
import me.mochibit.defcon.content.items.PluginItem
import me.mochibit.defcon.content.items.PluginItemProperties
import me.mochibit.defcon.utils.Logger
import org.bukkit.NamespacedKey
import org.bukkit.inventory.EquipmentSlot

object ItemsConfiguration : PluginConfiguration<List<ItemsConfiguration.ItemDefinition>>("items") {

    data class ItemDefinition(
        val id: String,
        val displayName: String,
        val description: String?,

        val minecraftId: String,
        val legacyMinecraftId: String,

        val itemModel: NamespacedKey?,
        val legacyItemModel: Int,

        val equipmentSlot: EquipmentSlot?,
        val maxStackSize: Int,
        val additionalData: Map<String, Any>,
        override val behaviour: ItemBehaviour,
        val shapedRecipe: ShapedCraftingRecipe?,
        val shapelessCraftingRecipe: ShapelessCraftingRecipe?,
    ) : ElementDefinition<PluginItemProperties, PluginItem> {

        data class ShapedCraftingRecipe(
            val resultAmount: Int, val pattern: List<String>, val keys: Map<Char, IngredientEntry>
        )

        data class ShapelessCraftingRecipe(
            val resultAmount: Int, val ingredients: List<IngredientEntry>
        )

        data class IngredientEntry(
            val itemNamespaced: String?, val tag: String?, val count: Int = 1
        )
    }

    override suspend fun cleanupSchema() {}

    override suspend fun loadSchema(): List<ItemDefinition> {
        val tempItems = mutableListOf<ItemDefinition>()

        val itemsSection = config.getConfigurationSection("items") ?: return listOf()

        itemsSection.getKeys(false).forEach { id ->
            val itemSection = itemsSection.getConfigurationSection(id) ?: return@forEach

            val displayName = itemSection.getString("display-name") ?: return@forEach
            val description = itemSection.getString("description")
            val minecraftId = itemSection.getString("minecraft-id") ?: return@forEach
            val legacyMinecraftId = itemSection.getString("legacy-minecraft-id") ?: minecraftId

            val itemModel = itemSection.getString("model", null)?.let {
                NamespacedKey.fromString(it)
            }

            val legacyModelId = itemSection.getInt("legacy-model-id", 0)

            val equipmentSlot = itemSection.getString("equip-slot", null)?.let {
                try {
                    EquipmentSlot.valueOf(it.uppercase())
                } catch (ex: IllegalArgumentException) {
                    Logger.err("Invalid equipment slot '$it' for item $id, using null")
                    null
                }
            }

            val maxStackSize = itemSection.getInt("max-stack-size", 64)
            val behaviourValue = itemSection.getString("behaviour") ?: return@forEach

            val itemBehaviour = try {
                ItemBehaviour.valueOf(behaviourValue.uppercase())
            } catch (ex: IllegalArgumentException) {
                Logger.err("Invalid item behaviour '$behaviourValue' for item $id, skipping..")
                return@forEach
            }

            val properties = mutableMapOf<String, Any>()
            itemSection.getConfigurationSection("properties")?.let { propertiesSection ->
                propertiesSection.getKeys(false).forEach { key ->
                    properties[key] = propertiesSection.get(key) ?: ""
                }
            }


            var shapedRecipe: ItemDefinition.ShapedCraftingRecipe? = null
            var shapelessRecipe: ItemDefinition.ShapelessCraftingRecipe? = null

            itemSection.getConfigurationSection("crafting")?.let { craftingSection ->
                val craftingType = craftingSection.getString("type") ?: return@let
                val resultAmount = craftingSection.getInt("result-amount", 1)

                when (craftingType.lowercase()) {
                    "shaped" -> {
                        val pattern = craftingSection.getStringList("pattern")
                        if (pattern.isEmpty()) {
                            Logger.err("Empty pattern for shaped recipe in item $id, skipping crafting")
                            return@let
                        }

                        val keys = mutableMapOf<Char, ItemDefinition.IngredientEntry>()
                        val keySection = craftingSection.getConfigurationSection("key")

                        keySection?.getKeys(false)?.forEach { charKey ->
                            if (charKey.length != 1) {
                                Logger.err("Invalid key character '$charKey' for item $id, skipping this key entry")
                                return@forEach
                            }

                            val keyEntrySection = keySection.getConfigurationSection(charKey)
                            if (keyEntrySection != null) {
                                val itemId = keyEntrySection.getString("item", null)
                                val tag = keyEntrySection.getString("tag", null)
                                val count = keyEntrySection.getInt("count", 1)

                                when {
                                    tag != null -> {
                                        keys[charKey[0]] = ItemDefinition.IngredientEntry(null, tag, count)
                                    }
                                    itemId != null -> {
                                        keys[charKey[0]] = ItemDefinition.IngredientEntry(itemId, null, count)
                                    }
                                    else -> {
                                        Logger.err("Missing 'item' or 'tag' in key entry for character '$charKey' in item $id")
                                    }
                                }
                            }
                        }

                        shapedRecipe = ItemDefinition.ShapedCraftingRecipe(
                            resultAmount = resultAmount, pattern = pattern, keys = keys
                        )
                    }

                    "shapeless" -> {
                        val ingredientsList = craftingSection.getMapList("ingredients")
                        if (ingredientsList.isEmpty()) {
                            Logger.err("Empty ingredients list for shapeless recipe in item $id, skipping crafting")
                            return@let
                        }

                        val ingredients = ingredientsList.mapNotNull { ingredientMap ->
                            val itemId = ingredientMap["item"] as? String
                            val tag = ingredientMap["tag"] as? String
                            val count = (ingredientMap["count"] as? Number)?.toInt() ?: 1

                            when {
                                tag != null -> ItemDefinition.IngredientEntry(null, tag, count)
                                itemId != null -> ItemDefinition.IngredientEntry(itemId, null, count)
                                else -> {
                                    Logger.err("Missing 'item' or 'tag' in ingredient for item $id")
                                    null
                                }
                            }
                        }

                        if (ingredients.isNotEmpty()) {
                            shapelessRecipe = ItemDefinition.ShapelessCraftingRecipe(
                                resultAmount = resultAmount, ingredients = ingredients
                            )
                        }
                    }

                    else -> {
                        Logger.err("Invalid crafting type '$craftingType' for item $id, skipping crafting")
                    }
                }
            }

            tempItems.add(
                ItemDefinition(
                    id = id,
                    displayName = displayName,
                    description = description,
                    minecraftId = minecraftId,
                    legacyMinecraftId = legacyMinecraftId,
                    itemModel = itemModel,
                    legacyItemModel = legacyModelId,
                    equipmentSlot = equipmentSlot,
                    maxStackSize = maxStackSize,
                    behaviour = itemBehaviour,
                    additionalData = properties,
                    shapedRecipe = shapedRecipe,
                    shapelessCraftingRecipe = shapelessRecipe
                )
            )
        }

        return tempItems.toList()
    }
}

