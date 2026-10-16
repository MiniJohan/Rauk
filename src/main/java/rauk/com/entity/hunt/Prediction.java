package rauk.com.entity.hunt;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import rauk.com.Rauk;
import rauk.com.entity.RaukEntity;
import rauk.com.entity.mind.PlayerProfile;
import rauk.com.entity.move.RaukPathfinder;
import rauk.com.entity.move.SurfaceNode;
import rauk.com.hotel.HotelBuilder;
import rauk.com.hotel.HotelData;
import rauk.com.hotel.HotelFrame;
import rauk.com.hotel.HotelGraph;
import rauk.com.hotel.HotelHeat;
import rauk.com.hotel.HotelPlan;

/**
 * Where a prey is going (design doc 6.4, 6.5). Rauk doesn't just follow: it reckons where you are
 * headed from what it has learned of you (the places you spend your time, by the heat map; the
 * places it keeps finding you; the bolt-holes you ran to before) and from which way you are running
 * now, and the doors that way, and goes there first.
 */
final class Prediction {
	/**
	 * A guess at where the prey will be: the floor there, why it thinks so, and how sure it is; and
	 * places in the spaces the prey goes through to get there (a stairwell), which a cut-off keeps
	 * out of.
	 */
	record Guess(Vec3 at, String why, double score, Set<Vec3> through) {
		Guess(Vec3 at, String why, double score) {
			this(at, why, score, Set.of());
		}
	}

	private record Candidate(Vec3 at, double weight, String why) {
	}

	private Prediction() {
	}

	/**
	 * The most likely place the prey is going: weighed by how much each known place counts and, if
	 * it is moving, by how well it lines up with the way it is going. Nothing closer than a few
	 * blocks to where it is now counts.
	 */
	@Nullable
	static Guess destination(ServerLevel level, RaukEntity rauk, UUID who, Vec3 from, Vec3 velocity) {
		Guess stairs = stairs(level, from, velocity);
		if (stairs != null) {
			return stairs;
		}
		if (velocity.horizontalDistance() > 1) {
			// On the move, right at a stairwell's door: through it.
			Guess door = throughStairwell(level, from, from, 1);
			if (door != null) {
				return door;
			}
		}
		Guess guess = around(level, rauk, who, from, velocity);
		if (guess != null && guess.why().equals("the door ahead")) {
			// Making for a stairwell's door: it goes on through, to come out on the next floor.
			Guess on = throughStairwell(level, guess.at(), from, 2);
			if (on != null) {
				return on;
			}
		}
		return guess;
	}

	/** The kind of space a section of the hotel's map is. */
	private static HotelPlan.Kind kind(HotelGraph graph, int part) {
		return graph.plan.areas.get(graph.area(part)).kind();
	}

	/**
	 * If there is a way near a place into a stairwell (not the space the prey is in): where that
	 * stairwell comes out on the next floor (up, mostly), else null.
	 */
	@Nullable
	static Guess throughStairwell(ServerLevel level, Vec3 near, Vec3 from, int reach) {
		HotelGraph graph = HotelGraph.get(level);
		if (graph == null) {
			return null;
		}
		int here = graph.partAt(from);
		if (here < 0) {
			return null;
		}
		for (int id : graph.gatesOut(here)) {
			HotelGraph.Gate g = graph.gates.get(id);
			if (g.at().distanceTo(near) > reach + 1.5 || kind(graph, g.to()) != HotelPlan.Kind.STAIRS) {
				continue;
			}
			Guess on = stairs(level, graph.node(g.in()).position(), Vec3.ZERO);
			if (on != null) {
				return on;
			}
		}
		return null;
	}

