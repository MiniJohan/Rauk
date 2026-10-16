package rauk.com.hotel;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import rauk.com.entity.RaukEntity;
import rauk.com.hotel.HotelPlan.Level;
import rauk.com.hotel.HotelPlan.Mark;
import rauk.com.registry.RaukEntities;

/**
 * Test controls for the hotel: {@code /rauk hotel build [pos] [turn]} puts a hotel there (its
 * north-west corner at the lobby floor) and makes it the world's hotel; {@code /rauk hotel tp
 * <place>} goes to a place in it (a level, the lobby, the den, the dormitory, the chute, the
 * front); {@code /rauk hotel info}; {@code /rauk hotel cut <level>} takes off everything above that
 * level's floor to look down into it, and {@code /rauk hotel restore} puts it back;
 * {@code /rauk hotel rauk} sets a Rauk loose in the Cabinet. {@code /rauk hotel items} gives you
 * one of each thing to carry; {@code /rauk hotel progress} says how far the players have got, and
 * {@code progress set <what> on|off} or {@code progress reset} changes it.
 */
public final class HotelCommands {
	private static final List<String> TURNS = List.of("none", "clockwise", "half", "counterclockwise");
	private static final List<String> PLACES = List.of("lobby", "atrium", "den", "dorm", "chute", "front", "B2", "B1", "1", "2", "3", "4",
			"R");

