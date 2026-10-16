package rauk.com.entity.nav;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import rauk.com.entity.move.RaukPathfinder;
import rauk.com.entity.move.SurfaceNode;

/**
 * A* over Rauk's places ({@link NavRules}): the cheapest way from one place to a goal, by what the
 * hunt pays for each step (design doc 6.1), keeping to the places it is allowed.
 */
public final class NavSearch {
	private NavSearch() {
	}

	/** Where it is going: when it is there, and a guess (never too high) at what is left. */
	public interface Goal {
		boolean reached(SurfaceNode n);

		double estimate(SurfaceNode n);

		/** Within reach of a point, on the given surface or any. */
		static Goal near(Vec3 at, Direction surface, double reach) {
			return new Goal() {
				@Override
				public boolean reached(SurfaceNode n) {
					return n.center().distanceTo(at) <= reach && (surface == null || n.surface() == surface);
				}

				@Override
				public double estimate(SurfaceNode n) {
					return Math.max(0, n.center().distanceTo(at) - reach);
				}
			};
		}
	}

	/**
	 * A way found: the places along it, whether it gets there or only as near as it could, and if
	 * not, whether that was for want of time (the way may well go on) rather than of ways.
	 */
	public record Result(List<SurfaceNode> path, boolean reached, boolean timedOut) {
		public static final Result NONE = new Result(List.of(), false, false);
	}

	/** A place, by its cell and side. */
	public record Key(long cell, int surface) {
		public static Key of(SurfaceNode n) {
			return new Key(n.cell().asLong(), n.surface().ordinal());
		}
	}

	private record Open(SurfaceNode node, double g, double f) {
	}

	/** How long a search may take before it settles for as near as it has got: it never holds up the game. */
	public static final long BUDGET = 25_000_000;

	/**
	 * The way from a place to a goal. Places the filter turns down aren't used; the search gives up
	 * after so many places, so far from the start or so long (see {@link #BUDGET}), and then gives
	 * the way to the place nearest the goal it got to (empty if it got no nearer).
	 */
	public static Result find(NavRules rules, SurfaceNode start, Goal goal, Predicate<SurfaceNode> allowed, RaukPathfinder.Costs costs,
			int maxNodes, double maxRange) {
		long until = System.nanoTime() + BUDGET;
		Map<Key, Double> best = new HashMap<>();
		Map<Key, SurfaceNode> from = new HashMap<>();
		PriorityQueue<Open> open = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));
		best.put(Key.of(start), 0.0);
		double h0 = goal.estimate(start);
		open.add(new Open(start, 0, h0));
		SurfaceNode closest = start;
		double closestH = h0;
		int expanded = 0;
		boolean late = false;
		Vec3 origin = start.center();
		while (!open.isEmpty() && expanded < maxNodes) {
			Open o = open.poll();
			Key k = Key.of(o.node);
			if (o.g > best.getOrDefault(k, Double.MAX_VALUE)) {
				continue;
			}
			expanded++;
			if ((expanded & 127) == 0 && System.nanoTime() > until) {
				late = true;
				break;
			}
			if (goal.reached(o.node)) {
				return new Result(build(o.node, from), true, false);
			}
			double h = o.f - o.g;
			if (h < closestH - 0.01) {
				closest = o.node;
				closestH = h;
			}
			for (NavRules.Move m : rules.moves(o.node)) {
				SurfaceNode to = m.to();
				if (to.center().distanceTo(origin) > maxRange || !allowed.test(to)) {
					continue;
				}
				Key mk = Key.of(to);
				double step = m.cost() + costs.extra(to) * Math.max(0.5, o.node.position().distanceTo(to.position()));
				double g = o.g + step;
				if (g < best.getOrDefault(mk, Double.MAX_VALUE)) {
					best.put(mk, g);
					from.put(mk, o.node);
					open.add(new Open(to, g, g + goal.estimate(to)));
				}
			}
		}
		return closest == start ? new Result(List.of(), false, late) : new Result(build(closest, from), false, late);
	}

	private static List<SurfaceNode> build(SurfaceNode end, Map<Key, SurfaceNode> from) {
		List<SurfaceNode> out = new ArrayList<>();
		for (SurfaceNode n = end; n != null; n = from.get(Key.of(n))) {
			out.add(n);
		}
		Collections.reverse(out);
		return out;
	}

	/** The cells along a way, for keeping out of them for a while when it gets stuck there. */
	public static List<BlockPos> cells(List<SurfaceNode> path) {
		List<BlockPos> out = new ArrayList<>(path.size());
		for (SurfaceNode n : path) {
			out.add(n.cell());
		}
		return out;
	}
}
