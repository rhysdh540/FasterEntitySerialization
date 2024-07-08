package dev.rdh.fastnbt.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import dev.rdh.fastnbt.FastNBT;

import net.minecraft.commands.arguments.selector.options.EntitySelectorOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

@Mixin(EntitySelectorOptions.class)
public abstract class EntitySelectorOptionsMixin {
	@ModifyArg(method = "lambda$bootStrap$50", at = @At(value = "INVOKE", target = "Lnet/minecraft/commands/arguments/selector/EntitySelectorParser;addPredicate(Ljava/util/function/Predicate;)V"))
	private static Predicate<Entity> a(final Predicate<Entity> predicate, final @Local CompoundTag expected, final @Local boolean invert) {
		return entity -> {
			CompoundTag found = new CompoundTag();

			// if we found a key that doesn't have a custom converter, we need to revert to vanilla behavior and save all keys
			boolean saveAll = false;

			for(String key : expected.getAllKeys()) {
				if(!FastNBT.hasCustomConverter(key)) {
					FastNBT.LOGGER.debug("No custom converter found for key '{}', saving all keys", key);
					saveAll = true;
					break;
				}

				Tag tag = FastNBT.get(key, entity);
				if(tag != null) {
					found.put(key, tag);
				}
			}

			if(saveAll) {
				// vanilla behavior
				found = entity.saveWithoutId(new CompoundTag());
				if (entity instanceof ServerPlayer p) {
					ItemStack held = p.getInventory().getSelected();
					if (!held.isEmpty()) {
						found.put("SelectedItem", held.save(new CompoundTag()));
					}
				}
			}

			return NbtUtils.compareNbt(expected, found, true) != invert;
		};
	}
}
