package mhwilds.optimizer.model;

import java.util.List;
import java.util.Map;

public record Weapon(
  int gameId,
  String kind,
  String name,
  int attackRaw,
  int affinity,
  int[] slots,
  Map<Integer, Integer> skills,
  List<String> coatings,
  Map<String, Object> extraFields,
  int rarity,
  boolean gogmazios
) {
  public Weapon {
    if (kind == null || kind.isBlank()) {
      throw new IllegalArgumentException("kind must not be blank");
    }

    if (slots == null) {
      slots = new int[0];
    }

    if (skills == null) {
      skills = Map.of();
    }

    if (coatings == null) {
      coatings = List.of();
    }

    if (extraFields == null) {
      extraFields = Map.of();
    }
  }

  public Weapon(
    int gameId,
    String kind,
    String name,
    int attackRaw,
    int affinity,
    int[] slots,
    Map<Integer, Integer> skills,
    List<String> coatings,
    Map<String, Object> extraFields,
    int rarity
  ) {
    this(
      gameId,
      kind,
      name,
      attackRaw,
      affinity,
      slots,
      skills,
      coatings,
      extraFields,
      rarity,
      false
    );
  }

  public Weapon(
    int gameId,
    String kind,
    String name,
    int attackRaw,
    int affinity,
    int[] slots,
    Map<Integer, Integer> skills,
    List<String> coatings
  ) {
    this(gameId, kind, name, attackRaw, affinity, slots, skills, coatings, Map.of(), 0, false);
  }

  public Weapon(
    int gameId,
    String kind,
    String name,
    int attackRaw,
    int affinity,
    int[] slots,
    Map<Integer, Integer> skills,
    List<String> coatings,
    int rarity
  ) {
    this(gameId, kind, name, attackRaw, affinity, slots, skills, coatings, Map.of(), rarity, false);
  }

  public int slotCount() {
    return slots.length;
  }
}
