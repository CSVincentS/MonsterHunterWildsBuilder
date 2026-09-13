package mhwilds.optimizer.solver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;
import mhwilds.optimizer.model.AmuletRank;
import mhwilds.optimizer.model.ArmorPiece;
import mhwilds.optimizer.model.ArmorSlot;
import mhwilds.optimizer.model.Build;
import mhwilds.optimizer.model.Decoration;
import mhwilds.optimizer.model.Skill;
import mhwilds.optimizer.model.SlotAssignment;
import mhwilds.optimizer.model.Weapon;

/**
 * Finds the exact top-K builds by free-slot score: best-first depth-first search over armor slots
 * with upper-bound pruning, a greedy minimal decoration fill, and a bounded result heap.
 */
public final class GreedySolver implements Solver {

  private static final long[] POW10 = { 1, 10, 100, 1000, 10000, 100000, 1000000 };
  private static final int[] EMPTY_INT = new int[0];

  private final int k;
  private final int threads;
  private final long equipmentBonus;

  private long totalLeavesEvaluated;
  private long totalBuildsFound;

  private ArmorSlot[] slotOrder;
  private EnumMap<ArmorSlot, List<ArmorPiece>> bySlot;
  private int skillCount;
  private List<Integer> reqIds;
  private int[] required;
  private int[] maxRank;

  private long[] remainingMaxScore;
  private long weaponMaxSlotScore;
  private int[] bestArmorDec;
  private int[] bestAmulet;
  private int[] maxWeaponInnate;
  private int[] bestWeaponSide;

  private List<Decoration>[] armorDecList;
  private List<Decoration>[] weaponDecList;

  private List<AmuletRank> amulets;
  private List<Weapon> weapons;
  private int[][] amuVec;
  private int[][] weaponInnate;
  private int[][] weaponSlots;
  private int[][] weaponSlotOrderAsc;
  private long[] weaponSlotScore;

  private int[] reqSkillIdx;
  private int numSkills;
  private Map<Integer, Integer> skillIndexMap;
  private boolean[] setReqFlag;
  private Map<Integer, Integer>[] setReqRanks;

  private IdentityHashMap<ArmorPiece, int[]> pieceSkill;
  private IdentityHashMap<Decoration, int[]> decSkill;
  private IdentityHashMap<AmuletRank, int[]> amuSkill;
  private IdentityHashMap<Weapon, int[]> weaponSkill;
  private int[][] remainingArmorMaxSkill;
  private int[] remainingMaxSlots;
  private int maxAmuletSkillCoverage;
  private boolean[] amuUseful;
  private long[] remainingArmorSkillTotal;
  private int maxDecoUsefulPoints;
  private long bestAmuletTotal;
  private long bestWeaponSideTotal;

  private final class SolverState {

    final int[] totals;
    final int[] cur;
    final int[] setCount;
    final ArmorPiece[] chosen;
    long leavesEvaluated;
    long buildsFound;
    final PriorityQueue<Build> topK;
    long worst;

    SolverState(int numSkills, int skillCount, int k) {
      this.totals = new int[numSkills];
      this.cur = new int[skillCount];
      this.setCount = new int[skillCount];
      this.chosen = new ArmorPiece[5];

      this.topK = new PriorityQueue<>(Comparator.comparingLong(GreedySolver.this::scoreOf));
    }
  }

  public GreedySolver() {
    this(1000);
  }

  public GreedySolver(int k) {
    this(k, Runtime.getRuntime().availableProcessors());
  }

  public GreedySolver(int k, int threads) {
    this(k, threads, 0L);
  }

  public GreedySolver(int k, int threads, long equipmentBonus) {
    if (k < 1) {
      throw new IllegalArgumentException("k must be >= 1, got " + k);
    }

    this.k = k;
    this.threads = Math.max(1, threads);
    this.equipmentBonus = Math.max(0, equipmentBonus);
  }

