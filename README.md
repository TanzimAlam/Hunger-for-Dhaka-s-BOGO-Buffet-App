# Hunger for Dhaka's BOGO Buffet (Android)

A small Android app that hunts for All-You-Can-Eat / buffet deals in Dhaka — Buy 1 Get 1,
Buy 1 Get 2/3/4, Dine 3 Pay 1, Eat 4 Pay 1, Eat 5 Pay 1 — and alerts you when a new one appears.

The home screen is a dark feed of photo cards. Each card carries the deal badge, the Google star
rating, the **price**, the card / bKash / cash condition and how long the offer has left.
Search and category chips sit above the list, and the default order is **by end date**, so whatever
is about to run out is at the top. The app icon and header use the restaurant's own emblem.

**It works with no API keys and no account.** Two public sources are read directly, a checked
set of Google ratings and buffet prices ships with the app, and missing prices are looked up with
a keyless search. Keys in Settings only widen coverage and refresh live data.

## Where offers come from

| Source | Key needed | What it gives |
| --- | --- | --- |
| Standard Chartered BD buffet page `av.sc.com/bd/edm/b1g1-offers/` | none | ~17 Dhaka five-star venues with the deal wording ("Dine 4 Pay 1"), card eligibility, "Valid till" date and the hotel's own photo |
| Google News RSS (`news.google.com/rss/search`) | none | Dated press coverage of new campaigns — catches short promotions like "Buy 1 Get 3 free buffet, 18–24 October" |
| DuckDuckGo HTML results | none | Price lookup when an ad omits the figure (default fallback) |
| Brave Search API (optional) | free key | General web results, including public Facebook pages that search engines have indexed, and a better price lookup |
| Google Places API (New) (optional) | free key | Refreshes each restaurant's star rating and review count |

## Prices — every card has one

The feed never renders a card without a figure. A price comes from, in order:

1. **The ad itself** (`PriceSources.AD`) — whatever the Facebook post, bank page or news article
   states, in `BDT`, `Tk`, `৳` or plain taka. A card's group price stays a group price; Sarina's
   Buy 1 Get 3 is shown as `৳9,000 · for 4 people`, not as a per-person rate.
2. **The bundled price book** (`app/src/main/assets/venue_prices.json`, `PriceSources.TYPICAL`) —
   checked standing buffet rates per venue (lunch and dinner), used when the ad only describes
   the deal. The card labels these **typical** so an estimate is never mistaken for a quote.
3. **A search for the restaurant** (`PriceSources.SEARCH`) — `BuffetPriceSearch` asks for
   `"<restaurant>" Dhaka buffet price BDT`, keeps only figures next to buffet wording and inside a
   sane BDT 500–20,000 band, and prefers amounts repeated across results or marked "per person".
   With a Brave key it also queries `site:facebook.com`; without one it uses DuckDuckGo's
   lightweight HTML results. No Facebook account, app token or scraping is involved.

If all three come back empty, the row stays in the database but is filtered out of the feed until a
price is found — the app would rather show an offer late than show it with no price at all.

## Star ratings

The list shows `★ 4.6` badges. They come from two places:

1. **Checked snapshot (always available).** `app/src/main/assets/venue_ratings.json` holds the Google
   rating and review count for 17 Dhaka venues, each checked on 25 October 2026, with spelling aliases
   so a bank page that writes "Hotel Sarina Dhaka" still matches "Hotel Sarina". Settings labels
   these **CACHED** and says when they were checked.
2. **Live refresh (optional).** With a Places API key the app refreshes every venue through
   Places API (New) Text Search, caches each result for 30 days and rewrites the row. Settings then
   shows **CONNECTED**.

Google does not expose review scores without a key: the Places API needs a Google Cloud project
with billing enabled, and the free monthly quota comfortably covers ~30 venues. There is no
keyless endpoint for this data, so the app never invents a rating — venues without one simply have
no star pill.

### Baking a key into the build (no in-app setup)

Create `local.properties` in the project root (it is not committed) with:

```properties
PLACES_API_KEY=your-key-here
```

`app/build.gradle.kts` reads that file (or the `PLACES_API_KEY` environment variable) into
`BuildConfig.PLACES_API_KEY`, and Settings treats it as the default key. A key typed into Settings
overrides it. Keys stay on the device or in the local build — never in the APK assets or the repo.

## What is in the box

| Area | Implementation |
| --- | --- |
| UI | Kotlin + Jetpack Compose (Material 3), dark-only theme, single activity |
| Storage | Room (`offers`, `restaurant_ratings`) with a v1→v2 migration for the price unit and source, DataStore for settings and the chosen sort order |
| Background work | WorkManager every 6 hours, plus a refresh on launch when the feed is older than 6 hours |
| Notifications | One summary notification per sync, only for offers never seen before |
| Bundled catalogue | 6 real, sourced Dhaka offers in `app/src/main/assets/seed_offers.json`, all priced |
| Price book | 22 venues' checked buffet rates in `app/src/main/assets/venue_prices.json`, used to price bank ads that never state one |
| Branding | Launcher icon and in-app header logo generated from the supplied emblem (`tools/IconGen.java`) |
| Parsing | `OfferParser` (English wording), `StandardCharteredSource`, `NewsRssSource` |

### Screens

1. **Feed** — the emblem in the header with the app name, LIVE chip and an alerts bell (counts
   offers ending within three days), search field, category chips (All / Buy 1 Get 1+ / Eat 3-4-5
   Pay 1 / 5★ Hotels), a sort menu (default **Ends soonest**, plus Newest, Price low→high, Top
   rated), and photo cards showing badge, Google rating, restaurant, title, price with its unit,
   payment condition, countdown and a details button. A price filled in from the book or a search
   carries a small **typical** tag.
