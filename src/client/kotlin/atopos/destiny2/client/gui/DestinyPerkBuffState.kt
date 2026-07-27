package atopos.destiny2.client.gui

/** 客户端只保存 HUD 所需的短时 Perk 状态；战斗逻辑仍完全由服务端裁定。 */
object DestinyPerkBuffState {
    data class ActiveBuff(val id: String, val name: String, val expiresAt: Long, val stacks: Int)

    private val active = linkedMapOf<String, ActiveBuff>()

    fun activate(id: String, name: String, durationTicks: Int, stacks: Int) {
        if (durationTicks <= 0 || stacks <= 0) {
            active.remove(id)
            return
        }
        active[id] = ActiveBuff(id, name, System.currentTimeMillis() + durationTicks.coerceAtLeast(20) * 50L, stacks.coerceAtLeast(1))
    }

    fun visible(): List<ActiveBuff> {
        val now = System.currentTimeMillis()
        active.entries.removeIf { it.value.expiresAt <= now }
        return active.values.sortedBy { it.expiresAt }.take(6)
    }

    fun isActive(id: String, name: String): Boolean {
        val buff = active[id] ?: return false
        return buff.name == name && buff.expiresAt > System.currentTimeMillis()
    }

    fun remove(id: String) {
        active.remove(id)
    }

    fun remainingSeconds(buff: ActiveBuff): Int = ((buff.expiresAt - System.currentTimeMillis()).coerceAtLeast(0) / 1000L + 1L).toInt()
}
