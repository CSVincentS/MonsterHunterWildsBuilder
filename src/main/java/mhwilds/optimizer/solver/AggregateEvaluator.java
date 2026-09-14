package mhwilds.optimizer.solver;

import java.util.List;
import mhwilds.optimizer.model.ArmorSlot;
import mhwilds.optimizer.solver.ArmorAggregates.Aggregate;
import mhwilds.optimizer.solver.DecoFill.FillResult;
import mhwilds.optimizer.solver.GearOptions.AmuletOpt;
import mhwilds.optimizer.solver.GearOptions.WeaponOpt;

/**
 * Evaluates one armor aggregate: a joint exact armor+weapon decoration fill
 * over every relevant amulet and weapon, keeping the best builds in a heap.
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
    long residual = 0;

    for (int i = 0; i < n; i++) {
      int innate = SkillLevels.levelAt(aggregate.signature().requiredLevels(), i);
      int res = required[i] - innate;

      if (res > 0) {
        if (res > bounds.residualCeiling(i, aggregate.slotCounts())) {
          return;
        }

        residual |= (long) res << (SkillLevels.BITS_PER_SKILL * i);
      }
    }

    long baseScore = aggregate.slotValue();

    if (bonus > 0) {
      baseScore += bonus * (ArmorSlot.values().length - aggregate.worn());
    }

    for (AmuletOpt amulet : amulets) {
      long afterAmulet = amulet == null ? residual : SkillLevels.sub(residual, amulet.pack());

      for (WeaponOpt weapon : weapons) {
        long afterWeapon =
          weapon == null ? afterAmulet : SkillLevels.sub(afterAmulet, weapon.innatePack());
        int[] weaponCounts = weapon == null ? ZERO_COUNTS : weapon.counts();

        if (!bounds.fillFeasible(afterWeapon, aggregate.slotCounts(), weaponCounts)) {
          continue;
        }

        long cost = fill.fillCost(afterWeapon, aggregate.slotCounts(), weaponCounts);

        if (cost == DecoFill.INF) {
          continue;
        }

        long score =
          baseScore -
          cost +
          (weapon == null ? 0 : weapon.slotValue()) +
          (amulet == null ? bonus : 0) +
          (weapon == null ? bonus : 0);

        if (!topK.accepts(score)) {
          continue;
        }

        FillResult result = fill.fillWithSteps(afterWeapon, aggregate.slotCounts(), weaponCounts);

        if (result.cost() == DecoFill.INF) {
          continue;
        }

        topK.offer(BuildReconstructor.from(aggregate, result.steps(), amulet, weapon), score);
      }
    }
  }
}
