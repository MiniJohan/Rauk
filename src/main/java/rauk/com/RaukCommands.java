package rauk.com;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import java.util.Comparator;
import java.util.function.Consumer;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import rauk.com.entity.RaukEntity;
import rauk.com.entity.RaukPosture;
import rauk.com.entity.mind.Belief;
import rauk.com.entity.mind.Prey;
import rauk.com.entity.mind.RaukDebug;
import rauk.com.entity.move.RaukPathfinder;
import rauk.com.entity.move.SurfaceNode;

/**
 * Test controls for the nearest Rauk, used while its body and behavior are being built:
 * {@code /rauk posture upright|quad}, {@code /rauk walk <pos> [blocks per second]},
 * {@code /rauk stop}, {@code /rauk gaze <entity>|none}, {@code /rauk tension <0-1>},
 * {@code /rauk attention <0-1>}, {@code /rauk chase <entity> [blocks per second]},
 * {@code /rauk rise on|off}, {@code /rauk startle [pos]}, {@code /rauk climb <pos> <surface> [speed]},
 * {@code /rauk leap <pos>}, {@code /rauk debug} (the overlay of its mind), {@code /rauk listen on|off},
 * {@code /rauk tap <block>} and {@code /rauk brain on|off}.
 */
public final class RaukCommands {
	private RaukCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("rauk").requires(source -> source.hasPermission(2))
				.then(literal("posture")
						.then(literal("upright").executes(ctx -> apply(ctx, r -> r.setFloorStyle(RaukPosture.UPRIGHT))))
						.then(literal("quad").executes(ctx -> apply(ctx, r -> r.setFloorStyle(RaukPosture.QUADRUPED)))))
				.then(literal("walk").then(argument("pos", Vec3Argument.vec3())
						.executes(ctx -> walk(ctx, 2.5))
						.then(argument("speed", DoubleArgumentType.doubleArg(0.1, 15))
								.executes(ctx -> walk(ctx, DoubleArgumentType.getDouble(ctx, "speed"))))))
				.then(literal("climb").then(argument("pos", Vec3Argument.vec3()).then(argument("surface", StringArgumentType.word())
						.suggests((ctx, b) -> SharedSuggestionProvider.suggest(SURFACES, b))
						.executes(ctx -> climb(ctx, 3))
						.then(argument("speed", DoubleArgumentType.doubleArg(0.1, 15))
								.executes(ctx -> climb(ctx, DoubleArgumentType.getDouble(ctx, "speed")))))))
				.then(literal("leap").then(argument("pos", Vec3Argument.vec3()).executes(RaukCommands::leap)))
				.then(literal("probe").then(argument("pos", Vec3Argument.vec3()).then(argument("surface", StringArgumentType.word())
						.suggests((ctx, b) -> SharedSuggestionProvider.suggest(SURFACES, b))
						.executes(ctx -> {
							BlockPos cell = BlockPos.containing(Vec3Argument.getVec3(ctx, "pos"));
							Direction surface = surface(StringArgumentType.getString(ctx, "surface"));
							String report = new RaukPathfinder(ctx.getSource().getLevel()).explain(cell, surface);
							ctx.getSource().sendSuccess(() -> Component.literal(report), false);
							Rauk.LOGGER.info("probe {}", report);
							return 1;
						}))))
				.then(literal("heat").executes(RaukCommands::heat)
						.then(literal("warm").then(argument("pos", Vec3Argument.vec3()).then(argument("seconds", DoubleArgumentType.doubleArg(0, 100000))
								.executes(ctx -> {
									var level = ctx.getSource().getLevel();
									var player = ctx.getSource().getPlayerOrException();
									int area = rauk.com.hotel.HotelHeat.area(level, Vec3Argument.getVec3(ctx, "pos"));
									if (area < 0) {
										ctx.getSource().sendFailure(Component.literal("Not in a space of the hotel"));
										return 0;
									}
									rauk.com.hotel.HotelHeat.get(level).add(player.getUUID(), area, DoubleArgumentType.getDouble(ctx, "seconds"),
											level.getGameTime());
									return heat(ctx);
								})))))
				.then(literal("nav").executes(ctx -> nav(ctx, null))
						.then(literal("route").then(argument("pos", Vec3Argument.vec3()).executes(ctx -> nav(ctx, Vec3Argument.getVec3(ctx, "pos"))))))
				.then(literal("stop").executes(ctx -> apply(ctx, RaukEntity::stopWalking)))
				.then(literal("gaze")
						.then(literal("none").executes(ctx -> apply(ctx, r -> r.setGazeTarget(null))))
						.then(argument("target", EntityArgument.entity()).executes(ctx -> {
							var target = EntityArgument.getEntity(ctx, "target");
							return apply(ctx, r -> r.setGazeTarget(target));
						})))
				.then(literal("tension").then(argument("value", FloatArgumentType.floatArg(0, 1))
						.executes(ctx -> apply(ctx, r -> r.setTension(FloatArgumentType.getFloat(ctx, "value"))))))
				.then(literal("attention").then(argument("value", FloatArgumentType.floatArg(0, 1))
						.executes(ctx -> apply(ctx, r -> r.setAttention(FloatArgumentType.getFloat(ctx, "value"))))))
				.then(literal("chase").then(argument("target", EntityArgument.entity())
						.executes(ctx -> chase(ctx, 11))
						.then(argument("speed", DoubleArgumentType.doubleArg(0.1, 15))
								.executes(ctx -> chase(ctx, DoubleArgumentType.getDouble(ctx, "speed"))))))
				.then(literal("rise")
						.then(literal("on").executes(ctx -> apply(ctx, r -> r.setRisen(true))))
						.then(literal("off").executes(ctx -> apply(ctx, r -> r.setRisen(false)))))
				.then(literal("startle")
						.executes(ctx -> {
							CommandSourceStack source = ctx.getSource();
							Vec3 at = source.getEntity() != null ? source.getEntity().getEyePosition() : source.getPosition();
							return apply(ctx, r -> r.startle(at));
						})
						.then(argument("pos", Vec3Argument.vec3()).executes(ctx -> {
							Vec3 at = Vec3Argument.getVec3(ctx, "pos");
							return apply(ctx, r -> r.startle(at));
						})))
				.then(literal("debug").executes(ctx -> {
					var player = ctx.getSource().getPlayerOrException();
					boolean on = RaukDebug.toggle(player);
					ctx.getSource().sendSuccess(() -> Component.literal("Rauk debug overlay " + (on ? "on" : "off")), false);
					return 1;
				}))
				.then(literal("forget").executes(ctx -> apply(ctx, r -> {
					r.mind().forget();
					r.brain().reset();
					r.setCarcass(null);
				})))
				.then(literal("prey").then(literal("creative")
						.then(literal("on").executes(ctx -> {
							Prey.huntCreative = true;
							return 1;
						}))
						.then(literal("off").executes(ctx -> {
							Prey.huntCreative = false;
							return 1;
						}))))
				// A rough idea where someone is: as if it had heard them, uncertainty blocks off.
				.then(literal("believe").then(argument("target", EntityArgument.entity())
						.then(argument("uncertainty", DoubleArgumentType.doubleArg(0, 24)).executes(ctx -> {
							var target = EntityArgument.getEntity(ctx, "target");
							double u = DoubleArgumentType.getDouble(ctx, "uncertainty");
							return apply(ctx, r -> {
								var b = r.mind().belief(target.getUUID());
								Vec3 off = new Vec3(r.getRandom().nextDouble() - 0.5, 0, r.getRandom().nextDouble() - 0.5).scale(u);
								b.observe(target.position().add(off), null, u, 0.35, Belief.Sense.HEARING, r.level().getGameTime());
							});
						}))))
				.then(literal("ambush")
						.then(literal("door").then(argument("door", BlockPosArgument.blockPos()).then(argument("room", Vec3Argument.vec3())
								.executes(ctx -> {
									BlockPos door = BlockPosArgument.getBlockPos(ctx, "door");
									Vec3 room = Vec3Argument.getVec3(ctx, "room");
									return apply(ctx, r -> r.brain().ambushDoor(door, room, r.level().getGameTime()));
								}))))
						.then(argument("pos", Vec3Argument.vec3()).executes(ctx -> {
							Vec3 pos = Vec3Argument.getVec3(ctx, "pos");
							return apply(ctx, r -> r.brain().ambushAt(pos, r.level().getGameTime()));
						})))
				.then(literal("survey").executes(ctx -> apply(ctx, r -> r.mind().territory.survey((net.minecraft.server.level.ServerLevel) r.level(),
						r.blockPosition(), 48, r.level().getGameTime()))))
				.then(literal("home").then(argument("pos", BlockPosArgument.blockPos()).executes(ctx -> {
					BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
					return apply(ctx, r -> r.setHome(pos));
				})))
				// How it goes about its prey: hunting it down now, or playing with it first.
				.then(literal("mood")
						.then(literal("hunt").executes(ctx -> apply(ctx, r -> r.brain().setMood(rauk.com.entity.hunt.RaukBrain.Mood.HUNT))))
						.then(literal("play").executes(ctx -> apply(ctx, r -> r.brain().setMood(rauk.com.entity.hunt.RaukBrain.Mood.PLAY)))))
				// Teaches it a place someone keeps being found in, as if it had found them there that often.
				.then(literal("haunt").then(argument("target", EntityArgument.entity()).then(argument("pos", BlockPosArgument.blockPos())
						.then(argument("weight", DoubleArgumentType.doubleArg(0, 10000)).executes(ctx -> {
							var target = EntityArgument.getEntity(ctx, "target");
							BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
							double w = DoubleArgumentType.getDouble(ctx, "weight");
							return apply(ctx, r -> r.mind().profile(target.getUUID()).addHaunt(pos, w));
						})))))
				.then(literal("hunger").then(argument("value", DoubleArgumentType.doubleArg(0, 1)).executes(ctx -> {
					double v = DoubleArgumentType.getDouble(ctx, "value");
					return apply(ctx, r -> r.brain().hunger = v);
				})))
				.then(literal("listen")
						.then(literal("on").executes(ctx -> apply(ctx, r -> r.mind().setListening(true))))
						.then(literal("off").executes(ctx -> apply(ctx, r -> r.mind().setListening(false)))))
				.then(literal("tap").then(argument("pos", BlockPosArgument.blockPos()).executes(ctx -> {
					BlockPos block = BlockPosArgument.getBlockPos(ctx, "pos");
					return apply(ctx, r -> {
						Vec3 c = Vec3.atCenterOf(block), eye = r.mind().eye();
						Direction toward = Direction.getNearest(eye.x - c.x, eye.y - c.y, eye.z - c.z);
						r.mind().tap(block, c.add(toward.getStepX() * 0.5, toward.getStepY() * 0.5, toward.getStepZ() * 0.5));
					});
				})))
				// Combat test: make it grab someone.
				.then(literal("grab").then(argument("target", EntityArgument.entity()).executes(ctx -> {
					var target = EntityArgument.getEntity(ctx, "target");
					return apply(ctx, r -> {
						if (target instanceof net.minecraft.world.entity.LivingEntity living) {
							r.combat().grab(living);
						}
					});
				})))
				.then(literal("brain")
						.then(literal("on").executes(ctx -> apply(ctx, r -> r.setBrainEnabled(true))))
						.then(literal("off").executes(ctx -> apply(ctx, r -> {
							r.setBrainEnabled(false);
							r.stopWalking();
						})))));
	}

	private static int chase(CommandContext<CommandSourceStack> ctx, double speed) throws CommandSyntaxException {
		var target = EntityArgument.getEntity(ctx, "target");
		return apply(ctx, r -> r.chase(target, speed, 3.5));
	}

	private static final java.util.List<String> SURFACES = java.util.List.of("floor", "ceiling", "north", "south", "east", "west");

	private static Direction surface(String name) {
		return switch (name) {
			case "ceiling", "up" -> Direction.UP;
			case "north" -> Direction.NORTH;
			case "south" -> Direction.SOUTH;
			case "east" -> Direction.EAST;
			case "west" -> Direction.WEST;
			default -> Direction.DOWN;
		};
	}

	private static int climb(CommandContext<CommandSourceStack> ctx, double speed) {
		Vec3 pos = Vec3Argument.getVec3(ctx, "pos");
		Direction surface = surface(StringArgumentType.getString(ctx, "surface"));
		return apply(ctx, r -> r.climbTo(pos, surface, speed));
	}

	/** Leaps to the given spot, taking hold of whichever solid side is next to it. */
	private static int leap(CommandContext<CommandSourceStack> ctx) {
		Vec3 pos = Vec3Argument.getVec3(ctx, "pos");
		return apply(ctx, r -> {
			BlockPos cell = BlockPos.containing(pos);
			RaukPathfinder finder = new RaukPathfinder(r.level());
			Direction surface = Direction.DOWN;
			for (Direction d : new Direction[] {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
				if (d == Direction.DOWN ? finder.blocked(cell.below()) : finder.grips(cell.relative(d), d.getOpposite())) {
					surface = d;
					break;
				}
			}
			SurfaceNode node = finder.node(cell, surface);
			Vec3 at = node != null ? node.position() : pos;
			if (!r.mover().leap(at, surface)) {
				ctx.getSource().sendFailure(Component.literal("Rauk can't leap there: out of range or the way isn't clear"));
			}
		});
	}

	/** The heat map (design doc 6.5): the warmest spaces of the hotel, for everyone, and when Rauk last looked in. */
	private static int heat(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
		var level = ctx.getSource().getLevel();
		long now = level.getGameTime();
		var heat = rauk.com.hotel.HotelHeat.get(level);
		var plan = rauk.com.hotel.HotelBuilder.plan();
		StringBuilder out = new StringBuilder("Warmest:");
		for (double[] w : heat.warmest(null, now, 8)) {
			out.append(" | ").append(plan.areas.get((int) w[0]).name()).append(' ').append(Math.round(w[1])).append("s, looked in ")
					.append(heat.sinceChecked((int) w[0], now) / 20).append("s ago");
		}
		ctx.getSource().sendSuccess(() -> Component.literal(out.toString()), false);
		Rauk.LOGGER.info("heat: {}", out);
		return 1;
	}

	/**
	 * Rauk's map of the hotel (design doc 4.7): worked out now if it isn't, and how big it is; with a
	 * place, the way from the nearest Rauk there, doorway by doorway, back from the place.
	 */
	private static int nav(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, Vec3 to) {
		var level = ctx.getSource().getLevel();
		rauk.com.hotel.HotelGraph graph = rauk.com.hotel.HotelGraph.now(level);
		if (graph == null) {
			ctx.getSource().sendFailure(Component.literal("No hotel here"));
			return 0;
		}
		String size = graph.places() + " places, " + graph.sections() + " sections, " + graph.gates.size() + " doorways, worked out in "
				+ graph.millis + " ms";
		ctx.getSource().sendSuccess(() -> Component.literal(size), false);
		Rauk.LOGGER.info("nav: {}", size);
		if (to == null) {
			return 1;
		}
		RaukEntity r = level.getEntitiesOfClass(RaukEntity.class, new net.minecraft.world.phys.AABB(BlockPos.containing(to)).inflate(400))
				.stream().findFirst().orElse(null);
		if (r == null) {
			ctx.getSource().sendFailure(Component.literal("No Rauk"));
			return 0;
		}
		long t0 = System.nanoTime();
		var field = graph.fieldTo(to, p -> false, g -> false, level.getGameTime());
		int here = graph.placeNear(r.position());
		var route = field == null ? null : field.route(here);
		long us = (System.nanoTime() - t0) / 1000;
		StringBuilder line = new StringBuilder("route from " + r.blockPosition().toShortString() + " ("
				+ (here < 0 ? "off the map" : graph.plan.areas.get(graph.area(graph.part(here))).name()) + ") to " + BlockPos.containing(to).toShortString()
				+ ": ");
		if (route == null) {
			line.append("NONE");
		} else {
			line.append(String.format("%.1f", route.cost())).append(" in ").append(us).append(" us");
			for (var g : route.gates()) {
				line.append(" | ").append(graph.plan.areas.get(graph.area(g.to())).name()).append(" @ ").append(BlockPos.containing(g.at()).toShortString());
			}
		}
		ctx.getSource().sendSuccess(() -> Component.literal(line.toString()), false);
		Rauk.LOGGER.info("nav: {}", line);
		return 1;
	}

	private static int walk(CommandContext<CommandSourceStack> ctx, double speed) {
		Vec3 pos = Vec3Argument.getVec3(ctx, "pos");
		return apply(ctx, r -> r.walkTo(pos, speed));
	}

	private static int apply(CommandContext<CommandSourceStack> ctx, Consumer<RaukEntity> action) {
		CommandSourceStack source = ctx.getSource();
		Vec3 at = source.getPosition();
		RaukEntity rauk = source.getLevel().getEntitiesOfClass(RaukEntity.class, new AABB(at, at).inflate(96)).stream()
				.min(Comparator.comparingDouble(r -> r.distanceToSqr(at))).orElse(null);
		if (rauk == null) {
			source.sendFailure(Component.literal("No Rauk within 96 blocks"));
			return 0;
		}
		action.accept(rauk);
		return 1;
	}
}
