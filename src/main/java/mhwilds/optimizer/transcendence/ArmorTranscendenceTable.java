package mhwilds.optimizer.transcendence;

import java.util.List;
import java.util.Map;
import mhwilds.optimizer.model.ArmorPiece;

public record ArmorTranscendenceTable(Map<Integer, ArmorTranscendence> byRarity) {
  private static final Map<Integer, Integer> UPGRADED_SLOTS_BY_RARITY = Map.of(
    5,
    3,
    6,
    2,
    7,
    0,
    8,
    0
  );

  public ArmorTranscendenceTable {
    byRarity = byRarity == null ? Map.of() : Map.copyOf(byRarity);
  }

  public static ArmorTranscendenceTable empty() {
    return new ArmorTranscendenceTable(Map.of());
  }

  public static int upgradedSlotsForRarity(int rarity) {
    return UPGRADED_SLOTS_BY_RARITY.getOrDefault(rarity, 0);
  }

  public ArmorTranscendence forRarity(int rarity) {
    return byRarity.get(rarity);
  }

  public List<ArmorPiece> transcend(List<ArmorPiece> pieces) {
    return pieces.stream().map(this::transcend).toList();
  }

  public ArmorPiece transcend(ArmorPiece piece) {
    ArmorTranscendence rule = byRarity.get(piece.rarity());

    if (rule == null || !rule.supported()) {
      return piece;
    }

    return piece.withSlotsAndMaxDefense(
      rule.transcendSlots(piece.slots()),
      rule.transcendedDefense(piece.baseDefense())
    );
  }
}
