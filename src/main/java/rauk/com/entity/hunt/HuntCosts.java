package rauk.com.entity.hunt;

import java.util.HashMap;
import java.util.Map;
import java.util.function.ToDoubleFunction;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import rauk.com.entity.move.RaukPathfinder;
import rauk.com.entity.move.SurfaceNode;

/**
 * How the hunt prices a route (design doc 6.1). Stalking pays for every place the prey could see
 * it (much more in front of its face than behind it), for light, and for floors that creak or
 * click; so hidden routes along ceilings, walls, carpet and dark corners come out of the search on
 * their own. Patrolling pays only for noise. Retreating pays heavily for being seen.
 */
public final class HuntCosts implements RaukPathfinder.Costs {
	private final Level level;
	@Nullable
	private final Vec3 watcher;
	@Nullable
	private final Vec3 look;
	private final double seen, light, noise;
	private final Map<Long, Double> cache = new HashMap<>();
	/** Where it has been hurt, to keep away from. */
	@Nullable
	private ToDoubleFunction<Vec3> danger;

	/** Adds the cost of places it has been hurt before (design doc 6.6, the danger map). */
	public HuntCosts avoiding(ToDoubleFunction<Vec3> danger) {
		this.danger = danger;
		return this;
	}

	private HuntCosts(Level level, @Nullable Vec3 watcher, @Nullable Vec3 look, double seen, double light, double noise) {
		this.level = level;
		this.watcher = watcher;
		this.look = look;
		this.seen = seen;
		this.light = light;
		this.noise = noise;
	}

	/** Out of sight, out of the light, off noisy floors: the more cautious, the more it pays to stay hidden. */
	public static HuntCosts stalk(Level level, Vec3 preyEye, @Nullable Vec3 preyLook, double caution) {
		return new HuntCosts(level, preyEye, preyLook, 3 + 5 * caution, 1.0, 1.0);
	}

	/** Getting away: being seen costs most of all. */
	public static HuntCosts retreat(Level level, Vec3 preyEye, @Nullable Vec3 preyLook) {
		return new HuntCosts(level, preyEye, preyLook, 12, 0.5, 0);
	}

	/** Moving about its hotel almost silently. */
	public static HuntCosts quiet(Level level) {
		return new HuntCosts(level, null, null, 0, 0.3, 1.0);
	}

	@Override
	public double extra(SurfaceNode node) {
		long key = node.cell().asLong() * 7 + node.surface().ordinal();
		return cache.computeIfAbsent(key, k -> compute(node));
	}

	private double compute(SurfaceNode node) {
		double cost = 0;
		Vec3 body = node.position().add(0, node.posture().height / 2, 0);
		if (watcher != null && seen > 0) {
			double d = body.distanceTo(watcher);
			if (d < 32 && visible(watcher, body)) {
				double facing = 0.35;
				if (look != null) {
					double cos = look.dot(body.subtract(watcher).normalize());
					facing = cos > Math.cos(Math.toRadians(50)) ? 1 : cos > 0 ? 0.5 : 0.25;
				}
				cost += seen * facing * (1 - 0.5 * d / 32);
			}
		}
		if (danger != null) {
			cost += Math.min(6, danger.applyAsDouble(node.position()) / 10);
		}
		if (light > 0) {
			cost += light * level.getMaxLocalRawBrightness(node.cell()) / 15.0;
		}
		if (noise > 0) {
			if (node.onFloor()) {
				SoundType sound = level.getBlockState(node.cell().below()).getSoundType();
				cost += noise * (sound == SoundType.WOOL ? 0 : sound == SoundType.WOOD ? 1.0 : 0.5);
			} else {
				cost += noise * 0.15;
			}
		}
		return cost;
	}

	private boolean visible(Vec3 from, Vec3 to) {
		var hit = level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty()));
		return hit.getType() == HitResult.Type.MISS;
	}

	/** Whether a point can be seen from an eye, through glass but not walls. */
	public static boolean visibleFrom(Level level, Vec3 eye, Vec3 point) {
		var hit = level.clip(new ClipContext(eye, point, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty()));
		return hit.getType() == HitResult.Type.MISS || BlockPos.containing(hit.getLocation()).equals(BlockPos.containing(point));
	}
}
