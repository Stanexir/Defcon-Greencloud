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

package me.mochibit.defcon.extensions

import me.mochibit.defcon.Defcon
import org.bukkit.NamespacedKey
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.jetbrains.annotations.NotNull

fun ItemMeta.setStringData(key: NamespacedKey, value: String) {
    this.persistentDataContainer.set(key, PersistentDataType.STRING, value)
}

fun ItemMeta.getStringData(key: NamespacedKey): String? {
    return this.persistentDataContainer.get(key, PersistentDataType.STRING)
}

fun ItemMeta.setIntData(key: NamespacedKey, value: Int) {
    this.persistentDataContainer.set(key, PersistentDataType.INTEGER, value)
}

fun ItemMeta.getIntData(key: NamespacedKey): Int? {
    return this.persistentDataContainer.get(key, PersistentDataType.INTEGER)
}

fun ItemMeta.setBooleanData(key: NamespacedKey, value: Boolean) {
    this.persistentDataContainer.set(key, PersistentDataType.BYTE, if (value) 1 else 0)
}

fun ItemMeta.getBooleanData(key: NamespacedKey): Boolean? {
    val byteValue = this.persistentDataContainer.get(key, PersistentDataType.BYTE) ?: return null
    return byteValue.toInt() != 0
}

fun ItemMeta.hasData(key: NamespacedKey): Boolean {
    return this.persistentDataContainer.has(key)
}