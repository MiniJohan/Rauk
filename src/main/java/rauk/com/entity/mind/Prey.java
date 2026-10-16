package rauk.com.entity.mind;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What Rauk counts as prey: players in survival or adventure, and any other creature tagged
 * {@code rauk_prey} (design doc 6.7: anything warm-blooded; what else lives in the hotel is up to
 * the hotel pass). Creative players are left alone, like vanilla monsters do, unless switched on
 * for testing ({@code /rauk prey creative on}).
 */
public final class Prey {
	public static final String TAG = "rauk_prey";
	/** Whether creative players count, for testing. */
	public static boolean huntCreative;

	private Prey() {
	}

	public static boolean is(Entity e) {
		if (!(e instanceof LivingEntity living) || !living.isAlive()) {
			return false;
		}
		if (e instanceof Player p) {
			return !p.isSpectator() && (huntCreative || !p.isCreative());
		}
		return e.getTags().contains(TAG);
	}

	/** Every prey within range of a point. */
	public static List<LivingEntity> around(ServerLevel level, Vec3 at, double range) {
		List<LivingEntity> out = new ArrayList<>();
		for (Player p : level.players()) {
			if (is(p) && p.position().distanceToSqr(at) < range * range) {
				out.add(p);
			}
		}
		out.addAll(level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(range),
				e -> !(e instanceof Player) && is(e)));
		return out;
	}
}
