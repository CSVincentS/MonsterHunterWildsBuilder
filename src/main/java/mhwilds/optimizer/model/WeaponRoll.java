package mhwilds.optimizer.model;

import java.util.Map;

public record WeaponRoll(
  boolean enabled,
  int setBonusSkillId,
  Map<Integer, Integer> setBonusRanks,
  int groupSkillId,
  Map<Integer, Integer> groupBonusRanks
) {
  public static final long EMPTY_SET_BONUS_POINTS = 3000L;

  public static final long EMPTY_GROUP_POINTS = 1500L;

  public static final WeaponRoll NONE = new WeaponRoll(false, 0, Map.of(), 0, Map.of());

  public WeaponRoll {
    if (setBonusRanks == null) {
      setBonusRanks = Map.of();
    }

    if (groupBonusRanks == null) {
      groupBonusRanks = Map.of();
    }
  }

  public WeaponRoll(
    int setBonusSkillId,
    Map<Integer, Integer> setBonusRanks,
    int groupSkillId,
    Map<Integer, Integer> groupBonusRanks
  ) {
    this(true, setBonusSkillId, setBonusRanks, groupSkillId, groupBonusRanks);
  }

  public static WeaponRoll unused() {
    return new WeaponRoll(true, 0, Map.of(), 0, Map.of());
  }

  public static WeaponRoll of(
    int setBonusSkillId,
    Map<Integer, Integer> setBonusRanks,
    int groupSkillId,
    Map<Integer, Integer> groupBonusRanks
  ) {
    return new WeaponRoll(true, setBonusSkillId, setBonusRanks, groupSkillId, groupBonusRanks);
  }

  public boolean setBonusEmpty() {
    return enabled && setBonusSkillId == 0;
  }

  public boolean groupEmpty() {
    return enabled && groupSkillId == 0;
  }

  public long emptyBonus() {
    if (!enabled) {
      return 0L;
    }

    return (
      (setBonusSkillId == 0 ? EMPTY_SET_BONUS_POINTS : 0L) +
      (groupSkillId == 0 ? EMPTY_GROUP_POINTS : 0L)
    );
  }

  public boolean contributesTo(int skillId) {
    return enabled && skillId != 0 && (skillId == setBonusSkillId || skillId == groupSkillId);
  }
}
