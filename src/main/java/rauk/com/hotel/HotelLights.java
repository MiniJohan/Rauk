package rauk.com.hotel;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import rauk.com.block.hotel.FurnitureBlock;
import rauk.com.block.hotel.HotelSwitch;
import rauk.com.hotel.HotelFurniture.Light;
import rauk.com.hotel.HotelPlan.Circuit;

/**
 * The hotel's electric light (hotel design 6.3): its lamps are wired room by room to the switch by
 * the door, and light only while the hotel has power. Pulling a switch (you, or Rauk) works its
 * circuit; the power coming on lights every circuit whose switch is on, and going off puts them
 * all out. A lamp standing on its own can be clicked on and off, but only with power.
 */
public final class HotelLights {
	private static Map<Long, Circuit> bySwitch;

	private HotelLights() {
	}

	public static void init() {
		HotelSwitch.wiring = HotelLights::switched;
		FurnitureBlock.power = (level, pos) -> {
			if (!(level instanceof ServerLevel server)) {
				return true;
			}
			HotelData data = HotelData.get(server);
			HotelFrame frame = data.frame();
			return frame == null || !frame.inBuilding(pos) || data.power();
		};
	}

	private static synchronized Map<Long, Circuit> circuits() {
		if (bySwitch == null) {
			bySwitch = new HashMap<>();
			for (Circuit c : HotelBuilder.plan().circuits) {
				bySwitch.put(BlockPos.asLong(c.light()[0], c.light()[1], c.light()[2]), c);
			}
		}
		return bySwitch;
	}

	/** A switch was pulled: its circuit's lamps follow it, if there's power. */
	public static void switched(ServerLevel level, BlockPos pos) {
		HotelData data = HotelData.get(level);
		HotelFrame frame = data.frame();
		if (frame == null || !frame.inBuilding(pos)) {
			return;
		}
		int[] at = frame.toPlan(pos);
		Circuit c = circuits().get(BlockPos.asLong(at[0], at[1], at[2]));
		if (c != null) {
			work(level, frame, c, data.power() && level.getBlockState(pos).getValue(LeverBlock.POWERED));
		}
	}

	/** Turns the power on or off: every circuit lit as its switch stands, or all put out. Returns the lamps lit. */
	public static int setPower(ServerLevel level, boolean on) {
		HotelData data = HotelData.get(level);
		data.setPower(on);
		HotelFrame frame = data.frame();
		if (frame == null) {
			return 0;
		}
		HotelLifts.power(level, on);
		int lit = 0;
		for (Circuit c : HotelBuilder.plan().circuits) {
			BlockState sw = level.getBlockState(frame.toWorld(c.light()[0], c.light()[1], c.light()[2]));
			boolean live = on && (!sw.hasProperty(LeverBlock.POWERED) || sw.getValue(LeverBlock.POWERED));
			lit += work(level, frame, c, live);
		}
		return lit;
	}

	/** How bright a chandelier's light is where it falls. */
	private static final int FILL = 12;

	private static int work(ServerLevel level, HotelFrame frame, Circuit c, boolean live) {
		int n = 0;
		for (int[] l : c.lamps()) {
			BlockPos pos = frame.toWorld(l[0], l[1], l[2]);
			BlockState s = level.getBlockState(pos);
			if (s.getBlock() instanceof FurnitureBlock f && f.piece().light == Light.ELECTRIC && s.getValue(FurnitureBlock.LIT) != live) {
				f.setLit(level, pos, s, live);
			} else if (s.is(net.minecraft.world.level.block.Blocks.LIGHT)
					&& s.getValue(net.minecraft.world.level.block.LightBlock.LEVEL) != (live ? FILL : 0)) {
				// A chandelier's light where it falls (HotelDresser): on and off with it.
				level.setBlock(pos, s.setValue(net.minecraft.world.level.block.LightBlock.LEVEL, live ? FILL : 0),
						net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
			}
			n += live ? 1 : 0;
		}
		return n;
	}
}
