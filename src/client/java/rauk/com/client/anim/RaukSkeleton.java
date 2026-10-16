package rauk.com.client.anim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import rauk.com.client.model.RaukRig;
import rauk.com.client.model.RaukRig.Part;
import rauk.com.client.model.RaukRig.V;

/**
 * The rig's parts as indexed joints with their rest transforms, grouped the way the animator
 * drives them. Built once from {@link RaukRig#shared()}.
 */
public final class RaukSkeleton {
	public final RaukRig rig;
	public final int count;
	public final String[] names;
	public final int[] parent;
	/** Rest transform relative to the parent (design space), and relative to the body. */
	public final Xform[] restLocal, restWorld;

	public final int pelvis, head, jaw;
	/** Vertebrae from the first above the pelvis to the atlas. */
	public final int[] spine;
	/** True for the vertebrae of {@link #spine} that carry ribs: the chest. */
	public final boolean[] chest;
	/** The neck: the tail end of {@link #spine}. */
	public final int[] neck;
	public final int[] tail;
	public final V[] tailJoints;
	public final double[] tailRadius;
	public final Limb[] legs = new Limb[2], arms = new Limb[2];
	/** The first segment of each rib, and the side it is on. */
	public final int[] ribs, ribSide;
	public final int[][] drool;

	private final Map<String, Integer> index = new HashMap<>();

	public record Limb(int side, int upper, int lower, int end, int[][] digits) {
	}

	private static RaukSkeleton shared;

	public static synchronized RaukSkeleton shared() {
		if (shared == null) {
			shared = new RaukSkeleton(RaukRig.shared());
		}
		return shared;
	}

	private RaukSkeleton(RaukRig rig) {
		this.rig = rig;
		count = rig.parts.size();
		names = new String[count];
		parent = new int[count];
		restLocal = new Xform[count];
		restWorld = new Xform[count];
		for (int i = 0; i < count; i++) {
			Part p = rig.parts.get(i);
			names[i] = p.name;
			index.put(p.name, i);
		}
		for (int i = 0; i < count; i++) {
			Part p = rig.parts.get(i);
			parent[i] = p.parent == null ? -1 : index.get(p.parent.name);
			restWorld[i] = Xform.of(p.frame);
			restLocal[i] = parent[i] < 0 ? restWorld[i] : restWorld[parent[i]].toLocal(restWorld[i]);
		}

		pelvis = index("pelvis");
		head = index("head");
		jaw = index("jaw");
		List<Integer> sp = new ArrayList<>(), nk = new ArrayList<>();
		chest = new boolean[rig.spine.size() - 1];
		for (int i = 1; i < rig.spine.size(); i++) {
			int j = index(rig.spine.get(i).name);
			double idx = rig.spineIndex.get(i);
			chest[i - 1] = idx > RaukRig.NECK_BASE + 0.3 && idx < RaukRig.RIBS_END + 0.3;
			sp.add(j);
			if (rig.spineIndex.get(i) < RaukRig.NECK_BASE + 0.3) {
				nk.add(j);
			}
		}
		spine = ints(sp);
		neck = ints(nk);

		tail = new int[rig.tail.size()];
		tailRadius = new double[tail.length];
		for (int i = 0; i < tail.length; i++) {
			tail[i] = index(rig.tail.get(i).name);
			tailRadius[i] = rig.tail.get(i).boxes.get(0).h / 2.0 / RaukRig.UNITS;
		}
		tailJoints = rig.tailJoints.toArray(new V[0]);

		for (int s = 0; s < 2; s++) {
			String n = s == 0 ? "l" : "r";
			legs[s] = new Limb(s == 0 ? 1 : -1, index("leg_" + n + "_thigh"), index("leg_" + n + "_shin"), index("leg_" + n + "_foot"),
					new int[][] {chain("toe_" + n + "_0_"), chain("toe_" + n + "_1_"), chain("toe_" + n + "_2_")});
			int[][] digits = new int[5][];
			for (int f = 0; f < 4; f++) {
				digits[f] = chain("finger_" + n + "_" + f + "_");
			}
			digits[4] = chain("thumb_" + n + "_");
			arms[s] = new Limb(s == 0 ? 1 : -1, index("arm_" + n + "_upper"), index("arm_" + n + "_fore"), index("hand_" + n), digits);
		}

		List<Integer> rb = new ArrayList<>(), rs = new ArrayList<>();
		for (int i = 0; i < rig.spine.size(); i++) {
			for (int s = 0; s < 2; s++) {
				Integer j = index.get("rib_" + (s == 0 ? "l" : "r") + "_" + i + "_0");
				if (j != null) {
					rb.add(j);
					rs.add(s == 0 ? 1 : -1);
				}
			}
		}
		ribs = ints(rb);
		ribSide = ints(rs);

		drool = new int[rig.drool.size()][];
		for (int i = 0; i < drool.length; i++) {
			drool[i] = rig.drool.get(i).stream().mapToInt(p -> index(p.name)).toArray();
		}
	}

	public int index(String name) {
		Integer i = index.get(name);
		if (i == null) {
			throw new IllegalArgumentException("No part " + name);
		}
		return i;
	}

	/** Parts named prefix0, prefix1, ... then prefix + "claw_0", "claw_1", ... in order. */
	private int[] chain(String prefix) {
		List<Integer> out = new ArrayList<>();
		for (int k = 0; index.containsKey(prefix + k); k++) {
			out.add(index.get(prefix + k));
		}
		for (int k = 0; index.containsKey(prefix + "claw_" + k); k++) {
			out.add(index.get(prefix + "claw_" + k));
		}
		return ints(out);
	}

	private static int[] ints(List<Integer> list) {
		return list.stream().mapToInt(Integer::intValue).toArray();
	}
}
