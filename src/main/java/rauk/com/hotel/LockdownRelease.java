package rauk.com.hotel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import rauk.com.block.hotel.FurnitureBlock;
import rauk.com.entity.RaukEntity;
import rauk.com.registry.HotelBlocks;
import rauk.com.registry.RaukEntities;
import rauk.com.registry.RaukItems;

/**
 * The finale, the Lockdown (hotel design 4.6). The release panel is in the night clerk's office by
 * the old front doors: with the power on (puzzle 2), the lockdown key turned in it (puzzle 5) and
 * the code's eight figures keyed in (the first half from puzzle 4, the second from puzzle 5), the
 * lever beside it lifts the lockdown.
 *
 * <p>Then the alarm rings through the whole building, every Rauk in it comes, hunting, not playing,
 * and the shutters rise one window at a time all over the hotel, slowly, the front doors' last of
 * all, about a minute after the lever. From the clerk's office to the front doors at the corner is
 * a hundred blocks: through the garden hall, across the courtyard, through the palm court and the
 * rotunda. Out of the front doors, and it's dawn. The hotel stays open behind you, its shutters up;
 * it is still inside.
 */
public final class LockdownRelease {
	public static final String KEY_IN = "lockdown_key_in", CODE_OK = "lockdown_code_ok", RELEASED = "lockdown_released", ESCAPED = "escaped";
	/** How long the alarm rings, and when the front doors' shutters start up, in ticks after the lever. */
	private static final int ALARM = 1400, FRONT = 1150;
	/** How much faster than real the shutters rise (tests). */
	public static int speed = 1;

	/** A shutter's cell, when it rises (ticks after the lever), and what it leaves (glass, or the way out). */
	private record Rising(int x, int y, int z, int at, String becomes) {
	}

	private static List<Rising> schedule;
	private static int done;
	private static final StringBuilder entered = new StringBuilder();
	/** Who was in the hotel once the lockdown lifted: walking out of it, they've escaped. */
	private static final Set<UUID> inside = new HashSet<>();

	private LockdownRelease() {
	}

	public static void init() {
		ServerTickEvents.END_WORLD_TICK.register(level -> {
			if (level.dimension() == Level.OVERWORLD && level.getGameTime() % 2 == 0 && HotelData.get(level).done(RELEASED)) {
				update(level);
			}
		});
	}

	/** Whether the alarm is ringing (Rauk hunts, whoever it's after). */
	public static boolean alarmed(Level level) {
		if (!(level instanceof ServerLevel server)) {
			return false;
		}
		HotelData data = HotelData.get(server);
		return data.done(RELEASED) && (level.getGameTime() - data.releasedAt()) * speed < ALARM;
	}

	// --- the panel and the lever ---------------------------------------------------------------------

