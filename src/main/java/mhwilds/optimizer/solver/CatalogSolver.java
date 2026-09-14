package mhwilds.optimizer.solver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.TreeSet;
import mhwilds.optimizer.model.AmuletRank;
import mhwilds.optimizer.model.ArmorPiece;
import mhwilds.optimizer.model.ArmorSlot;
import mhwilds.optimizer.model.Build;
import mhwilds.optimizer.model.Decoration;
import mhwilds.optimizer.model.Skill;
import mhwilds.optimizer.model.SlotAssignment;
import mhwilds.optimizer.model.Weapon;
import mhwilds.optimizer.solver.DecoFill.FillResult;
import mhwilds.optimizer.solver.DecoFill.Step;

/**
 * Exact top-K solver by free-slot score over a Pareto-reduced armor pool.
 *
 * <p>Armor combinations collapse into aggregate signatures — capped required-skill levels, slot
 * multiset, set-bonus piece counts, worn-slot subset — keeping only the highest-defense
 * representative of each. Aggregates are evaluated best-first by an upper bound on their achievable
 * score, and each evaluation is a joint exact armor+weapon decoration fill (see {@link DecoFill})
 * over every relevant amulet and weapon. This is strictly more general than the layered greedy fill
 * of {@link GreedySolver}: it can spill any residual skill to the amulet or weapon side and finds
 * the minimum consumed slot value, so it never loses a build the greedy fill could reach.
 *
 * <p>Result selection replicates {@link GreedySolver}: a score-only heap of up to {@code k} builds
 * that only accepts strictly-better scores at the boundary, ordered best-first by score then
 * total defense.
 */
public final class CatalogSolver implements Solver {

  private static final long[] POW10 = { 1, 10, 100, 1000, 10000, 100000, 1000000 };
  static final long INF = Long.MAX_VALUE >> 2;
  private static final int[] ZERO3 = { 0, 0, 0 };

  private final int k;
  private final long bonus;

  @SuppressWarnings("unused")
  private final int threads;

  private int n;
  private List<Integer> nonSetIds;
  private int[] required;
  private int[] maxRank;
  private List<Integer> setIds;
  private List<Map<Integer, Integer>> setRanks;
  private int[] requiredSet;
  private Map<Integer, Integer> skillPos;

  private DecoFill fill;
  private long weaponMaxSlotValue;
  private long[] bestAmu;
  private long[] bestWeaponInnate;
  private long[] bestArmorDeco;
  private long[] bestWeaponDeco;
  private int[][] armorDecoBest;
  private int[][] weaponDecoBest;
  private int[] armorDecoBestTotal;
  private int[] weaponDecoBestTotal;
  private int[] suffixSlots;
  private int[][] suffixSkill;

  private PriorityQueue<Build> topK;
  private long worst;

  private static final class AmuletOpt {

    final AmuletRank rank;
    final long pack;

    AmuletOpt(AmuletRank rank, long pack) {
      this.rank = rank;
      this.pack = pack;
    }
  }

  private static final class WeaponOpt {

    final Weapon weapon;
    final long innatePack;
    final long slotValue;
    final int[] counts;

    WeaponOpt(Weapon weapon, long innatePack, long slotValue, int[] counts) {
      this.weapon = weapon;
      this.innatePack = innatePack;
      this.slotValue = slotValue;
      this.counts = counts;
    }
  }

  private record AggKey(long a, long b) {}

  private static final class AggVal {

    long keyA;
    final int[] slotCounts = new int[3];
    final int[] setCount;
    int worn;
    int defense;
    long slotValue;
    final ArmorPiece[] pieces = new ArmorPiece[5];

    AggVal(int nSet) {
      this.setCount = new int[nSet];
    }
  }

  private static final class Agg {

    final long keyA;
    final long keyB;
    final int[] slotCounts;
    final int worn;
    final int defense;
    final long slotValue;
    final ArmorPiece[] pieces;

