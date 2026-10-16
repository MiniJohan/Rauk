package rauk.com.registry;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import rauk.com.Rauk;
import rauk.com.entity.RaukEntity;

public final class RaukEntities {
	/** Hitbox for the stalking posture (design doc 2.2); it will change with posture later. */
	public static final EntityType<RaukEntity> RAUK = Registry.register(BuiltInRegistries.ENTITY_TYPE, Rauk.id("rauk"),
			EntityType.Builder.of(RaukEntity::new, MobCategory.MONSTER)
					.sized(1.4f, 6.0f)
					.eyeHeight(4.2f)
					.clientTrackingRange(16)
					.build("rauk"));

	private RaukEntities() {
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(RAUK, RaukEntity.createAttributes());
	}
}
