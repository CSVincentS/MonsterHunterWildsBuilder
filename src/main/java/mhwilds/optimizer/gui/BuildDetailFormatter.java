package mhwilds.optimizer.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mhwilds.optimizer.model.ArmorPiece;
import mhwilds.optimizer.model.ArmorSlot;
import mhwilds.optimizer.model.Build;
import mhwilds.optimizer.model.Skill;
import mhwilds.optimizer.model.SlotAssignment;

public final class BuildDetailFormatter {

  private BuildDetailFormatter() {}

  public static String decorationsSummary(Build build) {
    Map<String, Integer> counts = new LinkedHashMap<>();

    for (SlotAssignment sa : build.armorDecorations()) {
      String target = sa.targetPiece() != null ? sa.targetPiece().name().toLowerCase() : "weapon";
      counts.merge(sa.decoration().name() + "→" + target, 1, Integer::sum);
    }

    for (SlotAssignment sa : build.weaponDecorations()) {
      counts.merge(sa.decoration().name() + "→weapon", 1, Integer::sum);
    }

    if (counts.isEmpty()) {
      return "(none)";
    }

    List<String> parts = new ArrayList<>(counts.keySet().stream().sorted().toList());

    for (int i = 0; i < parts.size(); i++) {
      int n = counts.get(parts.get(i));
      parts.set(i, n > 1 ? parts.get(i) + " ×" + n : parts.get(i));
    }

    return String.join(", ", parts);
  }

  public static String detailText(
    Build build,
    Map<Integer, Skill> skillMap,
    Map<Integer, Map<Integer, Integer>> setSkillRanks
  ) {
    return detailText(build, BuildScore.of(build, "free_slots", 0L), skillMap, setSkillRanks);
  }

  public static String detailText(
    Build build,
    BuildScore score,
    Map<Integer, Skill> skillMap,
    Map<Integer, Map<Integer, Integer>> setSkillRanks
  ) {
    StringBuilder sb = new StringBuilder();
    sb.append(score.headline()).append('\n');

    sb.append("Armor:\n");

    for (ArmorSlot slot : ArmorSlot.values()) {
      ArmorPiece piece = build.armorPiece(slot);

      if (piece == null) {
        continue;
      }

      sb.append(String.format("  %-5s  %-21s", slot.name().toLowerCase(), piece.setName()));
      appendSlotDetails(sb, build.armorDecorations(), piece.slots(), slot);
    }

    sb.append("Amulet: ")
      .append(build.amuletRank() != null ? build.amuletRank().name() : "none")
      .append('\n');
    sb.append("Weapon:");

    if (build.weapon() != null) {
      sb.append(String.format("     %-21s", build.weapon().name()));
      appendSlotDetails(sb, build.weaponDecorations(), build.weapon().slots(), null);
    } else {
      sb.append(" none\n");
    }

    sb.append("Skills:\n");

    var skills = build
      .combinedSkills()
      .entrySet()
      .stream()
      .filter(entry -> !setSkillRanks.containsKey(entry.getKey()))
      .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
      .toList();

    if (skills.isEmpty()) {
      sb.append("  (none)\n");
    } else {
      for (var entry : skills) {
        Skill skill = skillMap.get(entry.getKey());
        String name = skill != null ? skill.name() : String.valueOf(entry.getKey());
        int level = entry.getValue();
        int max = skill != null ? skill.maxRank() : level;

        if (level > max) {
          level = max;
        }
        sb.append(String.format("  %-21s %d/%d\n", name, level, max));
      }
    }

    Map<Integer, Integer> setBonuses = build.setBonusSkills();

    if (!setBonuses.isEmpty()) {
      sb.append("Set skills:\n");

      var sorted = setBonuses
        .entrySet()
        .stream()
        .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
        .toList();

      for (var entry : sorted) {
        Skill skill = skillMap.get(entry.getKey());
        String name = skill != null ? skill.name() : String.valueOf(entry.getKey());
        int level = entry.getValue();
        int max = skill != null ? skill.maxRank() : level;

        sb.append(String.format("  %-21s %d/%d\n", name, level, max));
      }
    }
    return sb.toString();
  }

  private static void appendSlotDetails(
    StringBuilder sb,
    List<SlotAssignment> decos,
    int[] slots,
    ArmorSlot targetSlot
  ) {
    for (int i = 0; i < slots.length; i++) {
      String decoName = "—";

      for (SlotAssignment sa : decos) {
        if (sa.slotIndexWithinPiece() == i && matchesTarget(sa, targetSlot)) {
          decoName = sa.decoration().name();
          break;
        }
      }

      if (i > 0) {
        sb.append("  ");
      }

      sb.append(String.format("L%d %s", slots[i], decoName));
    }

    sb.append('\n');
  }

  private static boolean matchesTarget(SlotAssignment sa, ArmorSlot targetSlot) {
    return targetSlot == null ? sa.targetPiece() == null : sa.targetPiece() == targetSlot;
  }
}