  @Override
  public List<Build> solve(SolverPool pool) {
    SkillThresholds thresholds = pool.thresholds();

    if (
      thresholds == null ||
      thresholds.requiredSkills() == null ||
      thresholds.requiredSkills().isEmpty()
    ) {
      return List.of();
    }

    reqIds = thresholds
      .requiredSkills()
      .entrySet()
      .stream()
      .filter(e -> e.getValue() != null && e.getValue() > 0)
      .map(Map.Entry::getKey)
      .sorted()
      .toList();

    if (reqIds.isEmpty()) {
      return List.of();
    }

    this.skillCount = reqIds.size();

    this.required = new int[skillCount];
    this.maxRank = new int[skillCount];
    this.setReqFlag = new boolean[skillCount];
    this.setReqRanks = new Map[skillCount];

    for (int k = 0; k < skillCount; k++) {
      int id = reqIds.get(k);
      required[k] = thresholds.required(id);
      Skill skill = pool.skillMap() == null ? null : pool.skillMap().get(id);
      int rank = skill == null ? required[k] : skill.maxRank();

      if (rank < required[k]) {
        return List.of();
      }

      maxRank[k] = rank;
      setReqFlag[k] = thresholds.isSetSkill(id);
      setReqRanks[k] = setReqFlag[k] ? thresholds.setSkillRanksOf(id) : Map.of();
      int maxPieces = ArmorSlot.values().length;

      if (setReqFlag[k] && activationLevel(setReqRanks[k], maxPieces) < required[k]) {
        return List.of();
      }
    }

    if (pool.skillMap() != null && !pool.skillMap().isEmpty()) {
      skillIndexMap = new java.util.HashMap<>();

      int si = 0;

      for (Integer id : pool.skillMap().keySet()) {
        skillIndexMap.put(id, si++);
      }

      numSkills = pool.skillMap().size();
    } else {
      skillIndexMap = Map.of();

      numSkills = 0;
    }

    this.reqSkillIdx = new int[skillCount];

    for (int k = 0; k < skillCount; k++) {
      reqSkillIdx[k] = skillIndexMap.getOrDefault(reqIds.get(k), -1);
    }

    precompute(pool);

    if (this.threads <= 1) {
      SolverState state = new SolverState(numSkills, skillCount, k);

      search(0, state, 0, 0);
      totalLeavesEvaluated = state.leavesEvaluated;
      totalBuildsFound = state.buildsFound;

      List<Build> out = new ArrayList<>(state.topK);
      out.sort(scoreRanking());

      return out;
    } else {
      return solveParallel();
    }
  }

  private List<Build> solveParallel() {
    List<ArmorPiece> headCandidates = new ArrayList<>(bySlot.get(ArmorSlot.HEAD));

    if (equipmentBonus > 0) {
      headCandidates.add(null);
    }

    int numTasks = Math.min(threads, headCandidates.size());
    int chunkSize = (headCandidates.size() + numTasks - 1) / numTasks;

    // Each chunk runs on its own SolverState, so the search itself shares no mutable data;
    // only the cumulative counters need synchronization, and chunk top-K lists are merged.
    ForkJoinPool pool = new ForkJoinPool(numTasks);

    try {
      List<ForkJoinTask<List<Build>>> forkTasks = new ArrayList<>();

      for (int i = 0; i < numTasks; i++) {
        int from = i * chunkSize;
        int to = Math.min(from + chunkSize, headCandidates.size());

        if (from >= to) {
          break;
        }

        forkTasks.add(pool.submit(new ChunkTask(headCandidates.subList(from, to))));
      }

      List<List<Build>> partialResults = new ArrayList<>();

      for (ForkJoinTask<List<Build>> ft : forkTasks) {
        partialResults.add(ft.join());
      }

      return mergeTopK(partialResults, k);
    } finally {
      pool.shutdown();
    }
  }

  private class ChunkTask extends RecursiveTask<List<Build>> {

    private final List<ArmorPiece> headPieces;

    ChunkTask(List<ArmorPiece> headPieces) {
      this.headPieces = headPieces;
    }

    @Override
    protected List<Build> compute() {
      SolverState state = new SolverState(numSkills, skillCount, k);

      for (ArmorPiece head : headPieces) {
        descend(state, 0, 0, 0, head);
      }

      synchronized (GreedySolver.this) {
        totalLeavesEvaluated += state.leavesEvaluated;
        totalBuildsFound += state.buildsFound;
      }

      return state.topK.stream().sorted(scoreRanking()).toList();
    }
  }

  /** The score a ranking-aware search orders by: free deco slots plus omitted-gear bonuses. */
  private long scoreOf(Build b) {
    return b.equipmentAwareScore(equipmentBonus);
  }

  private Comparator<Build> scoreRanking() {
    return Comparator.comparingLong(this::scoreOf)
      .reversed()
      .thenComparing(Comparator.comparingInt(Build::totalDefense).reversed());
  }

  private List<Build> mergeTopK(List<List<Build>> partialResults, int k) {
    PriorityQueue<Build> merged = new PriorityQueue<>(Comparator.comparingLong(this::scoreOf));

    for (List<Build> partial : partialResults) {
      for (Build b : partial) {
        if (merged.size() < k) {
          merged.add(b);
        } else if (scoreOf(b) > scoreOf(merged.peek())) {
          merged.poll();
          merged.add(b);
        }
      }
    }

    List<Build> out = new ArrayList<>(merged);
    out.sort(scoreRanking());

    return out;
  }

