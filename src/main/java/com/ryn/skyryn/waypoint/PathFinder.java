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

/**
 * Путь в обход препятствий: A* по "стоячим" клеткам (колонка x,z + подобранный
 * пол y), результат обрезается (string pulling) до ломаной из прямых отрезков —
 * без A*-лесенки по блокам. Считается в фоновом потоке, рендер путь не ждёт.
 *
 * Границы поиска — бокс старт-финиш + запас; бюджет узлов ограничен, чтобы не
 * зависнуть в открытой местности. Не нашли путь / он далёк / бюджет кончился —
 * currentPath() вернёт null, вызывающий рисует как раньше — прямую линию.
 */
public class PathFinder {

	private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "skyryn-pathfinder");
		t.setDaemon(true);
		return t;
	});

	/** Дальше не считаем вовсе — споты дальние, линия и так почти всегда открытая. */
	private static final double MAX_PATH_DIST = 1000;
	/** Запас вокруг бокса старт-финиш, в блоках. */
	private static final int MARGIN = 14;
	/** Запас по высоте. Больше обычного — под воду ныряют глубоко, а шахты высокие. */
	private static final int MARGIN_Y = 32;
	/**
	 * Не даём A* разрастись бесконечно. Считается в фоновом потоке (рендер не
	 * ждёт), так что цена — не подвисание игры, а лишние миллисекунды на CPU.
	 */
	private static final int NODE_BUDGET = 80000;
	/**
	 * Вес эвристики (weighted A*) — НЕ константа, а функция дистанции старт→цель
	 * ({@link #heurWeight}). Жадный вес гонит поиск по прямой к цели малым числом
	 * узлов (нужно для дальних спотов), но он же заглушает g-стоимость — штрафы за
	 * воду/обрыв перестают влиять, и путь ныряет в колодец, раз он «прямее». Поэтому:
	 *  - БЛИЗКИЕ цели считаем почти оптимально (вес ~{@value #HEUR_WEIGHT_NEAR}) —
	 *    штрафы реально работают, сухой обход выигрывает у нырка;
	 *  - ДАЛЬНИЕ — жадно (вес ~{@value #HEUR_WEIGHT_FAR}), чтобы вообще дотянуться.
	 * Между NEAR_DIST и FAR_DIST вес линейно нарастает.
	 */
	private static final double HEUR_WEIGHT_NEAR = 1.25;
	private static final double HEUR_WEIGHT_FAR = 2.4;
	private static final double NEAR_DIST = 80;
	private static final double FAR_DIST = 400;

	/** Вес эвристики по дистанции старт→цель: близко — почти оптимально, далеко — жадно. */
	private static double heurWeight(double startGoalDist) {
		if (startGoalDist <= NEAR_DIST) return HEUR_WEIGHT_NEAR;
		if (startGoalDist >= FAR_DIST) return HEUR_WEIGHT_FAR;
		double t = (startGoalDist - NEAR_DIST) / (FAR_DIST - NEAR_DIST);
		return HEUR_WEIGHT_NEAR + t * (HEUR_WEIGHT_FAR - HEUR_WEIGHT_NEAR);
	}
	/** Скачок дальше этого — телепорт/варп, а не ходьба; старый путь сразу прячем. */
	private static final double TELEPORT_JUMP = 20 * 20;

	private static volatile List<Vec3> lastPath = null;
	private static volatile Vec3 lastFrom = null;
	private static volatile Vec3 lastTo = null;
	private static volatile boolean computing = false;

	/** Готовый путь (без стартовой точки — её рисует вызывающий сам). null — нет/не готов/не нашли. */
	public static List<Vec3> currentPath() {
		return lastPath;
	}

	/** Просит пересчитать, если старт/цель заметно сдвинулись и сейчас не считаем. */
	public static void requestIfNeeded(Level level, Vec3 from, Vec3 to) {
		if (computing || level == null) return;

		boolean teleport = lastFrom != null && lastFrom.distanceToSqr(from) >= TELEPORT_JUMP;
		// Порог намеренно не маленький: пересчёт с чуть другой стартовой клетки
		// нередко находит другой, но РАВНОЦЕННЫЙ путь — и линия дёргается на
		// ровном месте. Реже пересчитываем — реже перескакивает.
		boolean movedEnough = lastFrom == null || teleport || lastFrom.distanceToSqr(from) >= 36;
		boolean targetChanged = lastTo == null || lastTo.distanceToSqr(to) >= 4;
		if (!movedEnough && !targetChanged) return;

		// Телепорт/варп — старый путь вёл из точки, которой уже нет рядом; лучше
		// на мгновение показать прямую, чем тянуть ломаную через полкарты.
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

	// ===== A* по стоячим клеткам =====

	private record Open(BlockPos pos, double f) { }

	private static List<BlockPos> aStar(Level level, BlockPos start, BlockPos goal) {
		// Большой перепад высоты обычно означает, что подъём/спуск (лестница,
		// склон) стоит В СТОРОНЕ от прямой старт-цель — расширяем коридор поиска,
		// иначе A* просто не увидит обход и упрётся в стену/обрыв.
		int vertGap = Math.abs(start.getY() - goal.getY());
		int margin = MARGIN + Math.min(60, vertGap / 2);
		int minX = Math.min(start.getX(), goal.getX()) - margin, maxX = Math.max(start.getX(), goal.getX()) + margin;
		int minZ = Math.min(start.getZ(), goal.getZ()) - margin, maxZ = Math.max(start.getZ(), goal.getZ()) + margin;
		int minY = Math.min(start.getY(), goal.getY()) - MARGIN_Y, maxY = Math.max(start.getY(), goal.getY()) + MARGIN_Y;

		// Вес эвристики зависит от дистанции: близко — почти оптимально (штрафы
		// за воду/обрыв работают, обходит колодец), далеко — жадно (дотягивается).
		double weight = heurWeight(Math.sqrt(start.distSqr(goal)));

		Map<BlockPos, Double> gScore = new HashMap<>();
		Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
		PriorityQueue<Open> open = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));

		gScore.put(start, 0.0);
		open.add(new Open(start, heuristic(weight, start, start, goal)));
		int expanded = 0;

		// Лучшее приближение к цели на случай, если за бюджет не дойдём: вернём
		// частичный путь до ближайшего узла, а вызывающий дорисует остаток прямой.
		// Это строго лучше, чем выбросить всё и показать прямую сквозь стены.
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
					// Нырок ВНИЗ (колодец) — дорого; горизонт/всплытие (Lumisquid) — почти даром.
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
		// Не дошли (бюджет/тупик) — отдаём путь до самого близкого к цели узла.
		return best.equals(start) ? null : reconstruct(cameFrom, best);
	}

	/**
	 * Weighted A*: weight × эвклид (гонит к цели) + крошечный тайбрейк по
	 * крест-произведению от прямой старт-финиш (чтобы на равноценных развилках
	 * поиск стабильно жался к прямой и линия не дёргалась между пересчётами).
	 * weight подобран в {@link #heurWeight} по дистанции старт→цель.
	 */
	private static double heuristic(double weight, BlockPos start, BlockPos a, BlockPos goal) {
		double dist = Math.sqrt(a.distSqr(goal));
		double dx1 = a.getX() - goal.getX(), dz1 = a.getZ() - goal.getZ();
		double dx2 = start.getX() - goal.getX(), dz2 = start.getZ() - goal.getZ();
		double cross = Math.abs(dx1 * dz2 - dx2 * dz1);
		return weight * dist + cross * 0.001;
	}

	/** Прыжок вверх дороже ровного шага — чтобы линия не подскакивала без нужды. */
	private static final double JUMP_COST = 1.3;
	/**
	 * Спуск сразу на >1 блок (обрыв) штрафуется КВАДРАТИЧНО по «лишним» блокам.
	 * Так пологий склон/лестница (спуск по -1 несколько раз) выигрывает у прыжка
	 * с обрыва, и путь перестаёт «спрыгивать там, где не надо».
	 */
	private static final double DROP_COST = 1.2;
	/**
	 * Штраф за воду НАПРАВЛЕННЫЙ — в этом вся суть отличия «колодца» от «Lumisquid».
	 * Нырок в колодец = спуск ВНИЗ сквозь воду (dy<0); заплыв к морскому споту =
	 * ход по горизонтали/всплытие. Поэтому:
	 *  - {@code WATER_DIVE} (спуск в воде) — ДОРОГО: сухой вход по тропинке
	 *    выигрывает у нырка в колодец (кейс Chill);
	 *  - {@code WATER_SWIM} (горизонталь/всплытие) — почти даром: путь к Lumisquid
	 *    и прочим подводным спотам, где нырять НАДО, не ломается.
	 * Оба — штраф, а не запрет: если сухого пути нет вовсе, нырок всё равно берётся.
	 */
	private static final double WATER_DIVE = 4.0;
	private static final double WATER_SWIM = 0.4;

	private static double stepCost(BlockPos from, BlockPos to) {
		int dx = Math.abs(to.getX() - from.getX()), dz = Math.abs(to.getZ() - from.getZ());
		// Чистая вертикаль (dx=dz=0, ход по воде вверх/вниз) горизонтали не имеет.
		double horiz = (dx != 0 && dz != 0) ? 1.4142 : (dx + dz == 0 ? 0.0 : 1.0);
		int dy = to.getY() - from.getY();
		double vert;
		if (dy > 0) {
			vert = dy * JUMP_COST;                                   // подъём/прыжок
		} else if (dy < 0) {
			int drop = -dy;
			vert = drop + (drop > 1 ? (drop - 1) * (drop - 1) * DROP_COST : 0); // обрыв дороже квадратично
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

	/**
	 * Соседние клетки. Два режима движения:
	 *  - ПЛАВАНИЕ (ноги в воде): полное 3D — 8 сторон на своей глубине (±2), плюс
	 *    чистый вверх/вниз. Так проходятся подводные пещеры и вертикальные шахты,
	 *    чего плоский «ground»-поиск не умел вовсе.
	 *  - ХОДЬБА (ноги в воздухе): 8 сторон, шаг вверх максимум 1 (прыжок), вниз до 3.
	 */
	private static List<BlockPos> neighbors(Level level, BlockPos from,
											 int minX, int maxX, int minZ, int maxZ, int minY, int maxY) {
		boolean swim = inWater(level, from);
		List<BlockPos> out = new ArrayList<>(10);
		// Порядок перебора dy: у ходьбы приоритет держаться уровня/подниматься,
		// у плавания — держать текущую глубину, потом расходиться вверх/вниз.
		int[] walkDy = {1, 0, -1, -2, -3};
		int[] swimDy = {0, -1, 1, -2, 2};
		int[] dyOrder = swim ? swimDy : walkDy;

		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0) continue;
				int nx = from.getX() + dx, nz = from.getZ() + dz;
				if (nx < minX || nx > maxX || nz < minZ || nz > maxZ) continue;

				// Диагональ мимо угла стены — не режем угол: обе ортогонали должны
				// быть проходимы на уровне ног.
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

		// В воде добавляем чистый вертикальный ход — иначе шахту/омут не пройти.
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

	/**
	 * Можно ли находиться в клетке (ноги тут). Ноги и голова свободны (воздух/вода,
	 * не лава, не блок), и клетка «держит»: пол снизу/в ногах ЛИБО вода (плавание).
	 * Без водной ветки подводные пещеры считались непроходимыми.
	 */
	private static boolean enterable(Level level, BlockPos foot) {
		if (!level.hasChunkAt(foot)) return false;
		boolean footOk = passable(level, foot) || isLowFloor(level, foot);
		if (!footOk || !passable(level, foot.above())) return false;
		return supported(level, foot);
	}

	/** Есть ли опора: низкий блок в ногах, вода (плаваем), твёрдый пол или вода снизу. */
	private static boolean supported(Level level, BlockPos foot) {
		if (isLowFloor(level, foot)) return true;          // стоим на плите внутри клетки
		if (isWater(level, foot)) return true;             // ноги в воде — плывём
		BlockPos below = foot.below();
		BlockState bs = level.getBlockState(below);
		if (bs.getFluidState().getType().is(FluidTags.LAVA)) return false; // над лавой не стоим
		if (!bs.getCollisionShape(level, below).isEmpty()) return true;    // твёрдый пол
		return !bs.getFluidState().isEmpty();              // вода снизу — поверхность/всплытие
	}

	/** Ноги в воде (не лаве). */
	private static boolean inWater(Level level, BlockPos foot) {
		return isWater(level, foot);
	}

	private static boolean isWater(Level level, BlockPos pos) {
		var fluid = level.getBlockState(pos).getFluidState();
		return !fluid.isEmpty() && !fluid.getType().is(FluidTags.LAVA);
	}

	/** Нижний полублок/ковёр/плита — верх формы не выше половины клетки. */
	private static boolean isLowFloor(Level level, BlockPos pos) {
		var shape = level.getBlockState(pos).getCollisionShape(level, pos);
		return !shape.isEmpty() && shape.bounds().maxY <= 0.55;
	}

	private static boolean passable(Level level, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		if (!s.getCollisionShape(level, pos).isEmpty()) return false;
		return !s.getFluidState().getType().is(FluidTags.LAVA);
	}

	// ===== Сглаживание (string pulling) =====

	/** Убирает промежуточные узлы там, где до более дальнего узла и так есть видимость. */
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

	/** Примерная видимость: семплируем отрезок каждые ~0.4 блока, смотрим ноги+голову. */
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
