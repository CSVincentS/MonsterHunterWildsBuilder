package mhwilds.optimizer.solver;

import java.util.Map;
import java.util.Set;

public record GogmaziosWeapons(
  boolean enabled,
  Set<Integer> setBonusSkillIds,
  Set<Integer> groupBonusSkillIds,
  Map<Integer, Map<Integer, Integer>> skillRanks
) {
  public static final GogmaziosWeapons DISABLED = new GogmaziosWeapons(
    false,
    Set.of(),
    Set.of(),
    Map.of()
  );

  public GogmaziosWeapons {
    if (setBonusSkillIds == null) {
      setBonusSkillIds = Set.of();
    }

    if (groupBonusSkillIds == null) {
      groupBonusSkillIds = Set.of();
    }

    if (skillRanks == null) {
      skillRanks = Map.of();
    }
  }

  public static GogmaziosWeapons enabled(
    Set<Integer> setBonusSkillIds,
    Set<Integer> groupBonusSkillIds,
    Map<Integer, Map<Integer, Integer>> skillRanks
  ) {
    return new GogmaziosWeapons(true, setBonusSkillIds, groupBonusSkillIds, skillRanks);
  }
}