  private void search(int depth, SolverState state, long slotAcc, int slotsUsed) {
    if (depth == slotOrder.length) {
      processLeaf(state.chosen, slotAcc, state.cur, state);

      return;
    }

    if (!skillsFeasible(depth, state, slotsUsed)) {
      return;
    }

    // Remaining armor, weapon, and amulet free-slot potential (including equipment-omission
    // bonuses) bound what any completion of this branch can score; if that upper bound cannot
    // beat the heap's worst, the subtree cannot reach the current top-K, so prune it.
    if (
      heapFull(state) &&
      slotAcc + remainingMaxScore[depth] + weaponMaxSlotScore + equipmentBonus <= state.worst
    ) {
      return;
    }

    for (ArmorPiece piece : bySlot.get(slotOrder[depth])) {
      descend(state, depth, slotAcc, slotsUsed, piece);
    }

    if (equipmentBonus > 0) {
      descend(state, depth, slotAcc, slotsUsed, null);
    }
  }

  /**
   * Binds {@code piece} (or {@code null}, meaning the equipment slot is left empty) at {@code
   * depth}, recursing into the remaining slots and reverting all state afterwards. An omitted
   * armor slot contributes the equipment bonus instead of any decoration slots.
   */
  private void descend(
    SolverState state,
    int depth,
    long slotAcc,
    int slotsUsed,
    ArmorPiece piece
  ) {
    state.chosen[depth] = piece;
    int pieceSlots = 0;
    long ps = equipmentBonus;

    if (piece != null) {
      pieceSlots = piece.slots().length;
      ps = slotScore(piece.slots());
    }

    for (int k = 0; k < skillCount; k++) {
      state.cur[k] += piece != null ? piece.skills().getOrDefault(reqIds.get(k), 0) : 0;
    }

    updateSetCounts(state, piece, 1);
    int[] sv = piece == null ? EMPTY_INT : pieceSkill.get(piece);

    for (int i = 0; i < sv.length; i += 2) {
      state.totals[sv[i]] += sv[i + 1];
    }

    search(depth + 1, state, slotAcc + ps, slotsUsed + pieceSlots);

    for (int i = 0; i < sv.length; i += 2) {
      state.totals[sv[i]] -= sv[i + 1];
    }

    state.chosen[depth] = null;
    updateSetCounts(state, piece, -1);

    for (int k = 0; k < skillCount; k++) {
      state.cur[k] -= piece != null ? piece.skills().getOrDefault(reqIds.get(k), 0) : 0;
    }
  }

  /**
   * Upper-bound check on whether the remaining search can still satisfy every required skill. Each
   * term is a maximum (per-position best remaining armor, max deco potential, best amulet and best
   * weapon side), so a branch that fails here can never meet the thresholds — regardless of whether
   * the top-K heap is full yet.
   */
  private boolean skillsFeasible(int depth, SolverState state, int slotsUsed) {
    int[] cur = state.cur;

    long aggregateNeeded = 0;
    int piecesLeft = slotOrder.length - depth;

    for (int k = 0; k < skillCount; k++) {
      if (setReqFlag[k]) {
        // Set-skill requirements can only be met by wearing bonus pieces; every
        // remaining slot could still be one, so this is a sound upper bound.
        if (activationLevel(setReqRanks[k], state.setCount[k] + piecesLeft) < required[k]) {
          return false;
        }

        continue;
      }
      long effective = Math.min(cur[k] + remainingArmorMaxSkill[depth][k], maxRank[k]);
      long decPotential = (long) (slotsUsed + remainingMaxSlots[depth]) * bestArmorDec[k];
      long other = (long) bestAmulet[k] + bestWeaponSide[k];

      if (effective + decPotential + other < required[k]) {
        return false;
      }

      aggregateNeeded += Math.max(0, required[k] - Math.min(cur[k], maxRank[k]));
    }

    long remainingSlotPoints = slotsUsed + remainingMaxSlots[depth];

    // Each remaining slot can hold one decoration contributing at most maxDecoUsefulPoints
    // useful points (in aggregate across skills), and a decoration may be socketed any number
    // of times, so slotCount * maxDecoUsefulPoints is a sound upper bound on how much the
    // decorations can still contribute.
    long aggregateAvail =
      remainingArmorSkillTotal[depth] +
      (long) remainingSlotPoints * maxDecoUsefulPoints +
      bestAmuletTotal +
      bestWeaponSideTotal;

    return aggregateAvail >= aggregateNeeded;
  }

