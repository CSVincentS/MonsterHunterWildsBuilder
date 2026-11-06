package mhwilds.optimizer.solver;

import java.util.List;
import java.util.Map;
import mhwilds.optimizer.model.ArmorSlot;
import mhwilds.optimizer.model.SetBonusActivation;
import mhwilds.optimizer.solver.ArmorAggregates.Aggregate;
import mhwilds.optimizer.solver.DecoFill.FillResult;
import mhwilds.optimizer.solver.GearOptions.AmuletOpt;
import mhwilds.optimizer.solver.GearOptions.WeaponOpt;

final class AggregateEvaluator {

  private static final int[] ZERO_COUNTS = { 0, 0, 0 };

  private final int n;
  private final int[] required;
  private final long bonus;
  private final SearchBounds bounds;
  private final DecoFill fill;
  private final List<Integer> setIds;
  private final List<Map<Integer, Integer>> setRanks;
  private final int[] requiredSet;
  private final boolean gogmaziosSkills;

  AggregateEvaluator(
    int n,
    int[] required,
    long bonus,
    SearchBounds bounds,
    DecoFill fill,
    List<Integer> setIds,
    List<Map<Integer, Integer>> setRanks,
    int[] requiredSet,
    boolean gogmaziosSkills
  ) {
    this.n = n;
    this.required = required;
    this.bonus = bonus;
    this.bounds = bounds;
    this.fill = fill;
    this.setIds = setIds;
    this.setRanks = setRanks;
    this.requiredSet = requiredSet;
    this.gogmaziosSkills = gogmaziosSkills;
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
      int[] armorCounts = combinedCounts(
        aggregate.slotCounts(),
        amulet == null ? null : amulet.armorCounts()
      );
      long amuletSlotValue = amulet == null ? 0 : amulet.slotValue();

      for (WeaponOpt weapon : weapons) {
        if (gogmaziosSkills && !setBonusReached(aggregate, weapon)) {
          continue;
        }

        long afterWeapon =
          weapon == null ? afterAmulet : SkillLevels.sub(afterAmulet, weapon.innatePack());
        int[] weaponCounts = combinedCounts(
          weapon == null ? ZERO_COUNTS : weapon.counts(),
          amulet == null ? null : amulet.weaponCounts()
        );

        if (!bounds.fillFeasible(afterWeapon, armorCounts, weaponCounts)) {
          continue;
        }

        long cost = fill.fillCost(afterWeapon, armorCounts, weaponCounts);

        if (cost == DecoFill.INF) {
          continue;
        }

        long score =
          baseScore +
          amuletSlotValue -
          cost +
          (weapon == null ? 0 : weapon.slotValue() + weapon.rollBonus()) +
          (amulet == null ? bonus : 0) +
          (weapon == null ? bonus : 0);

        if (!topK.accepts(score)) {
          continue;
        }

        FillResult result = fill.fillWithSteps(afterWeapon, armorCounts, weaponCounts);

        if (result.cost() == DecoFill.INF) {
          continue;
        }

        topK.offer(BuildReconstructor.from(aggregate, result.steps(), amulet, weapon), score);
      }
    }
  }

  private static int[] combinedCounts(int[] base, int[] extra) {
    if (extra == null) {
      return base;
    }

    int[] out = base.clone();

    for (int s = 0; s < out.length && s < extra.length; s++) {
      out[s] = Math.min(15, out[s] + extra[s]);
    }

    return out;
  }

  private boolean setBonusReached(Aggregate aggregate, WeaponOpt weapon) {
    int[] setCounts = aggregate.setCounts();

    for (int j = 0; j < setIds.size(); j++) {
      int pieces = setCounts[j] + (weapon != null && weapon.contributesTo(setIds.get(j)) ? 1 : 0);

      if (SetBonusActivation.levelFor(pieces, setRanks.get(j)) < requiredSet[j]) {
        return false;
      }
    }

    return true;
  }
}
