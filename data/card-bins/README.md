# Bangladeshi bank cards and BINs

Reference data for matching a bank's card-offer rules to the card a diner actually holds.
Everything here is **data**, not app code — nothing in this folder is packaged into the APK.

## What a BIN is

The first six digits of a card number are the **BIN** (bank identification number, also called
IIN). They identify the issuing bank, the card network and usually the product tier — which is
exactly what a "Dine 2 Pay 1 for Visa Signature cardholders" offer keys on. BINs are public
routing ranges; they say nothing about any individual cardholder.

## Files

| File | What it holds | Rows (28 Oct 2026) |
| --- | --- | --- |
| `bins.json` | One record per BIN: `bin`, `bank`, `brand`, `type`, `tier`, `card` | 630 |
| `cards.csv` | One row per card product: bank, card name, brand, type, tier, all its BINs, a matching hint | 410 |
| `banks/<bank-slug>.json` | The same cards grouped under one bank, ready to load per bank | 50 files |
| `card-products.csv` | Official product names from 21 banks' own card pages, each with the page URL it came from | 169 products |
| `summary.json` | Counts by brand and by type | — |

### Schemas

`bins.json`

```json
{"bin":"408860","bank":"BRAC Bank","brand":"VISA","type":"Credit","tier":"Signature","card":"BRAC Bank VISA Signature Credit Card"}
```

`cards.csv`

```
bank,card,brand,type,tier,bins,best_for
BRAC Bank,BRAC Bank VISA Signature Credit Card,VISA,Credit,Signature,408860,premium dining and BOGO buffet offers
```

`banks/brac-bank.json`

```json
{
  "bank": "BRAC Bank",
  "cards": [
    {
      "name": "BRAC Bank VISA Signature Credit Card",
      "brand": "VISA",
      "type": "Credit",
      "tier": "Signature",
      "bestFor": "premium dining and BOGO buffet offers",
      "bins": ["408860"]
    }
  ]
}
```

## How to join this to an offer

An offer usually states a bank plus a card family, for example *"Dine 2 Pay 1 — Visa Signature
credit cards"*. Join on `bank` + `brand` + `type` + `tier`:

```
offer.bank == cards.bank
offer.brand ∈ {cards.brand, "any"}
offer.type  ∈ {cards.type, "any"}
offer.tier  ∈ {cards.tier, "any"}      // "Visa Signature" → tier "Signature"
```

`cards.csv` is the easiest table for that; `banks/*.json` is convenient when the app already knows
which bank's offer it is showing. `best_for` is a coarse hint for the UI ("premium dining and BOGO
buffet offers"), not a rule.

## Sources

- **BIN rows** — the public `bin-list-data` dataset (a bin-list export), filtered to
  `CountryName = BANGLADESH`, keeping only rows whose issuer folds onto a known bank. Bank names
  in the source are inconsistent (`BRAC BANK`, `BRAC BANK PLC.`, `BRAC BANK PLC`), so
  `tools/BinExport.java` normalises them through an explicit alias table. Nothing is inferred: a
  card appears only when a real BIN row maps to it.
- **Product names** — the banks' own card pages, listed in the `source` column of
  `card-products.csv`, one URL per product.
- **Checked** — 28 October 2026.

## Regenerating

```bash
java tools/BinExport.java bin-list-data.csv data/card-bins
```

The tool takes the CSV export from the `bin-list-data` repository and rewrites `bins.json`,
`cards.csv`, `banks/` and `summary.json`. Edit the alias table in `tools/BinExport.java` when a
source spells a bank in a new way.

## Caveats — read before using this as production data

1. **BIN ranges change.** Banks re-issue ranges, merge and retire products. The snapshot is dated;
   re-run against a fresh export before relying on it.
2. **Coverage is partial by nature.** A BIN list only contains ranges that have been published or
   observed. A bank with no BIN here still issues cards — absence is not proof of absence.
3. **Tier is inferred by the BIN source.** `Platinum` in a BIN row and `Platinum` in a marketing
   page usually agree, but not always (co-branded and Islamic variants are the common mismatch).
   Use `card-products.csv` when the product name matters.
4. **Local schemes appear too.** `Local brand`, `Private label`, `Q-Cash` and `TakaPay` rows are
   domestic ATM/debit ranges, not Visa or Mastercard.
5. **This is not verification data.** Do not use BINs to validate a real card number, to check
   whether a card is valid, or for anything to do with a cardholder's identity.
6. **Some product names are unverified.** Banks whose card pages were unreachable or
   image-only are omitted from `card-products.json`; their BINs still appear in `bins.json` if
   the BIN source had them.

## Where the offers live

`app/src/main/assets/seed_offers.json` holds the curated buffet offers, each with the bank and
card family it names. The bank dining pages the app reads are listed in
`app/src/main/java/com/buffetbogo/hunter/ingest/BankPageSource.kt`.