  private void processLeaf(ArmorPiece[] chosen, long slotAcc, int[] cur, SolverState state) {
    state.leavesEvaluated++;

    int slots = 0;

    for (int p = 0; p < slotOrder.length; p++) {
      slots += chosen[p] != null ? chosen[p].slots().length : 0;
    }

    for (int k = 0; k < skillCount; k++) {
      if (setReqFlag[k]) {
        if (activationLevel(setReqRanks[k], state.setCount[k]) < required[k]) {
          return;
        }

        continue;
      }

      long best =
        Math.min(cur[k], maxRank[k]) +
        (long) bestAmulet[k] +
        maxWeaponInnate[k] +
        (long) slots * bestArmorDec[k];

      if (best < required[k]) {
        return;
      }
    }

    if (heapFull(state) && slotAcc + weaponMaxSlotScore + equipmentBonus <= state.worst) {
      return;
    }

    int[] baseNeed = new int[skillCount];

    for (int k = 0; k < skillCount; k++) {
      baseNeed[k] = setReqFlag[k] ? 0 : Math.max(0, required[k] - Math.min(cur[k], maxRank[k]));
    }

    ArmorPiece[] finalArmor = chosen.clone();

    // Case 0: reserve nothing for the amulet and cover every need with armor decos. Only
    // feasible when the greedy fill completes; any amulet (or none) is then acceptable.
    {
      int[] need = baseNeed.clone();
      int[] filled = cur.clone();
      int[] leafTot = state.totals.clone();
      boolean[][] occupied = emptyOccupied(chosen);
      List<SlotAssignment> armorAssign = new ArrayList<>();

      if (greedyArmorFill(chosen, need, filled, leafTot, occupied, armorAssign)) {
        long free = slotAcc - occupiedScore(chosen, occupied);

        if (!(heapFull(state) && free + weaponMaxSlotScore + equipmentBonus <= state.worst)) {
          for (int a = -1; a < amulets.size(); a++) {
            if (a == -1 && equipmentBonus <= 0) {
              continue;
            }

            processLeafSequentialWeapon(
              state,
              finalArmor,
              armorAssign,
              leafTot,
              new int[skillCount],
              free,
              a
            );
          }
        }
      }
    }

    // Case 1 per skill: reserve exactly one non-set skill for the amulet (or for the weapon
    // side) and cover every other need with armor decos. This is what actually makes
    // "amulet-only" builds findable: the amulet skill is kept out of the deco fill from the
    // start, so its residual is never split across skills and a single amulet can cover it.
    for (int k0 = 0; k0 < skillCount; k0++) {
      if (setReqFlag[k0] || baseNeed[k0] <= 0) {
        continue;
      }

      if (baseNeed[k0] > bestAmulet[k0] + bestWeaponSide[k0]) {
        continue;
      }

      int[] need = baseNeed.clone();
      need[k0] = 0;
      int[] filled = cur.clone();
      int[] leafTot = state.totals.clone();
      boolean[][] occupied = emptyOccupied(chosen);
      List<SlotAssignment> armorAssign = new ArrayList<>();

      if (!greedyArmorFill(chosen, need, filled, leafTot, occupied, armorAssign)) {
        continue;
      }

      long free = slotAcc - occupiedScore(chosen, occupied);

      if (!(heapFull(state) && free + weaponMaxSlotScore + equipmentBonus <= state.worst)) {
        for (int a = -1; a < amulets.size(); a++) {
          if (a == -1) {
            if (equipmentBonus <= 0 || baseNeed[k0] > bestWeaponSide[k0]) {
              continue;
            }

            processLeafSequentialWeapon(
              state,
              finalArmor,
              armorAssign,
              leafTot,
              residualFor(new int[skillCount], k0, baseNeed[k0], 0),
              free,
              a
            );
          } else if (amuVec[a][k0] > 0) {
            int[] rem = residualFor(new int[skillCount], k0, baseNeed[k0], amuVec[a][k0]);
            boolean amuletOk = true;

            for (int k = 0; k < skillCount; k++) {
              if (rem[k] > bestWeaponSide[k]) {
                amuletOk = false;
                break;
              }
            }

            if (!amuletOk) {
              continue;
            }

            processLeafSequentialWeapon(state, finalArmor, armorAssign, leafTot, rem, free, a);
          }
        }
      }
    }
  }

  private static int[] residualFor(int[] rem, int k0, int residual, int covered) {
    rem[k0] = Math.max(0, residual - covered);
    return rem;
  }

  private static boolean[][] emptyOccupied(ArmorPiece[] chosen) {
    boolean[][] occupied = new boolean[chosen.length][];

    for (int i = 0; i < chosen.length; i++) {
      occupied[i] = new boolean[chosen[i] != null ? chosen[i].slots().length : 0];
    }

    return occupied;
  }

  private static long occupiedScore(ArmorPiece[] chosen, boolean[][] occupied) {
    long score = 0;

    for (int p = 0; p < chosen.length; p++) {
      if (chosen[p] == null) {
        continue;
      }

      int[] slotsArr = chosen[p].slots();

      for (int i = 0; i < slotsArr.length; i++) {
        if (occupied[p][i]) {
          score += POW10[slotsArr[i]];
        }
      }
    }

    return score;
  }