    Agg(
      long keyA,
      long keyB,
      int[] slotCounts,
      int worn,
      int defense,
      long slotValue,
      ArmorPiece[] pieces
    ) {
      this.keyA = keyA;
      this.keyB = keyB;
      this.slotCounts = slotCounts;
      this.worn = worn;
      this.defense = defense;
      this.slotValue = slotValue;
      this.pieces = pieces;
    }
  }

  public CatalogSolver() {
    this(1000);
  }

  public CatalogSolver(int k) {
    this(k, 1, 0L);
  }

  public CatalogSolver(int k, int threads, long equipmentBonus) {
    if (k < 1) {
      throw new IllegalArgumentException("k must be >= 1, got " + k);
    }

    this.k = k;
    this.threads = Math.max(1, threads);
    this.bonus = Math.max(0, equipmentBonus);
  }

  @Override
  public List<Build> solve(SolverPool pool) {
    SkillThresholds thr = pool.thresholds();

    if (thr == null || thr.requiredSkills() == null || thr.requiredSkills().isEmpty()) {
      return List.of();
    }

    TreeSet<Integer> ids = new TreeSet<>();

    for (Map.Entry<Integer, Integer> e : thr.requiredSkills().entrySet()) {
      if (e.getValue() != null && e.getValue() > 0) {
        ids.add(e.getKey());
      }
    }

    if (ids.isEmpty()) {
      return List.of();
    }

    nonSetIds = new ArrayList<>();
    setIds = new ArrayList<>();

    for (Integer id : ids) {
      (thr.isSetSkill(id) ? setIds : nonSetIds).add(id);
    }

    n = nonSetIds.size();

    if (4 * n + 4 * 6 > 60) {
      throw new IllegalArgumentException(
        "CatalogSolver supports at most 9 required non-set skills, got " + n
      );
    }

    Map<Integer, Skill> skillMap = pool.skillMap() == null ? Map.of() : pool.skillMap();
    required = new int[n];
    maxRank = new int[n];
    skillPos = new HashMap<>();

    for (int i = 0; i < n; i++) {
      int id = nonSetIds.get(i);
      required[i] = thr.required(id);
      Skill skill = skillMap.get(id);
      int rank = skill == null ? required[i] : skill.maxRank();

      if (rank < required[i]) {
        return List.of();
      }

      maxRank[i] = rank;
      skillPos.put(id, i);
    }

    int nSet = setIds.size();
    requiredSet = new int[nSet];
    setRanks = new ArrayList<>(nSet);

    for (int j = 0; j < nSet; j++) {
      int id = setIds.get(j);
      Map<Integer, Integer> ranks = thr.setSkillRanksOf(id);
      requiredSet[j] = thr.required(id);

      if (activationLevel(ranks, ArmorSlot.values().length) < requiredSet[j]) {
        return List.of();
      }

      setRanks.add(ranks);
    }

    fill = new DecoFill(pool.armorDecorations(), pool.weaponDecorations(), skillPos);

    EnumMap<ArmorSlot, List<ArmorPiece>> bySlot = new EnumMap<>(ArmorSlot.class);

    for (ArmorSlot s : ArmorSlot.values()) {
      bySlot.put(s, new ArrayList<>());
    }

    for (ArmorPiece p : ParetoFilter.filter(pool.armorPieces(), thr)) {
      bySlot.get(p.kind()).add(p);
    }

    List<AmuletOpt> amuOptions = amuletOptions(pool.amuletRanks());
    List<WeaponOpt> wepOptions = weaponOptions(pool.weapons());

    if (bonus == 0 && (amuOptions.isEmpty() || wepOptions.isEmpty())) {
      return List.of();
    }

    computeMaxima(
      pool.armorDecorations(),
      pool.weaponDecorations(),
      amuOptions,
      wepOptions,
      bySlot
    );

    List<AmuletOpt> amuEval = new ArrayList<>(amuOptions);
    List<WeaponOpt> wepEval = new ArrayList<>(wepOptions);

    if (bonus > 0) {
      amuEval.add(null);
      wepEval.add(null);
    }

    topK = new PriorityQueue<>(Comparator.comparingLong(this::scoreOf));
    worst = 0;

    List<Agg> aggregates = enumerateAggregates(bySlot, nSet);

    if (aggregates.isEmpty()) {
      return List.of();
    }

    aggregates.sort(this::aggregateOrder);

    for (int idx = 0; idx < aggregates.size(); idx++) {
      Agg agg = aggregates.get(idx);

      if (topK.size() >= k && ub(agg) <= worst) {
        break;
      }

      evaluate(agg, amuEval, wepEval, nSet);
    }

    List<Build> out = new ArrayList<>(topK);
    out.sort(ranking());

    return out;
  }

