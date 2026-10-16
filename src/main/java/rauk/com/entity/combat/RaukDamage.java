package rauk.com.entity.combat;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import rauk.com.Rauk;

/**
 * Rauk's own damage types (data/rauk/damage_type). The bite ignores armor, enchantments and
 * shields, and a Totem of Undying doesn't save you from it (design doc 7.2). The grip is the
 * steady damage of being held.
 */
public final class RaukDamage {
	public static final ResourceKey<DamageType> BITE = ResourceKey.create(Registries.DAMAGE_TYPE, Rauk.id("bite"));
	public static final ResourceKey<DamageType> GRIP = ResourceKey.create(Registries.DAMAGE_TYPE, Rauk.id("grip"));

	private RaukDamage() {
	}

	public static DamageSource of(Entity rauk, ResourceKey<DamageType> type) {
		var holder = rauk.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type);
		return new DamageSource(holder, rauk);
	}
}
