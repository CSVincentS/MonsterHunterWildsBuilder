package mhwilds.optimizer.loader;

import java.util.List;
import java.util.Map;
import mhwilds.optimizer.model.AmuletRank;
import mhwilds.optimizer.model.ArmorPiece;
import mhwilds.optimizer.model.Decoration;
import mhwilds.optimizer.model.Skill;
import mhwilds.optimizer.model.Weapon;

public record GameData(
  Map<Integer, Skill> skills,
  List<ArmorPiece> armorPieces,
  List<Decoration> decorations,
  List<AmuletRank> amuletRanks,
  List<Weapon> weapons,
  Map<Integer, Map<Integer, Integer>> setSkillRanks
) {
  public GameData {
    if (skills == null) {
      throw new NullPointerException("skills must not be null");
    }

    if (armorPieces == null) {
      throw new NullPointerException("armorPieces must not be null");
    }

    if (decorations == null) {
      throw new NullPointerException("decorations must not be null");
    }

    if (amuletRanks == null) {
      throw new NullPointerException("amuletRanks must not be null");
    }

    if (weapons == null) {
      throw new NullPointerException("weapons must not be null");
    }

    if (setSkillRanks == null) {
      setSkillRanks = Map.of();
    }
  }
}