  private void computeMaxima(
    List<Decoration> armorDecos,
    List<Decoration> weaponDecos,
    List<AmuletOpt> amuOptions,
    List<WeaponOpt> wepOptions,
    EnumMap<ArmorSlot, List<ArmorPiece>> bySlot
  ) {
    bestAmu = new long[n];
    bestWeaponInnate = new long[n];
    bestArmorDeco = new long[n];
    bestWeaponDeco = new long[n];

    for (AmuletOpt a : amuOptions) {
      for (int i = 0; i < n; i++) {
        bestAmu[i] = Math.max(bestAmu[i], levelAt(a.pack, i));
      }
    }

    for (WeaponOpt w : wepOptions) {
      weaponMaxSlotValue = Math.max(weaponMaxSlotValue, w.slotValue);

      for (int i = 0; i < n; i++) {
        bestWeaponInnate[i] = Math.max(bestWeaponInnate[i], levelAt(w.innatePack, i));
      }
    }

    for (Decoration d : armorDecos) {
      for (int i = 0; i < n; i++) {
        bestArmorDeco[i] = Math.max(bestArmorDeco[i], levelAt(packOf(d), i));
      }
    }

    for (Decoration d : weaponDecos) {
      for (int i = 0; i < n; i++) {
        bestWeaponDeco[i] = Math.max(bestWeaponDeco[i], levelAt(packOf(d), i));
      }
    }

    armorDecoBest = new int[3][n];
    weaponDecoBest = new int[3][n];
    armorDecoBestTotal = new int[3];
    weaponDecoBestTotal = new int[3];

    for (Decoration d : armorDecos) {
      long pack = packOf(d);

      if (pack == 0) {
        continue;
      }

      int total = 0;

      for (int i = 0; i < n; i++) {
        total += levelAt(pack, i);
      }

      for (int s = d.level() - 1; s < 3; s++) {
        for (int i = 0; i < n; i++) {
          armorDecoBest[s][i] = Math.max(armorDecoBest[s][i], levelAt(pack, i));
        }

        armorDecoBestTotal[s] = Math.max(armorDecoBestTotal[s], total);
      }
    }

    for (Decoration d : weaponDecos) {
      long pack = packOf(d);

      if (pack == 0) {
        continue;
      }

      int total = 0;

      for (int i = 0; i < n; i++) {
        total += levelAt(pack, i);
      }

      for (int s = d.level() - 1; s < 3; s++) {
        for (int i = 0; i < n; i++) {
          weaponDecoBest[s][i] = Math.max(weaponDecoBest[s][i], levelAt(pack, i));
        }

        weaponDecoBestTotal[s] = Math.max(weaponDecoBestTotal[s], total);
      }
    }

    int[][] posMaxSkill = new int[5][n];
    int[] posMaxSlots = new int[5];

    for (int pos = 0; pos < 5; pos++) {
      ArmorSlot slot = ArmorSlot.values()[pos];

      for (ArmorPiece p : bySlot.get(slot)) {
        posMaxSlots[pos] = Math.max(posMaxSlots[pos], p.slotCount());

        for (int i = 0; i < n; i++) {
          int level = p.skills().getOrDefault(nonSetIds.get(i), 0);
          posMaxSkill[pos][i] = Math.max(posMaxSkill[pos][i], level);
        }
      }
    }

    suffixSlots = new int[6];
    suffixSkill = new int[6][n];

    for (int pos = 4; pos >= 0; pos--) {
      suffixSlots[pos] = suffixSlots[pos + 1] + posMaxSlots[pos];

      for (int i = 0; i < n; i++) {
        suffixSkill[pos][i] = suffixSkill[pos + 1][i] + posMaxSkill[pos][i];
      }
    }
  }

