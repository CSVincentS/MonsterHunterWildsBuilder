package mhwilds.optimizer.ranking;

public class RankingFactory {

  public static final String FREE_SLOTS = "free_slots";
  public static final String FREE_EQUIPMENT_SLOTS = "free_equipment_slots";

  public static RankingStrategy create(String name) {
    return create(name, 0L);
  }

  /**
   * Creates the named ranking. When the strategy is {@link #FREE_EQUIPMENT_SLOTS}, {@code bonus}
   * is the point value of each omitted equipment slot; other strategies ignore it.
   */
  public static RankingStrategy create(String name, long bonus) {
    return switch (name) {
      case FREE_SLOTS -> new FreeSlotRanking();
      case FREE_EQUIPMENT_SLOTS -> new FreeEquipmentSlotRanking(bonus);
      default -> throw new IllegalArgumentException("Unknown ranking strategy: " + name);
    };
  }

  /** True when the strategy scores on free equipment slots (omitted gear) as well as deco slots. */
  public static boolean isEquipmentSlotRanking(String name) {
    return FREE_EQUIPMENT_SLOTS.equals(name);
  }
}
