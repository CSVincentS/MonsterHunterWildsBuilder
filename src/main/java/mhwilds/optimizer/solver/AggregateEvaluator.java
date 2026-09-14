package mhwilds.optimizer.solver;

import java.util.List;
import mhwilds.optimizer.solver.ArmorAggregates.Aggregate;
import mhwilds.optimizer.solver.DecoFill.FillResult;
import mhwilds.optimizer.solver.GearOptions.AmuletOpt;
import mhwilds.optimizer.solver.GearOptions.WeaponOpt;

/**
 * Evaluates one armor aggregate: a joint exact armor+weapon decoration fill (see {@link DecoFill})
 * over every relevant amulet and weapon, keeping the best builds in a {@link TopK} heap.
 */
final class AggregateEvaluator {

  private static final int[] ZERO_COUNTS = { 0, 0, 0 };

  private final int n;
  private final int[] required;
  private final long bonus;
  private final SearchBounds bounds;
  private final DecoFill fill;

  AggregateEvaluator(int n, int[] required, long bonus, SearchBounds bounds, DecoFill fill) {
    this.n = n;
    this.required = required;
    this.bonus = bonus;
    this.bounds = bounds;
    this.fill = fill;
  }

  void evaluate(Aggregate aggregate, List<AmuletOpt> amulets, List<WeaponOpt> weapons, TopK topK) {
    long need0 = 0;

    for (int i = 0; i < n; i++) {
      int innate = SkillLevels.levelAt(aggregate.signature().requiredLevels(), i);
      int res = required[i] - innate;

      if (res > 0) {
        if (res > bounds.residualCeiling(i, aggregate.slotCounts())) {
          return;
        }

        need0 |= (long) res << (4 * i);
      }
    }

    long fixed = aggregate.slotValue();

    if (bonus > 0) {
      fixed += bonus * (5L - aggregate.worn());
    }

    for (AmuletOpt amulet : amulets) {
      long needA = amulet == null ? need0 : SkillLevels.sub(need0, amulet.pack());

      for (WeaponOpt weapon : weapons) {
        long needW = weapon == null ? needA : SkillLevels.sub(needA, weapon.innatePack());
        int[] weaponCounts = weapon == null ? ZERO_COUNTS : weapon.counts();

        if (!bounds.fillFeasible(needW, aggregate.slotCounts(), weaponCounts)) {
          continue;
        }

        long cost = fill.fillCost(needW, aggregate.slotCounts(), weaponCounts);

        if (cost == DecoFill.INF) {
          continue;
        }

        long score =
          fixed -
          cost +
          (weapon == null ? 0 : weapon.slotValue()) +
          (amulet == null ? bonus : 0) +
          (weapon == null ? bonus : 0);

        if (!topK.accepts(score)) {
          continue;
        }

        FillResult result = fill.fillWithSteps(needW, aggregate.slotCounts(), weaponCounts);

        if (result.cost() == DecoFill.INF) {
          continue;
        }

        topK.offer(BuildReconstructor.from(aggregate, result.steps(), amulet, weapon), score);
      }
    }
  }
}