  private List<AmuletOpt> amuletOptions(List<AmuletRank> amulets) {
    List<AmuletOpt> opts = new ArrayList<>();

    for (AmuletRank a : amulets) {
      long pack = 0;

      for (Map.Entry<Integer, Integer> e : a.skills().entrySet()) {
        Integer pos = skillPos.get(e.getKey());

        if (pos != null) {
          pack |= (long) (e.getValue() & 0xF) << (4 * pos);
        }
      }

      opts.add(new AmuletOpt(a, pack));
    }

    return pruneAmulets(opts);
  }

  private List<AmuletOpt> pruneAmulets(List<AmuletOpt> opts) {
    List<AmuletOpt> out = new ArrayList<>();

    outer: for (AmuletOpt a : opts) {
      for (AmuletOpt b : opts) {
        if (b == a) {
          continue;
        }

        if (dominatesAmulet(b.pack, a.pack)) {
          continue outer;
        }
      }

      out.add(a);
    }

    return out;
  }

  private boolean dominatesAmulet(long b, long a) {
    boolean strict = false;

    for (int i = 0; i < n; i++) {
      int bv = levelAt(b, i);
      int av = levelAt(a, i);

      if (bv < av) {
        return false;
      }

      if (bv > av) {
        strict = true;
      }
    }

    return strict;
  }

  private List<WeaponOpt> weaponOptions(List<Weapon> weapons) {
    List<WeaponOpt> opts = new ArrayList<>();

    for (Weapon w : weapons) {
      long pack = 0;

      for (Map.Entry<Integer, Integer> e : w.skills().entrySet()) {
        Integer pos = skillPos.get(e.getKey());

        if (pos != null) {
          pack |= (long) (e.getValue() & 0xF) << (4 * pos);
        }
      }

      int[] counts = new int[3];

      for (int s : w.slots()) {
        if (s >= 1 && s <= 3) {
          counts[s - 1]++;
        }
      }

      opts.add(new WeaponOpt(w, pack, slotScore(w.slots()), counts));
    }

    return pruneWeapons(opts);
  }

  private List<WeaponOpt> pruneWeapons(List<WeaponOpt> opts) {
    List<WeaponOpt> out = new ArrayList<>();

    outer: for (WeaponOpt a : opts) {
      for (WeaponOpt b : opts) {
        if (b == a) {
          continue;
        }

        if (dominatesWeapon(b, a)) {
          continue outer;
        }
      }

      out.add(a);
    }

    return out;
  }

  private boolean dominatesWeapon(WeaponOpt b, WeaponOpt a) {
    boolean strict = false;

    for (int s = 0; s < 3; s++) {
      if (b.counts[s] < a.counts[s]) {
        return false;
      }

      if (b.counts[s] > a.counts[s]) {
        strict = true;
      }
    }

    for (int i = 0; i < n; i++) {
      int bv = levelAt(b.innatePack, i);
      int av = levelAt(a.innatePack, i);

      if (bv < av) {
        return false;
      }

      if (bv > av) {
        strict = true;
      }
    }

    return strict;
  }

  private long packOf(Decoration d) {
    long pack = 0;

    for (Map.Entry<Integer, Integer> e : d.skills().entrySet()) {
      Integer pos = skillPos.get(e.getKey());

      if (pos != null) {
        pack |= (long) (e.getValue() & 0xF) << (4 * pos);
      }
    }

    return pack;
  }