  private boolean greedyArmorFill(
    ArmorPiece[] chosen,
    int[] need,
    int[] filled,
    int[] leafTot,
    boolean[][] occupied,
    List<SlotAssignment> armorAssign
  ) {
    List<int[]> slotRefs = new ArrayList<>();

    for (int p = 0; p < chosen.length; p++) {
      if (chosen[p] == null) {
        continue;
      }

      int[] slotsArr = chosen[p].slots();

      for (int i = 0; i < slotsArr.length; i++) {
        slotRefs.add(new int[] { slotsArr[i], p, i });
      }
    }

    slotRefs.sort(Comparator.comparingInt(r -> r[0]));

    // Spend the cheapest slots first: a slot paid to a decoration forfeits its free-slot
    // score, so filling ascending keeps the highest-valued slots unoccupied wherever possible.
    for (int[] ref : slotRefs) {
      if (allMet(need)) {
        break;
      }

      int size = ref[0];
      int p = ref[1];
      int idx = ref[2];
      Decoration bestDec = null;
      int bestK = -1;
      int bestC = 0;

      for (int k = 0; k < skillCount; k++) {
        if (need[k] <= 0) {
          continue;
        }

        for (Decoration d : armorDecList[k]) {
          int c = d.skills().getOrDefault(reqIds.get(k), 0);

          if (c <= 0 || d.level() > size) {
            continue;
          }

          if (filled[k] + c > maxRank[k]) {
            continue;
          }

          if (!decFits(leafTot, d)) {
            continue;
          }

          if (bestDec == null || c > bestC || (c == bestC && d.level() < bestDec.level())) {
            bestDec = d;
            bestK = k;
            bestC = c;
          }
        }
      }

      if (bestDec != null) {
        occupied[p][idx] = true;
        filled[bestK] += bestC;
        need[bestK] = Math.max(0, need[bestK] - bestC);
        applyPacked(leafTot, decSkill.get(bestDec));
        armorAssign.add(new SlotAssignment(bestDec, slotOrder[p], idx));
      }
    }

    return allMet(need);
  }

  private void processLeafSequentialWeapon(
    SolverState state,
    ArmorPiece[] finalArmor,
    List<SlotAssignment> armorAssign,
    int[] leafTot,
    int[] ra,
    long armorFreeScore,
    int amuletIdx
  ) {
    boolean noAmulet = amuletIdx == -1;
    long amuletBonusScore = noAmulet ? equipmentBonus : 0L;
    int[] ap = noAmulet ? EMPTY_INT : amuSkill.get(amulets.get(amuletIdx));

    for (int b = -1; b < weapons.size(); b++) {
      if (b == -1 && equipmentBonus <= 0) {
        continue;
      }

      boolean noWeapon = b == -1;
      long weaponOmitScore = noWeapon ? equipmentBonus : 0L;
      int[] bp = noWeapon ? EMPTY_INT : weaponSkill.get(weapons.get(b));

      int[] rb = new int[skillCount];
      boolean zero = true;

      for (int k = 0; k < skillCount; k++) {
        int innate = noWeapon ? 0 : weaponInnate[b][k];
        rb[k] = Math.max(0, ra[k] - innate);

        if (rb[k] > 0) {
          zero = false;
        }
      }

      long weaponFree;
      List<SlotAssignment> weaponAssign = List.of();

      if (noWeapon) {
        if (!zero) {
          continue;
        }

        weaponFree = 0L;
      } else if (!zero) {
        weaponAssign = new ArrayList<>();
        int[] effBase = leafTot.clone();
        applyPacked(effBase, ap);
        applyPacked(effBase, bp);
        weaponFree = fillWeaponDecs(b, rb, weaponAssign, effBase);

        if (weaponFree < 0) {
          continue;
        }
      } else {
        weaponFree = weaponSlotScore[b];
      }
      long score = armorFreeScore + weaponFree + amuletBonusScore + weaponOmitScore;

      if (heapFull(state) && score <= state.worst) {
        continue;
      }

      Build build = new Build(
        finalArmor.clone(),
        new ArrayList<>(armorAssign),
        weaponAssign,
        noAmulet ? null : amulets.get(amuletIdx),
        noWeapon ? null : weapons.get(b)
      );
      offer(state, build, score);
    }
  }

