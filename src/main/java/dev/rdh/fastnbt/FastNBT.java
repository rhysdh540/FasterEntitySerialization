package dev.rdh.fastnbt;

import com.ibm.icu.util.CodePointTrie.Fast;
import com.mojang.serialization.DataResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Component.Serializer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public abstract class FastNBT {
	public static final String ID = "fastnbt";
	public static final Logger LOGGER = LogManager.getLogger(ID);

	private static final Map<String, Function<Entity, @Nullable Tag>> ENTITY_NBT = new HashMap<>();

	public static void init() {
		registerBaseEntityConverters();
		registerLivingEntityConverters();
	}

	private static void registerBaseEntityConverters() {
		register("Pos", entity -> {
			Entity toSave = entity.getVehicle() == null ? entity : entity.getVehicle();
			return newDoubleList(toSave.getX(), toSave.getY(), toSave.getZ());
		});
		register("Motion", entity -> {
			Vec3 motion = entity.getDeltaMovement();
			return newDoubleList(motion.x, motion.y, motion.z);
		});
		register("Rotation", entity -> newFloatList(entity.getYRot(), entity.getXRot()));
		register("FallDistance", entity -> DoubleTag.valueOf(entity.fallDistance));
		register("Fire", entity -> ShortTag.valueOf((short) entity.getRemainingFireTicks()));
		register("Air", entity -> ShortTag.valueOf((short) entity.getAirSupply()));
		register("OnGround", entity -> ByteTag.valueOf(entity.onGround()));
		register("Invulnerable", entity -> ByteTag.valueOf(entity.isInvulnerable()));
		register("PortalCooldown", entity -> IntTag.valueOf(entity.getPortalCooldown()));
		register("UUID", entity -> NbtUtils.createUUID(entity.getUUID()));
		register("CustomName", entity -> {
			Component customName = entity.getCustomName();
			if(customName == null) return null;
			return StringTag.valueOf(Serializer.toJson(customName));
		});
		register("CustomNameVisible", entity -> entity.isCustomNameVisible() ? ByteTag.ONE : null); // vanilla behavior is to not save the tag if it's false
		register("Silent", entity -> entity.isSilent() ? ByteTag.ONE : null);
		register("NoGravity", entity -> entity.isNoGravity() ? ByteTag.ONE : null);
		register("Glowing", entity -> entity.hasGlowingTag() ? ByteTag.ONE : null);
		register("TicksFrozen", entity -> {
			int ticksFrozen = entity.getTicksFrozen();
			return ticksFrozen > 0 ? IntTag.valueOf(ticksFrozen) : null;
		});
		register("HasVisualFire", entity -> entity.hasVisualFire ? ByteTag.ONE : null);
		register("Tags", entity -> {
			Set<String> tags = entity.getTags();
			if(tags.isEmpty()) return null;
			ListTag list = new ListTag();
			for(String tag : tags) {
				list.add(StringTag.valueOf(tag));
			}
			return list;
		});
		register("Passengers", entity -> {
			ListTag list = new ListTag();
			for(Entity passenger : entity.getPassengers()) {
				CompoundTag tag = new CompoundTag();
				if(passenger.saveAsPassenger(tag)) {
					list.add(tag);
				}
			}

			return list.isEmpty() ? null : list;
		});
		register("SelectedItem", entity -> {
			if (entity instanceof ServerPlayer p) {
				ItemStack held = p.getInventory().getSelected();
				if (!held.isEmpty()) {
					return held.save(new CompoundTag());
				}
			}
			return null;
		});
	}

	private static void registerLivingEntityConverters() {
		register("Health", LivingEntity.class, entity -> FloatTag.valueOf(entity.getHealth()));
		register("HurtTime", LivingEntity.class, entity -> ShortTag.valueOf((short) entity.hurtTime));
		register("HurtByTimestamp", LivingEntity.class, entity -> IntTag.valueOf(entity.getLastHurtByMobTimestamp()));
		register("DeathTime", LivingEntity.class, entity -> ShortTag.valueOf((short) entity.deathTime));
		register("AbsorptionAmount", LivingEntity.class, entity -> FloatTag.valueOf(entity.getAbsorptionAmount()));
		register("Attributes", LivingEntity.class, entity -> entity.getAttributes().save());
		register("active_effects", LivingEntity.class, entity -> {
			Collection<MobEffectInstance> effects = entity.getActiveEffects();
			if(effects.isEmpty()) return null;

			ListTag list = new ListTag();

			for(MobEffectInstance effect : effects) {
				list.add(effect.save(new CompoundTag()));
			}

			return list;
		});
		register("FallFlying", LivingEntity.class, entity -> ByteTag.valueOf(entity.isFallFlying()));
		register("SleepingX", LivingEntity.class, entity -> entity.getSleepingPos()
				.map(blockPos -> IntTag.valueOf(blockPos.getX())).orElse(null));
		register("SleepingY", LivingEntity.class, entity -> entity.getSleepingPos()
				.map(blockPos -> IntTag.valueOf(blockPos.getY())).orElse(null));
		register("SleepingZ", LivingEntity.class, entity -> entity.getSleepingPos()
				.map(blockPos -> IntTag.valueOf(blockPos.getZ())).orElse(null));
		register("Brain", LivingEntity.class, entity -> {
			Optional<Tag> dataResult = entity.getBrain().serializeStart(NbtOps.INSTANCE).resultOrPartial(LOGGER::error);
			return dataResult.orElse(null);
		});
	}

	public static void register(String id, Function<Entity, @Nullable Tag> converter) {
		ENTITY_NBT.put(id, converter);
	}

	public static <E extends Entity> void register(String id, Class<E> clazz, Function<E, @Nullable Tag> converter) {
		register(id, entity -> clazz.isInstance(entity) ? converter.apply(clazz.cast(entity)) : null);
	}

	public static boolean hasCustomConverter(String id) {
		return ENTITY_NBT.containsKey(id);
	}

	public static @Nullable Tag get(String id, Entity entity) {
		return ENTITY_NBT.get(id).apply(entity);
	}

	private static ListTag newDoubleList(double... values) {
		ListTag list = new ListTag();
		for(double value : values) {
			list.add(DoubleTag.valueOf(value));
		}
		return list;
	}

	private static ListTag newFloatList(float... values) {
		ListTag list = new ListTag();
		for(float value : values) {
			list.add(DoubleTag.valueOf(value));
		}
		return list;
	}

	private FastNBT() {
	}
}
