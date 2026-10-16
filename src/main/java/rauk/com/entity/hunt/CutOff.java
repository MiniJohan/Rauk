package rauk.com.entity.hunt;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntPredicate;

import org.jetbrains.annotations.Nullable;

import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import rauk.com.Rauk;
import rauk.com.entity.RaukEntity;
import rauk.com.hotel.HotelGraph;
import rauk.com.hotel.HotelHeat;

/**
 * Cutting a prey off (design doc 6.3, outsmarting you): it doesn't run behind you if it can be
 * where you're going first. From where the prey is, every doorway it can get to on foot soon
 * (stairs and doors, no walls or shafts) is weighed by how well it lines up with the way the prey is
 * running, how straight on its way it lies, and how much time the prey has spent in the space
 * beyond (the heat map). For the likeliest few, Rauk works out how soon it can be just through the
 * doorway by its own ways (up walls, through shafts and crawlspaces), never through the space the
 * prey is in; the likeliest it beats the prey to, it goes for, and waits there.
 */
final class CutOff {
	/** Where to wait (just through a doorway the prey is making for), why, how sure, where not to go on the way, and the race. */
	record Plan(Vec3 at, String why, double score, Set<Vec3> shun, double theirs, double mine) {
	}

	private CutOff() {
	}

	private static final boolean DEBUG = Boolean.getBoolean("rauk.devshot");

	/**
	 * The best cut-off for a prey seen at a place going a way (blocks a second), lost from sight a
	 * number of seconds ago (it is that much further on); null if there is none it can win, or no
	 * hotel map here.
	 */
	@Nullable
	static Plan plan(ServerLevel level, RaukEntity rauk, UUID who, Vec3 from, Vec3 velocity, double lostFor) {
		HotelGraph graph = HotelGraph.get(level);
		if (graph == null) {
			return null;
		}
		int preyPlace = graph.placeNear(from), raukPlace = graph.placeNear(rauk.position());
		if (preyPlace < 0 || raukPlace < 0) {
			return null;
		}
		int preyPart = graph.part(preyPlace), raukPart = graph.part(raukPlace);
		long now = level.getGameTime();
		Int2DoubleOpenHashMap foot = graph.onFoot(from, 90);
		Vec3 flat = new Vec3(velocity.x, 0, velocity.z);
		Vec3 heading = flat.length() > 0.6 ? flat.normalize() : null;
		double theirSpeed = Mth.clamp(velocity.length(), 4.3, 7);
		boolean climbing = Math.abs(velocity.y) > 0.6;
		HotelHeat heat = HotelHeat.get(level);
		// What it reckons already (through the stairwell's door, out at the top; their room; a bolt-hole):
		// the doorways near there count for more.
		Prediction.Guess guess = Prediction.destination(level, rauk, who, from, velocity);

		List<double[]> candidates = new ArrayList<>();
		for (HotelGraph.Gate g : graph.gates) {
			if (g.to() == preyPart) {
				continue;
			}
			Vec3 in = graph.node(g.in()).position();
			double onFoot = graph.onFootTo(foot, in);
			if (onFoot < 5 || onFoot > 90) {
				continue;
			}
			Vec3 to = new Vec3(in.x - from.x, 0, in.z - from.z);
			double d = to.length();
			double align = heading == null || d < 1 ? 0.5 : heading.dot(to.scale(1 / d));
			if (align < 0.25) {
				continue;
			}
			double straight = Mth.clamp(Math.max(d, Math.abs(in.y - from.y) * 2) / onFoot, 0.2, 1);
			int area = graph.area(g.to());
			double warm = heat.heat(who, area, now) + 0.3 * heat.heat(area, now);
			double weight = align * align * straight * (1 + Math.min(2, warm / 60)) / (1 + onFoot / 35);
			if (climbing) {
				// Up or down the stairs: the doorways on the floor they're making for.
				double dy = in.y - from.y;
				weight *= dy * velocity.y > 0 && Math.abs(dy) > 2 ? 2.5 : 0.4;
			}
			if (guess != null && in.distanceTo(guess.at()) < 5) {
				weight *= 3;
			}
			candidates.add(new double[] {g.id(), weight, onFoot});
		}
		candidates.sort((a, b) -> Double.compare(b[1], a[1]));

		double myPace = rauk.panting() ? 4.5 : 9.5;
		IntPredicate avoid = p -> p == preyPart && raukPart != preyPart;
		Set<Vec3> shun = raukPart != preyPart ? Set.of(from) : Set.of();
		Plan best = null;
		for (int k = 0; k < Math.min(6, candidates.size()); k++) {
			double[] c = candidates.get(k);
			HotelGraph.Gate g = graph.gates.get((int) c[0]);
			Vec3 in = graph.node(g.in()).position();
			HotelGraph.Field f = graph.fieldTo(in, avoid, x -> false, now);
			if (f == null) {
				continue;
			}
			double mineCost = f.from(raukPlace);
			if (mineCost >= Double.MAX_VALUE) {
				continue;
			}
			double mine = mineCost / myPace, theirs = c[2] / theirSpeed - lostFor;
			String name = graph.plan.areas.get(g.to() < 0 ? 0 : graph.area(g.to())).name();
			if (DEBUG) {
				Rauk.LOGGER.info("  cut off at the way into {}? weight {}, theirs {}s on foot, mine {}s", name, String.format("%.2f", c[1]),
						String.format("%.1f", theirs), String.format("%.1f", mine));
			}
			// Even a moment ahead of them is enough: they come through the door into it.
			if (theirs < 0.8 || mine + 0.4 >= theirs) {
				continue;
			}
			// How likely they go that way counts most; how far ahead of them it would be, a little.
			double score = c[1] * (1 + 0.25 * Math.min(1, (theirs - mine) / 3));
			if (best == null || score > best.score()) {
				best = new Plan(in, "the way into " + name, score, shun, theirs, mine);
			}
		}
		return best;
	}
}
