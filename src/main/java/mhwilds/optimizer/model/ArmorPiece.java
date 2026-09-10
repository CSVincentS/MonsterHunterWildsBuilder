package mhwilds.optimizer.model;

import java.util.Map;

public record ArmorPiece(
  int gameId,
  String setName,
  ArmorSlot kind,
  int[] slots,
  Map<Integer, Integer> skills,
  int baseDefense,
  int maxDefense,
  int rarity,
  int setBonusId,
  Map<Integer, Integer> setBonusRanks,
  int groupBonusId,
  Map<Integer, Integer> groupBonusRanks
) {
  public ArmorPiece {
    if (kind == null) {
      throw new NullPointerException("kind must not be null");
    }

    if (slots == null) {
      throw new NullPointerException("slots must not be null");
    }

    if (skills == null) {
      skills = Map.of();
    }

    if (setBonusRanks == null) {
      setBonusRanks = Map.of();
    }

    if (groupBonusRanks == null) {
      groupBonusRanks = Map.of();
    }
  }

  /** Convenience constructor; rarity is set to 0 (unspecified) for fabricated/test data. */
  public ArmorPiece(
    int gameId,
    String setName,
    ArmorSlot kind,
    int[] slots,
    Map<Integer, Integer> skills,
    int baseDefense,
    int maxDefense
  ) {
    this(gameId, setName, kind, slots, skills, baseDefense, maxDefense, 0);
  }

  /** Convenience constructor with no set/group bonus skills. */
  public ArmorPiece(
    int gameId,
    String setName,
    ArmorSlot kind,
    int[] slots,
    Map<Integer, Integer> skills,
    int baseDefense,
    int maxDefense,
    int rarity
  ) {
    this(
      gameId,
      setName,
      kind,
      slots,
      skills,
      baseDefense,
      maxDefense,
      rarity,
      0,
      Map.of(),
      0,
      Map.of()
    );
  }

  public int slotCount() {
    return slots.length;
  }
}