	private HotelCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("rauk").requires(source -> source.hasPermission(2)).then(literal("hotel")
				.then(literal("build")
						.executes(ctx -> build(ctx, BlockPos.containing(ctx.getSource().getPosition()), Rotation.NONE))
						.then(argument("pos", BlockPosArgument.blockPos())
								.executes(ctx -> build(ctx, BlockPosArgument.getBlockPos(ctx, "pos"), Rotation.NONE))
								.then(argument("turn", StringArgumentType.word())
										.suggests((ctx, b) -> SharedSuggestionProvider.suggest(TURNS, b))
										.executes(ctx -> build(ctx, BlockPosArgument.getBlockPos(ctx, "pos"),
												Rotation.values()[Math.max(0, TURNS.indexOf(StringArgumentType.getString(ctx, "turn")))])))))
				.then(literal("tp").then(argument("place", StringArgumentType.word())
						.suggests((ctx, b) -> SharedSuggestionProvider.suggest(PLACES, b))
						.executes(ctx -> tp(ctx, StringArgumentType.getString(ctx, "place")))))
				.then(literal("info").executes(HotelCommands::info))
				.then(literal("cut").then(argument("level", StringArgumentType.word())
						.suggests((ctx, b) -> SharedSuggestionProvider.suggest(PLACES.subList(6, PLACES.size()), b))
						.executes(ctx -> cut(ctx, StringArgumentType.getString(ctx, "level")))))
				.then(literal("restore").executes(ctx -> {
					HotelFrame frame = frame(ctx);
					int n = HotelWriter.write(ctx.getSource().getLevel(), frame, null, HotelRules.QUIET, Integer.MIN_VALUE, Integer.MAX_VALUE,
							false);
					// The cars are back where the plan has them.
					HotelData.get(ctx.getSource().getLevel()).resetLifts();
					HotelLifts.reset();
					ctx.getSource().sendSuccess(() -> Component.literal("Hotel restored (" + n + " blocks)"), false);
					return 1;
				}))
				.then(literal("rauk").executes(ctx -> loose(ctx, false)).then(literal("random").executes(ctx -> loose(ctx, true))))
				.then(literal("clear").executes(HotelCommands::clear))
				.then(literal("power").then(argument("on", StringArgumentType.word())
						.suggests((ctx, b) -> SharedSuggestionProvider.suggest(List.of("on", "off"), b)).executes(ctx -> {
							boolean on = StringArgumentType.getString(ctx, "on").equals("on");
							int n = HotelLights.setPower(ctx.getSource().getLevel(), on);
							ctx.getSource().sendSuccess(() -> Component.literal("Power " + (on ? "on" : "off") + ": " + n + " lamps lit"), false);
							return n;
						})))
				.then(literal("lift").then(argument("lift", StringArgumentType.word())
						.suggests((ctx, b) -> SharedSuggestionProvider.suggest(List.of("service", "guest", "rotunda"), b))
						.then(argument("floor", StringArgumentType.word())
								.suggests((ctx, b) -> SharedSuggestionProvider.suggest(List.of("B2", "B1", "1", "2", "3", "4"), b))
								.executes(ctx -> {
									LiftCars.Lift lift = HotelLifts.byId(StringArgumentType.getString(ctx, "lift"));
									String label = StringArgumentType.getString(ctx, "floor");
									HotelPlan.Level to = null;
									for (HotelPlan.Level lv : lift == null ? List.<HotelPlan.Level>of() : lift.floors()) {
										if (lv.label.equalsIgnoreCase(label)) {
											to = lv;
										}
									}
									if (to == null) {
										throw fail("No such lift or floor");
									}
									boolean ok = HotelLifts.ride(ctx.getSource().getLevel(), lift, to, ctx.getSource().getPlayer());
									HotelPlan.Level dest = to;
									ctx.getSource().sendSuccess(() -> Component.literal(ok ? lift.name() + " going to " + dest.label : lift.name() + " won't go"),
											false);
									return ok ? 1 : 0;
								}))))
				.then(literal("door").then(argument("pos", BlockPosArgument.blockPos()).then(argument("how", StringArgumentType.word())
						.suggests((ctx, b) -> SharedSuggestionProvider.suggest(List.of("open", "close", "ajar", "creep", "slam", "shut", "peek"), b))
						.executes(ctx -> {
							String how = StringArgumentType.getString(ctx, "how");
							float[] swing = switch (how) {
								case "close" -> new float[] {0, rauk.com.block.hotel.HotelDoorBlock.NORMAL};
								case "ajar" -> new float[] {rauk.com.block.hotel.HotelDoorBlock.AJAR, rauk.com.block.hotel.HotelDoorBlock.CAREFUL};
								case "peek" -> new float[] {rauk.com.block.hotel.HotelDoorBlock.AJAR, rauk.com.block.hotel.HotelDoorBlock.PEEK};
								case "creep" -> new float[] {rauk.com.block.hotel.HotelDoorBlock.FULL, rauk.com.block.hotel.HotelDoorBlock.CAREFUL};
								case "slam" -> new float[] {rauk.com.block.hotel.HotelDoorBlock.FULL, rauk.com.block.hotel.HotelDoorBlock.SLAM};
								case "shut" -> new float[] {0, rauk.com.block.hotel.HotelDoorBlock.SLAM};
								default -> new float[] {rauk.com.block.hotel.HotelDoorBlock.FULL, rauk.com.block.hotel.HotelDoorBlock.NORMAL};
							};
							boolean ok = rauk.com.block.hotel.HotelDoorBlock.swing(ctx.getSource().getLevel(), BlockPosArgument.getBlockPos(ctx, "pos"),
									swing[0], swing[1], ctx.getSource().getEntity(), true);
							ctx.getSource().sendSuccess(() -> Component.literal(ok ? "Door: " + how : "No hotel door there"), false);
							return ok ? 1 : 0;
						}))))
				.then(literal("solutions").executes(HotelCommands::solutions))
				.then(literal("items").executes(ctx -> {
					var player = ctx.getSource().getPlayerOrException();
					for (var item : rauk.com.registry.RaukItems.CARRIED.values()) {
						player.getInventory().add(new net.minecraft.world.item.ItemStack(item, item == rauk.com.registry.RaukItems.FUSE ? 3 : 1));
					}
					ctx.getSource().sendSuccess(() -> Component.literal("Given one of each thing to carry"), false);
					return 1;
				}))
				.then(literal("progress").executes(HotelCommands::progress)
						.then(literal("reset").executes(ctx -> {
							HotelData.get(ctx.getSource().getLevel()).resetProgress();
							ctx.getSource().sendSuccess(() -> Component.literal("Progress reset (doors already unlocked stay so in the world)"), false);
							return 1;
						}))
						.then(literal("set").then(argument("what", StringArgumentType.word())
								.suggests((ctx, b) -> SharedSuggestionProvider.suggest(List.of(HotelProgress.CRANK, HotelProgress.PENTHOUSE_KEY), b))
								.then(argument("on", StringArgumentType.word())
										.suggests((ctx, b) -> SharedSuggestionProvider.suggest(List.of("on", "off"), b))
										.executes(ctx -> {
											String what = StringArgumentType.getString(ctx, "what");
											boolean on = StringArgumentType.getString(ctx, "on").equals("on");
											HotelData.get(ctx.getSource().getLevel()).setDone(what, on);
											ctx.getSource().sendSuccess(() -> Component.literal(what + (on ? " done" : " not done")), false);
											return 1;
										})))))
				.then(literal("showroom").then(argument("pos", BlockPosArgument.blockPos()).executes(ctx -> {
					int n = HotelShowroom.build(ctx.getSource().getLevel(), BlockPosArgument.getBlockPos(ctx, "pos")).size();
					ctx.getSource().sendSuccess(() -> Component.literal("Showroom of " + n + " pieces"), false);
					return n;
				})))));
	}

	/** Every puzzle's answer in this world, for testing (the safe, the shelves and the code come from its seed). */
	private static int solutions(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = ctx.getSource().getLevel();
		long seed = level.getSeed();
		StringBuilder safe = new StringBuilder();
		for (int d : NightLedger.combination(level)) {
			safe.append(d).append(' ');
		}
		List<String> shelves = new java.util.ArrayList<>();
		List<CollectorsShelves.Specimen> order = CollectorsShelves.numbered(seed);
		for (int i = 0; i < order.size(); i++) {
			shelves.add((i + 1) + " " + order.get(i).name());
		}
		int[] code = LockdownCode.of(seed);
		StringBuilder release = new StringBuilder();
		for (int i = 0; i < code.length; i++) {
			release.append(code[i]).append(i == 3 ? "  " : " ");
		}
		String[] lines = {
				"1 The safe behind reception: " + safe.toString().strip() + "  (dials left to right; click +1, sneak-click -1)",
				"2 The generator: pump x3, choke open, crank x3, then close the choke when she catches",
				"3 The waltz, white keys from the left: 3 5 5, 2 4 4, 1 3 5, 8 5 3 (no gap over 5 s)",
				"4 The catalogue shelf, top row then bottom, left to right: " + String.join(", ", shelves),
				"5 The study clock: 11:47 (from 6:20: 5 sneak-clicks, then 27 clicks)",
				"Finale, the release code: " + release.toString().strip() + "  (key in first, power on)"};
		for (String line : lines) {
			ctx.getSource().sendSuccess(() -> Component.literal(line), false);
		}
		return 1;
	}

	private static int progress(CommandContext<CommandSourceStack> ctx) {
		HotelData data = HotelData.get(ctx.getSource().getLevel());
		String done = data.allDone().isEmpty() ? "nothing yet" : String.join(", ", data.allDone());
		ctx.getSource().sendSuccess(() -> Component.literal("Done: " + done + ". Power " + (data.power() ? "on" : "off") + ". Doors unlocked: "
				+ data.unlocked().size() / 2), false);
		return data.allDone().size();
	}

	private static HotelFrame frame(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		HotelFrame frame = HotelData.get(ctx.getSource().getLevel()).frame();
		if (frame == null) {
			throw fail("This world has no hotel");
		}
		return frame;
	}

	private static int build(CommandContext<CommandSourceStack> ctx, BlockPos at, Rotation turn) {
		ServerLevel level = ctx.getSource().getLevel();
		HotelFrame frame = new HotelFrame(at, turn);
		long start = System.nanoTime();
		int n = HotelWriter.write(level, frame, null, HotelRules.QUIET, Integer.MIN_VALUE, Integer.MAX_VALUE, false);
		HotelData.get(level).set(frame, false);
		HotelLifts.reset();
		long ms = (System.nanoTime() - start) / 1_000_000;
		ctx.getSource().sendSuccess(() -> Component.literal("Built the hotel at " + at.toShortString() + " (" + n + " blocks, " + ms + " ms)"),
				true);
		return 1;
	}

	private static int tp(CommandContext<CommandSourceStack> ctx, String place) throws CommandSyntaxException {
		HotelFrame frame = frame(ctx);
		HotelPlan plan = HotelBuilder.plan();
		int[] at = switch (place) {
			case "lobby" -> mark(plan, "lobby");
			case "atrium" -> new int[] {40, 0, 30};
			case "den" -> mark(plan, "rauk_den");
			case "dorm" -> mark(plan, "dormitory_bunk");
			case "chute" -> {
				int[] m = mark(plan, "chute_top");
				yield new int[] {m[0] - 2, m[1] + 1, m[2]};
			}
			case "front" -> new int[] {39, 0, HotelPlan.FRONT_Z + 8};
			case "court" -> new int[] {30, 0, 66};
			case "R" -> new int[] {30, Level.R.base, 5};
			case "4" -> new int[] {40, Level.L4.base, 9};
			default -> {
				for (Level lv : Level.values()) {
					if (lv.label.equals(place)) {
						yield new int[] {40, lv.base, 7};
					}
				}
				throw fail("No such place: " + place);
			}
		};
		BlockPos pos = frame.toWorld(at[0], at[1], at[2]);
		var entity = ctx.getSource().getEntityOrException();
		entity.teleportTo(ctx.getSource().getLevel(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, java.util.Set.of(), entity.getYRot(),
				entity.getXRot());
		return 1;
	}

	private static CommandSyntaxException fail(String message) {
		return new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal(message)).create();
	}

	private static int[] mark(HotelPlan plan, String name) {
		Mark m = plan.marks.get(name);
		return new int[] {m.x(), m.y(), m.z()};
	}

	private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		HotelFrame frame = frame(ctx);
		BlockPos c = frame.center();
		int distance = (int) Math.hypot(c.getX(), c.getZ());
		ctx.getSource().sendSuccess(() -> Component.literal("The Raukvik Grand: middle " + c.toShortString() + ", " + distance
				+ " blocks from the world's middle, turned " + frame.rotation().getSerializedName()), false);
		return 1;
	}

	private static int cut(CommandContext<CommandSourceStack> ctx, String label) throws CommandSyntaxException {
		HotelFrame frame = frame(ctx);
		for (Level lv : Level.values()) {
			if (lv.label.equals(label)) {
				int n = HotelWriter.write(ctx.getSource().getLevel(), frame, null, HotelRules.QUIET, lv.base + 3, Integer.MAX_VALUE, true);
				ctx.getSource().sendSuccess(() -> Component.literal("Cut the hotel open above level " + label + " (" + n + " blocks)"), false);
				return 1;
			}
		}
		throw fail("No such level: " + label);
	}

	/** Clears everything in the hotel's box, the ground round it included: for rebuilding a test hotel. */
	private static int clear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		HotelFrame frame = frame(ctx);
		ServerLevel level = ctx.getSource().getLevel();
		var box = frame.bounds();
		var air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int n = 0;
		for (int y = box.minY(); y <= box.maxY(); y++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				for (int x = box.minX(); x <= box.maxX(); x++) {
					if (!level.getBlockState(pos.set(x, y, z)).isAir()) {
						level.setBlock(pos, air, HotelRules.QUIET);
						n++;
					}
				}
			}
		}
		int cleared = n;
		ctx.getSource().sendSuccess(() -> Component.literal("Cleared " + cleared + " blocks"), false);
		return 1;
	}

	/** A Rauk at home in this hotel: in the Cabinet, or (random) wherever it might be when you find it. */
	private static int loose(CommandContext<CommandSourceStack> ctx, boolean anywhere) throws CommandSyntaxException {
		HotelFrame frame = frame(ctx);
		int[] den = mark(HotelBuilder.plan(), "rauk_den");
		BlockPos home = frame.toWorld(den[0], den[1], den[2]);
		ServerLevel level = ctx.getSource().getLevel();
		int[] start = anywhere ? RaukStart.pick(HotelBuilder.plan(), level.getRandom().nextLong()) : den;
		BlockPos at = frame.toWorld(start[0], start[1], start[2]);
		RaukEntity rauk = RaukEntities.RAUK.create(level);
		if (rauk == null) {
			return 0;
		}
		rauk.moveTo(Vec3.atBottomCenterOf(at), 180, 0);
		rauk.setHome(home);
		rauk.setPersistenceRequired();
		level.addFreshEntity(rauk);
		String where = HotelBuilder.plan().owner(start[0], start[1], start[2]) == null ? "?" : HotelBuilder.plan().owner(start[0], start[1], start[2]).name();
		ctx.getSource().sendSuccess(() -> Component.literal("Rauk is loose: " + where), true);
		return 1;
	}
}