  private void precompute(SolverPool pool) {
    slotOrder = ArmorSlot.values();
    bySlot = new EnumMap<>(ArmorSlot.class);

    for (ArmorSlot s : slotOrder) {
      bySlot.put(s, new ArrayList<>());
    }

    for (ArmorPiece p : pool.armorPieces()) {
      bySlot.get(p.kind()).add(p);
    }

    pieceSkill = new IdentityHashMap<>();

    for (List<ArmorPiece> pieces : bySlot.values()) {
      for (ArmorPiece p : pieces) {
        pieceSkill.put(p, pack(p.skills()));
      }
    }

    Comparator<ArmorPiece> armorOrder = (a, b) -> {
      long sa = slotScore(a.slots());
      long sb = slotScore(b.slots());

      if (sa != sb) {
        return Long.compare(sb, sa);
      }

      int relA = 0;
      int relB = 0;

      for (int k = 0; k < skillCount; k++) {
        relA += Math.min(a.skills().getOrDefault(reqIds.get(k), 0), required[k]);
        relB += Math.min(b.skills().getOrDefault(reqIds.get(k), 0), required[k]);
      }

      return Integer.compare(relB, relA);
    };

    for (ArmorSlot s : slotOrder) {
      bySlot.get(s).sort(armorOrder);
    }

    int slotTypes = slotOrder.length;

    precomputeArmorAggregates(slotTypes);
    long[] maxSlotScorePerSlot = new long[slotTypes];

    for (int i = 0; i < slotTypes; i++) {
      for (ArmorPiece p : bySlot.get(slotOrder[i])) {
        maxSlotScorePerSlot[i] = Math.max(maxSlotScorePerSlot[i], slotScore(p.slots()));
      }

      if (equipmentBonus > 0) {
        maxSlotScorePerSlot[i] = Math.max(maxSlotScorePerSlot[i], equipmentBonus);
      }
    }

    remainingMaxScore = new long[slotTypes + 1];

    for (int i = slotTypes - 1; i >= 0; i--) {
      remainingMaxScore[i] = remainingMaxScore[i + 1] + maxSlotScorePerSlot[i];
    }

    // Skill-feasibility pruning data: per slot position, the max contribution each
    // required skill can get from armor (innate) and the max number of deco slots.
    int[] maxSlotsPerPos = new int[slotTypes];
    int[][] maxSkillPerPos = new int[slotTypes][skillCount];

    for (int i = 0; i < slotTypes; i++) {
      for (ArmorPiece p : bySlot.get(slotOrder[i])) {
        maxSlotsPerPos[i] = Math.max(maxSlotsPerPos[i], p.slots().length);

        for (int k = 0; k < skillCount; k++) {
          int c = p.skills().getOrDefault(reqIds.get(k), 0);

          if (c > maxSkillPerPos[i][k]) {
            maxSkillPerPos[i][k] = c;
          }
        }
      }
    }

    remainingArmorMaxSkill = new int[slotTypes + 1][skillCount];
    remainingMaxSlots = new int[slotTypes + 1];

    for (int i = slotTypes - 1; i >= 0; i--) {
      remainingMaxSlots[i] = remainingMaxSlots[i + 1] + maxSlotsPerPos[i];

      for (int k = 0; k < skillCount; k++) {
        remainingArmorMaxSkill[i][k] = maxSkillPerPos[i][k] + remainingArmorMaxSkill[i + 1][k];
      }
    }

    weapons = pool.weapons();
    weaponSlots = new int[weapons.size()][];
    weaponSlotOrderAsc = new int[weapons.size()][];
    weaponSlotScore = new long[weapons.size()];
    weaponMaxSlotScore = 0;

    for (int b = 0; b < weapons.size(); b++) {
      int[] slots = weapons.get(b).slots();
      weaponSlots[b] = slots;
      weaponSlotScore[b] = slotScore(slots);
      weaponMaxSlotScore = Math.max(weaponMaxSlotScore, weaponSlotScore[b]);
      Integer[] idx = new Integer[slots.length];

      for (int i = 0; i < idx.length; i++) {
        idx[i] = i;
      }

      java.util.Arrays.sort(idx, Comparator.comparingInt(i -> slots[i]));
      int[] order = new int[idx.length];

      for (int i = 0; i < idx.length; i++) {
        order[i] = idx[i];
      }

      weaponSlotOrderAsc[b] = order;
    }

    if (equipmentBonus > 0) {
      weaponMaxSlotScore = Math.max(weaponMaxSlotScore, equipmentBonus);
    }

    bestArmorDec = new int[skillCount];
    armorDecList = new List[skillCount];
    decSkill = new IdentityHashMap<>();

    for (int k = 0; k < skillCount; k++) {
      armorDecList[k] = new ArrayList<>();
    }

    for (Decoration d : pool.armorDecorations()) {
      decSkill.put(d, pack(d.skills()));
      int decUsefulPoints = 0;

      for (int k = 0; k < skillCount; k++) {
        int c = d.skills().getOrDefault(reqIds.get(k), 0);

        if (c > 0) {
          armorDecList[k].add(d);
          bestArmorDec[k] = Math.max(bestArmorDec[k], c);
          decUsefulPoints += c;
        }
      }
      maxDecoUsefulPoints = Math.max(maxDecoUsefulPoints, decUsefulPoints);
    }

    for (int k = 0; k < skillCount; k++) {
      armorDecList[k].sort(decOrder(k));
    }

    weaponDecList = new List[skillCount];

    for (int k = 0; k < skillCount; k++) {
      weaponDecList[k] = new ArrayList<>();
    }

    for (Decoration d : pool.weaponDecorations()) {
      for (int k = 0; k < skillCount; k++) {
        int c = d.skills().getOrDefault(reqIds.get(k), 0);

        if (c > 0) {
          weaponDecList[k].add(d);
        }
      }
    }

    for (int k = 0; k < skillCount; k++) {
      weaponDecList[k].sort(decOrder(k));
    }

    amulets = pool.amuletRanks();
    amuSkill = new IdentityHashMap<>();
    amuVec = new int[amulets.size()][skillCount];
    bestAmulet = new int[skillCount];
    amuUseful = new boolean[amulets.size()];
    maxAmuletSkillCoverage = 0;

    for (int a = 0; a < amulets.size(); a++) {
      AmuletRank rank = amulets.get(a);
      amuSkill.put(rank, pack(rank.skills()));
      int coverage = 0;
      int amuTotal = 0;

      for (int k = 0; k < skillCount; k++) {
        int c = rank.skills().getOrDefault(reqIds.get(k), 0);
        int capped = c > maxRank[k] ? maxRank[k] : c;
        amuVec[a][k] = c;

        if (c > 0) {
          coverage++;
          bestAmulet[k] = Math.max(bestAmulet[k], c);
        }

        amuTotal += capped;
      }

      if (coverage > 0) {
        amuUseful[a] = true;
      }
      maxAmuletSkillCoverage = Math.max(maxAmuletSkillCoverage, coverage);
      bestAmuletTotal = Math.max(bestAmuletTotal, amuTotal);
    }

    weaponInnate = new int[weapons.size()][skillCount];
    maxWeaponInnate = new int[skillCount];
    bestWeaponSide = new int[skillCount];
    weaponSkill = new IdentityHashMap<>();

    for (int b = 0; b < weapons.size(); b++) {
      weaponSkill.put(weapons.get(b), pack(weapons.get(b).skills()));

      for (int k = 0; k < skillCount; k++) {
        int innate = weapons.get(b).skills().getOrDefault(reqIds.get(k), 0);
        weaponInnate[b][k] = innate;
        maxWeaponInnate[k] = Math.max(maxWeaponInnate[k], innate);
      }

      int weaponSideTotal = 0;

      for (int k = 0; k < skillCount; k++) {
        int[] slotsDesc = weaponSlots[b].clone();
        java.util.Arrays.sort(slotsDesc);
        int wep = 0;

        for (int i = slotsDesc.length - 1; i >= 0; i--) {
          int size = slotsDesc[i];

          for (Decoration d : weaponDecList[k]) {
            if (d.level() <= size) {
              wep += d.skills().getOrDefault(reqIds.get(k), 0);
              break;
            }
          }
        }

        int cappedInnate = weaponInnate[b][k] > maxRank[k] ? maxRank[k] : weaponInnate[b][k];
        bestWeaponSide[k] = Math.max(bestWeaponSide[k], cappedInnate + wep);
        weaponSideTotal += cappedInnate + wep;
      }

      bestWeaponSideTotal = Math.max(bestWeaponSideTotal, weaponSideTotal);
    }
  }