  private List<Agg> enumerateAggregates(EnumMap<ArmorSlot, List<ArmorPiece>> bySlot, int nSet) {
    Map<AggKey, AggVal> dp = new HashMap<>();
    dp.put(new AggKey(0, 0), new AggVal(nSet));

    for (int pos = 0; pos < 5; pos++) {
      List<ArmorPiece> candidates = new ArrayList<>(bySlot.get(ArmorSlot.values()[pos]));

      if (bonus > 0) {
        candidates.add(null);
      }

      Map<AggKey, AggVal> next = new HashMap<>(dp.size() * 2);

      for (Map.Entry<AggKey, AggVal> e : dp.entrySet()) {
        AggVal v = e.getValue();

        for (ArmorPiece p : candidates) {
          AggVal nv = extend(v, pos, p);

          if (nv == null) {
            continue;
          }

          AggKey key = keyOf(nv);
          AggVal old = next.get(key);

          if (old == null || nv.defense > old.defense) {
            next.put(key, nv);
          }
        }
      }

      dp = next;

      if (dp.isEmpty()) {
        return List.of();
      }
    }

    List<Agg> aggregates = new ArrayList<>(dp.size());

    for (Map.Entry<AggKey, AggVal> e : dp.entrySet()) {
      AggVal v = e.getValue();
      boolean ok = true;

      for (int j = 0; j < nSet; j++) {
        if (activationLevel(setRanks.get(j), v.setCount[j]) < requiredSet[j]) {
          ok = false;
          break;
        }
      }

      if (!ok) {
        continue;
      }

      AggKey key = e.getKey();
      aggregates.add(
        new Agg(
          v.keyA,
          key.b(),
          v.slotCounts.clone(),
          v.worn,
          v.defense,
          v.slotValue,
          v.pieces.clone()
        )
      );
    }

    return aggregates;
  }

  private AggVal extend(AggVal v, int pos, ArmorPiece p) {
    AggVal r = new AggVal(setIds.size());
    r.keyA = v.keyA;
    r.worn = v.worn;
    r.defense = v.defense;
    r.slotValue = v.slotValue;
    System.arraycopy(v.slotCounts, 0, r.slotCounts, 0, 3);
    System.arraycopy(v.setCount, 0, r.setCount, 0, v.setCount.length);
    System.arraycopy(v.pieces, 0, r.pieces, 0, 5);

    if (p == null) {
      r.pieces[pos] = null;
    } else {
      r.pieces[pos] = p;
      r.worn++;
      r.defense += p.maxDefense();

      for (Map.Entry<Integer, Integer> se : p.skills().entrySet()) {
        Integer skillPosIdx = skillPos.get(se.getKey());

        if (skillPosIdx == null) {
          continue;
        }

        int cur = levelAt(r.keyA, skillPosIdx);
        int next = Math.min(cur + se.getValue(), maxRank[skillPosIdx]);
        r.keyA = setLevel(r.keyA, skillPosIdx, next);
      }

      for (int s : p.slots()) {
        if (s >= 1 && s <= 3) {
          r.slotCounts[s - 1] = Math.min(15, r.slotCounts[s - 1] + 1);
        }

        r.slotValue += POW10[s];
      }

      for (int j = 0; j < setIds.size(); j++) {
        int sid = setIds.get(j);

        if (p.setBonusId() == sid || p.groupBonusId() == sid) {
          r.setCount[j] = Math.min(7, r.setCount[j] + 1);
        }
      }
    }

    if (!feasiblePartial(r, pos + 1)) {
      return null;
    }

    return r;
  }

  private boolean feasiblePartial(AggVal r, int chosen) {
    int nextPos = chosen;

    if (nextPos >= 5) {
      for (int i = 0; i < n; i++) {
        int innate = levelAt(r.keyA, i);
        int totalSlots = 0;

        for (int s = 0; s < 3; s++) {
          totalSlots += r.slotCounts[s];
        }

        if (
          innate +
            bestAmu[i] +
            bestWeaponInnate[i] +
            (long) totalSlots * bestArmorDeco[i] +
            3L * bestWeaponDeco[i] <
          required[i]
        ) {
          return false;
        }
      }

      for (int j = 0; j < setIds.size(); j++) {
        if (activationLevel(setRanks.get(j), r.setCount[j]) < requiredSet[j]) {
          return false;
        }
      }

      return true;
    }

    int piecesLeft = 5 - chosen;

    for (int i = 0; i < n; i++) {
      int innate = levelAt(r.keyA, i);
      int totalSlots = 0;

      for (int s = 0; s < 3; s++) {
        totalSlots += r.slotCounts[s];
      }

      if (
        innate +
          suffixSkill[nextPos][i] +
          bestAmu[i] +
          bestWeaponInnate[i] +
          (long) (totalSlots + suffixSlots[nextPos]) * bestArmorDeco[i] +
          3L * bestWeaponDeco[i] <
        required[i]
      ) {
        return false;
      }
    }

    for (int j = 0; j < setIds.size(); j++) {
      if (activationLevel(setRanks.get(j), r.setCount[j] + piecesLeft) < requiredSet[j]) {
        return false;
      }
    }

    return true;
  }

