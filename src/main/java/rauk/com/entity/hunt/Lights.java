package rauk.com.entity.hunt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import rauk.com.block.hotel.FurnitureBlock;
import rauk.com.entity.RaukEntity;
import rauk.com.hotel.HotelFurniture;
import rauk.com.entity.mind.Territory;

/**
 * Rauk and the hotel's lights (design doc 4.6). It learns which lever works which lights, by
 * trying levers itself or by seeing a light change when someone else uses one, and uses them to
 * hide (lights off along its way in), to steer (lights on where it waits) and to flush (a room's
 * lights off and on, then listening). Lanterns and candles it just knocks down. The hotel's own
 * electric lamps count as lights, its candles and oil lamps as things to knock down.
 */
public final class Lights {
	private static final int RADIUS = 12;

	private final RaukEntity rauk;
	/** For each lever it knows, the lights it works (empty if it works none). */
	private final Map<Long, Set<Long>> leverLights = new HashMap<>();
	private final List<Pending> pending = new ArrayList<>();
	private long lastAnomalySeen;

	private record Pending(BlockPos lever, Map<Long, Boolean> before, long due) {
	}

	public Lights(RaukEntity rauk) {
		this.rauk = rauk;
	}

	public static boolean isLight(BlockState state) {
		return (state.getBlock() instanceof RedstoneLampBlock || state.getBlock() instanceof CopperBulbBlock
				|| state.getBlock() instanceof FurnitureBlock f && f.piece().light == HotelFurniture.Light.ELECTRIC)
				&& state.hasProperty(BlockStateProperties.LIT);
	}

	private static Map<Long, Boolean> snapshot(ServerLevel level, BlockPos center) {
		Map<Long, Boolean> out = new HashMap<>();
		for (BlockPos p : BlockPos.betweenClosed(center.offset(-RADIUS, -RADIUS, -RADIUS), center.offset(RADIUS, RADIUS, RADIUS))) {
			BlockState s = level.getBlockState(p);
			if (isLight(s)) {
				out.put(p.asLong(), s.getValue(BlockStateProperties.LIT));
			}
		}
		return out;
	}

	/** Pulls a lever, and works out from what changes which lights it works. */
	public void pull(ServerLevel level, BlockPos lever) {
		BlockState state = level.getBlockState(lever);
		if (!(state.getBlock() instanceof LeverBlock block)) {
			return;
		}
		long now = level.getGameTime();
		Map<Long, Boolean> before = snapshot(level, lever);
		Territory territory = rauk.mind().territory;
		territory.ignoreChangesNear(lever, RADIUS + 2, now + 40);
		block.pull(state, level, lever, null);
		territory.expect(lever, level.getBlockState(lever));
		pending.add(new Pending(lever.immutable(), before, now + 8));
	}

	public void tick(ServerLevel level, long now) {
		pending.removeIf(p -> {
			if (now < p.due()) {
				return false;
			}
			Map<Long, Boolean> after = snapshot(level, p.lever());
			Set<Long> works = new HashSet<>();
			for (Map.Entry<Long, Boolean> e : after.entrySet()) {
				Boolean was = p.before().get(e.getKey());
				if (was != null && !was.equals(e.getValue())) {
					works.add(e.getKey());
				}
			}
			leverLights.merge(p.lever().asLong(), works, (a, b) -> b.isEmpty() ? a : b);
			return true;
		});
		// Watching someone else use a lever: the lights near it that are no longer as it remembers them.
		Territory territory = rauk.mind().territory;
		for (Territory.Anomaly a : territory.anomalies) {
			if (a.kind() == Territory.Kind.LEVER && a.time() > lastAnomalySeen && now - a.time() > 8) {
				lastAnomalySeen = a.time();
				Set<Long> works = new HashSet<>();
				for (Map.Entry<Long, Boolean> e : snapshot(level, a.pos()).entrySet()) {
					BlockState was = territory.remembered(BlockPos.of(e.getKey()));
					if (was != null && isLight(was) && was.getValue(BlockStateProperties.LIT) != e.getValue()) {
						works.add(e.getKey());
					}
				}
				if (!works.isEmpty()) {
					leverLights.put(a.pos().asLong(), works);
				}
			}
		}
	}