  private void precomputeArmorAggregates(int slotTypes) {
    int[] maxSkillTotalPerPos = new int[slotTypes];

    for (int i = 0; i < slotTypes; i++) {
      for (ArmorPiece p : bySlot.get(slotOrder[i])) {
        int total = 0;

        for (int k = 0; k < skillCount; k++) {
          int c = p.skills().getOrDefault(reqIds.get(k), 0);
          total += c > maxRank[k] ? maxRank[k] : c;
        }

        if (total > maxSkillTotalPerPos[i]) {
          maxSkillTotalPerPos[i] = total;
        }
      }
    }

    remainingArmorSkillTotal = new long[slotTypes + 1];

    for (int i = slotTypes - 1; i >= 0; i--) {
      remainingArmorSkillTotal[i] = remainingArmorSkillTotal[i + 1] + maxSkillTotalPerPos[i];
    }
  }

  private Comparator<Decoration> decOrder(int k) {
    int skillId = reqIds.get(k);

    return (x, y) -> {
      int cx = x.skills().getOrDefault(skillId, 0);
      int cy = y.skills().getOrDefault(skillId, 0);

      if (cx != cy) {
        return Integer.compare(cy, cx);
      }

      return Integer.compare(x.level(), y.level());
    };
  }

  private static long slotScore(int[] slots) {
    long s = 0;

    for (int sz : slots) {
      s += POW10[sz];
    }

    return s;
  }

