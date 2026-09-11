package mhwilds.optimizer.ranking;

import mhwilds.optimizer.model.Build;

/**
 * Ranks builds with equipment omission rewarded: every empty equipment slot (armor piece, weapon,
 * or amulet) is worth {@code bonus} points, so a build that needs fewer pieces can score as high as
 * or higher than one carrying a free level-3 decoration slot.
 */
public class FreeEquipmentSlotRanking implements RankingStrategy {

  private final long bonus;

  public FreeEquipmentSlotRanking(long bonus) {
    this.bonus = Math.max(0, bonus);
  }

  @Override
  public int compare(Build a, Build b) {
    int byScore = Long.compare(b.equipmentAwareScore(bonus), a.equipmentAwareScore(bonus));

    if (byScore != 0) {
      return byScore;
    }

    return Integer.compare(b.totalDefense(), a.totalDefense());
  }
}
