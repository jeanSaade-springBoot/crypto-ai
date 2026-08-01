package com.crypto.service;

import com.crypto.domain.MarketFundamental;
import com.crypto.dto.FundamentalComponentScore;
import com.crypto.dto.FundamentalScoreResult;
import com.crypto.dto.FundamentalOwnershipDetails;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class FundamentalScoringService {

    public static final int MAXIMUM_SCORE = 10;
    private static final int COMPONENT_MAXIMUM = 2;

    private static final BigDecimal FIVE_HUNDRED_MILLION = new BigDecimal("500000000");
    private static final BigDecimal TWENTY_BILLION = new BigDecimal("20000000000");

    public FundamentalScoreResult score(MarketFundamental fundamental) {
        if (fundamental == null) {
            return neutralResult("No recent fundamental record");
        }

        FundamentalComponentScore marketCap = marketCapScore(fundamental.getMarketCap());
        FundamentalComponentScore fdv = fdvScore(
                fundamental.getMarketCap(),
                fundamental.getFullyDilutedValuation()
        );
        FundamentalComponentScore turnover = volumeMarketCapScore(
                fundamental.getVolume24h(),
                fundamental.getMarketCap()
        );
        FundamentalComponentScore supply = supplyScore(
                fundamental.getCirculatingSupply(),
                fundamental.getMaxSupply(),
                fundamental.getTotalSupply()
        );
        FundamentalComponentScore liquidity = listingLiquidityScore(
                fundamental.getTier1ExchangeCount(),
                fundamental.getExchangeCount(),
                fundamental.getVolume24h(),
                fundamental.getMarketCap()
        );

        List<FundamentalComponentScore> components = List.of(
                marketCap,
                fdv,
                turnover,
                supply,
                liquidity
        );
        int total = components.stream().mapToInt(FundamentalComponentScore::score).sum();

        return new FundamentalScoreResult(
                total,
                MAXIMUM_SCORE,
                components,
                riskLevel(fdv.score(), supply.score(), liquidity.score(), marketCap.score()),
                ownershipDetails(fundamental)
        );
    }

    private FundamentalComponentScore marketCapScore(BigDecimal marketCap) {
        if (!positive(marketCap)) {
            return neutral("MARKET_CAP", "Market Cap", "Market-cap data unavailable");
        }

        int score;
        String status;
        if (marketCap.compareTo(TWENTY_BILLION) >= 0) {
            score = 2;
            status = "Large established market capitalization";
        } else if (marketCap.compareTo(FIVE_HUNDRED_MILLION) >= 0) {
            score = 1;
            status = "Mid or small-cap project with higher volatility risk";
        } else {
            score = 0;
            status = "Micro-cap project with elevated liquidity and volatility risk";
        }

        return component("MARKET_CAP", "Market Cap", score, marketCap,
                moneyMetric(marketCap), status);
    }

    private FundamentalComponentScore fdvScore(BigDecimal marketCap, BigDecimal fdv) {
        if (!positive(marketCap) || !positive(fdv)) {
            return neutral("FDV_RATIO", "FDV Ratio", "Market-cap or FDV data unavailable");
        }

        BigDecimal ratio = marketCap.divide(fdv, 8, RoundingMode.HALF_UP);
        int score;
        String status;
        if (ratio.compareTo(new BigDecimal("0.90")) >= 0) {
            score = 2;
            status = "Healthy valuation: little dilution remains between market cap and FDV";
        } else if (ratio.compareTo(new BigDecimal("0.60")) >= 0) {
            score = 1;
            status = "Moderate future dilution risk";
        } else {
            score = 0;
            status = "High future dilution risk: FDV is much larger than market cap";
        }

        return component("FDV_RATIO", "FDV Ratio", score, ratio,
                percentMetric(ratio), status);
    }

    private FundamentalComponentScore volumeMarketCapScore(BigDecimal volume24h, BigDecimal marketCap) {
        if (!positive(volume24h) || !positive(marketCap)) {
            return neutral("VOLUME_MARKET_CAP", "Volume / Market Cap",
                    "24-hour volume or market-cap data unavailable");
        }

        BigDecimal ratio = volume24h.divide(marketCap, 8, RoundingMode.HALF_UP);
        int score;
        String status;
        if (ratio.compareTo(new BigDecimal("0.10")) >= 0) {
            score = 2;
            status = "Strong trading activity relative to project size";
        } else if (ratio.compareTo(new BigDecimal("0.03")) >= 0) {
            score = 1;
            status = "Moderate trading activity";
        } else {
            score = 0;
            status = "Weak turnover; entry and exit liquidity may be limited";
        }

        return component("VOLUME_MARKET_CAP", "Volume / Market Cap", score, ratio,
                percentMetric(ratio), status);
    }

    private FundamentalComponentScore supplyScore(
            BigDecimal circulatingSupply,
            BigDecimal maxSupply,
            BigDecimal totalSupply
    ) {
        BigDecimal referenceSupply = positive(maxSupply) ? maxSupply : totalSupply;
        if (!positive(circulatingSupply) || !positive(referenceSupply)) {
            return neutral("SUPPLY_DISTRIBUTION", "Supply Distribution",
                    "Circulating and maximum/total supply data unavailable");
        }

        BigDecimal ratio = circulatingSupply.divide(referenceSupply, 8, RoundingMode.HALF_UP);
        int score;
        String status;
        if (ratio.compareTo(new BigDecimal("0.80")) >= 0) {
            score = 2;
            status = "Most supply is already circulating; lower unlock pressure";
        } else if (ratio.compareTo(new BigDecimal("0.50")) >= 0) {
            score = 1;
            status = "Moderate supply remains outside circulation";
        } else {
            score = 0;
            status = "Large future unlock potential and dilution risk";
        }

        String source = positive(maxSupply) ? "of max supply" : "of total supply";
        return component("SUPPLY_DISTRIBUTION", "Supply Distribution", score, ratio,
                percentMetric(ratio) + " " + source, status);
    }

    private FundamentalComponentScore listingLiquidityScore(
            Integer tier1ExchangeCount,
            Integer exchangeCount,
            BigDecimal volume24h,
            BigDecimal marketCap
    ) {
        if (tier1ExchangeCount == null && exchangeCount == null) {
            return neutral("LISTING_LIQUIDITY", "Listing & Liquidity",
                    "Exchange-listing coverage is unavailable");
        }

        int tier1 = tier1ExchangeCount == null ? 0 : Math.max(0, tier1ExchangeCount);
        int total = exchangeCount == null ? tier1 : Math.max(0, exchangeCount);
        BigDecimal turnover = positive(volume24h) && positive(marketCap)
                ? volume24h.divide(marketCap, 8, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        int score;
        String status;
        if (tier1 >= 2 || (tier1 >= 1 && turnover.compareTo(new BigDecimal("0.10")) >= 0)) {
            score = 2;
            status = "Strong exchange coverage and accessible liquidity";
        } else if (tier1 >= 1 || total >= 3 || turnover.compareTo(new BigDecimal("0.03")) >= 0) {
            score = 1;
            status = "Acceptable but not broad exchange liquidity";
        } else {
            score = 0;
            status = "Limited exchange coverage and liquidity risk";
        }

        return component("LISTING_LIQUIDITY", "Listing & Liquidity", score,
                BigDecimal.valueOf(tier1),
                tier1 + " Tier-1 / " + total + " total exchanges", status);
    }

    private FundamentalScoreResult neutralResult(String reason) {
        List<FundamentalComponentScore> components = List.of(
                neutral("MARKET_CAP", "Market Cap", reason),
                neutral("FDV_RATIO", "FDV Ratio", reason),
                neutral("VOLUME_MARKET_CAP", "Volume / Market Cap", reason),
                neutral("SUPPLY_DISTRIBUTION", "Supply Distribution", reason),
                neutral("LISTING_LIQUIDITY", "Listing & Liquidity", reason)
        );
        return new FundamentalScoreResult(
                5,
                MAXIMUM_SCORE,
                components,
                "UNKNOWN",
                emptyOwnership("Ownership allocation data unavailable")
        );
    }


    private FundamentalOwnershipDetails ownershipDetails(MarketFundamental fundamental) {
        BigDecimal referenceSupply = positive(fundamental.getMaxSupply())
                ? fundamental.getMaxSupply()
                : fundamental.getTotalSupply();
        String referenceLabel = positive(fundamental.getMaxSupply())
                ? "Max supply"
                : "Total supply";

        BigDecimal circulating = safePositive(fundamental.getCirculatingSupply());
        BigDecimal team = safePositive(fundamental.getTeamSupply());
        BigDecimal treasury = safePositive(fundamental.getTreasurySupply());
        BigDecimal privateInvestors = safePositive(fundamental.getPrivateInvestorSupply());
        BigDecimal locked = safePositive(fundamental.getLockedSupply());
        BigDecimal knownControlled = team.add(treasury).add(privateInvestors);

        if (!positive(referenceSupply)) {
            return new FundamentalOwnershipDetails(
                    circulating, null, null, team, treasury, privateInvestors, locked,
                    knownControlled, null, null, "Unavailable",
                    "Supply reference unavailable; company ownership cannot be estimated"
            );
        }

        BigDecimal nonCirculating = referenceSupply.subtract(circulating).max(BigDecimal.ZERO);
        BigDecimal publicRatio = circulating.divide(referenceSupply, 8, RoundingMode.HALF_UP);
        BigDecimal controlledRatio = knownControlled.divide(referenceSupply, 8, RoundingMode.HALF_UP);

        String status;
        if (knownControlled.signum() == 0) {
            status = "Company/team allocation was not provided; non-circulating supply must not be assumed company-owned";
        } else if (controlledRatio.compareTo(new BigDecimal("0.20")) >= 0) {
            status = "High known insider/company concentration";
        } else if (controlledRatio.compareTo(new BigDecimal("0.10")) >= 0) {
            status = "Moderate known insider/company concentration";
        } else {
            status = "Low known insider/company concentration";
        }

        return new FundamentalOwnershipDetails(
                circulating, referenceSupply, nonCirculating, team, treasury,
                privateInvestors, locked, knownControlled, publicRatio,
                controlledRatio, referenceLabel, status
        );
    }

    private FundamentalOwnershipDetails emptyOwnership(String status) {
        return new FundamentalOwnershipDetails(
                null, null, null, null, null, null, null, null,
                null, null, "Unavailable", status
        );
    }

    private BigDecimal safePositive(BigDecimal value) {
        return positive(value) ? value : BigDecimal.ZERO;
    }

    private FundamentalComponentScore neutral(String code, String label, String status) {
        return component(code, label, 1, null, "Data unavailable — neutral score", status);
    }

    private FundamentalComponentScore component(
            String code,
            String label,
            int score,
            BigDecimal value,
            String metric,
            String status
    ) {
        return new FundamentalComponentScore(
                code,
                label,
                score,
                COMPONENT_MAXIMUM,
                value,
                metric,
                status
        );
    }

    private String riskLevel(int fdv, int supply, int liquidity, int marketCap) {
        int riskProtection = fdv + supply + liquidity + marketCap;
        if (riskProtection >= 7) return "LOW";
        if (riskProtection >= 4) return "MEDIUM";
        return "HIGH";
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private String percentMetric(BigDecimal ratio) {
        return ratio.multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP)
                .toPlainString() + "%";
    }

    private String moneyMetric(BigDecimal amount) {
        return "$" + amount.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }
}
