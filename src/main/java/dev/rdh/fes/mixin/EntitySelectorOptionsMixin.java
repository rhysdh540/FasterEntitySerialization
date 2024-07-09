package dev.rdh.fes.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import dev.rdh.fes.FasterEntitySerialization;

import net.minecraft.commands.arguments.selector.options.EntitySelectorOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;

import java.util.function.Predicate;

@Mixin(EntitySelectorOptions.class)
public abstract class EntitySelectorOptionsMixin {
	@ModifyArg(method = "lambda$bootStrap$50", at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/commands/arguments/selector/EntitySelectorParser;addPredicate(Ljava/util/function/Predicate;)V"
	))
	private static Predicate<Entity> fes$dontSerializeTheWholeEntity(final Predicate<Entity> original,
																		 final @Local CompoundTag expected,
																		 final @Local boolean invert) {
		return entity -> {
			CompoundTag found = new CompoundTag();
			boolean vanilla = false;

			for(String key : expected.getAllKeys()) {
				if(!FasterEntitySerialization.hasCustomConverter(key)) {
					FasterEntitySerialization.LOGGER.debug("No custom converter found for key '{}', saving all keys", key);
					vanilla = true;
					break;
				}

				Tag tag = FasterEntitySerialization.get(key, entity);
				if(tag != null) {
					found.put(key, tag);
				}
			}

			if(vanilla) {
				return original.test(entity);
			}

			return NbtUtils.compareNbt(expected, found, true) != invert;
		};
	}
}
