package mhwilds.optimizer.gui;

import java.util.List;
import java.util.Map;
import mhwilds.optimizer.model.ArmorPiece;
import mhwilds.optimizer.model.ArmorSlot;
import mhwilds.optimizer.model.Build;
import mhwilds.optimizer.model.Skill;
import mhwilds.optimizer.model.SlotAssignment;
import mhwilds.optimizer.model.WeaponRoll;

public final class BuildDetailFormatter {

  private BuildDetailFormatter() {}

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

    sb.append("Amulet: ").append(build.amuletRank() != null ? build.amuletRank().name() : "none");

    if (build.amuletRank() != null) {
      appendAmuletSlots(sb, build);
    } else {
      sb.append('\n');
    }

    sb.append("Weapon:");

    if (build.weapon() != null) {
      sb.append(String.format("     %-21s", build.weapon().name()));
      appendSlotDetails(sb, build.weaponDecorations(), build.weapon().slots(), null);
    } else {
      sb.append(" none\n");
    }

    if (build.weapon() != null && build.weapon().gogmazios() && build.weaponRoll().enabled()) {
      WeaponRoll roll = build.weaponRoll();
      sb.append(
        String.format(
          "  Rolls: set=%s group=%s%n",
          roll.setBonusEmpty() ? "unused" : skillName(skillMap, roll.setBonusSkillId()),
          roll.groupEmpty() ? "unused" : skillName(skillMap, roll.groupSkillId())
        )
      );
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

  private static String skillName(Map<Integer, Skill> skillMap, int skillId) {
    Skill skill = skillMap.get(skillId);

    return skill != null ? skill.name() : String.valueOf(skillId);
  }

  private static void appendAmuletSlots(StringBuilder sb, Build build) {
    int[] armorSlots = build.amuletRank().armorSlots();
    int[] weaponSlots = build.amuletRank().weaponSlots();

    if (armorSlots.length == 0 && weaponSlots.length == 0) {
      sb.append('\n');

      return;
    }

    sb.append("  ");

    boolean first = true;

    for (int i = 0; i < armorSlots.length; i++) {
      if (!first) {
        sb.append("  ");
      }

      first = false;
      sb.append(String.format("L%d %s", armorSlots[i], amuletDecoName(build, true, i)));
    }

    for (int i = 0; i < weaponSlots.length; i++) {
      if (!first) {
        sb.append("  ");
      }

      first = false;
      sb.append(String.format("W%d %s", weaponSlots[i], amuletDecoName(build, false, i)));
    }

    sb.append('\n');
  }

  private static String amuletDecoName(Build build, boolean armor, int slotIndex) {
    List<SlotAssignment> assignments = armor
      ? build.amuletArmorDecorations()
      : build.amuletWeaponDecorations();

    for (SlotAssignment sa : assignments) {
      if (sa.slotIndexWithinPiece() == slotIndex) {
        return sa.decoration().name();
      }
    }

    return "—";
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
