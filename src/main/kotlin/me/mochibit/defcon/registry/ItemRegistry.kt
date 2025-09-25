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

package me.mochibit.defcon.registry

import me.mochibit.defcon.Defcon
import me.mochibit.defcon.config.ItemsConfiguration
import me.mochibit.defcon.content.items.PluginItem
import me.mochibit.defcon.content.items.PluginItemFactory
import me.mochibit.defcon.utils.Logger
import me.mochibit.defcon.utils.Logger.info
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Tag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.RecipeChoice
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.inventory.ShapelessRecipe

/**
 * This class handles the registration of the definitions items
 * All the registered items are stored and returned in a form of a HashMap(id, CustomItem)
 *
 * To initialize create an instance of ItemRegister and execute the method registerItems, it will automatically
 * load up correctly the definitions items
 *
 */
object ItemRegistry {
    // Change from HashMap<String?, PluginItem?> to Map<String, PluginItem> for type safety
    private var _registeredItems: MutableMap<String, PluginItem> = mutableMapOf()
    val registeredItems: Map<String, PluginItem> get() = _registeredItems

    /**
     *
     * @return boolean - True if all items are registered, false if some error occurred.
     */
    suspend fun registerItems(): Boolean {
        info("Registering plugin items...")
        _registeredItems.clear()

        val configurationItems = ItemsConfiguration.getSchema()
        if (configurationItems.isEmpty()) {
            Logger.warn("No items found in the configuration, skipping item registration")
            return false
        }

        configurationItems.forEach { item ->
            if (_registeredItems.containsKey(item.id)) {
                Logger.warn("Item ${item.id} is already registered (probably duplicated?), skipping")
                return@forEach
            }
            val customItem = PluginItemFactory.create(item)
            if (item.isBlockItem) {
                val block = BlockRegistry.getBlockTemplate(item.id)
                if (block != null) {
                    customItem.linkBlock(block)
                }
            }
            info("Registered item ${item.id}")
            _registeredItems[customItem.properties.id] = customItem
        }

        info("Registering recipes for the items")
        configurationItems.forEach { item ->
            when (item.craftingRecipe) {
                is ItemsConfiguration.ItemDefinition.CraftingRecipe.ShapedCraftingRecipe ->
                    registerShapedRecipe(item.craftingRecipe, item)

                is ItemsConfiguration.ItemDefinition.CraftingRecipe.ShapelessCraftingRecipe ->
                    registerShapelessRecipe(item.craftingRecipe, item)

                else -> return@forEach
            }
        }
        return true
    }


    private fun registerShapedRecipe(
        recipe: ItemsConfiguration.ItemDefinition.CraftingRecipe.ShapedCraftingRecipe,
        item: ItemsConfiguration.ItemDefinition
    ) {
        val resultItem = registeredItems[item.id] ?: return
        val resultItemStack = resultItem.itemStack.apply {
            amount = recipe.resultAmount
        }

        val namespacedKey = NamespacedKey(Defcon, "item_${item.id}_shaped")
        val shapedRecipe = ShapedRecipe(namespacedKey, resultItemStack)

        // Validate pattern
        if (recipe.pattern.size > 3 || recipe.pattern.any { it.length > 3 }) {
            Logger.err("Invalid pattern size for item ${item.id}, must be 3x3 or smaller")
            return
        }

        shapedRecipe.shape(*recipe.pattern.toTypedArray())

        recipe.keys.forEach { (char, ingredientEntry) ->
            val choice = when {
                ingredientEntry.itemNamespaced != null -> {
                    val ingredientItem = getItemStackFromNamespace(ingredientEntry.itemNamespaced, 1)
                    if (ingredientItem == null) {
                        Logger.err("Ingredient item ${ingredientEntry.itemNamespaced} not found for shaped recipe of item ${item.id}, skipping this ingredient")
                        return@forEach
                    }
                    RecipeChoice.ExactChoice(ingredientItem)
                }

                ingredientEntry.tag != null -> {
                    val materialTag = getMaterialTagFromString(ingredientEntry.tag)
                    if (materialTag == null) {
                        Logger.err("Invalid material tag '${ingredientEntry.tag}' for shaped recipe of item ${item.id}, skipping this ingredient")
                        return@forEach
                    }
                    RecipeChoice.MaterialChoice(materialTag)
                }

                else -> {
                    Logger.err("Ingredient entry for shaped recipe of item ${item.id} must have either 'itemNamespaced' or 'tag', skipping this ingredient")
                    return@forEach
                }
            }
            shapedRecipe.setIngredient(char, choice)
        }

        Bukkit.addRecipe(shapedRecipe)
        Logger.info("Registered shaped recipe for item ${item.id}")
    }

