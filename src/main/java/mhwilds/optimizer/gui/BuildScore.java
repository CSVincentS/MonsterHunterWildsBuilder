package mhwilds.optimizer.gui;

import mhwilds.optimizer.model.Build;
import mhwilds.optimizer.ranking.RankingFactory;

public record BuildScore(
  long total,
  long freeSlots,
  int omittedEquipment,
  long bonus,
  boolean equipmentAware
) {
  public static BuildScore of(Build build, String ranking, long bonus) {
    if (RankingFactory.isEquipmentSlotRanking(ranking)) {
      return new BuildScore(
        build.equipmentAwareScore(bonus),
        build.freeSlotScore(),
        build.omittedEquipmentCount(),
        bonus,
        true
      );
    }

    return new BuildScore(build.freeSlotScore(), build.freeSlotScore(), 0, 0L, false);
  }

  public String headline() {
    if (!equipmentAware) {
      return "Free-slot score: " + total;
    }

    return String.format(
      "Equipment-slot score: %d  (free slots %d, %d omitted × %d bonus)",
      total,
      freeSlots,
      omittedEquipment,
      bonus
    );
  }
}