2. **Offer detail** — hero photo, offer text, price, rating with review count, validity, payment
   conditions, source, "Open the original ad" and "Hide this offer".
3. **Settings** — Google ratings status and key, alerts toggle, sources, manual check, reload
   listings, and the optional Brave key.

## Build it

Requirements: Android Studio (Ladybug or newer) with Android SDK 35, JDK 17.

```bash
./gradlew assembleDebug        # APK at app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # JVM tests: parser, live parsers, sorting, ratings, prices, catalogue
```

Open the folder in Android Studio and press Run (Android 8.0 / API 26+).

## First run

1. Launch — the checked catalogue shows immediately, and a refresh against the two keyless sources
   starts in the background.
2. Tap the refresh chip any time, or wait for the 6-hourly check.
3. Optional: paste a Places key for live ratings and/or a
   [Brave key](https://brave.com/search/api/) for broader search.

## The bundled catalogue

The rows in `seed_offers.json` are real promotions, each with the page they were verified on:
Hotel Sarina's standing Buy 1 Get 3 at BDT 9,000 net for four (any payment method), Beyond Buffet's
15–30 October discount, Teriyaki Boy's Buy 1 Get 1 steak buffet, Amari's Pay 1 Eat 3 lunch and
Eat 5 Pay 1 dinner, and Six Seasons' daily net-price buffet. Every row carries a price and the unit
that price covers.
They carry absolute dates, so they disappear like any other offer once a campaign ends; bump
`SeedLoader.VERSION` when you edit the file and installed apps replace the old rows on next launch.

## How discovery actually works (and the Facebook reality)

- `StandardCharteredSource` splits the bank page on `div class=box`, reads the `<h3>` venue, every
  `offer-info` line, the "Valid till" date and the thumbnail, and turns each Dhaka venue into one offer.
- `NewsRssSource` runs six queries (`buffet Dhaka buy 1 get 1`, `buffet offer Dhaka hotel`,
  `b1g1 buffet Dhaka`, `dine 3 pay 1 buffet Dhaka`, …), drops anything older than 60 days or without
  a recognisable deal, and keeps the rest. The RSS item is only a headline, so when no end date is
  stated the offer stays visible for 21 days and says so.
- `OfferParser` extracts the deal kind, dates, price, payment conditions, restaurant (matched
  against known Dhaka hotels) and area. Anything without a buffet word and a recognisable deal is
  dropped, as is anything already expired.

**About Facebook:** Meta provides no way for a third-party app to keyword-search ordinary public
restaurant posts. The Ad Library API covers political/social-issue ads worldwide plus all ad types
in the EU/UK — not commercial restaurant ads in Bangladesh — and scraping is prohibited by Meta's
Automated Data Collection Terms. With a Brave key the app searches `site:facebook.com` and labels
those hits "Facebook"; it never scrapes Facebook.

References:

- <https://developers.facebook.com/docs/features-reference/page-public-content-access/>
- <https://www.facebook.com/legal/automated_data_collection_terms>
- <https://transparency.meta.com/researchtools/ad-library-tools/>
- Google Custom Search JSON API is closed to new customers: <https://developers.google.com/custom-search/v1/overview>
- Places field/SKU mapping: <https://developers.google.com/maps/documentation/places/web-service/data-fields>

## Expiry and ordering rules

- `validUntil` is the end of the last date found (23:59 local). A single date is the expiry; two
  dates become `validFrom`/`validUntil`; a bank page's "Valid till 31-Dec-27" is parsed as-is.
- The list flow filters `hidden == false && (validUntil == null || validUntil >= now)` and
  re-evaluates every 60 seconds, so an offer vanishes while the app is open.
- **Default order is `ENDS_SOONEST`** — offers with a date first, earliest deadline at the top,
  offers with no stated end date last. The other orders are `NEWEST` (post date), `PRICE_LOW_HIGH`
  (unlisted prices last) and `RATING_HIGH_LOW` (unrated last). The choice is stored in DataStore.
- New rows use the publish date when the source has one, otherwise the moment they were first
  found — and that first-seen date is preserved on later refreshes so standing offers do not jump
  back to the top.
- Rows expired more than 45 days are pruned.

## Known limitations

- The bundled ratings are a checked snapshot; without a Places key they will drift as reviews
  accumulate. Settings states the check date rather than pretending they are live.
- Google News RSS states it is for personal, non-commercial feed readers. That fits a personal
  install; a published app should move discovery to its own backend.
- The parser understands English wording. Bangla ("১টি কিনুন ১টি ফ্রি") is not parsed yet.
- Prices filled from the book or a search are marked **typical**; they are the restaurant's
  standing rate, not necessarily the exact campaign price. Some venues publish no buffet price at
  all, so their offers stay hidden until a lookup succeeds.
- The keyless price fallback reads DuckDuckGo's HTML results. It is fine for personal use but can
  rate-limit or change markup; with a Brave key the app uses the official API instead.
- Facebook posts are only ever seen through a search index, so an unindexed post is invisible.
- No device or emulator was available in this environment: the app compiles and 61 JVM tests pass,
  but the screens and the launcher icon have not been inspected on hardware.

## Next steps

- Move discovery to a backend with a cron job so the phone needs no keys at all and every user
  shares one checked feed.
- Add Bangla numeral/wording support and OCR for offer posters.
- Filter by area, by card you actually hold, or by meal period.
