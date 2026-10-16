package rauk.com.hotel;

import java.util.Random;

/**
 * The lockdown's release code (hotel design 4.4 to 4.6): eight figures, in two halves. The first
 * half is on a card in Wennberg's hidden study (puzzle 4), the second in the lockdown control room
 * behind his clock (puzzle 5); the release panel wants all eight (the finale). Each world has its
 * own, from its seed.
 */
public final class LockdownCode {
	private LockdownCode() {
	}

	/** The eight figures. */
	public static int[] of(long seed) {
		Random random = new Random(seed * 13 + 2347);
		int[] code = new int[8];
		for (int i = 0; i < 8; i++) {
			code[i] = random.nextInt(10);
		}
		return code;
	}
}
