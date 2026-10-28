import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Turns a bin-list CSV export into the Bangladeshi card dataset under data/card-bins/.
 *
 * <p>Usage: {@code java tools/BinExport.java bin-list-data.csv data/card-bins}
 *
 * <p>Input columns (bin-list-data): BIN, Brand, Type, Category, Issuer, IssuerPhone, IssuerUrl,
 * isoCode2, isoCode3, CountryName. Only rows whose country is Bangladesh are kept. Issuer names in
 * the source are inconsistent ("BRAC BANK", "BRAC BANK PLC.", "BRAC BANK PLC"), so they are folded
 * onto one canonical bank name before grouping. Nothing here guesses a BIN: a card is written only
 * when at least one real BIN row maps to it.
 */
public final class BinExport {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: BinExport <bin-list-data.csv> <output-dir>");
            System.exit(2);
        }
        List<Map<String, String>> rows = readCsv(Paths.get(args[0]));
        Path outDir = Paths.get(args[1]);
        Files.createDirectories(outDir.resolve("banks"));

        List<Bin> bins = new ArrayList<>();
        for (Map<String, String> row : rows) {
            if (!"BANGLADESH".equalsIgnoreCase(row.getOrDefault("CountryName", "").trim())) continue;
            String bin = row.getOrDefault("BIN", "").trim();
            if (!bin.matches("\\d{6}")) continue;
            String bank = BankNames.canonical(row.getOrDefault("Issuer", ""));
            if (bank == null) continue;
            bins.add(new Bin(
                    bin,
                    bank,
                    BrandNames.canonical(row.getOrDefault("Brand", "")),
                    TypeNames.canonical(row.getOrDefault("Type", "")),
                    TierNames.canonical(row.getOrDefault("Category", ""))
            ));
        }
        bins.sort(Comparator.comparing((Bin b) -> b.bank).thenComparing(b -> b.bin));

        // One entry per distinct bank + brand + type + tier; the BINs collected underneath it.
        Map<String, Card> cards = new LinkedHashMap<>();
        for (Bin bin : bins) {
            String key = bin.bank + "|" + bin.brand + "|" + bin.type + "|" + bin.tier;
            cards.computeIfAbsent(key, ignored -> new Card(
                    bin.bank, bin.brand, bin.type, bin.tier, cardName(bin)
            )).bins.add(bin.bin);
        }

        Map<String, List<Card>> byBank = new TreeMap<>();
        for (Card card : cards.values()) {
            byBank.computeIfAbsent(card.bank, ignored -> new ArrayList<>()).add(card);
        }
        for (List<Card> list : byBank.values()) {
            list.sort(Comparator.comparing((Card c) -> c.brand)
                    .thenComparing(c -> c.type)
                    .thenComparing(c -> c.tier));
        }

        writeBinsJson(outDir.resolve("bins.json"), bins);
        writeCardsCsv(outDir.resolve("cards.csv"), cards.values());
        writeBanks(outDir, byBank);
        writeSummary(outDir.resolve("summary.json"), bins, byBank);

        System.out.printf(Locale.US, "%d BINs, %d cards, %d banks -> %s%n",
                bins.size(), cards.size(), byBank.size(), outDir.toAbsolutePath());
    }

    /** "BRAC Bank VISA Platinum Credit Card" — the name a person would recognise. */
    private static String cardName(Bin bin) {
        StringBuilder name = new StringBuilder(bin.bank).append(' ');
        if (!bin.brand.equals("Local brand") && !bin.brand.equals("Private label")) {
            name.append(bin.brand).append(' ');
        }
        if (!bin.tier.equals("Standard")) name.append(bin.tier).append(' ');
        name.append(bin.type);
        if (!bin.type.endsWith("Card")) name.append(" Card");
        return name.toString();
    }

    private static void writeBinsJson(Path path, List<Bin> bins) throws IOException {
        try (PrintWriter out = writer(path)) {
            out.println("[");
            for (int i = 0; i < bins.size(); i++) {
                Bin bin = bins.get(i);
                out.printf(Locale.US,
                        "  {\"bin\":\"%s\",\"bank\":\"%s\",\"brand\":\"%s\",\"type\":\"%s\","
                                + "\"tier\":\"%s\",\"card\":\"%s\"}%s%n",
                        bin.bin, esc(bin.bank), esc(bin.brand), esc(bin.type), esc(bin.tier),
                        esc(cardName(bin)), i + 1 < bins.size() ? "," : "");
            }
            out.println("]");
        }
    }

    private static void writeCardsCsv(Path path, Iterable<Card> cards) throws IOException {
        try (PrintWriter out = writer(path)) {
            out.println("bank,card,brand,type,tier,bins,best_for");
            for (Card card : cards) {
                out.printf(Locale.US, "%s,%s,%s,%s,%s,%s,%s%n",
                        csv(card.bank), csv(card.name), csv(card.brand), csv(card.type),
                        csv(card.tier), csv(String.join(" ", card.bins)),
                        csv(bestFor(card)));
            }
        }
    }

    /** A hint for card-offer matching: which buffet deals a card tier usually unlocks. */
    private static String bestFor(Card card) {
        String tier = card.tier.toLowerCase(Locale.ROOT);
        if (card.type.equals("Credit")) {
            if (tier.contains("signature") || tier.contains("world elite")
                    || tier.contains("infinite")) {
                return "premium dining and BOGO buffet offers";
            }
            if (tier.contains("platinum") || tier.contains("world") || tier.contains("titanium")) {
                return "mid-tier dining offers";
            }
            return "entry-level dining offers";
        }
        if (card.type.equals("Debit")) return "debit-card dining offers";
        return "prepaid / travel";
    }

    private static void writeBanks(Path outDir, Map<String, List<Card>> byBank) throws IOException {
        for (Map.Entry<String, List<Card>> entry : byBank.entrySet()) {
            Path path = outDir.resolve("banks").resolve(slug(entry.getKey()) + ".json");
            try (PrintWriter out = writer(path)) {
                out.println("{");
                out.printf(Locale.US, "  \"bank\": \"%s\",%n", esc(entry.getKey()));
                out.println("  \"cards\": [");
                List<Card> list = entry.getValue();
                for (int i = 0; i < list.size(); i++) {
                    Card card = list.get(i);
                    out.println("    {");
                    out.printf(Locale.US, "      \"name\": \"%s\",%n", esc(card.name));
                    out.printf(Locale.US, "      \"brand\": \"%s\",%n", esc(card.brand));
                    out.printf(Locale.US, "      \"type\": \"%s\",%n", esc(card.type));
                    out.printf(Locale.US, "      \"tier\": \"%s\",%n", esc(card.tier));
                    out.printf(Locale.US, "      \"bestFor\": \"%s\",%n", esc(bestFor(card)));
                    out.printf(Locale.US, "      \"bins\": [%s]%n", quoted(card.bins));
                    out.println("    }" + (i + 1 < list.size() ? "," : ""));
                }
                out.println("  ]");
                out.println("}");
            }
        }
    }

    private static void writeSummary(Path path, List<Bin> bins, Map<String, List<Card>> byBank)
            throws IOException {
        try (PrintWriter out = writer(path)) {
            out.println("{");
            out.printf(Locale.US, "  \"bins\": %d,%n", bins.size());
            out.printf(Locale.US, "  \"banks\": %d,%n", byBank.size());
            out.printf(Locale.US, "  \"countries\": [\"BD\"],%n");
            out.println("  \"byBrand\": {");
            Map<String, Integer> brands = new TreeMap<>();
            for (Bin bin : bins) brands.merge(bin.brand, 1, Integer::sum);
            List<Map.Entry<String, Integer>> brandRows = new ArrayList<>(brands.entrySet());
            for (int i = 0; i < brandRows.size(); i++) {
                out.printf(Locale.US, "    \"%s\": %d%s%n", esc(brandRows.get(i).getKey()),
                        brandRows.get(i).getValue(), i + 1 < brandRows.size() ? "," : "");
            }
            out.println("  },");
            out.println("  \"byType\": {");
            Map<String, Integer> types = new TreeMap<>();
            for (Bin bin : bins) types.merge(bin.type, 1, Integer::sum);
            List<Map.Entry<String, Integer>> typeRows = new ArrayList<>(types.entrySet());
            for (int i = 0; i < typeRows.size(); i++) {
                out.printf(Locale.US, "    \"%s\": %d%s%n", esc(typeRows.get(i).getKey()),
                        typeRows.get(i).getValue(), i + 1 < typeRows.size() ? "," : "");
            }
            out.println("  }");
            out.println("}");
        }
    }

    // ---------------------------------------------------------------- helpers

    private static PrintWriter writer(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
        return new PrintWriter(Files.newBufferedWriter(path, StandardCharsets.UTF_8));
    }

    private static String quoted(Iterable<String> values) {
        StringBuilder text = new StringBuilder();
        for (String value : values) {
            if (text.length() > 0) text.append(", ");
            text.append('"').append(value).append('"');
        }
        return text.toString();
    }

    private static String esc(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String csv(String text) {
        if (text.contains(",") || text.contains("\"")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private static String slug(String name) {
        return name.toLowerCase(Locale.ROOT)
                .replace("&", "and")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    /** Minimal RFC 4180 reader: quoted fields, doubled quotes, commas and newlines inside quotes. */
    private static List<Map<String, String>> readCsv(Path path) throws IOException {
        String text = Files.readString(path, StandardCharsets.UTF_8);
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                record.add(field.toString());
                field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                record.add(field.toString());
                field.setLength(0);
                if (!record.isEmpty()) records.add(record);
                record = new ArrayList<>();
            } else {
                field.append(c);
            }
        }
        if (field.length() > 0 || !record.isEmpty()) {
            record.add(field.toString());
            records.add(record);
        }
        if (records.isEmpty()) return List.of();
        List<String> header = records.get(0);
        List<Map<String, String>> rows = new ArrayList<>(records.size() - 1);
        for (int i = 1; i < records.size(); i++) {
            List<String> values = records.get(i);
            Map<String, String> row = new LinkedHashMap<>();
            for (int j = 0; j < header.size(); j++) {
                row.put(header.get(j).trim(), j < values.size() ? values.get(j).trim() : "");
            }
            rows.add(row);
        }
        return rows;
    }

    private static final class Bin {
        final String bin;
        final String bank;
        final String brand;
        final String type;
        final String tier;

        Bin(String bin, String bank, String brand, String type, String tier) {
            this.bin = bin;
            this.bank = bank;
            this.brand = brand;
            this.type = type;
            this.tier = tier;
        }
    }

    private static final class Card {
        final String bank;
        final String brand;
        final String type;
        final String tier;
        final String name;
        final LinkedHashSet<String> bins = new LinkedHashSet<>();

        Card(String bank, String brand, String type, String tier, String name) {
            this.bank = bank;
            this.brand = brand;
            this.type = type;
            this.tier = tier;
            this.name = name;
        }
    }

    /** Folds the source's many spellings of one bank onto a single display name. */
    private static final class BankNames {
        private static final Map<String, String> EXACT = new LinkedHashMap<>();

        static {
            alias("BRAC Bank", "BRAC BANK", "BRAC BANK PLC", "BRAC BANK PLC.", "BRAC BANK LTD",
                    "BRAC BANK LIMITED");
            alias("Eastern Bank", "EASTERN BANK", "EASTERN BANK PLC", "EASTERN BANK PLC.",
                    "EASTERN BANK LTD", "EASTERN BANK LTD BANGLADESH", "EASTERN BANK LIMITED");
            alias("Dutch-Bangla Bank", "DUTCH-BANGLA BANK", "DUTCH BANGLA BANK",
                    "DUTCH-BANGLA BANK LTD", "DUTCH BANGLA BANK LTD", "DBBL");
            alias("City Bank", "THE CITY BANK", "CITY BANK", "CITY BANK PLC", "CITY BANK PLC.",
                    "THE CITY BANK LTD", "THE CITY BANK LIMITED");
            alias("United Commercial Bank", "UNITED COMMERCIAL BANK", "UNITED COMMERCIAL BANK LTD",
                    "UNITED COMMERCIAL BANK LIMITED", "UCB");
            alias("Bank Asia", "BANK ASIA", "BANK ASIA LTD", "BANK ASIA LTD.", "BANK ASIA LIMITED");
            alias("Prime Bank", "PRIME BANK", "PRIME BANK LTD", "PRIME BANK LTD.",
                    "PRIME BANK LIMITED");
            alias("Mutual Trust Bank", "MUTUAL TRUST BANK", "MUTUAL TRUST BANK LTD",
                    "MUTUAL TRUST BANK LIMITED", "MTB");
            alias("Trust Bank", "TRUST BANK", "TRUST BANK LTD", "TRUST BANK LTD.",
                    "TRUST BANK LIMITED");
            alias("Southeast Bank", "SOUTHEAST BANK", "SOUTHEAST BANK LTD", "SOUTH EAST BANK LTD",
                    "SOUTHEAST BANK LIMITED", "SOUTH EAST BANK LIMITED");
            alias("Mercantile Bank", "MERCANTILE BANK", "MERCANTILE BANK LTD",
                    "MERCANTILE BANK LIMITED", "MERCANTILE BANK PLC");
            alias("IFIC Bank", "IFIC BANK", "IFIC BANK PLC", "IFIC BANK LTD", "IFIC BANK LIMITED");
            alias("Pubali Bank", "PUBALI BANK", "PUBALI BANK PLC", "PUBALI BANK PLC.",
                    "PUBALI BANK LTD", "PUBALI BANK LIMITED");
            alias("Dhaka Bank", "DHAKA BANK", "DHAKA BANK PLC", "DHAKA BANK LTD",
                    "DHAKA BANK LIMITED");
            alias("Premier Bank", "THE PREMIER BANK", "PREMIER BANK", "THE PREMIER BANK PLC",
                    "THE PREMIER BANK PLC.", "PREMIER BANK LTD");
            alias("Jamuna Bank", "JAMUNA BANK", "JAMUNA BANK LTD", "JAMUNA BANK LIMITED");
            alias("Shahjalal Islami Bank", "SHAHJALAL ISLAMI BANK", "SHAHJALAL ISLAMI BANK LTD",
                    "SHAHJALAL ISLAMI BANK LTD.", "SHAHJALAL ISLAMI BANK LIMITED", "SJIBL");
            alias("Social Islami Bank", "SOCIAL ISLAMI BANK", "SOCIAL ISLAMI BANK LTD",
                    "SOCIAL ISLAMI BANK LIMITED", "SIBL");
            alias("Al-Arafah Islami Bank", "AL-ARAFAH ISLAMI BANK", "AL ARAFAH ISLAMI BANK",
                    "AL-ARAFAH ISLAMI BANK LTD", "AL-ARAFAH ISLAMI BANK LIMITED", "AIBL");
            alias("First Security Islami Bank", "FIRST SECURITY ISLAMI BANK",
                    "FIRST SECURITY ISLAMI BANK LTD", "FIRST SECURITY ISLAMI BANK LIMITED", "FSIBL");
            alias("Standard Bank", "STANDARD BANK", "STANDARD BANK LTD", "STANDARD BANK LIMITED");
            alias("NRB Bank", "NRB BANK", "NRB COMMERCIAL BANK", "NRB BANK LTD",
                    "NRB COMMERCIAL BANK LTD");
            alias("ONE Bank", "ONE BANK", "ONE BANK PLC", "ONE BANK LIMITED", "THE ONE BANK LTD");
            alias("NCC Bank", "NCC BANK", "NCC BANK LTD", "NCC BANK LTD.",
                    "NATIONAL CREDIT AND COMMERCE BANK", "NATIONAL CREDIT AND COMMERCE BANK PLC",
                    "NCCBL");
            alias("EXIM Bank", "EXIM BANK", "EXPORT IMPORT BANK OF BANGLADESH",
                    "EXPORT IMPORT BANK OF BANGLADESH_LTD", "EXPORT IMPORT BANK OF BANGLADESH LTD",
                    "EXIM BANK LTD");
            alias("Shimanto Bank", "SHIMANTO BANK", "SHIMANTO BANK PLC", "SHIMANTO BANK LTD");
            alias("Sonali Bank", "SONALI BANK", "SONALI BANK PLC", "SONALI BANK PLC.",
                    "SONALI BANK LTD", "SONALI BANK LTD.", "SONALI BANK LIMITED");
            alias("Agrani Bank", "AGRANI BANK", "AGRANI BANK LTD", "AGRANI BANK LIMITED");
            alias("Janata Bank", "JANATA BANK", "JANATA BANK LTD", "JANATA BANK LIMITED");
            alias("Rupali Bank", "RUPALI BANK", "RUPALI BANK LTD", "RUPALI BANK LTD.",
                    "RUPALI BANK PLC");
            alias("Habib Bank", "HABIB BANK", "HABIB BANK LTD", "HABIB BANK LIMITED");
            alias("Bank Al-Falah", "BANK ALFALAH", "BANK AL-FALAH", "BANK ALFALAH LTD",
                    "BANK ALFALAH LIMITED");
            alias("State Bank of India", "STATE BANK OF INDIA");
            alias("Woori Bank", "WOORI BANK", "WOORI BANK LTD", "WOORI BANK LIMITED");
            alias("Commercial Bank of Ceylon", "COMMERCIAL BANK OF CEYLON PLC",
                    "COMMERCIAL BANK OF CEYLON", "COMMERCIAL BANK OF CEYLON LTD");
            alias("National Bank", "NATIONAL BANK", "NATIONAL BANK LTD", "NATIONAL BANK LIMITED");
            alias("Uttara Bank", "UTTARA BANK", "UTTARA BANK LTD", "UTTARA BANK LTD.",
                    "UTTARA BANK LIMITED");
            alias("Union Bank", "UNION BANK", "UNION BANK PLC", "UNION BANK LTD");
            alias("Modhumoti Bank", "MODHUMOTI BANK", "MODHUMOTI BANK PLC", "MODHUMOTI BANK LTD");
            alias("Midland Bank", "MIDLAND BANK", "MIDLAND BANK PLC", "MIDLAND BANK LTD");
            alias("Global Islami Bank", "GLOBAL ISLAMI BANK", "GLOBAL ISLAMI BANK PLC",
                    "GLOBAL ISLAMI BANK LTD");
            alias("Meghna Bank", "MEGHNA BANK", "MEGHNA BANK PLC", "MEGHNA BANK PLC.",
                    "MEGHNA BANK LTD");
            alias("Citizens Bank", "CITIZENS BANK", "CITIZENS BANK PLC", "CITIZENS BANK LTD");
            alias("Bengal Commercial Bank", "BENGAL COMMERCIAL BANK",
                    "BENGAL COMMERCIAL BANK PLC");
            alias("Bangladesh Commerce Bank", "BANGLADESH COMMERCE BANK",
                    "BANGLADESH COMMERCE BANK LTD");
            alias("South Bangla Agriculture and Commerce Bank",
                    "SOUTH BANGLA AGRICULTURE AND COMMERCE BANK",
                    "SOUTH BANGLA AGRICULTURE AND COMMERCE BANK LTD", "SBAC BANK");
            alias("Bank of Small Industries and Commerce Bangladesh",
                    "BANK OF SMALL INDUSTRIES AND COMMERCE BANGLADESH",
                    "BANK OF SMALL INDUSTRIES AND COMMERCE BANGLADESH LTD", "BASIC BANK");
            alias("ICB Islamic Bank", "ICB ISLAMI BANK", "ICB ISLAMI BANK LTD");
            alias("HSBC", "HSBC", "HSBC BANK", "HSBC BANK PLC");
            alias("Standard Chartered Bank", "STANDARD CHARTERED BANK",
                    "STANDARD CHARTERED BANK BANGLADESH", "SCB");
            alias("Citibank", "CITIBANK", "CITIBANK N.A.", "CITI BANK");
            alias("Bangladesh Post Office", "BANGLADESH POST OFFICE", "BANGLADESH POST");
        }

        private static void alias(String canonical, String... spellings) {
            for (String spelling : spellings) {
                EXACT.put(spelling.toUpperCase(Locale.ROOT).trim(), canonical);
            }
        }

        /** Canonical display name, or null when the row is not attributable to a bank. */
        static String canonical(String raw) {
            String cleaned = raw.replace("\"", "").replace(",", " ").trim()
                    .replaceAll("\\s+", " ")
                    .replaceAll("[.,]+$", "")
                    .toUpperCase(Locale.ROOT);
            if (cleaned.isBlank()) return null;
            String exact = EXACT.get(cleaned);
            if (exact != null) return exact;
            // Fall back to the longest known alias contained in the name, so "BRAC BANK PLC - X"
            // still lands on BRAC Bank.
            String best = null;
            int bestLength = 0;
            for (Map.Entry<String, String> entry : EXACT.entrySet()) {
                if (cleaned.contains(entry.getKey()) && entry.getKey().length() > bestLength) {
                    best = entry.getValue();
                    bestLength = entry.getKey().length();
                }
            }
            return best;
        }
    }

    private static final class BrandNames {
        static String canonical(String raw) {
            String brand = raw.replace("\"", "").trim().toUpperCase(Locale.ROOT);
            return switch (brand) {
                case "VISA" -> "VISA";
                case "MASTERCARD", "MASTER CARD" -> "Mastercard";
                case "MAESTRO" -> "Maestro";
                case "AMERICAN EXPRESS", "AMEX" -> "American Express";
                case "DINERS CLUB INTERNATIONAL", "DINERS CLUB", "DINERS" -> "Diners Club";
                case "DISCOVER" -> "Discover";
                case "JCB" -> "JCB";
                case "CHINA UNION PAY", "UNIONPAY", "UNION PAY" -> "UnionPay";
                case "LOCAL BRAND" -> "Local brand";
                case "PRIVATE LABEL" -> "Private label";
                default -> brand.isBlank() ? "Unknown" : title(brand);
            };
        }
    }

    private static final class TypeNames {
        static String canonical(String raw) {
            return switch (raw.trim().toUpperCase(Locale.ROOT)) {
                case "CREDIT" -> "Credit";
                case "DEBIT" -> "Debit";
                case "PREPAID", "PRE-PAID" -> "Prepaid";
                default -> "Unknown";
            };
        }
    }

    private static final class TierNames {
        static String canonical(String raw) {
            String tier = raw.replace("\"", "").trim().toUpperCase(Locale.ROOT)
                    .replaceAll("\\s+CARD$", "");
            if (tier.equals("ATM")) return "ATM";
            return switch (tier) {
                case "STANDARD" -> "Standard";
                case "CLASSIC" -> "Classic";
                case "GOLD" -> "Gold";
                case "PLATINUM" -> "Platinum";
                case "TITANIUM" -> "Titanium";
                case "SIGNATURE" -> "Signature";
                case "WORLD" -> "World";
                case "WORLD ELITE" -> "World Elite";
                case "INFINITE" -> "Infinite";
                case "BUSINESS" -> "Business";
                case "CORPORATE" -> "Corporate";
                case "STUDENT" -> "Student";
                case "PREPAID" -> "Prepaid";
                case "PROPRIETARY" -> "Proprietary";
                case "Q-CASH", "QCASH" -> "Q-Cash";
                case "" -> "Standard";
                default -> title(tier);
            };
        }
    }

    private static String title(String text) {
        StringBuilder out = new StringBuilder();
        for (String word : text.toLowerCase(Locale.ROOT).split("\\s+")) {
            if (word.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    private BinExport() {
    }
}
