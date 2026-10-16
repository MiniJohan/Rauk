package rauk.com.hotel;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import rauk.com.block.hotel.FurnitureBlock;
import rauk.com.hotel.HotelFurniture.Form;
import rauk.com.hotel.HotelFurniture.Light;
import rauk.com.hotel.HotelFurniture.Mount;
import rauk.com.hotel.HotelFurniture.Piece;
import rauk.com.hotel.HotelMaterials.Shape;
import rauk.com.registry.HotelBlocks;

/**
 * A showroom of every piece of furniture, for review (/rauk hotel showroom): a row of bays along
 * x, each with a parquet floor, a panelled back wall to the south and a ceiling, a piece in each,
 * facing north out of its bay. Rows and stacks are shown three long, tables two by three, lights
 * lit; then the shelves and cases that fill a block, two high against the wall.
 */
public final class HotelShowroom {
	private HotelShowroom() {
	}

	/** A bay: what's in it (a piece, or a hotel material by its id), where it starts along x, how wide. */
	public record Bay(String id, Piece piece, int x, int w) {
	}

	public static List<Bay> bays() {
		List<Bay> out = new ArrayList<>();
		int x = 0;
		for (Piece p : HotelFurniture.all()) {
			int w = Math.max(p.w, p.form == Form.RUN || p.form == Form.TABLE ? 3 : 1) + 2;
			out.add(new Bay(p.id, p, x, w));
			x += w;
		}
		for (HotelMaterials.Material m : HotelMaterials.ALL) {
			if (m.shape() == Shape.FACING && m.other() != null && m.other().startsWith("f_") || m.id().equals("crate")) {
				out.add(new Bay(m.id(), null, x, 3));
				x += 3;
			}
		}
		return out;
	}

	/** Builds it with its first bay's front left corner at {@code at}. */
	public static List<Bay> build(ServerLevel level, BlockPos at) {
		List<Bay> bays = bays();
		Bay last = bays.get(bays.size() - 1);
		int length = last.x() + last.w();
		int flags = Block.UPDATE_CLIENTS;
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-1, 0, -1), at.offset(length, 5, 7))) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), flags);
		}
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-1, -1, -1), at.offset(length, -1, 7))) {
			level.setBlock(pos, HotelBlocks.get("parquet").defaultBlockState(), flags);
		}
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-1, 0, 6), at.offset(length, 4, 6))) {
			level.setBlock(pos, HotelBlocks.get(pos.getY() == at.getY() ? "mahogany_wainscot" : "damask_red").defaultBlockState(), flags);
		}
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-1, 5, -1), at.offset(length, 5, 6))) {
			level.setBlock(pos, HotelBlocks.get("coffered_mahogany").defaultBlockState(), flags);
		}
		for (Bay bay : bays) {
			if (bay.piece() == null) {
				BlockState s = HotelBlocks.get(bay.id()).defaultBlockState();
				if (s.hasProperty(FurnitureBlock.FACING)) {
					s = s.setValue(FurnitureBlock.FACING, Direction.NORTH);
				}
				level.setBlock(at.offset(bay.x() + 1, 0, 5), s, flags);
				level.setBlock(at.offset(bay.x() + 1, 1, 5), s, flags);
				continue;
			}
			Piece p = bay.piece();
			BlockPos spot = at.offset(bay.x() + 1, 0, p.mount == Mount.WALL ? 5 : 2);
			if (p.mount == Mount.CEILING) {
				spot = at.offset(bay.x() + 1, 4, 3);
			}
			if (p.kind.startsWith("lift_")) {
				BlockState s = (p.kind.equals("lift_control") ? HotelBlocks.liftControl : HotelBlocks.liftButton).defaultBlockState();
				level.setBlock(spot.above(), s.setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.NORTH), flags);
				continue;
			}
			if (p.kind.equals("switch")) {
				level.setBlock(spot.above(), HotelBlocks.lightSwitch.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.WALL)
						.setValue(LeverBlock.FACING, Direction.NORTH).setValue(LeverBlock.POWERED, true), flags);
				continue;
			}
			place(level, p, spot);
		}
		return bays;
	}

	private static void place(ServerLevel level, Piece p, BlockPos at) {
		FurnitureBlock block = HotelBlocks.FURNITURE.get(p.id);
		BlockState base = block.defaultBlockState();
		if (p.facing) {
			base = base.setValue(FurnitureBlock.FACING, Direction.NORTH);
		}
		if (p.light != Light.NONE) {
			base = base.setValue(FurnitureBlock.LIT, true);
		}
		List<BlockPos> cells = new ArrayList<>();
		switch (p.form) {
			case RUN -> {
				for (int i = 0; i < 3; i++) {
					cells.add(at.east(i));
				}
			}
			case STACK -> {
				for (int i = 0; i < 3; i++) {
					cells.add(at.above(i));
				}
			}
			case TABLE -> {
				for (int i = 0; i < 2; i++) {
					for (int j = 0; j < 3; j++) {
						cells.add(at.offset(j, 0, i));
					}
				}
			}
			case MULTI -> {
				for (int part = 0; part < p.parts(); part++) {
					level.setBlock(block.partPos(at, Direction.NORTH, part), block.withPart(base, part), Block.UPDATE_CLIENTS);
				}
				return;
			}
			default -> cells.add(at);
		}
		for (BlockPos c : cells) {
			level.setBlock(c, base, Block.UPDATE_CLIENTS);
		}
		for (BlockPos c : cells) {
			level.setBlock(c, block.joined(level.getBlockState(c), level, c), Block.UPDATE_CLIENTS);
		}
	}
}