    private fun registerShapelessRecipe(
        recipe: ItemsConfiguration.ItemDefinition.CraftingRecipe.ShapelessCraftingRecipe,
        item: ItemsConfiguration.ItemDefinition
    ) {
        val resultItem = registeredItems[item.id] ?: return
        val resultItemStack = resultItem.itemStack.apply {
            amount = recipe.resultAmount
        }

        val namespacedKey = NamespacedKey(Defcon, "item_${item.id}_shapeless")
        val shapelessRecipe = ShapelessRecipe(namespacedKey, resultItemStack)
        recipe.ingredients.forEach { ingredientEntry ->
            when {
                ingredientEntry.itemNamespaced != null -> {
                    val ingredientItem =
                        getItemStackFromNamespace(ingredientEntry.itemNamespaced, ingredientEntry.count)
                    if (ingredientItem == null) {
                        Logger.err("Ingredient item ${ingredientEntry.itemNamespaced} not found for shapeless recipe of item ${item.id}, skipping this ingredient")
                        return@forEach
                    }
                    val choice = RecipeChoice.ExactChoice(ingredientItem)
                    repeat(ingredientEntry.count) { shapelessRecipe.addIngredient(choice) }
                }

                ingredientEntry.tag != null -> {
                    val materialTag = getMaterialTagFromString(ingredientEntry.tag)
                    if (materialTag == null) {
                        Logger.err("Invalid material tag '${ingredientEntry.tag}' for shapeless recipe of item ${item.id}, skipping this ingredient")
                        return@forEach
                    }
                    val choice = RecipeChoice.MaterialChoice(materialTag.values.toList())
                    repeat(ingredientEntry.count) { shapelessRecipe.addIngredient(choice) }
                }

                else -> {
                    Logger.err("Ingredient entry for shapeless recipe of item ${item.id} must have either 'itemId' or 'tag', skipping this ingredient")
                    return@forEach
                }
            }

        }
        Bukkit.addRecipe(shapelessRecipe)
    }

    private fun getItemStackFromNamespace(namespace: String, amount: Int = 1): ItemStack? {
        val parts = namespace.split(":")
        if (parts.size != 2) {
            Logger.err("Invalid namespace format: $namespace. Expected format is 'namespace:key'")
            return null
        }
        when (parts[0]) {
            "minecraft" -> {
                val material = Material.getMaterial(parts[1].uppercase())
                if (material == null) {
                    Logger.err("Material ${parts[1]} not found in minecraft namespace")
                    return null
                }
                return ItemStack(material, amount)
            }

            "defcon" -> {
                val customItem = registeredItems[parts[1]]
                if (customItem == null) {
                    Logger.err("Custom item ${parts[1]} not found in defcon namespace")
                    return null
                }
                val itemStack = customItem.itemStack
                itemStack.amount = amount
                return itemStack
            }

            else -> {
                Logger.err("Unknown namespace: ${parts[0]}")
                return null
            }
        }
    }

    private fun getMaterialTagFromString(string: String): Tag<Material>? {
        val key = NamespacedKey.fromString(string) ?: return null

        // Try each registry
        val registries = listOf("blocks", "items", "fluids")

        for (registry in registries) {
            val tag = Bukkit.getTag(registry, key, Material::class.java)
            if (tag != null) return tag
        }

        return null
    }

    // Add proper getter methods with cloning support

    /**
     * Retrieves an item by ID. Returns a copy to prevent shared state issues.
     * This is the main retrieval method that ensures thread safety and state isolation.
     */
    fun getItem(id: String): PluginItem? = _registeredItems[id]?.copy()

    /**
     * Gets the original registered item template (not a copy).
     * USE WITH CAUTION: This returns the actual registered instance.
     * Only use this for template inspection, never for runtime item instances.
     */
    fun getItemTemplate(id: String): PluginItem? = _registeredItems[id]

    /**
     * Returns copies of all registered items to prevent shared state issues.
     */
    fun getAllItems(): Collection<PluginItem> = _registeredItems.values.map { it.copy() }

    /**
     * Returns the original templates of all registered items.
     * USE WITH CAUTION: These are the actual registered instances.
     */
    fun getAllItemTemplates(): Collection<PluginItem> = _registeredItems.values
}