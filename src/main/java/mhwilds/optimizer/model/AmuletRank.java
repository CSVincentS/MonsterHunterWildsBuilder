package mhwilds.optimizer.model;

import java.util.Map;

public record AmuletRank(
  int familyGameId,
  int rankLevel,
  String name,
  Map<Integer, Integer> skills,
  int rarity
) {
  public AmuletRank {
    if (rankLevel < 1) {
      throw new IllegalArgumentException("rankLevel must be >= 1");
    }

    if (skills == null) {
      skills = Map.of();
    }
  }

  /** Convenience constructor; rarity is set to 0 (unspecified) for fabricated/test data. */
  public AmuletRank(int familyGameId, int rankLevel, String name, Map<Integer, Integer> skills) {
    this(familyGameId, rankLevel, name, skills, 0);
  }
}
