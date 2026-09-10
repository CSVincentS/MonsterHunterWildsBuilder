package mhwilds.optimizer.ranking;

public class RankingFactory {

  public static RankingStrategy create(String name) {
    return switch (name) {
      case "free_slots" -> new FreeSlotRanking();
      default -> throw new IllegalArgumentException("Unknown ranking strategy: " + name);
    };
  }
}