	/** The panel, clicked: a key of its keypad pressed (only with the power on and the key turned in it). */
	static InteractionResult panel(ServerLevel level, BlockPos pos, BlockState state, Player player, BlockHitResult hit) {
		HotelData data = HotelData.get(level);
		if (data.done(RELEASED)) {
			say(player, "released");
			return InteractionResult.SUCCESS;
		}
		if (!data.power()) {
			say(player, "dead");
			return InteractionResult.SUCCESS;
		}
		if (!data.done(KEY_IN)) {
			say(player, "no_key");
			return InteractionResult.SUCCESS;
		}
		FurnitureBlock block = (FurnitureBlock) state.getBlock();
		double across = block.across(pos, state, hit.getLocation()), up = (hit.getLocation().y - pos.getY()) * 16;
		int col = -1, row = -1;
		for (int i = 0; i < 3; i++) {
			if (Math.abs(across - FurnitureCatalog.PANEL_COLUMNS[i]) < 1.4) {
				col = i;
			}
		}
		for (int i = 0; i < 4; i++) {
			if (Math.abs(up - FurnitureCatalog.PANEL_ROWS[i]) < 1) {
				row = i;
			}
		}
		if (col < 0 || row < 0 || row == 3 && col == 2) {
			return InteractionResult.PASS;
		}
		level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.6f, 1.5f);
		level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
		if (row == 3 && col == 0) {
			entered.setLength(0);
			data.setDone(CODE_OK, false);
			block.setLook(level, pos, state, 1);
			say(player, "cleared");
			return InteractionResult.SUCCESS;
		}
		if (data.done(CODE_OK) || entered.length() >= 8) {
			entered.setLength(0);
			data.setDone(CODE_OK, false);
			block.setLook(level, pos, state, 1);
		}
		entered.append(row == 3 ? 0 : row * 3 + col + 1);
		player.displayClientMessage(Component.literal(shown()), true);
		if (entered.length() == 8) {
			int[] code = LockdownCode.of(level.getSeed());
			StringBuilder want = new StringBuilder();
			for (int c : code) {
				want.append(c);
			}
			if (want.toString().contentEquals(entered)) {
				data.setDone(CODE_OK, true);
				block.setLook(level, pos, state, 2);
				level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 1, 1);
				say(player, "accepted");
			} else {
				entered.setLength(0);
				level.playSound(null, pos, SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), SoundSource.BLOCKS, 1, 0.6f);
				say(player, "wrong");
			}
		}
		return InteractionResult.SUCCESS;
	}

	/** The figures keyed in so far, the rest blank. */
	private static String shown() {
		StringBuilder s = new StringBuilder();
		for (int i = 0; i < 8; i++) {
			s.append(i < entered.length() ? entered.charAt(i) : '_').append(i == 3 ? "   " : " ");
		}
		return s.toString().strip();
	}

	/** The lockdown key held to the panel: it turns, and stays. */
	static ItemInteractionResult panelItem(ServerLevel level, BlockPos pos, BlockState state, Player player, ItemStack stack) {
		HotelData data = HotelData.get(level);
		if (!stack.is(RaukItems.LOCKDOWN_KEY) || data.done(KEY_IN)) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (!data.power()) {
			say(player, "dead");
			return ItemInteractionResult.SUCCESS;
		}
		data.setDone(KEY_IN, true);
		stack.consume(1, player);
		((FurnitureBlock) state.getBlock()).setLook(level, pos, state, 1);
		level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.8f, 1.5f);
		say(player, "key_in");
		return ItemInteractionResult.SUCCESS;
	}

	/** The lever: with everything ready, the lockdown lifts. */
	static InteractionResult lever(ServerLevel level, BlockPos pos, BlockState state, Player player) {
		HotelData data = HotelData.get(level);
		if (data.done(RELEASED)) {
			say(player, "released");
			return InteractionResult.SUCCESS;
		}
		if (!data.power() || !data.done(KEY_IN) || !data.done(CODE_OK)) {
			level.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.6f, 0.6f);
			say(player, "lever_stuck");
			return InteractionResult.SUCCESS;
		}
		((FurnitureBlock) state.getBlock()).setLook(level, pos, state, 1);
		data.setDone(RELEASED, true);
		data.setReleasedAt(level.getGameTime());
		schedule = null;
		done = 0;
		inside.clear();
		level.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1, 0.5f);
		// Everything in the house hears it, and comes.
		for (RaukEntity rauk : level.getEntities(RaukEntities.RAUK, r -> true)) {
			rauk.mind().hearFar(pos.getCenter(), "lockdown");
		}
		level.gameEvent(player, GameEvent.EXPLODE, pos);
		say(player, "pulled");
		return InteractionResult.SUCCESS;
	}

	// --- after the lever -------------------------------------------------------------------------------

	private static void update(ServerLevel level) {
		HotelData data = HotelData.get(level);
		HotelFrame frame = data.frame();
		if (frame == null) {
			return;
		}
		long t = (level.getGameTime() - data.releasedAt()) * speed;
		// The alarm, at every ear in the building.
		for (ServerPlayer p : level.players()) {
			boolean in = frame.inBuilding(p.blockPosition());
			if (in) {
				inside.add(p.getUUID());
				if (t < ALARM && level.getGameTime() % 10 == 0) {
					p.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 0.8f, 1.7f);
				}
			} else if (inside.remove(p.getUUID())) {
				// Out, with the lockdown lifted: the front doors were the only way.
				escaped(level, p);
			}
		}
		// The shutters, as their time comes.
		List<Rising> s = schedule();
		while (done < s.size() && s.get(done).at() <= t) {
			Rising r = s.get(done++);
			BlockPos at = frame.toWorld(r.x(), r.y(), r.z());
			BlockState here = level.getBlockState(at);
			String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(here.getBlock()).getPath();
			if (!id.equals("shutter") && !id.equals("slant_shutter")) {
				continue;
			}
			BlockState next = r.becomes().equals("air") ? Blocks.AIR.defaultBlockState()
					: id.equals("slant_shutter") ? HotelBlocks.get("slant_glass").withPropertiesOf(here) : Blocks.GLASS.defaultBlockState();
			level.setBlock(at, next, 3);
			if (r.y() == lowest(r)) {
				level.playSound(null, at, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.2f, 0.5f);
			}
		}
	}

	/** Someone out of the front doors with the lockdown lifted: dawn. */
	private static void escaped(ServerLevel level, ServerPlayer player) {
		HotelData data = HotelData.get(level);
		if (!data.done(ESCAPED)) {
			data.setDone(ESCAPED, true);
			level.setDayTime(23200);
		}
		player.connection.send(new ClientboundSetTitlesAnimationPacket(30, 120, 40));
		player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("hotel.rauk.ending.title")));
		player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("hotel.rauk.ending.subtitle")));
		player.sendSystemMessage(Component.translatable("hotel.rauk.ending.line"));
	}

	/** The shutters' rising, in order: every window's column at its own moment, bottom up, the front doors last. */
	private static synchronized List<Rising> schedule() {
		if (schedule == null) {
			HotelPlan plan = HotelBuilder.plan();
			List<Rising> out = new ArrayList<>();
			for (int y = HotelPlan.Y0; y < HotelPlan.Y0 + HotelPlan.SY; y++) {
				for (int z = HotelPlan.Z0; z < HotelPlan.Z0 + HotelPlan.SZ; z++) {
					for (int x = HotelPlan.X0; x < HotelPlan.X0 + HotelPlan.SX; x++) {
						String s = plan.get(x, y, z);
						if (!s.startsWith("rauk:shutter") && !s.startsWith("rauk:slant_shutter")) {
							continue;
						}
						boolean front = z == HotelPlan.FRONT_Z && x >= 38 && x <= 41 && y >= 0 && y <= 3;
						int column = Math.floorMod(x * 7919 + z * 104729, 1000);
						int lowest = y;
						while (plan.get(x, lowest - 1, z).equals(s)) {
							lowest--;
						}
						int at = (front ? FRONT : 40 + column) + (y - lowest) * 4 + (front ? (x - 38) * 3 : 0);
						out.add(new Rising(x, y, z, at, front ? "air" : "glass"));
					}
				}
			}
			out.sort(Comparator.comparingInt(Rising::at));
			schedule = out;
		}
		return schedule;
	}

	/** The bottom of a window's column of shutters: its rising is heard once, as it starts. */
	private static int lowest(Rising r) {
		HotelPlan plan = HotelBuilder.plan();
		int y = r.y();
		String here = plan.get(r.x(), y, r.z());
		while (plan.get(r.x(), y - 1, r.z()).equals(here)) {
			y--;
		}
		return y;
	}

	private static void say(Player player, String what) {
		if (player != null) {
			player.displayClientMessage(Component.translatable("hotel.rauk.lockdown." + what), true);
		}
	}
}
