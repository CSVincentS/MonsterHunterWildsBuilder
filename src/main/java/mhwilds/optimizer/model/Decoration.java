package mhwilds.optimizer.model;

import java.util.Map;

public record Decoration(
  int gameId,
  String name,
  int level,
  Map<Integer, Integer> skills,
  SlotTarget allowedOn,
  int rarity
) {
  public Decoration {
    if (level < 1) {
      throw new IllegalArgumentException("level must be >= 1, got " + level);
    }

    if (skills == null) {
      skills = Map.of();
    }

    if (allowedOn == null) {
      throw new NullPointerException("allowedOn must not be null");
    }
  }

  public Decoration(
    int gameId,
    String name,
    int level,
    Map<Integer, Integer> skills,
    SlotTarget allowedOn
  ) {
    this(gameId, name, level, skills, allowedOn, 0);
  }

  public boolean fitsInSlot(int slotSize) {
    return level <= slotSize;
  }
}