  private long ub(Agg agg) {
    long upper = agg.slotValue;

    if (bonus > 0) {
      upper += bonus * (5L - agg.worn);
      upper += bonus;
      upper += Math.max(weaponMaxSlotValue, bonus);
    } else {
      upper += weaponMaxSlotValue;
    }

    return upper;
  }

  private int aggregateOrder(Agg x, Agg y) {
    int c = Long.compare(ub(y), ub(x));

    if (c != 0) {
      return c;
    }

    c = Integer.compare(y.defense, x.defense);

    if (c != 0) {
      return c;
    }

    c = Long.compare(x.keyA, y.keyA);

    if (c != 0) {
      return c;
    }

    return Long.compare(x.keyB, y.keyB);
  }

  /** Total max deco levels for one skill over a slot-count multiset (sound over-estimate). */
  private static long slotCover(int[] counts, int[][] best, int skill) {
    long cover = 0;

    for (int s = 0; s < 3; s++) {
      cover += (long) counts[s] * best[s][skill];
    }

    return cover;
  }

  /**
   * Fast, provably-needed pre-filter before the exact fill: whether the residual could still be
   * covered by the available slots at all. Both prunes are sound necessary conditions — a real
   * deco assignment satisfies them, so a build is never wrongly dropped.
   */
  private boolean fillFeasible(long needW, int[] armorCount, int[] wepCount) {
    long needPoints = 0;

    for (int i = 0; i < n; i++) {
      int r = (int) ((needW >>> (4 * i)) & 0xF);

      if (r == 0) {
        continue;
      }

      needPoints += r;

      if (r > slotCover(armorCount, armorDecoBest, i) + slotCover(wepCount, weaponDecoBest, i)) {
        return false;
      }
    }

    long slotPoints = 0;

    for (int s = 0; s < 3; s++) {
      slotPoints +=
        (long) armorCount[s] * armorDecoBestTotal[s] + (long) wepCount[s] * weaponDecoBestTotal[s];
    }

    return needPoints <= slotPoints;
  }

  private void evaluate(Agg agg, List<AmuletOpt> amuEval, List<WeaponOpt> wepEval, int nSet) {
    long need0 = 0;

    for (int i = 0; i < n; i++) {
      int innate = levelAt(agg.keyA, i);
      int res = required[i] - innate;

      if (res > 0) {
        if (
          res >
          bestAmu[i] +
            bestWeaponInnate[i] +
            slotCover(agg.slotCounts, armorDecoBest, i) +
            3L * bestWeaponDeco[i]
        ) {
          return;
        }

        need0 |= (long) res << (4 * i);
      }
    }

    long fixed = agg.slotValue;

    if (bonus > 0) {
      fixed += bonus * (5L - agg.worn);
    }

    for (AmuletOpt a : amuEval) {
      long needA = a == null ? need0 : DecoFill.sub(need0, a.pack);

      for (WeaponOpt w : wepEval) {
        long needW = w == null ? needA : DecoFill.sub(needA, w.innatePack);

        if (!fillFeasible(needW, agg.slotCounts, w == null ? ZERO3 : w.counts)) {
          continue;
        }

        long cost = fill.fillCost(needW, agg.slotCounts, w == null ? ZERO3 : w.counts);

        if (cost == INF) {
          continue;
        }

        long score =
          fixed -
          cost +
          (w == null ? 0 : w.slotValue) +
          (a == null ? bonus : 0) +
          (w == null ? bonus : 0);

        if (topK.size() >= k && score <= worst) {
          continue;
        }

        FillResult res = fill.fillWithSteps(needW, agg.slotCounts, w == null ? ZERO3 : w.counts);

        if (res.cost() == INF) {
          continue;
        }

        Build build = toBuild(agg, res.steps(), a, w);
        offer(build, score);
      }
    }
  }

