package rauk.com.hotel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import rauk.com.Rauk;
import rauk.com.block.hotel.FurnitureBlock;
import rauk.com.block.hotel.HotelDoorBlock;
import rauk.com.block.hotel.LiftControlBlock;
import rauk.com.hotel.LiftCars.Lift;
import rauk.com.hotel.LiftCars.Part;
import rauk.com.network.LiftPayload;
import rauk.com.registry.RaukItems;

/**
 * The lifts running (hotel design 3.2): you step into the car, its gate closes behind you, and
 * it carries you up or down the shaft, rattling and humming, the floors going by, the cage's
 * brass bands sliding past outside in the atrium; the gate opens on the other floor. The ride is
 * the slowest few seconds in the game, and Rauk hears every one of them.
 *
 * <p>The car stands as blocks at the floor where it is. While it moves it is block displays, and
 * whoever is in it rides an invisible seat beside them, so the whole thing moves smoothly; at the
 * other end it is blocks again. It goes nowhere without power. The operator's lever in the car
 * sends it up a floor (sneaking, down a floor); a landing's call button brings it there. A
 * landing's gate opens by hand only with the car behind it.
 */
public final class HotelLifts {
	/** Its top speed, in blocks a tick; and how long the gate takes to close before it moves. */
	private static final double SPEED = 0.2;
	private static final int CLOSING = 18;

	private static final Map<String, Ride> rides = new HashMap<>();
	private static final Map<String, BlockState> parsed = new HashMap<>();

	private HotelLifts() {
	}

	/** A ride under way. */
	private static final class Ride {
		final Lift lift;
		final HotelPlan.Level from, to;
		int tick, duration;
		boolean moving;
		final List<Entity> displays = new ArrayList<>();
		final List<Part> shown = new ArrayList<>();
		/** Who's in the car, and how high their feet are above its floor. */
		final Map<Entity, Double> riders = new LinkedHashMap<>();
		final Map<ServerPlayer, ArmorStand> seats = new LinkedHashMap<>();
		double y;

		Ride(Lift lift, HotelPlan.Level from, HotelPlan.Level to) {
			this.lift = lift;
			this.from = from;
			this.to = to;
		}
	}

	public static void init() {
		HotelDoorBlock.gateOpens = HotelLifts::carAtGate;
		LiftControlBlock.handler = HotelLifts::use;
		LiftControlBlock.itemHandler = HotelLifts::useItem;
		ServerTickEvents.END_WORLD_TICK.register(level -> {
			if (level.dimension() == Level.OVERWORLD && !rides.isEmpty()) {
				tick(level);
			}
		});
	}

	public static Lift byId(String id) {
		for (Lift l : LiftCars.ALL) {
			if (l.id().equals(id)) {
				return l;
			}
		}
		return null;
	}

	/** Which floor a lift's car is at (as saved with the world). */
	public static HotelPlan.Level carFloor(ServerLevel level, Lift lift) {
		int i = HotelData.get(level).liftFloor(lift.id(), lift.index(lift.home()));
		return lift.floors().get(Math.clamp(i, 0, lift.floors().size() - 1));
	}

	private static boolean carAtGate(Level level, BlockPos pos) {
		if (!(level instanceof ServerLevel server)) {
			return true;
		}
		HotelFrame frame = HotelData.get(server).frame();
		if (frame == null || !frame.inBuilding(pos)) {
			return true;
		}
		int[] at = frame.toPlan(pos);
		Lift lift = LiftCars.gateAt(at[0], at[1], at[2]);
		return lift == null || !rides.containsKey(lift.id()) && carFloor(server, lift) == LiftCars.floorAt(lift, at[1]);
	}

	/** The lever in a car or a landing's call button, used. */
	private static void use(ServerLevel level, BlockPos pos, Player player, boolean lever, boolean down) {
		HotelFrame frame = HotelData.get(level).frame();
		if (frame == null || !frame.inBuilding(pos)) {
			return;
		}
		int[] at = frame.toPlan(pos);
		Lift lift = null;
		for (Lift l : LiftCars.ALL) {
			if (lever ? l.controlX() == at[0] && l.controlZ() == at[2] : l.callX() == at[0] && l.callZ() == at[2]) {
				lift = l;
			}
		}
		if (lift == null) {
			return;
		}
		level.playSound(null, pos, lever ? SoundEvents.LEVER_CLICK : SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.6f, 0.9f);
		HotelPlan.Level here = carFloor(level, lift);
		HotelPlan.Level to;
		if (lever) {
			int i = lift.index(here) + (down ? -1 : 1);
			if (i < 0 || i >= lift.floors().size()) {
				player.displayClientMessage(Component.literal(down ? "The car is at the bottom." : "The car is at the top."), true);
				return;
			}
			to = lift.floors().get(i);
		} else {
			to = LiftCars.floorAt(lift, at[1] - 1);
			if (to == null) {
				return;
			}
		}
		ride(level, lift, to, player);
	}

