package mhwilds.optimizer.loader;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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

  public Map<String, Integer> skillNamesById() {
    Map<String, Integer> names = new HashMap<>();

    for (Skill skill : skills.values()) {
      names.put(skill.name().toLowerCase(Locale.ROOT), skill.gameId());
    }

    return names;
  }

  public Integer resolveSkillRef(String raw) {
    String key = raw.trim();

    try {
      int id = Integer.parseInt(key);

      return skills.containsKey(id) ? id : null;
    } catch (NumberFormatException e) {
      return skillNamesById().get(key.toLowerCase(Locale.ROOT));
    }
  }
}
