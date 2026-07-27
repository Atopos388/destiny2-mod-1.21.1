package atopos.destiny2.common.mixin

import atopos.destiny2.common.player.PlayerDestinyData
import atopos.destiny2.common.player.PlayerDestinyDataHolder
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(ServerPlayer::class)
abstract class MixinServerPlayerDestinyData : PlayerDestinyDataHolder {
    @Unique
    private var destiny2PlayerData: PlayerDestinyData = PlayerDestinyData.createDefault()

    override fun `destiny2-mod$getDestinyData`(): PlayerDestinyData {
        return destiny2PlayerData
    }

    override fun `destiny2-mod$setDestinyData`(data: PlayerDestinyData) {
        destiny2PlayerData = data
    }

    @Inject(method = ["readAdditionalSaveData"], at = [At("TAIL")])
    private fun destiny2modReadDestinyData(tag: CompoundTag, ci: CallbackInfo) {
        if (tag.contains(PlayerDestinyData.NBT_KEY)) {
            destiny2PlayerData = PlayerDestinyData.fromTag(tag.getCompound(PlayerDestinyData.NBT_KEY), (this as Any as ServerPlayer).registryAccess())
        }
    }

    @Inject(method = ["addAdditionalSaveData"], at = [At("TAIL")])
    private fun destiny2modWriteDestinyData(tag: CompoundTag, ci: CallbackInfo) {
        tag.put(PlayerDestinyData.NBT_KEY, destiny2PlayerData.toTag((this as Any as ServerPlayer).registryAccess()))
    }

    @Inject(method = ["restoreFrom"], at = [At("TAIL")])
    private fun destiny2modCopyDestinyData(oldPlayer: ServerPlayer, alive: Boolean, ci: CallbackInfo) {
        val oldData = (oldPlayer as PlayerDestinyDataHolder).`destiny2-mod$getDestinyData`()
        destiny2PlayerData = oldData.copy()
        if (!alive) {
            destiny2PlayerData.combatState.resetAfterDeath()
        }
    }
}
