package mhwilds.optimizer.model;

public enum ArmorSlot {
  HEAD,
  CHEST,
  ARMS,
  WAIST,
  LEGS;

  public static ArmorSlot fromString(String s) {
    return switch (s.toLowerCase()) {
      case "head" -> HEAD;
      case "chest" -> CHEST;
      case "arms" -> ARMS;
      case "waist" -> WAIST;
      case "legs" -> LEGS;
      default -> throw new IllegalArgumentException("Unknown armor slot: " + s);
    };
  }
}
