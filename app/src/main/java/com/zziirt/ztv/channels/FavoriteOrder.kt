package com.zziirt.ztv.channels

object FavoriteOrder {
    fun move(order: List<String>, channelKey: String, direction: Int): List<String> {
        val index = order.indexOf(channelKey)
        if (index < 0) return order

        val target = (index + direction).coerceIn(0, order.lastIndex)
        if (index == target) return order

        return order.toMutableList().apply {
            add(target, removeAt(index))
        }
    }
}
