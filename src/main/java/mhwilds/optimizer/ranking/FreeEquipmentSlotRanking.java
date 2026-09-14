package mhwilds.optimizer.ranking;

import mhwilds.optimizer.model.Build;

public class FreeEquipmentSlotRanking extends ScoreRanking {

  private final long bonus;

  public FreeEquipmentSlotRanking(long bonus) {
    this.bonus = Math.max(0, bonus);
  }

  @Override
  long scoreOf(Build build) {
    return build.equipmentAwareScore(bonus);
  }
}