  /** Packs a skill map into interleaved (skillIndex, level) pairs. */
  private int[] pack(Map<Integer, Integer> skills) {
    int n = 0;

    for (Integer id : skills.keySet()) {
      if (skillIndexMap.containsKey(id)) {
        n++;
      }
    }

    int[] out = new int[2 * n];
    int w = 0;

    for (Map.Entry<Integer, Integer> e : skills.entrySet()) {
      Integer si = skillIndexMap.get(e.getKey());

      if (si == null) {
        continue;
      }

      out[w++] = si;
      out[w++] = e.getValue();
    }

    return out;
  }

  private static void applyPacked(int[] base, int[] packed) {
    for (int i = 0; i < packed.length; i += 2) {
      base[packed[i]] += packed[i + 1];
    }
  }

  /** Tracks chosen-piece counts for required set/group bonus skills (skill id == bonus id). */
  private void updateSetCounts(SolverState state, ArmorPiece piece, int delta) {
    if (state == null || piece == null) {
      return;
    }

    for (int k = 0; k < skillCount; k++) {
      if (!setReqFlag[k]) {
        continue;
      }

      if (piece.setBonusId() == reqIds.get(k) || piece.groupBonusId() == reqIds.get(k)) {
        state.setCount[k] += delta;
      }
    }
  }

  /** Highest skill level achievable by a bonus's thresholds with the given piece count. */
  private static int activationLevel(Map<Integer, Integer> thresholds, int pieceCount) {
    int best = 0;

    for (Map.Entry<Integer, Integer> rank : thresholds.entrySet()) {
      if (pieceCount >= rank.getKey() && rank.getValue() > best) {
        best = rank.getValue();
      }
    }

    return best;
  }

  private boolean decFits(int[] base, Decoration d) {
    return true;
  }

  private boolean overCaps(int[] base, int[] amuPacked, int[] weaponPacked) {
    return false;
  }

  private long fillWeaponDecs(
    int weaponIdx,
    int[] deficit,
    List<SlotAssignment> assign,
    int[] base
  ) {
    int[] slots = weaponSlots[weaponIdx];
    boolean[] occupied = new boolean[slots.length];

    long occupiedScore = 0;
    int[] order = weaponSlotOrderAsc[weaponIdx];

    for (int oi = 0; oi < order.length; oi++) {
      if (allMet(deficit)) {
        break;
      }

      int idx = order[oi];
      int size = slots[idx];
      Decoration bestDec = null;
      int bestK = -1;
      int bestC = 0;

      for (int k = 0; k < skillCount; k++) {
        if (deficit[k] <= 0) {
          continue;
        }

        for (Decoration d : weaponDecList[k]) {
          int c = d.skills().getOrDefault(reqIds.get(k), 0);

          if (c <= 0 || d.level() > size) {
            continue;
          }

          if (reqSkillIdx[k] >= 0 && base[reqSkillIdx[k]] + c > maxRank[k]) {
            continue;
          }

          if (!decFits(base, d)) {
            continue;
          }

          if (bestDec == null || c > bestC || (c == bestC && d.level() < bestDec.level())) {
            bestDec = d;
            bestK = k;
            bestC = c;
          }
        }
      }

      if (bestDec != null) {
        occupied[idx] = true;
        assign.add(new SlotAssignment(bestDec, null, idx));
        deficit[bestK] = Math.max(0, deficit[bestK] - bestC);
        applyPacked(base, decSkill.get(bestDec));
        occupiedScore += POW10[size];
      }
    }

    if (!allMet(deficit)) {
      return -1;
    }

    return weaponSlotScore[weaponIdx] - occupiedScore;
  }

  private boolean allMet(int[] need) {
    for (int k = 0; k < skillCount; k++) {
      if (need[k] > 0) {
        return false;
      }
    }

    return true;
  }

  private boolean heapFull(SolverState state) {
    return state.topK.size() >= k;
  }

  private void offer(SolverState state, Build build, long score) {
    if (state.topK.size() < k) {
      state.topK.add(build);
      state.buildsFound++;
      state.worst = scoreOf(state.topK.peek());
    } else if (score > state.worst) {
      state.topK.poll();
      state.topK.add(build);
      state.buildsFound++;
      state.worst = scoreOf(state.topK.peek());
    }
  }
}
