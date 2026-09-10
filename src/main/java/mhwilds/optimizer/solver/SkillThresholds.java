package mhwilds.optimizer.solver;

import java.util.Map;

public record SkillThresholds(
  Map<Integer, Integer> requiredSkills,
  Map<Integer, Map<Integer, Integer>> setSkillRanks
) {
  public SkillThresholds {
    if (requiredSkills == null) {
      requiredSkills = Map.of();
    }

    if (setSkillRanks == null) {
      setSkillRanks = Map.of();
    }
  }

  /** Convenience constructor; no set-skill requirements (the common case). */
  public SkillThresholds(Map<Integer, Integer> requiredSkills) {
    this(requiredSkills, Map.of());
  }

  public int required(int skillId) {
    return requiredSkills().getOrDefault(skillId, 0);
  }

  /** True when this requirement must be met by activating a set/group bonus. */
  public boolean isSetSkill(int skillId) {
    return setSkillRanks().containsKey(skillId);
  }

  /** Activation thresholds for a set-skill requirement: {@code {piecesRequired: skillLevel}}. */
  public Map<Integer, Integer> setSkillRanksOf(int skillId) {
    return setSkillRanks().getOrDefault(skillId, Map.of());
  }
}