	/**
	 * A lever within reach that would make the lights near an area the way it wants them (on or
	 * off), or one it hasn't tried yet that might. Null if there is none.
	 */
	@Nullable
	public BlockPos leverFor(ServerLevel level, Vec3 area, double areaRadius, boolean wantOn, double maxFromRauk) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		Territory territory = rauk.mind().territory;
		for (BlockPos lever : territory.known(rauk.blockPosition(), maxFromRauk, s -> s.getBlock() instanceof LeverBlock)) {
			Set<Long> works = leverLights.get(lever.asLong());
			boolean useful;
			if (works == null) {
				// Never tried: worth a try if there are lights the wrong way near it and near the area.
				useful = Vec3.atCenterOf(lever).distanceTo(area) < areaRadius + 8 && anyLight(level, lever, 8, !wantOn);
			} else {
				useful = false;
				for (long l : works) {
					BlockPos p = BlockPos.of(l);
					BlockState s = level.getBlockState(p);
					if (isLight(s) && s.getValue(BlockStateProperties.LIT) != wantOn && Vec3.atCenterOf(p).distanceTo(area) < areaRadius) {
						useful = true;
						break;
					}
				}
			}
			double d = Vec3.atCenterOf(lever).distanceTo(rauk.position());
			if (useful && d < bestDistance) {
				best = lever;
				bestDistance = d;
			}
		}
		return best;
	}

	/** Whether the lever is known to work lights, and they are within range of a point. */
	public boolean worksLightsNear(BlockPos lever, Vec3 at, double range) {
		Set<Long> works = leverLights.get(lever.asLong());
		if (works == null) {
			return false;
		}
		for (long l : works) {
			if (Vec3.atCenterOf(BlockPos.of(l)).distanceTo(at) < range) {
				return true;
			}
		}
		return false;
	}

	private static boolean anyLight(ServerLevel level, BlockPos center, int radius, boolean lit) {
		for (BlockPos p : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
			BlockState s = level.getBlockState(p);
			if (isLight(s) && s.getValue(BlockStateProperties.LIT) == lit) {
				return true;
			}
		}
		return false;
	}

	/** A lantern or lit candle within reach of its hands, to knock down. */
	@Nullable
	public static BlockPos lampWithinReach(ServerLevel level, Vec3 hands) {
		BlockPos c = BlockPos.containing(hands);
		for (BlockPos p : BlockPos.betweenClosed(c.offset(-2, -2, -2), c.offset(2, 2, 2))) {
			BlockState s = level.getBlockState(p);
			if (s.getBlock() instanceof LanternBlock
					|| (s.getBlock() instanceof CandleBlock && s.getValue(BlockStateProperties.LIT))
					|| (s.getBlock() instanceof FurnitureBlock f && f.piece().light == HotelFurniture.Light.FLAME
							&& f.piece().form != HotelFurniture.Form.MULTI && s.getValue(BlockStateProperties.LIT))) {
				return p.immutable();
			}
		}
		return null;
	}

	// --- saving ---------------------------------------------------------------------------------

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		for (Map.Entry<Long, Set<Long>> e : leverLights.entrySet()) {
			tag.putLongArray(Long.toString(e.getKey()), e.getValue().stream().mapToLong(Long::longValue).toArray());
		}
		return tag;
	}

	public void load(CompoundTag tag) {
		leverLights.clear();
		for (String key : tag.getAllKeys()) {
			Set<Long> works = new HashSet<>();
			for (long l : tag.getLongArray(key)) {
				works.add(l);
			}
			try {
				leverLights.put(Long.parseLong(key), works);
			} catch (NumberFormatException ignored) {
				// Not a lever entry.
			}
		}
	}
}
