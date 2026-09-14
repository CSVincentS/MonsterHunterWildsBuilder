package mhwilds.optimizer.solver;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import mhwilds.optimizer.model.ArmorPiece;
import mhwilds.optimizer.model.ArmorSlot;
import mhwilds.optimizer.model.Build;
import mhwilds.optimizer.model.Skill;
import mhwilds.optimizer.solver.ArmorAggregates.Aggregate;
import mhwilds.optimizer.solver.GearOptions.AmuletOpt;
import mhwilds.optimizer.solver.GearOptions.WeaponOpt;

/**
 * Exact top-K solver by free-slot score over a Pareto-reduced armor pool.
 *
 * <p>Armor combinations collapse into aggregate signatures — capped required-skill levels, slot
 * multiset, set-bonus piece counts, worn-slot subset — keeping only the highest-defense
 * representative of each. Aggregates are evaluated best-first by an upper bound on their achievable
 * score, and each evaluation is a joint exact armor+weapon decoration fill (see {@link DecoFill})
 * over every relevant amulet and weapon. This spills any residual skill to the amulet or weapon
 * side and finds the minimum consumed slot value, so it never misses a reachable build.
 *
 * <p>This class only orchestrates the pipeline; each stage has a single responsibility and lives in
 * its own class: {@link GearOptions} (amulet/weapon pruning), {@link SearchBounds} (upper bounds),
 * {@link ArmorAggregates} (armor DP), {@link AggregateEvaluator} (per-aggregate exact fill), {@link
 * TopK} (result selection).
 */
public final class CatalogSolver implements Solver {

  private final int k;
  private final long bonus;

  public CatalogSolver() {
    this(1000, 0L);
  }

  public CatalogSolver(int k) {
    this(k, 0L);
  }

  public CatalogSolver(int k, long equipmentBonus) {
    if (k < 1) {
      throw new IllegalArgumentException("k must be >= 1, got " + k);
    }

    this.k = k;
    this.bonus = Math.max(0, equipmentBonus);
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

    List<Integer> nonSetIds = new ArrayList<>();
    List<Integer> setIds = new ArrayList<>();
    TreeSet<Integer> ids = new TreeSet<>();

    for (Map.Entry<Integer, Integer> e : thresholds.requiredSkills().entrySet()) {
      if (e.getValue() != null && e.getValue() > 0) {
        ids.add(e.getKey());
      }
    }

    if (ids.isEmpty()) {
      return List.of();
    }

    for (Integer id : ids) {
      (thresholds.isSetSkill(id) ? setIds : nonSetIds).add(id);
    }

    int n = nonSetIds.size();
    int nSet = setIds.size();
    SkillLevels.requireCapacity(n, DecoFill.GROUPS, "CatalogSolver");

    Map<Integer, Skill> skillMap = pool.skillMap() == null ? Map.of() : pool.skillMap();
    int[] required = new int[n];
    int[] maxRank = new int[n];
    Map<Integer, Integer> skillPos = new HashMap<>();

    for (int i = 0; i < n; i++) {
      int id = nonSetIds.get(i);
      required[i] = thresholds.required(id);
      Skill skill = skillMap.get(id);
      int rank = skill == null ? required[i] : skill.maxRank();

      if (rank < required[i]) {
        return List.of();
      }

      maxRank[i] = rank;
      skillPos.put(id, i);
    }

    int[] requiredSet = new int[nSet];
    List<Map<Integer, Integer>> setRanks = new ArrayList<>(nSet);

    for (int j = 0; j < nSet; j++) {
      int id = setIds.get(j);
      Map<Integer, Integer> ranks = thresholds.setSkillRanksOf(id);
      requiredSet[j] = thresholds.required(id);

      if (ArmorAggregates.activationLevel(ranks, ArmorSlot.values().length) < requiredSet[j]) {
        return List.of();
      }

      setRanks.add(ranks);
    }

    EnumMap<ArmorSlot, List<ArmorPiece>> bySlot = new EnumMap<>(ArmorSlot.class);

    for (ArmorSlot slot : ArmorSlot.values()) {
      bySlot.put(slot, new ArrayList<>());
    }

    for (ArmorPiece piece : ParetoFilter.filter(pool.armorPieces(), thresholds)) {
      bySlot.get(piece.kind()).add(piece);
    }

    DecoFill fill = new DecoFill(pool.armorDecorations(), pool.weaponDecorations(), skillPos);
    GearOptions gear = new GearOptions(skillPos);
    List<AmuletOpt> amuOptions = gear.amuletOptions(pool.amuletRanks());
    List<WeaponOpt> wepOptions = gear.weaponOptions(pool.weapons());

    if (bonus == 0 && (amuOptions.isEmpty() || wepOptions.isEmpty())) {
      return List.of();
    }

    SearchBounds bounds = new SearchBounds(n, required, skillPos, nonSetIds);
    bounds.compute(
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

    TopK topK = new TopK(k, bonus);

    ArmorAggregates armor = new ArmorAggregates(
      n,
      setIds,
      skillPos,
      maxRank,
      setRanks,
      requiredSet,
      bounds
    );
    List<Aggregate> aggregates = armor.enumerate(bySlot, bonus);

    if (aggregates.isEmpty()) {
      return List.of();
    }

    aggregates.sort((x, y) -> aggregateOrder(x, y, bounds, bonus));

    AggregateEvaluator evaluator = new AggregateEvaluator(n, required, bonus, bounds, fill);

    for (Aggregate aggregate : aggregates) {
      if (
        topK.isFull() &&
        bounds.upperBound(aggregate.slotValue(), aggregate.worn(), bonus) <= topK.worst()
      ) {
        break;
      }

      evaluator.evaluate(aggregate, amuEval, wepEval, topK);
    }

    return topK.sorted();
  }

  private static int aggregateOrder(Aggregate x, Aggregate y, SearchBounds bounds, long bonus) {
    int c = Long.compare(
      bounds.upperBound(y.slotValue(), y.worn(), bonus),
      bounds.upperBound(x.slotValue(), x.worn(), bonus)
    );

    if (c != 0) {
      return c;
    }

    c = Integer.compare(y.defense(), x.defense());

    if (c != 0) {
      return c;
    }

    c = Long.compare(x.signature().requiredLevels(), y.signature().requiredLevels());

    if (c != 0) {
      return c;
    }

    return Long.compare(x.signature().extras(), y.signature().extras());
  }
}
