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
import mhwilds.optimizer.ranking.FreeSlotRanking;

/**
 * Finds the exact top-K builds by free-slot score: best-first depth-first search over armor slots
 * with upper-bound pruning, a greedy minimal decoration fill, and a bounded result heap.
 */
public final class GreedySolver implements Solver {

  private static final long[] POW10 = { 1, 10, 100, 1000, 10000, 100000, 1000000 };

  private final int k;
  private final int threads;

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
  private int[] skillMaxRank;
  private boolean[] requiredFlag;
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
  private long armorDecUsefulTotal;
  private long bestAmuletTotal;
  private long bestWeaponSideTotal;

  private static final class SolverState {

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

      this.topK = new PriorityQueue<>(Comparator.comparingLong(Build::freeSlotScore));
    }
  }

  public GreedySolver() {
    this(1000);
  }

  public GreedySolver(int k) {
    this(k, Runtime.getRuntime().availableProcessors());
  }

  public GreedySolver(int k, int threads) {
    if (k < 1) {
      throw new IllegalArgumentException("k must be >= 1, got " + k);
    }

    this.k = k;
    this.threads = Math.max(1, threads);
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
      skillMaxRank = new int[numSkills];

      for (Map.Entry<Integer, Skill> e : pool.skillMap().entrySet()) {
        skillMaxRank[skillIndexMap.get(e.getKey())] = e.getValue().maxRank();
      }

      requiredFlag = new boolean[numSkills];

      for (int k = 0; k < skillCount; k++) {
        requiredFlag[skillIndexMap.get(reqIds.get(k))] = true;
      }
    } else {
      skillIndexMap = Map.of();

      numSkills = 0;
      skillMaxRank = new int[0];
      requiredFlag = new boolean[0];
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
      out.sort(new FreeSlotRanking());

      return out;
    } else {
      return solveParallel();
    }
  }

  private List<Build> solveParallel() {
    List<ArmorPiece> headPieces = bySlot.get(ArmorSlot.HEAD);
    int numTasks = Math.min(threads, headPieces.size());
    int chunkSize = (headPieces.size() + numTasks - 1) / numTasks;

    // Each chunk runs on its own SolverState, so the search itself shares no mutable data;
    // only the cumulative counters need synchronization, and chunk top-K lists are merged.
    ForkJoinPool pool = new ForkJoinPool(numTasks);

    try {
      List<ForkJoinTask<List<Build>>> forkTasks = new ArrayList<>();

      for (int i = 0; i < numTasks; i++) {
        int from = i * chunkSize;
        int to = Math.min(from + chunkSize, headPieces.size());

        if (from >= to) {
          break;
        }

        forkTasks.add(pool.submit(new ChunkTask(headPieces.subList(from, to))));
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
        state.chosen[0] = head;
        long ps = slotScore(head.slots());

        for (int k = 0; k < skillCount; k++) {
          state.cur[k] += head.skills().getOrDefault(reqIds.get(k), 0);
        }

        updateSetCounts(state, head, 1);
        int[] sv = pieceSkill.get(head);
        boolean over = false;

        for (int i = 0; i < sv.length; i += 2) {
          int si = sv[i];
          state.totals[si] += sv[i + 1];

          if (!requiredFlag[si] && state.totals[si] > skillMaxRank[si]) {
            over = true;
          }
        }

        if (!over) {
          search(1, state, ps, head.slots().length);
        }

        for (int i = 0; i < sv.length; i += 2) {
          state.totals[sv[i]] -= sv[i + 1];
        }

        state.chosen[0] = null;
        updateSetCounts(state, head, -1);

        for (int k = 0; k < skillCount; k++) {
          state.cur[k] -= head.skills().getOrDefault(reqIds.get(k), 0);
        }
      }

      synchronized (GreedySolver.this) {
        totalLeavesEvaluated += state.leavesEvaluated;
        totalBuildsFound += state.buildsFound;
      }

      return state.topK.stream().sorted(new FreeSlotRanking()).toList();
    }
  }

  static List<Build> mergeTopK(List<List<Build>> partialResults, int k) {
    PriorityQueue<Build> merged = new PriorityQueue<>(
      Comparator.comparingLong(Build::freeSlotScore)
    );

    for (List<Build> partial : partialResults) {
      for (Build b : partial) {
        if (merged.size() < k) {
          merged.add(b);
        } else if (b.freeSlotScore() > merged.peek().freeSlotScore()) {
          merged.poll();
          merged.add(b);
        }
      }
    }

    List<Build> out = new ArrayList<>(merged);
    out.sort(new FreeSlotRanking());

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

    // Remaining armor and weapon free-slot potential bound what any completion of this branch
    // can score; if that upper bound cannot beat the heap's worst, the subtree cannot reach the
    // current top-K, so prune without expanding it.
    if (heapFull(state) && slotAcc + remainingMaxScore[depth] + weaponMaxSlotScore <= state.worst) {
      return;
    }

    for (ArmorPiece piece : bySlot.get(slotOrder[depth])) {
      state.chosen[depth] = piece;
      int pieceSlots = piece.slots().length;
      long ps = slotScore(piece.slots());

      for (int k = 0; k < skillCount; k++) {
        state.cur[k] += piece.skills().getOrDefault(reqIds.get(k), 0);
      }

      updateSetCounts(state, piece, 1);
      int[] sv = pieceSkill.get(piece);
      boolean over = false;

      for (int i = 0; i < sv.length; i += 2) {
        int si = sv[i];
        state.totals[si] += sv[i + 1];

        if (!requiredFlag[si] && state.totals[si] > skillMaxRank[si]) {
          over = true;
        }
      }

      if (!over) {
        search(depth + 1, state, slotAcc + ps, slotsUsed + pieceSlots);
      }

      for (int i = 0; i < sv.length; i += 2) {
        state.totals[sv[i]] -= sv[i + 1];
      }

      state.chosen[depth] = null;
      updateSetCounts(state, piece, -1);

      for (int k = 0; k < skillCount; k++) {
        state.cur[k] -= piece.skills().getOrDefault(reqIds.get(k), 0);
      }
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

    long aggregateAvail =
      remainingArmorSkillTotal[depth] +
      Math.min(remainingSlotPoints, armorDecUsefulTotal) +
      bestAmuletTotal +
      bestWeaponSideTotal;

    return aggregateAvail >= aggregateNeeded;
  }

  private void processLeaf(ArmorPiece[] chosen, long slotAcc, int[] cur, SolverState state) {
    state.leavesEvaluated++;

    int slots = 0;

    for (int p = 0; p < slotOrder.length; p++) {
      slots += chosen[p].slots().length;
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

    if (heapFull(state) && slotAcc + weaponMaxSlotScore <= state.worst) {
      return;
    }

    int[] need = new int[skillCount];

    for (int k = 0; k < skillCount; k++) {
      need[k] = setReqFlag[k] ? 0 : Math.max(0, required[k] - Math.min(cur[k], maxRank[k]));
    }

    int[] leafTot = state.totals.clone();

    boolean[][] occupied = new boolean[slotOrder.length][];

    for (int i = 0; i < slotOrder.length; i++) {
      occupied[i] = new boolean[chosen[i].slots().length];
    }

    List<SlotAssignment> armorAssign = new ArrayList<>();
    int[] filled = cur.clone();

    long occupiedScore = 0;

    List<int[]> slotRefs = new ArrayList<>();

    for (int p = 0; p < slotOrder.length; p++) {
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
        armorAssign.add(new SlotAssignment(bestDec, slotOrder[p], idx));
        filled[bestK] += bestC;
        need[bestK] = Math.max(0, need[bestK] - bestC);
        applyPacked(leafTot, decSkill.get(bestDec));
        occupiedScore += POW10[size];
      }
    }

    int[] r0 = new int[skillCount];
    boolean infeasible = false;
    int amuletNeeded = 0;
    boolean amuletStrictlyNeeded = false;

    for (int k = 0; k < skillCount; k++) {
      r0[k] = setReqFlag[k] ? 0 : Math.max(0, required[k] - Math.min(filled[k], maxRank[k]));

      if (r0[k] > bestAmulet[k] + bestWeaponSide[k]) {
        infeasible = true;
      }

      if (r0[k] > bestWeaponSide[k]) {
        amuletStrictlyNeeded = true;
        amuletNeeded++;
      }
    }

    if (infeasible) {
      return;
    }

    if (amuletNeeded > maxAmuletSkillCoverage) {
      return;
    }

    long armorFreeScore = slotAcc - occupiedScore;

    if (heapFull(state) && armorFreeScore + weaponMaxSlotScore <= state.worst) {
      return;
    }

    ArmorPiece[] finalArmor = chosen.clone();
    processLeafSequentialAmuletWeapon(
      state,
      finalArmor,
      armorAssign,
      leafTot,
      r0,
      armorFreeScore,
      amuletStrictlyNeeded
    );
  }

  private void processLeafSequentialAmuletWeapon(
    SolverState state,
    ArmorPiece[] finalArmor,
    List<SlotAssignment> armorAssign,
    int[] leafTot,
    int[] r0,
    long armorFreeScore,
    boolean amuletStrictlyNeeded
  ) {
    for (int a = 0; a < amulets.size(); a++) {
      if (amuletStrictlyNeeded && !amuUseful[a]) {
        continue;
      }

      int[] ra = new int[skillCount];
      boolean amuletOk = true;

      for (int k = 0; k < skillCount; k++) {
        ra[k] = Math.max(0, r0[k] - amuVec[a][k]);

        if (ra[k] > bestWeaponSide[k]) {
          amuletOk = false;
          break;
        }

        if (
          amuVec[a][k] > 0 &&
          reqSkillIdx[k] >= 0 &&
          leafTot[reqSkillIdx[k]] + amuVec[a][k] > maxRank[k]
        ) {
          amuletOk = false;
          break;
        }
      }

      if (!amuletOk) {
        continue;
      }

      int[] ap = amuSkill.get(amulets.get(a));

      for (int b = 0; b < weapons.size(); b++) {
        int[] bp = weaponSkill.get(weapons.get(b));

        if (overCaps(leafTot, ap, bp)) {
          continue;
        }

        boolean weaponOk = true;

        for (int k = 0; k < skillCount; k++) {
          if (
            weaponInnate[b][k] > 0 &&
            reqSkillIdx[k] >= 0 &&
            leafTot[reqSkillIdx[k]] + amuVec[a][k] + weaponInnate[b][k] > maxRank[k]
          ) {
            weaponOk = false;
            break;
          }
        }

        if (!weaponOk) {
          continue;
        }

        int[] rb = new int[skillCount];
        boolean zero = true;

        for (int k = 0; k < skillCount; k++) {
          rb[k] = Math.max(0, ra[k] - weaponInnate[b][k]);

          if (rb[k] > 0) {
            zero = false;
          }
        }

        long weaponFree;
        List<SlotAssignment> weaponAssign = List.of();

        if (!zero) {
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
        long score = armorFreeScore + weaponFree;

        if (heapFull(state) && score <= state.worst) {
          continue;
        }

        Build build = new Build(
          finalArmor.clone(),
          new ArrayList<>(armorAssign),
          weaponAssign,
          amulets.get(a),
          weapons.get(b)
        );
        offer(state, build, score);
      }
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
      armorDecUsefulTotal += decUsefulPoints;
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

  private static int contrib(int[] packed, int si) {
    for (int i = 0; i < packed.length; i += 2) {
      if (packed[i] == si) {
        return packed[i + 1];
      }
    }

    return 0;
  }

  /** Tracks chosen-piece counts for required set/group bonus skills (skill id == bonus id). */
  private void updateSetCounts(SolverState state, ArmorPiece piece, int delta) {
    if (state == null) {
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
    int[] p = decSkill.get(d);

    for (int i = 0; i < p.length; i += 2) {
      int si = p[i];

      if (!requiredFlag[si] && base[si] + p[i + 1] > skillMaxRank[si]) {
        return false;
      }
    }

    return true;
  }

  private boolean overCaps(int[] base, int[] amuPacked, int[] weaponPacked) {
    for (int i = 0; i < amuPacked.length; i += 2) {
      int si = amuPacked[i];

      if (
        !requiredFlag[si] &&
        base[si] + amuPacked[i + 1] + contrib(weaponPacked, si) > skillMaxRank[si]
      ) {
        return true;
      }
    }

    for (int i = 0; i < weaponPacked.length; i += 2) {
      int si = weaponPacked[i];

      if (requiredFlag[si] || contrib(amuPacked, si) != 0) {
        continue;
      }

      if (base[si] + weaponPacked[i + 1] > skillMaxRank[si]) {
        return true;
      }
    }

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
      state.worst = state.topK.peek().freeSlotScore();
    } else if (score > state.worst) {
      state.topK.poll();
      state.topK.add(build);
      state.buildsFound++;
      state.worst = state.topK.peek().freeSlotScore();
    }
  }
}
