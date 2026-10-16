package rauk.com.entity;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.WanderingTrader;
import rauk.com.mixin.MobAccessor;

/**
 * The rest of the hotel's life around Rauk (design doc 6.7). Anything alive that can flee flees
 * when it is near: every animal (and wandering trader) gets a goal to keep away from it once it
 * has seen it. The undead it ignores, and kills silently when they get in its way (see
 * {@link RaukEntity}).
 */
public final class OtherCreatures {
	private OtherCreatures() {
	}

	public static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof PathfinderMob mob && (entity instanceof Animal || entity instanceof WanderingTrader)) {
				((MobAccessor) mob).rauk$goals().addGoal(1, new AvoidEntityGoal<>(mob, RaukEntity.class, 14, 1.3, 1.7));
			}
		});
	}
}
