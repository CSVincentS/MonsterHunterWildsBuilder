package mhwilds.optimizer.model;

public record Skill(int gameId, String name, int maxRank, SkillKind kind) {
  public Skill(int gameId, String name, int maxRank) {
    this(gameId, name, maxRank, SkillKind.UNKNOWN);
  }

  public Skill {
    if (maxRank < 1) {
      throw new IllegalArgumentException("maxRank must be >= 1, got " + maxRank);
    }

    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }

    if (kind == null) {
      kind = SkillKind.UNKNOWN;
    }
  }
}
