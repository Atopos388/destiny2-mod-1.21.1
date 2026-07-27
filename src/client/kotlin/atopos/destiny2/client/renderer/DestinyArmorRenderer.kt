package atopos.destiny2.client.renderer

import atopos.destiny2.client.model.DestinyArmorModel
import atopos.destiny2.common.item.DestinyArmorItem
import atopos.destiny2.common.item.DestinyArmorModelSet
import software.bernie.geckolib.cache.`object`.GeoBone
import software.bernie.geckolib.model.GeoModel
import software.bernie.geckolib.renderer.GeoArmorRenderer

class DestinyArmorRenderer : GeoArmorRenderer<DestinyArmorItem>(DestinyArmorModel()) {
    override fun getHeadBone(model: GeoModel<DestinyArmorItem>): GeoBone? {
        return model.bone("armorHead", "bipedHead")
    }

    override fun getBodyBone(model: GeoModel<DestinyArmorItem>): GeoBone? {
        return model.bone("armorBody", "bipedBody")
    }

    override fun getRightArmBone(model: GeoModel<DestinyArmorItem>): GeoBone? {
        return model.bone("armorRightArm", "bipedRightArm")
    }

    override fun getLeftArmBone(model: GeoModel<DestinyArmorItem>): GeoBone? {
        return model.bone("armorLeftArm", "bipedLeftArm")
    }

    override fun getRightLegBone(model: GeoModel<DestinyArmorItem>): GeoBone? {
        if (currentArmorSet() == DestinyArmorModelSet.BAORAN) {
            return model.bone("bipedRightLeg")
        }
        return model.bone("armorRightLeg", "bipedRightLeg")
    }

    override fun getLeftLegBone(model: GeoModel<DestinyArmorItem>): GeoBone? {
        if (currentArmorSet() == DestinyArmorModelSet.BAORAN) {
            return model.bone("bipedLeftLeg")
        }
        return model.bone("armorLeftLeg", "bipedLeftLeg")
    }

    override fun getRightBootBone(model: GeoModel<DestinyArmorItem>): GeoBone? {
        if (currentArmorSet() == DestinyArmorModelSet.BAORAN) {
            return null
        }
        return model.bone("armorRightBoot", "bipedRightLeg")
    }

    override fun getLeftBootBone(model: GeoModel<DestinyArmorItem>): GeoBone? {
        if (currentArmorSet() == DestinyArmorModelSet.BAORAN) {
            return null
        }
        return model.bone("armorLeftBoot", "bipedLeftLeg")
    }

    private fun currentArmorSet(): DestinyArmorModelSet? {
        return (currentStack?.item as? DestinyArmorItem)?.modelSet
    }

    private fun GeoModel<DestinyArmorItem>.bone(vararg names: String): GeoBone? {
        for (name in names) {
            val bone = getBone(name)
            if (bone.isPresent) {
                return bone.get()
            }
        }
        return null
    }
}
