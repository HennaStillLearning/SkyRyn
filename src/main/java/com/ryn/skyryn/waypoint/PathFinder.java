package com.ryn.skyryn.waypoint;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PathFinder {
	private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "skyryn-pathfinder");
		t.setDaemon(true);
		return t;
	});

	private static final double MAX_PATH_DIST = 1000;
	private static final int MARGIN = 14;
	private static final int MARGIN_Y = 32;
	private static final int NODE_BUDGET = 80000;
	private static final double HEUR_WEIGHT_NEAR = 1.25;
	private static final double HEUR_WEIGHT_FAR = 2.4;
	private static final double NEAR_DIST = 80;
	private static final double FAR_DIST = 400;

	private static double heurWeight(double startGoalDist) {
		if (startGoalDist <= NEAR_DIST) return HEUR_WEIGHT_NEAR;
		if (startGoalDist >= FAR_DIST) return HEUR_WEIGHT_FAR;
		double t = (startGoalDist - NEAR_DIST) / (FAR_DIST - NEAR_DIST);
		return HEUR_WEIGHT_NEAR + t * (HEUR_WEIGHT_FAR - HEUR_WEIGHT_NEAR);
	}
	private static final double TELEPORT_JUMP = 20 * 20;

	private static volatile List<Vec3> lastPath = null;
	private static volatile Vec3 lastFrom = null;
	private static volatile Vec3 lastTo = null;
	private static volatile boolean computing = false;

	public static List<Vec3> currentPath() {
		return lastPath;
	}

	public static void requestIfNeeded(Level level, Vec3 from, Vec3 to) {
		if (computing || level == null) return;

		boolean teleport = lastFrom != null && lastFrom.distanceToSqr(from) >= TELEPORT_JUMP;
		boolean movedEnough = lastFrom == null || teleport || lastFrom.distanceToSqr(from) >= 36;
		boolean targetChanged = lastTo == null || lastTo.distanceToSqr(to) >= 4;
		if (!movedEnough && !targetChanged) return;

		if (teleport) lastPath = null;

		computing = true;
		EXEC.submit(() -> {
			try {
				lastPath = compute(level, from, to);
			} catch (Exception e) {
				lastPath = null;
			} finally {
				lastFrom = from;
				lastTo = to;
				computing = false;
			}
		});
	}

	private static List<Vec3> compute(Level level, Vec3 from, Vec3 to) {
		if (from.distanceToSqr(to) > MAX_PATH_DIST * MAX_PATH_DIST) return null;

		BlockPos start = findStand(level, BlockPos.containing(from));
		BlockPos goal = findStand(level, BlockPos.containing(to));
		if (start == null || goal == null || start.equals(goal)) return null;

		List<BlockPos> raw = aStar(level, start, goal);
		if (raw == null || raw.size() < 2) return null;

		List<Vec3> pts = new ArrayList<>(raw.size());
		for (BlockPos p : raw) pts.add(new Vec3(p.getX() + 0.5, p.getY() + 0.1, p.getZ() + 0.5));
		return smooth(level, pts);
	}

	private record Open(BlockPos pos, double f) { }

	private static List<BlockPos> aStar(Level level, BlockPos start, BlockPos goal) {
		int vertGap = Math.abs(start.getY() - goal.getY());
		int margin = MARGIN + Math.min(60, vertGap / 2);
		int minX = Math.min(start.getX(), goal.getX()) - margin, maxX = Math.max(start.getX(), goal.getX()) + margin;
		int minZ = Math.min(start.getZ(), goal.getZ()) - margin, maxZ = Math.max(start.getZ(), goal.getZ()) + margin;
		int minY = Math.min(start.getY(), goal.getY()) - MARGIN_Y, maxY = Math.max(start.getY(), goal.getY()) + MARGIN_Y;

		double weight = heurWeight(Math.sqrt(start.distSqr(goal)));

		Map<BlockPos, Double> gScore = new HashMap<>();
		Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
		PriorityQueue<Open> open = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));

		gScore.put(start, 0.0);
		open.add(new Open(start, heuristic(weight, start, start, goal)));
		int expanded = 0;

		BlockPos best = start;
		double bestD = start.distSqr(goal);

		while (!open.isEmpty()) {
			BlockPos cur = open.poll().pos;
			if (cur.equals(goal)) return reconstruct(cameFrom, cur);
			double d = cur.distSqr(goal);
			if (d < bestD) { bestD = d; best = cur; }
			if (++expanded > NODE_BUDGET) break;

			double curG = gScore.getOrDefault(cur, Double.MAX_VALUE);
			for (BlockPos next : neighbors(level, cur, minX, maxX, minZ, maxZ, minY, maxY)) {
				double step = stepCost(cur, next);
				if (inWater(level, next)) {
					step += (next.getY() < cur.getY()) ? WATER_DIVE : WATER_SWIM;
				}
				double g = curG + step;
				if (g < gScore.getOrDefault(next, Double.MAX_VALUE)) {
					gScore.put(next, g);
					cameFrom.put(next, cur);
					open.add(new Open(next, g + heuristic(weight, start, next, goal)));
				}
			}
		}
		return best.equals(start) ? null : reconstruct(cameFrom, best);
	}

	private static double heuristic(double weight, BlockPos start, BlockPos a, BlockPos goal) {
		double dist = Math.sqrt(a.distSqr(goal));
		double dx1 = a.getX() - goal.getX(), dz1 = a.getZ() - goal.getZ();
		double dx2 = start.getX() - goal.getX(), dz2 = start.getZ() - goal.getZ();
		double cross = Math.abs(dx1 * dz2 - dx2 * dz1);
		return weight * dist + cross * 0.001;
	}

	private static final double JUMP_COST = 1.3;
	private static final double DROP_COST = 1.2;
	private static final double WATER_DIVE = 4.0;
	private static final double WATER_SWIM = 0.4;

	private static double stepCost(BlockPos from, BlockPos to) {
		int dx = Math.abs(to.getX() - from.getX()), dz = Math.abs(to.getZ() - from.getZ());
		double horiz = (dx != 0 && dz != 0) ? 1.4142 : (dx + dz == 0 ? 0.0 : 1.0);
		int dy = to.getY() - from.getY();
		double vert;
		if (dy > 0) {
			vert = dy * JUMP_COST;
		} else if (dy < 0) {
			int drop = -dy;
			vert = drop + (drop > 1 ? (drop - 1) * (drop - 1) * DROP_COST : 0);
		} else {
			vert = 0;
		}
		return horiz + vert;
	}

	private static List<BlockPos> reconstruct(Map<BlockPos, BlockPos> cameFrom, BlockPos cur) {
		List<BlockPos> out = new ArrayList<>();
		out.add(cur);
		while (cameFrom.containsKey(cur)) {
			cur = cameFrom.get(cur);
			out.add(cur);
		}
		java.util.Collections.reverse(out);
		return out;
	}

	private static List<BlockPos> neighbors(Level level, BlockPos from,
											 int minX, int maxX, int minZ, int maxZ, int minY, int maxY) {
		boolean swim = inWater(level, from);
		List<BlockPos> out = new ArrayList<>(10);
		int[] walkDy = {1, 0, -1, -2, -3};
		int[] swimDy = {0, -1, 1, -2, 2};
		int[] dyOrder = swim ? swimDy : walkDy;

		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0) continue;
				int nx = from.getX() + dx, nz = from.getZ() + dz;
				if (nx < minX || nx > maxX || nz < minZ || nz > maxZ) continue;

				if (dx != 0 && dz != 0) {
					if (!passable(level, new BlockPos(from.getX() + dx, from.getY(), from.getZ()))
							|| !passable(level, new BlockPos(from.getX(), from.getY(), from.getZ() + dz))) {
						continue;
					}
				}

				for (int dy : dyOrder) {
					int ny = from.getY() + dy;
					if (ny < minY || ny > maxY) continue;
					BlockPos cand = new BlockPos(nx, ny, nz);
					if (enterable(level, cand)) { out.add(cand); break; }
				}
			}
		}

		if (swim) {
			BlockPos up = from.above(), down = from.below();
			if (up.getY() <= maxY && enterable(level, up)) out.add(up);
			if (down.getY() >= minY && enterable(level, down)) out.add(down);
		}
		return out;
	}

	private static BlockPos findStand(Level level, BlockPos near) {
		for (int dy = 2; dy >= -3; dy--) {
			BlockPos p = near.offset(0, dy, 0);
			if (enterable(level, p)) return p;
		}
		return null;
	}

	private static boolean enterable(Level level, BlockPos foot) {
		if (!level.hasChunkAt(foot)) return false;
		boolean footOk = passable(level, foot) || isLowFloor(level, foot);
		if (!footOk || !passable(level, foot.above())) return false;
		return supported(level, foot);
	}

	private static boolean supported(Level level, BlockPos foot) {
		if (isLowFloor(level, foot)) return true;
		if (isWater(level, foot)) return true;
		BlockPos below = foot.below();
		BlockState bs = level.getBlockState(below);
		if (bs.getFluidState().getType().is(FluidTags.LAVA)) return false;
		if (!bs.getCollisionShape(level, below).isEmpty()) return true;
		return !bs.getFluidState().isEmpty();
	}

	private static boolean inWater(Level level, BlockPos foot) {
		return isWater(level, foot);
	}

	private static boolean isWater(Level level, BlockPos pos) {
		var fluid = level.getBlockState(pos).getFluidState();
		return !fluid.isEmpty() && !fluid.getType().is(FluidTags.LAVA);
	}

	private static boolean isLowFloor(Level level, BlockPos pos) {
		var shape = level.getBlockState(pos).getCollisionShape(level, pos);
		return !shape.isEmpty() && shape.bounds().maxY <= 0.55;
	}

	private static boolean passable(Level level, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		if (!s.getCollisionShape(level, pos).isEmpty()) return false;
		return !s.getFluidState().getType().is(FluidTags.LAVA);
	}

	private static List<Vec3> smooth(Level level, List<Vec3> raw) {
		if (raw.size() <= 2) return raw;
		List<Vec3> out = new ArrayList<>();
		out.add(raw.get(0));
		int anchor = 0;
		for (int i = 2; i < raw.size(); i++) {
			if (!lineOfSight(level, raw.get(anchor), raw.get(i))) {
				out.add(raw.get(i - 1));
				anchor = i - 1;
			}
		}
		out.add(raw.get(raw.size() - 1));
		return out;
	}

	private static boolean lineOfSight(Level level, Vec3 a, Vec3 b) {
		double dist = a.distanceTo(b);
		int steps = Math.max(1, (int) Math.ceil(dist / 0.4));
		for (int i = 0; i <= steps; i++) {
			double t = (double) i / steps;
			double x = a.x + (b.x - a.x) * t;
			double y = a.y + (b.y - a.y) * t;
			double z = a.z + (b.z - a.z) * t;
			BlockPos p = BlockPos.containing(x, y, z);
			boolean footOk = passable(level, p) || isLowFloor(level, p);
			if (!footOk || !passable(level, p.above())) return false;
		}
		return true;
	}
}