  private Build toBuild(Agg agg, List<Step> steps, AmuletOpt a, WeaponOpt w) {
    ArmorPiece[] worn = agg.pieces.clone();
    boolean[][] occ = new boolean[5][];

    for (int i = 0; i < 5; i++) {
      occ[i] = new boolean[worn[i] == null ? 0 : worn[i].slotCount()];
    }

    List<SlotAssignment> armorAssign = new ArrayList<>();
    boolean[] weaponOcc = w == null ? null : new boolean[w.weapon.slotCount()];
    List<SlotAssignment> weaponAssign = new ArrayList<>();

    for (Step s : steps) {
      if (s.kind() == DecoFill.ARMOR) {
        boolean placed = false;

        for (int i = 0; i < 5 && !placed; i++) {
          ArmorPiece p = worn[i];

          if (p == null) {
            continue;
          }

          for (int si = 0; si < p.slotCount(); si++) {
            if (p.slots()[si] == s.size() && !occ[i][si]) {
              occ[i][si] = true;
              armorAssign.add(new SlotAssignment(s.deco(), p.kind(), si));
              placed = true;
              break;
            }
          }
        }

        if (!placed) {
          throw new IllegalStateException("no armor slot of size " + s.size());
        }
      } else {
        boolean placed = false;

        for (int si = 0; si < weaponOcc.length; si++) {
          if (w.weapon.slots()[si] == s.size() && !weaponOcc[si]) {
            weaponOcc[si] = true;
            weaponAssign.add(new SlotAssignment(s.deco(), null, si));
            placed = true;
            break;
          }
        }

        if (!placed) {
          throw new IllegalStateException("no weapon slot of size " + s.size());
        }
      }
    }

    return new Build(
      worn,
      armorAssign,
      weaponAssign,
      a == null ? null : a.rank,
      w == null ? null : w.weapon
    );
  }

  private void offer(Build build, long score) {
    if (topK.size() < k) {
      topK.add(build);
      worst = scoreOf(topK.peek());
    } else if (score > worst) {
      topK.poll();
      topK.add(build);
      worst = scoreOf(topK.peek());
    }
  }

  private long scoreOf(Build b) {
    return b.equipmentAwareScore(bonus);
  }

  private Comparator<Build> ranking() {
    return Comparator.comparingLong(this::scoreOf)
      .reversed()
      .thenComparing(Comparator.comparingInt(Build::totalDefense).reversed());
  }

  private static int levelAt(long pack, int pos) {
    return (int) ((pack >>> (4 * pos)) & 0xF);
  }

  private static long setLevel(long pack, int pos, int level) {
    return (pack & ~((long) 0xF << (4 * pos))) | ((long) (level & 0xF) << (4 * pos));
  }

  private static long slotScore(int[] slots) {
    long s = 0;

    for (int sz : slots) {
      s += POW10[sz];
    }

    return s;
  }

  private AggKey keyOf(AggVal v) {
    long b = 0;

    for (int sz = 1; sz <= 3; sz++) {
      b |= (long) v.slotCounts[sz - 1] << (4 * (sz - 1));
    }

    b |= (long) v.worn << 12;

    for (int j = 0; j < v.setCount.length; j++) {
      b |= (long) v.setCount[j] << (15 + 3 * j);
    }

    return new AggKey(v.keyA, b);
  }

  /** Highest skill level achieved by {@code pieceCount} pieces under the bonus thresholds. */
  private static int activationLevel(Map<Integer, Integer> thresholds, int pieceCount) {
    int best = 0;

    for (Map.Entry<Integer, Integer> rank : thresholds.entrySet()) {
      if (pieceCount >= rank.getKey() && rank.getValue() > best) {
        best = rank.getValue();
      }
    }

    return best;
  }
}
