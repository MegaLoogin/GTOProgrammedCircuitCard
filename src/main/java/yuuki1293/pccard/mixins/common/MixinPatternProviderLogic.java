package yuuki1293.pccard.mixins.common;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.sugar.Local;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import yuuki1293.pccard.PCCard;
import yuuki1293.pccard.impl.PatternProviderLogicImpl;

// ExpandedAE replaces the smart-mode input set at priority 1001. Applying PCC first makes this modifier run last,
// preserving the programmed circuit in the final set.
@Mixin(value = PatternProviderLogic.class, remap = false, priority = 1100)
public abstract class MixinPatternProviderLogic implements IUpgradeableObject {

    @Shadow
    @Final
    private PatternProviderLogicHost host;

    @Shadow
    public abstract void updatePatterns();

    @ModifyArg(
        method = "updatePatterns",
        at = @At(
            value = "INVOKE",
            target = "Lcom/gtolib/api/ae2/MyPatternDetailsHelper;decodePattern(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/block/entity/BlockEntity;Lappeng/api/networking/IGrid;)Lappeng/api/crafting/IPatternDetails;"))
    private ItemStack updatePatterns(ItemStack stack) {
        if (!isUpgradedWith(PCCard.PROGRAMMED_CIRCUIT_CARD_ITEM.get())) return stack;

        return PatternProviderLogicImpl.updatePatterns(stack);
    }

    // GTOCore replaces pushPattern with gtolib$pushPattern, so the target moved there.
    @ModifyArg(
        method = "gtolib$pushPattern",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/helpers/patternprovider/PatternProviderTarget;containsPatternInput(Ljava/util/Set;)Z"))
    private Set<AEKey> includeProgrammedCircuit(Set<AEKey> patternInputs,
        @Local(ordinal = 0, argsOnly = true) IPatternDetails patternDetails) {
        var result = new HashSet<>(patternInputs);
        PatternProviderLogicImpl.addCircuitToPatternInputs(patternDetails, result);
        return result;
    }

    // Two push paths exist in GTOCore: gtolib$pushCraftingMachine (molecular assemblers) and gtolib$pushTarget
    // (external inventories). Each has exactly one onPushPatternSuccess call, so require = 2 covers both.
    @Inject(
        method = { "gtolib$pushCraftingMachine", "gtolib$pushTarget" },
        at = @At(
            value = "INVOKE",
            target = "Lcom/gtocore/mixin/ae2/crafting/PatternProviderLogicMixin;onPushPatternSuccess(Lappeng/api/crafting/IPatternDetails;)V"),
        require = 2)
    private void pushPattern(CallbackInfo ci, @Local(ordinal = 0, argsOnly = true) IPatternDetails patternDetails,
        @Local(ordinal = 0, argsOnly = true) Direction direction) {
        if (!isUpgradedWith(PCCard.PROGRAMMED_CIRCUIT_CARD_ITEM.get())) return;

        var be = this.host.getBlockEntity();
        var blockPoses = PatternProviderLogicImpl.getSendPos(be, direction);
        PatternProviderLogicImpl.setPCNumber(patternDetails, be, blockPoses);
    }

    /**
     * For AE2-fork by Cosmic-Frontier
     */
    @Inject(method = "onUpgradesChanged", at = @At(value = "HEAD"), require = 0)
    private void onUpgradesChanged(CallbackInfo ci) {
        this.updatePatterns();
    }
}
