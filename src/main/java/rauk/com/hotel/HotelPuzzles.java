package rauk.com.hotel;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The puzzles' working parts in the furniture (hotel design 4): a piece whose kind is
 * "puzzle:<name>" is worked here, clicked bare-handed or with something held to it.
 */
public final class HotelPuzzles {
	private HotelPuzzles() {
	}

	public static InteractionResult use(Level level, BlockPos pos, BlockState state, Player player, String name, BlockHitResult hit) {
		if (!(level instanceof ServerLevel server)) {
			return InteractionResult.SUCCESS;
		}
		return switch (name) {
			case "generator", "primer", "choke", "crank" -> Generator.use(server, pos, state, player, name);
			case "fuse_board" -> Generator.board(server, pos, state, player, ItemStack.EMPTY).result();
			case "till" -> Generator.till(server, pos, state, player);
			case "piano" -> LastWaltz.key(server, pos, state, player, hit);
			case "drawer" -> LastWaltz.drawer(server, pos, state, player);
			case "release_panel" -> LockdownRelease.panel(server, pos, state, player, hit);
			case "release_lever" -> LockdownRelease.lever(server, pos, state, player);
			default -> InteractionResult.PASS;
		};
	}

	public static ItemInteractionResult useItem(ItemStack stack, Level level, BlockPos pos, BlockState state, Player player, String name) {
		if (name.equals("fuse_board") && stack.is(rauk.com.registry.RaukItems.FUSE)) {
			return level instanceof ServerLevel server ? Generator.board(server, pos, state, player, stack) : ItemInteractionResult.SUCCESS;
		}
		if (name.equals("release_panel") && stack.is(rauk.com.registry.RaukItems.LOCKDOWN_KEY)) {
			return level instanceof ServerLevel server ? LockdownRelease.panelItem(server, pos, state, player, stack) : ItemInteractionResult.SUCCESS;
		}
		return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}
}
