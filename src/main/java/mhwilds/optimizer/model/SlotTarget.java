package mhwilds.optimizer.model;

public enum SlotTarget {
  ARMOR,
  WEAPON;

  public static SlotTarget fromString(String s) {
    return switch (s.toLowerCase()) {
      case "armor" -> ARMOR;
      case "weapon" -> WEAPON;
      default -> throw new IllegalArgumentException("Unknown slot target: " + s);
    };
  }
}
