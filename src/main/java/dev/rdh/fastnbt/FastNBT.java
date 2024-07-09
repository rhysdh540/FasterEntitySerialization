package dev.rdh.fastnbt;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Component.Serializer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public abstract class FastNBT {
	public static final String ID = "fastnbt";
	public static final Logger LOGGER = LogManager.getLogger(ID);

	private static final Map<String, Function<Entity, @Nullable Tag>> ENTITY_NBT = new HashMap<>();

	public static void init() {
		registerBaseEntityConverters();
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

	public static void register(String id, Function<Entity, @Nullable Tag> nbt) {
		ENTITY_NBT.put(id, nbt);
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
