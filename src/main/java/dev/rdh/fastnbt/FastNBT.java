package dev.rdh.fastnbt;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import net.minecraft.SharedConstants;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Component.Serializer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.warden.WardenSpawnTracker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public abstract class FastNBT {
	public static final String ID = "fastnbt";
	public static final Logger LOGGER = LogManager.getLogger(ID);

	private static final Map<String, Function<Entity, @Nullable Tag>> CONVERTERS = new Object2ObjectOpenHashMap<>();

	public static void init() {
		registerBaseEntityConverters();
		registerLivingEntityConverters();
		registerPlayerConverters();
		registerServerPlayerConverters();
	}

	/**
	 * @see net.minecraft.world.entity.Entity#saveWithoutId
	 */
	private static void registerBaseEntityConverters() {
		register("Pos", entity -> {
			Entity toSave = entity.getVehicle() == null ? entity : entity.getVehicle();
			return new ListTag(
					List.of(
							DoubleTag.valueOf(toSave.getX()),
							DoubleTag.valueOf(toSave.getY()),
							DoubleTag.valueOf(toSave.getZ())
					),
					DoubleTag.ZERO.getId()
			);
		});
		register("Motion", entity -> {
			Vec3 motion = entity.getDeltaMovement();
			return new ListTag(
					List.of(
							DoubleTag.valueOf(motion.x),
							DoubleTag.valueOf(motion.y),
							DoubleTag.valueOf(motion.z)
					),
					DoubleTag.ZERO.getId()
			);
		});
		register("Rotation", entity -> new ListTag(
				List.of(
						FloatTag.valueOf(entity.getYRot()),
						FloatTag.valueOf(entity.getXRot())
				),
				FloatTag.ZERO.getId()
		));
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

	/**
	 * @see net.minecraft.world.entity.LivingEntity#addAdditionalSaveData
	 */
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

	/**
	 * @see net.minecraft.world.entity.player.Player#addAdditionalSaveData
	 */
	private static void registerPlayerConverters() {
		register("DataVersion", Player.class, entity -> // why is this in Player?
				IntTag.valueOf(SharedConstants.getCurrentVersion().getDataVersion().getVersion()));
		register("Inventory", Player.class, entity -> entity.getInventory().save(new ListTag()));
		register("SelectedItemSlot", Player.class, entity -> IntTag.valueOf(entity.getInventory().selected));
		register("SleepTimer", Player.class, entity -> ShortTag.valueOf((short) entity.getSleepTimer()));
		register("XpP", Player.class, entity -> FloatTag.valueOf(entity.experienceProgress));
		register("XpLevel", Player.class, entity -> IntTag.valueOf(entity.experienceLevel));
		register("XpTotal", Player.class, entity -> IntTag.valueOf(entity.totalExperience));
		register("XpSeed", Player.class, entity -> LongTag.valueOf(entity.getEnchantmentSeed()));
		register("Score", Player.class, entity -> IntTag.valueOf(entity.getScore()));

		// food data
		register("foodLevel", Player.class, entity -> IntTag.valueOf(entity.getFoodData().getFoodLevel()));
		register("foodTickTimer", Player.class, entity -> IntTag.valueOf(entity.getFoodData().tickTimer));
		register("foodSaturationLevel", Player.class, entity -> FloatTag.valueOf(entity.getFoodData().getSaturationLevel()));
		register("foodExhaustionLevel", Player.class, entity -> FloatTag.valueOf(entity.getFoodData().getExhaustionLevel()));

		register("abilities", Player.class, entity -> {
			CompoundTag tag = new CompoundTag();
			entity.getAbilities().addSaveData(tag);
			return tag.get("abilities");
		});
		register("EnderItems", Player.class, entity -> entity.getEnderChestInventory().createTag());
		register("ShoulderEntityLeft", Player.class, entity -> entity.getShoulderEntityLeft().isEmpty() ? null : entity.getShoulderEntityLeft());
		register("ShoulderEntityRight", Player.class, entity -> entity.getShoulderEntityRight().isEmpty() ? null : entity.getShoulderEntityRight());
		register("LastDeathLocation", Player.class, entity -> entity.getLastDeathLocation()
				.flatMap(arg -> GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, arg).resultOrPartial(LOGGER::error))
				.orElse(null)
		);
	}

	/**
	 * @see net.minecraft.server.level.ServerPlayer#addAdditionalSaveData
	 */
	public static void registerServerPlayerConverters() {
		register("warden_spawn_tracker", ServerPlayer.class, entity ->
				entity.getWardenSpawnTracker().flatMap(spawnTracker ->
						WardenSpawnTracker.CODEC.encodeStart(NbtOps.INSTANCE, spawnTracker)
								.resultOrPartial(LOGGER::error)
				).orElse(null));
		register("playerGameType", ServerPlayer.class, entity -> IntTag.valueOf(entity.gameMode.getGameModeForPlayer().getId()));
		register("previousPlayerGameType", ServerPlayer.class, entity -> {
			var gameMode = entity.gameMode.getPreviousGameModeForPlayer();
			return gameMode == null ? null : IntTag.valueOf(gameMode.getId());
		});
		register("seenCredits", ServerPlayer.class, entity -> ByteTag.valueOf(entity.seenCredits));
		register("enteredNetherPosition", ServerPlayer.class, entity -> {
			if(entity.enteredNetherPosition == null) return null;
			CompoundTag tag = new CompoundTag();
			tag.put("x", DoubleTag.valueOf(entity.enteredNetherPosition.x));
			tag.put("y", DoubleTag.valueOf(entity.enteredNetherPosition.y));
			tag.put("z", DoubleTag.valueOf(entity.enteredNetherPosition.z));
			return tag;
		});
		register("RootVehicle", ServerPlayer.class, entity -> {
			Entity rootVehicle = entity.getRootVehicle();
			Entity vehicle = entity.getVehicle();
			if (vehicle != null && rootVehicle != entity && rootVehicle.hasExactlyOnePlayerPassenger()) {
				CompoundTag tag = new CompoundTag();
				CompoundTag entityTag = new CompoundTag();
				rootVehicle.save(entityTag);
				tag.putUUID("Attach", vehicle.getUUID());
				tag.put("Entity", entityTag);
				return tag;
			}
			return null;
		});
		register("recipeBook", ServerPlayer.class, entity -> entity.getRecipeBook().toNbt());
		register("Dimension", ServerPlayer.class, entity -> StringTag.valueOf(entity.level().dimension().location().toString()));
		register("SpawnX", ServerPlayer.class, entity -> entity.getRespawnPosition() == null ? null : IntTag.valueOf(entity.getRespawnPosition().getX()));
		register("SpawnY", ServerPlayer.class, entity -> entity.getRespawnPosition() == null ? null : IntTag.valueOf(entity.getRespawnPosition().getY()));
		register("SpawnZ", ServerPlayer.class, entity -> entity.getRespawnPosition() == null ? null : IntTag.valueOf(entity.getRespawnPosition().getZ()));
		register("SpawnForced", ServerPlayer.class, entity -> entity.getRespawnPosition() == null ? null : ByteTag.valueOf(entity.isRespawnForced()));
		register("SpawnAngle", ServerPlayer.class, entity -> entity.getRespawnPosition() == null ? null : FloatTag.valueOf(entity.getRespawnAngle()));
		register("SpawnDimension", ServerPlayer.class, entity -> entity.getRespawnPosition() == null ? null : ResourceLocation.CODEC
				.encodeStart(NbtOps.INSTANCE, entity.getRespawnDimension().location())
				.resultOrPartial(LOGGER::error)
				.orElse(null));
	}

	public static void register(String id, Function<Entity, @Nullable Tag> converter) {
		CONVERTERS.put(id, converter);
	}

	public static <E extends Entity> void register(String id, Class<E> clazz, Function<E, @Nullable Tag> converter) {
		register(id, entity -> clazz.isInstance(entity) ? converter.apply(clazz.cast(entity)) : null);
	}

	public static boolean hasCustomConverter(String id) {
		return CONVERTERS.containsKey(id);
	}

	public static @Nullable Tag get(String id, Entity entity) {
		return CONVERTERS.get(id).apply(entity);
	}

	private FastNBT() {
	}
}
