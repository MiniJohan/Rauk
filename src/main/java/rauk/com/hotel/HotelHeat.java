package rauk.com.hotel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import rauk.com.entity.mind.Prey;

/**
 * Where in its hotel the players spend their time (design doc 6.5, the heat map): every second a
 * player is in a space of the hotel (a room, a corridor, a stairwell), that space warms for them;
 * the warmth fades, the last few minutes counting most and the habits of the last hour or two
 * underneath. Rauk smells where they have been (their scent soaks into the rooms they stay in):
 * it looks in the warm places more often, predicts by them, and lies in wait in them. It also
 * keeps when it last looked into each space itself, so it doesn't look in the same one twice
 * while another goes unchecked. Saved with the world, for all its Rauks.
 */
public final class HotelHeat extends SavedData {
	private static final String NAME = "rauk_heat";
	/** How fast the warmth fades, in ticks to halve: what they did lately, and their habits. */
	private static final double RECENT_HALF = 20 * 60 * 5, HABIT_HALF = 20 * 60 * 90;

	/** One player's warmth in one space: lately and by habit, as of a time. */
	private static final class Warmth {
		double recent, habit;
		long at;

		void decay(long now) {
			if (now > at) {
				double dt = now - at;
				recent *= Math.pow(0.5, dt / RECENT_HALF);
				habit *= Math.pow(0.5, dt / HABIT_HALF);
				at = now;
			}
		}
	}

	/** By player, by space (its index in the plan's areas). */
	private final Map<UUID, Map<Integer, Warmth>> warmth = new HashMap<>();
	/** When Rauk last looked into each space. */
	private final Map<Integer, Long> checked = new HashMap<>();
	private int areaCount;

	public static HotelHeat get(ServerLevel level) {
		HotelHeat heat = level.getServer().overworld().getDataStorage().computeIfAbsent(new Factory<>(HotelHeat::new, HotelHeat::load, null),
				NAME);
		int areas = HotelBuilder.plan().areas.size();
		if (heat.areaCount != areas) {
			// A different plan (another version of the mod): what it knew is about other rooms.
			heat.warmth.clear();
			heat.checked.clear();
			heat.areaCount = areas;
		}
		return heat;
	}

	/** Every second: each player in the hotel warms the space they are in. */
	public static void tick(ServerLevel level) {
		if (level.getGameTime() % 20 != 0 || HotelData.get(level).frame() == null) {
			return;
		}
		HotelHeat heat = null;
		for (Player p : level.players()) {
			if (!Prey.is(p)) {
				continue;
			}
			int area = area(level, p.position());
			if (area < 0) {
				continue;
			}
			if (heat == null) {
				heat = get(level);
			}
			heat.add(p.getUUID(), area, 1, level.getGameTime());
		}
	}

	public void add(UUID who, int area, double amount, long now) {
		Warmth w = warmth.computeIfAbsent(who, u -> new HashMap<>()).computeIfAbsent(area, a -> new Warmth());
		w.decay(now);
		w.recent += amount;
		w.habit += amount;
		if (now % 400 == 0) {
			setDirty();
		}
	}

	/** Rauk looked into a space. */
	public void checked(int area, long now) {
		if (area >= 0) {
			checked.put(area, now);
			setDirty();
		}
	}

	/** Ticks since Rauk last looked into a space (a long time if never). */
	public long sinceChecked(int area, long now) {
		Long at = checked.get(area);
		return at == null ? 20 * 60 * 60 : now - at;
	}

	/** How warm a space is for one player: lately, and a third as much by habit. In seconds, roughly. */
	public double heat(UUID who, int area, long now) {
		Map<Integer, Warmth> m = warmth.get(who);
		Warmth w = m == null ? null : m.get(area);
		if (w == null) {
			return 0;
		}
		w.decay(now);
		return w.recent + w.habit / 3;
	}

	/** How warm a space is for everyone. */
	public double heat(int area, long now) {
		double sum = 0;
		for (UUID who : warmth.keySet()) {
			sum += heat(who, area, now);
		}
		return sum;
	}

	/** The warmest spaces, for everyone or one player, warmest first, with how warm. */
	public List<double[]> warmest(@Nullable UUID who, long now, int max) {
		Map<Integer, Double> total = new HashMap<>();
		for (var e : warmth.entrySet()) {
			if (who != null && !who.equals(e.getKey())) {
				continue;
			}
			for (int area : e.getValue().keySet()) {
				total.merge(area, heat(e.getKey(), area, now), Double::sum);
			}
		}
		List<double[]> out = new ArrayList<>();
		total.forEach((a, h) -> {
			if (h > 0.5) {
				out.add(new double[] {a, h});
			}
		});
		out.sort((a, b) -> Double.compare(b[1], a[1]));
		return out.size() > max ? out.subList(0, max) : out;
	}

	/** The space of the hotel a point is in (its index in the plan's areas), or -1. */
	public static int area(ServerLevel level, Vec3 at) {
		HotelFrame frame = HotelData.get(level).frame();
		if (frame == null) {
			return -1;
		}
		HotelPlan plan = HotelBuilder.plan();
		BlockPos b = BlockPos.containing(at.add(0, 0.2, 0));
		int[] p = frame.toPlan(b);
		for (int dy : new int[] {0, 1, -1}) {
			HotelPlan.Area a = plan.owner(p[0], p[1] + dy, p[2]);
			if (a != null) {
				return plan.areas.indexOf(a);
			}
		}
		return -1;
	}

	// --- saving ---------------------------------------------------------------------------------

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		tag.putInt("areas", areaCount);
		ListTag players = new ListTag();
		for (var e : warmth.entrySet()) {
			CompoundTag p = new CompoundTag();
			p.putUUID("player", e.getKey());
			ListTag spaces = new ListTag();
			for (var w : e.getValue().entrySet()) {
				CompoundTag s = new CompoundTag();
				s.putInt("area", w.getKey());
				s.putDouble("recent", w.getValue().recent);
				s.putDouble("habit", w.getValue().habit);
				s.putLong("at", w.getValue().at);
				spaces.add(s);
			}
			p.put("spaces", spaces);
			players.add(p);
		}
		tag.put("players", players);
		CompoundTag c = new CompoundTag();
		checked.forEach((a, t) -> c.putLong(String.valueOf(a), t));
		tag.put("checked", c);
		return tag;
	}

	private static HotelHeat load(CompoundTag tag, HolderLookup.Provider registries) {
		HotelHeat heat = new HotelHeat();
		heat.areaCount = tag.getInt("areas");
		for (Tag t : tag.getList("players", Tag.TAG_COMPOUND)) {
			CompoundTag p = (CompoundTag) t;
			Map<Integer, Warmth> m = new HashMap<>();
			for (Tag st : p.getList("spaces", Tag.TAG_COMPOUND)) {
				CompoundTag s = (CompoundTag) st;
				Warmth w = new Warmth();
				w.recent = s.getDouble("recent");
				w.habit = s.getDouble("habit");
				w.at = s.getLong("at");
				m.put(s.getInt("area"), w);
			}
			heat.warmth.put(p.getUUID("player"), m);
		}
		CompoundTag c = tag.getCompound("checked");
		for (String k : c.getAllKeys()) {
			heat.checked.put(Integer.parseInt(k), c.getLong(k));
		}
		return heat;
	}
}
