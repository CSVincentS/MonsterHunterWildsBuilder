package mhwilds.optimizer.gui;

public record RarityBounds(
  Integer armorMin,
  Integer armorMax,
  Integer decoMin,
  Integer decoMax,
  Integer amuletMin,
  Integer amuletMax,
  Integer weaponMin,
  Integer weaponMax
) {
  public static RarityBounds unbounded() {
    return new RarityBounds(null, null, null, null, null, null, null, null);
  }
}