	/** The most likely place on its floor: where they spend their time, their haunts, their bolt-holes, the doors ahead, straight on. */
	@Nullable
	private static Guess around(ServerLevel level, RaukEntity rauk, UUID who, Vec3 from, Vec3 velocity) {
		PlayerProfile profile = rauk.mind().profile(who);
		BlockPos here = BlockPos.containing(from);
		Vec3 flat = new Vec3(velocity.x, 0, velocity.z);
		Vec3 heading = flat.length() > 0.6 ? flat.normalize() : null;

		List<Candidate> candidates = new ArrayList<>();
		// The heat map: the spaces they have spent the most time in lately (their scent is all over them).
		HotelFrame frame = HotelData.get(level).frame();
		if (frame != null) {
			HotelPlan plan = HotelBuilder.plan();
			for (double[] w : HotelHeat.get(level).warmest(who, level.getGameTime(), 8)) {
				if (w[1] < 20) {
					break;
				}
				HotelPlan.Area a = plan.areas.get((int) w[0]);
				BlockPos c = frame.toWorld((int) Math.floor(a.centerX()), a.y0(), (int) Math.floor(a.centerZ()));
				candidates.add(new Candidate(Vec3.atBottomCenterOf(c), 2.4 * Math.min(1, w[1] / 120), "where they keep going"));
			}
		}
		List<PlayerProfile.Place> haunts = profile.haunts(here, 48);
		// A haunt counts fully once it has found them there for the equivalent of a few seconds' looking.
		for (int i = 0; i < Math.min(8, haunts.size()); i++) {
			PlayerProfile.Place p = haunts.get(i);
			candidates.add(new Candidate(Vec3.atBottomCenterOf(p.at()), 2.0 * Math.min(1, p.weight() / 80), i == 0 ? "their place" : "a haunt"));
		}
		List<PlayerProfile.Place> holes = profile.boltHoles(here, 48);
		// A bolt-hole counts for a lot the first time, and fully the second.
		for (int i = 0; i < Math.min(6, holes.size()); i++) {
			PlayerProfile.Place p = holes.get(i);
			candidates.add(new Candidate(Vec3.atBottomCenterOf(p.at()), 2.2 * Math.min(1, 0.6 + 0.4 * (p.weight() - 1)), "bolt-hole"));
		}
		if (heading != null) {
			for (BlockPos door : rauk.mind().territory.known(here, 20,
					s -> s.getBlock() instanceof DoorBlock && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER)) {
				// Doors on its own floor: the ones over and under its head aren't ahead of it.
				if (Math.abs(door.getY() - from.y) < 2.5) {
					candidates.add(new Candidate(Vec3.atBottomCenterOf(door), 0.7, "the door ahead"));
				}
			}
			candidates.add(new Candidate(from.add(heading.scale(12)), 0.3, "where it's running"));
		}

		Candidate best = null;
		double bestScore = 0;
		for (Candidate c : candidates) {
			Vec3 to = c.at().subtract(from).multiply(1, 0, 1);
			double d = to.length();
			// Nothing right where it is counts, except the door it's about to go through; nor anything on another floor far off.
			if (d < (c.why().equals("the door ahead") ? 1 : 4) || d > 64 || Math.abs(c.at().y - from.y) > 16) {
				continue;
			}
			double align = 1;
			if (heading != null) {
				align = heading.dot(to.scale(1 / d));
				if (align < 0.35) {
					continue;
				}
				align *= align;
			}
			double score = c.weight() * align / (1 + d / 24);
			if (Boolean.getBoolean("rauk.devshot")) {
				Rauk.LOGGER.info("  guess {} at {}: {} blocks, lined up {}, score {}", c.why(), BlockPos.containing(c.at()).toShortString(),
						String.format("%.1f", d), String.format("%.2f", align), String.format("%.2f", score));
			}
			if (score > bestScore) {
				bestScore = score;
				best = c;
			}
		}
		if (best == null) {
			return null;
		}
		RaukPathfinder finder = new RaukPathfinder(level);
		BlockPos at = BlockPos.containing(best.at());
		for (BlockPos p : BlockPos.withinManhattan(at, 3, 3, 3)) {
			SurfaceNode node = finder.node(p, Direction.DOWN);
			if (node != null) {
				return new Guess(node.position(), best.why(), bestScore);
			}
		}
		return new Guess(best.at(), best.why(), bestScore);
	}

	/**
	 * Going up or down (a stair, a flight, a stairwell): it comes out at the nearest way out that
	 * way, the door at the top or the foot of it. The guess is the floor just through it, on the
	 * far side, where something waiting by the door takes whoever comes out.
	 */
	@Nullable
	private static Guess stairs(ServerLevel level, Vec3 from, Vec3 velocity) {
		HotelGraph graph = HotelGraph.get(level);
		if (graph == null) {
			return null;
		}
		int here = graph.partAt(from);
		if (here < 0) {
			return null;
		}
		boolean climbing = Math.abs(velocity.y) >= 0.6;
		if (!climbing && kind(graph, here) != HotelPlan.Kind.STAIRS) {
			return null;
		}
		// Climbing, the nearest way out that way; in a stairwell and not yet climbing, the nearest
		// floor up (people run up, mostly), or down.
		HotelGraph.Gate way = null;
		Vec3 out = null;
		double wayScore = Double.MAX_VALUE;
		for (int id : graph.gatesOut(here)) {
			HotelGraph.Gate g = graph.gates.get(id);
			Vec3 in = graph.node(g.in()).position();
			double rise = in.y - from.y;
			if (Math.abs(rise) <= 2.5 || climbing && rise * velocity.y <= 0 || kind(graph, g.to()) == HotelPlan.Kind.LIFT
					|| kind(graph, g.to()) == HotelPlan.Kind.CRAWL) {
				continue;
			}
			double score = Math.abs(rise) + (rise < 0 ? 3 : 0);
			if (score < wayScore) {
				wayScore = score;
				way = g;
				out = in;
			}
		}
		if (way == null) {
			return null;
		}
		return new Guess(out, out.y > from.y ? "the top of the stairs" : "the foot of the stairs", 3.0, Set.of(from));
	}
}
