package kz.nurgissa.kasestockexchangeparser.model.dtos;

public record SubscriberStatsDto(
        long newBondsCount,
        long discountsCount,
        long whalesCount,
        long couponsCount,
        long stocksCount,
        long totalUsers
) {
    public static SubscriberStatsDto empty() {
        return new SubscriberStatsDto(0, 0, 0, 0, 0, 0);
    }
}