	/** The lift whose lever (or a landing's call button) is at a place, or null. */
	private static Lift controlled(ServerLevel level, BlockPos pos, boolean lever) {
		HotelFrame frame = HotelData.get(level).frame();
		if (frame == null || !frame.inBuilding(pos)) {
			return null;
		}
		int[] at = frame.toPlan(pos);
		for (Lift l : LiftCars.ALL) {
			if (lever ? l.controlX() == at[0] && l.controlZ() == at[2] : l.callX() == at[0] && l.callZ() == at[2]) {
				return l;
			}
		}
		return null;
	}

	/**
	 * The crank or the penthouse key held to the service lift's lever (hotel design 4.1 and 4.4):
	 * the crank goes onto the winding gear for good, the key into the panel. Whether it was taken.
	 */
	private static boolean useItem(ServerLevel level, BlockPos pos, Player player, boolean lever, ItemStack stack) {
		boolean crank = stack.is(RaukItems.LIFT_CRANK), key = stack.is(RaukItems.PENTHOUSE_KEY);
		Lift lift = lever && (crank || key) ? controlled(level, pos, true) : null;
		if (lift == null || !lift.id().equals("service")) {
			return false;
		}
		HotelData data = HotelData.get(level);
		String flag = crank ? HotelProgress.CRANK : HotelProgress.PENTHOUSE_KEY;
		if (data.done(flag)) {
			return false;
		}
		data.setDone(flag, true);
		stack.consume(1, player);
		level.playSound(null, pos, crank ? SoundEvents.CHAIN_PLACE : SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.9f, crank ? 0.7f : 1.6f);
		player.displayClientMessage(Component.translatable(crank ? "hotel.rauk.lift.crank_fitted" : "hotel.rauk.lift.key_turned"), true);
		return true;
	}

	/** Sends a lift's car to a floor, if it can go: power, and not moving already. Returns whether it went. */
	public static boolean ride(ServerLevel level, Lift lift, HotelPlan.Level to, Player who) {
		HotelData data = HotelData.get(level);
		HotelFrame frame = data.frame();
		if (frame == null) {
			return false;
		}
		if (lift.id().equals("service") && !data.done(HotelProgress.CRANK)) {
			// Its crank was taken off the winding gear and locked away (puzzle 1).
			if (who != null) {
				who.displayClientMessage(Component.translatable("hotel.rauk.lift.no_crank"), true);
			}
			return false;
		}
		if (!data.power()) {
			if (who != null) {
				who.displayClientMessage(Component.literal("Nothing. The lift has no power."), true);
			}
			return false;
		}
		if (lift.id().equals("service") && to == HotelPlan.Level.L4 && !data.done(HotelProgress.PENTHOUSE_KEY)) {
			// Wennberg's floor, on his key only (puzzle 4).
			if (who != null) {
				who.displayClientMessage(Component.translatable("hotel.rauk.lift.no_key"), true);
			}
			return false;
		}
		if (rides.containsKey(lift.id())) {
			if (who != null) {
				who.displayClientMessage(Component.literal("The lift is moving."), true);
			}
			return false;
		}
		HotelPlan.Level from = carFloor(level, lift);
		if (from == to) {
			// Already here: the gate opens.
			HotelDoorBlock.swing(level, frame.toWorld(lift.gateX(), to.base, lift.gateZ()), HotelDoorBlock.FULL, HotelDoorBlock.NORMAL, who,
					false);
			return true;
		}
		Ride r = new Ride(lift, from, to);
		HotelDoorBlock.swing(level, frame.toWorld(lift.gateX(), from.base, lift.gateZ()), 0, HotelDoorBlock.NORMAL, who, false);
		rides.put(lift.id(), r);
		return true;
	}

	// --- the ride ------------------------------------------------------------------------------------

	private static void tick(ServerLevel level) {
		HotelFrame frame = HotelData.get(level).frame();
		rides.values().removeIf(r -> {
			if (frame == null) {
				return true;
			}
			r.tick++;
			if (!r.moving && r.tick >= CLOSING) {
				depart(level, frame, r);
			}
			if (r.moving) {
				return move(level, frame, r);
			}
			return false;
		});
	}

	/** The car at its floor's interior, in the world: three by three, as high as the roof. */
	private static AABB inside(HotelFrame frame, Lift lift, HotelPlan.Level lv) {
		BlockPos a = frame.toWorld(lift.x0(), lv.base, lift.z0()), b = frame.toWorld(lift.x0() + 2, lv.base + 2, lift.z0() + 2);
		return new AABB(Math.min(a.getX(), b.getX()), lv.base + frame.origin().getY(), Math.min(a.getZ(), b.getZ()), Math.max(a.getX(), b.getX()) + 1,
				lv.base + frame.origin().getY() + 3, Math.max(a.getZ(), b.getZ()) + 1);
	}

