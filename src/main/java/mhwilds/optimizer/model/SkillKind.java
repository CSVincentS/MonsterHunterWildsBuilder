package mhwilds.optimizer.model;

import java.util.Locale;

public enum SkillKind {
  ARMOR,
  GROUP,
  SET,
  WEAPON,
  UNKNOWN;

  public static SkillKind fromString(String s) {
    if (s == null || s.isBlank()) {
      return UNKNOWN;
    }

    return switch (s.toLowerCase(Locale.ROOT)) {
      case "armor" -> ARMOR;
      case "group" -> GROUP;
      case "set" -> SET;
      case "weapon" -> WEAPON;
      default -> UNKNOWN;
    };
  }
}
