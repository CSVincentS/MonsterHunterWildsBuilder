package mhwilds.optimizer.gui;

public record SkillRequirement(String skillName, int minLevel) {
  public SkillRequirement {
    if (skillName == null || skillName.isBlank()) {
      throw new IllegalArgumentException("skillName must not be blank");
    }
    if (minLevel < 1) {
      throw new IllegalArgumentException("minLevel must be >= 1");
    }
  }
}