	/** The gate's shut: the car leaves its floor as blocks and goes on as displays, with whoever's in it. */
	private static void depart(ServerLevel level, HotelFrame frame, Ride r) {
		r.moving = true;
		r.tick = 0;
		Lift lift = r.lift;
		double floorY = r.from.base + frame.origin().getY();
		for (Entity e : level.getEntitiesOfClass(LivingEntity.class, inside(frame, lift, r.from), e -> !e.isSpectator())) {
			r.riders.put(e, e.getY() - floorY);
		}
		HotelPlan plan = HotelBuilder.plan();
		for (Part part : LiftCars.car(lift, true)) {
			int y = r.from.base + part.dy();
			BlockPos pos = frame.toWorld(part.x(), y, part.z());
			// The car's blocks become displays; where it stood is the shaft again (or the pit's floor).
			BlockState was = level.getBlockState(pos);
			String under = plan.underCar.getOrDefault(HotelPlan.cellKey(part.x(), y, part.z()), "minecraft:air");
			level.setBlock(pos, state(under.isEmpty() ? "minecraft:air" : under, frame), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
			if (!was.isAir()) {
				CompoundTag tag = new CompoundTag();
				tag.putString("id", "minecraft:block_display");
				tag.put("block_state", NbtUtils.writeBlockState(was));
				tag.putInt("teleport_duration", 3);
				CompoundTag light = new CompoundTag();
				light.putInt("block", 11);
				light.putInt("sky", 0);
				tag.put("brightness", light);
				Entity d = EntityType.loadEntityRecursive(tag, level, e -> {
					e.moveTo(pos.getX(), pos.getY(), pos.getZ(), 0, 0);
					return e;
				});
				if (d != null && level.addFreshEntity(d)) {
					r.displays.add(d);
					r.shown.add(part);
				}
			}
		}
		// Players ride an invisible seat, so they move smoothly; anything else is carried along.
		for (Map.Entry<Entity, Double> e : r.riders.entrySet()) {
			if (e.getKey() instanceof ServerPlayer p) {
				ArmorStand seat = EntityType.ARMOR_STAND.create(level);
				if (seat != null) {
					seat.setInvisible(true);
					seat.setNoGravity(true);
					seat.setInvulnerable(true);
					seat.setSilent(true);
					CompoundTag t = new CompoundTag();
					seat.addAdditionalSaveData(t);
					t.putBoolean("Marker", true);
					seat.readAdditionalSaveData(t);
					seat.moveTo(p.getX(), floorY + e.getValue() + 0.6, p.getZ(), p.getYRot(), 0);
					level.addFreshEntity(seat);
					p.startRiding(seat, true);
					r.seats.put(p, seat);
				}
			}
		}
		double dy = r.to.base - r.from.base;
		r.duration = (int) Math.ceil(Math.abs(dy) / SPEED) + 16;
		r.y = floorY;
		level.playSound(null, frame.toWorld(lift.x0() + 1, r.from.base + 1, lift.z0() + 1), SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.9f,
				0.5f);
		level.gameEvent(null, GameEvent.BLOCK_ACTIVATE, frame.toWorld(lift.x0() + 1, r.from.base + 1, lift.z0() + 1));
		for (ServerPlayer p : r.seats.keySet()) {
			ServerPlayNetworking.send(p, new LiftPayload(r.duration, 1.2f));
		}
	}

	/** A tick of the car moving; true when it's arrived and done. */
	private static boolean move(ServerLevel level, HotelFrame frame, Ride r) {
		Lift lift = r.lift;
		double baseY = frame.origin().getY();
		double fromY = r.from.base + baseY, dy = r.to.base - r.from.base;
		double t = Math.min(1, (double) r.tick / r.duration);
		double s = t * t * (3 - 2 * t);
		double y = fromY + dy * s;
		double prev = r.y;
		r.y = y;
		for (int i = 0; i < r.displays.size(); i++) {
			Entity d = r.displays.get(i);
			Part part = r.shown.get(i);
			BlockPos start = frame.toWorld(part.x(), r.from.base + part.dy(), part.z());
			d.teleportTo(start.getX(), start.getY() + (y - fromY), start.getZ());
		}
		for (Map.Entry<Entity, Double> e : r.riders.entrySet()) {
			Entity who = e.getKey();
			ArmorStand seat = who instanceof ServerPlayer p ? r.seats.get(p) : null;
			if (seat != null) {
				seat.teleportTo(seat.getX(), y + e.getValue() + 0.6, seat.getZ());
				if (who.getVehicle() != seat) {
					who.startRiding(seat, true);
				}
			} else if (who.isAlive()) {
				who.teleportTo(who.getX(), y + e.getValue(), who.getZ());
				who.setDeltaMovement(0, 0, 0);
			}
		}
		// The hum and the rattle; the floors going by, on the indicator and in Rauk's ears.
		BlockPos at = BlockPos.containing(frame.toWorld(lift.x0() + 1, 0, lift.z0() + 1).getX(), y + 1, frame.toWorld(lift.x0() + 1, 0, lift.z0() + 1).getZ());
		if (r.tick % 9 == 0) {
			level.playSound(null, at, SoundEvents.CHAIN_STEP, SoundSource.BLOCKS, 0.8f, 0.6f + level.random.nextFloat() * 0.2f);
		}
		if (r.tick % 30 == 0) {
			level.playSound(null, at, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.5f, 0.55f);
		}
		for (HotelPlan.Level lv : lift.floors()) {
			double floorY = lv.base + baseY;
			if ((prev < floorY && y >= floorY || prev > floorY && y <= floorY) && lv != r.from) {
				String arrow = dy > 0 ? "▲ " : "▼ ";
				for (ServerPlayer p : r.seats.keySet()) {
					p.displayClientMessage(Component.literal(arrow + lv.label), true);
				}
				level.playSound(null, at, SoundEvents.WOODEN_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.6f, 0.6f);
				level.gameEvent(null, GameEvent.BLOCK_ACTIVATE, at);
			}
		}
		if (t < 1) {
			return false;
		}
		arrive(level, frame, r);
		return true;
	}

	/** There: blocks again, everyone set down on its floor, the bell, the gate. */
	private static void arrive(ServerLevel level, HotelFrame frame, Ride r) {
		Lift lift = r.lift;
		HotelData data = HotelData.get(level);
		for (Part part : LiftCars.car(lift, data.power())) {
			BlockPos pos = frame.toWorld(part.x(), r.to.base + part.dy(), part.z());
			level.setBlock(pos, state(part.block(), frame), Block.UPDATE_ALL);
		}
		r.displays.forEach(Entity::discard);
		double floorY = r.to.base + frame.origin().getY();
		for (Map.Entry<Entity, Double> e : r.riders.entrySet()) {
			Entity who = e.getKey();
			ArmorStand seat = who instanceof ServerPlayer p ? r.seats.get(p) : null;
			if (seat != null) {
				who.stopRiding();
				seat.discard();
			}
			if (who instanceof ServerPlayer p) {
				p.teleportTo(level, p.getX(), floorY + Math.max(0, e.getValue()), p.getZ(), p.getYRot(), p.getXRot());
				ServerPlayNetworking.send(p, new LiftPayload(0, 1.5f));
				p.displayClientMessage(Component.literal(r.to.label + ": " + r.to.title), true);
			} else if (who.isAlive()) {
				who.teleportTo(who.getX(), floorY + Math.max(0, e.getValue()), who.getZ());
			}
		}
		data.setLiftFloor(lift.id(), lift.index(r.to));
		BlockPos gate = frame.toWorld(lift.gateX(), r.to.base, lift.gateZ());
		level.playSound(null, gate, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.9f, 1.19f);
		level.playSound(null, gate, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.7f, 0.6f);
		level.gameEvent(null, GameEvent.BLOCK_ACTIVATE, gate);
		HotelDoorBlock.swing(level, gate, HotelDoorBlock.FULL, HotelDoorBlock.NORMAL, null, false);
	}

	/** A plan block state, turned with the hotel. */
	private static BlockState state(String s, HotelFrame frame) {
		BlockState st = parsed.computeIfAbsent(s, k -> {
			try {
				return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), k, false).blockState();
			} catch (Exception e) {
				Rauk.LOGGER.warn("Lift: no block state {}", k);
				return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
			}
		});
		return st.rotate(frame.rotation());
	}

	/** The power on or off: every standing car's lamp with it. */
	public static void power(ServerLevel level, boolean on) {
		HotelFrame frame = HotelData.get(level).frame();
		if (frame == null) {
			return;
		}
		for (Lift lift : LiftCars.ALL) {
			if (rides.containsKey(lift.id())) {
				continue;
			}
			BlockPos lamp = frame.toWorld(lift.lampX(), carFloor(level, lift).base + 2, lift.lampZ());
			BlockState s = level.getBlockState(lamp);
			if (s.getBlock() instanceof FurnitureBlock f && s.hasProperty(FurnitureBlock.LIT)) {
				f.setLit(level, lamp, s, on);
			}
		}
	}

	/** Forgets any ride under way (the hotel rebuilt). */
	public static void reset() {
		rides.clear();
	}
}
