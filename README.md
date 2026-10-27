# Hunger for Dhaka's BOGO Buffet (Android)

A small Android app that hunts for All-You-Can-Eat / buffet deals in Dhaka — Buy 1 Get 1,
Buy 1 Get 2/3/4, Dine 3 Pay 1, Eat 4 Pay 1, Eat 5 Pay 1 — and alerts you when a new one appears.

The home screen is a warm charcoal feed with **one card per restaurant**, coloured from the app's
own emblem. A venue running three deals is still one card: every deal's short badge (B1G1, B1G3,
Eat 3 Pay 1) sits side by side on the top-left of the thumbnail, and each deal is listed inside the
card with its own **price**, Google star rating and remaining days. Search and category chips sit
above the list, and the default order is **by end date**, so whatever is about to run out is at the
top. Opening a deal and coming back leaves the feed exactly where it was — same card, same search,
same filter.

**It works with no API keys and no account.** Nine public pages are read directly, a checked
set of Google ratings and buffet prices ships with the app, and missing prices are looked up with
a keyless search. Keys in Settings only widen coverage and refresh live data.

## Where offers come from

| Source | Key needed | What it gives |
| --- | --- | --- |
| Bank dining pages: Standard Chartered `av.sc.com/bd/edm/b1g1-offers/`, City Bank, Mutual Trust Bank, Prime Bank, Trust Bank, Shimanto Bank, NCC Bank | none | **Discovery only** — which Dhaka venues currently run a buffet deal, the deal wording ("Dine 4 Pay 1", "BOGO Free Buffet") and card eligibility. A bank's link is never shown or opened |
| Google News RSS (`news.google.com/rss/search`) | none | Dated press coverage of new campaigns — catches short promotions like "Buy 1 Get 3 free buffet, 18–24 October" |
| `app/src/main/assets/venue_socials.json` | none | Every venue's **official Facebook page** and its own dining page — the ad link each card opens |
| Facebook Graph API (optional) | Meta app + Page token | The venues' own posts: the restaurant's price, dates, serving hours and conditions, read through the official API |
| DuckDuckGo HTML / Brave results | none / free key | Finds a venue's exact promotion post (`site:facebook.com`) and prices an ad that omits the figure |
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

## The ad behind every card

A bank page proves an offer exists; it is not the ad. It usually omits the price, the serving hours
and the restaurant's own conditions, so it is used only as a lead. Every card resolves to a link on
the restaurant's own side, in this order:

1. **The restaurant's own post** — the permalink of the buffet post itself, from the Graph API or a
   `site:facebook.com "<restaurant>" buffet` search hit, and only when it comes from that venue's
   page.
2. **The restaurant's official page** — from the bundled `venue_socials.json` (31 venues, each with
   the page it actually posts offers on, checked on 27 October 2026).
3. **The restaurant's own dining page** — when a venue has no verified Facebook page.
4. **Facebook's search for that restaurant** — only when none of the above is known yet; the button
   then says "Search Facebook for this offer" instead of pretending to be the ad.

A Standard Chartered, news or any other discovery URL is never used as the ad link. The detail
screen says where the listing was found, but the link always goes to the restaurant.

## Facebook (optional, real Graph API)

Facebook is where Dhaka's hotels publish these offers, so the app speaks to Meta's Graph API
directly when it is allowed to. `FacebookGraph` calls `/{page}/feed` on version `v26.0` for each
venue in the directory, reads `message`, `created_time`, `permalink_url` and `full_picture`, and
keeps only posts about a buffet deal. Those posts supply the card's ad link, price, end date,
serving window ("Dinner 6:30 pm – 10:30 pm") and conditions; `AdEnricher` folds them into the
corresponding discovered offer instead of showing the same deal twice.

Meta does not let an app read another page's posts without **Page Public Content Access**, which
must be approved for the Meta app (business verification included). Add a Page token in
**Settings → Facebook posts** and the app reads the venues' pages; if Meta refuses, its own message
is shown verbatim — e.g. `(#10) Application does not have permission for this call` — and the app
keeps working through the page and search links. The token can also be baked in:

```properties
FACEBOOK_PAGE_TOKEN=your-page-token
```

No token is required for the app to be useful: the link to the restaurant's own page always works.

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
| Storage | Room (`offers`, `restaurant_ratings`) with v1→v2 (price unit/source) and v2→v3 (ad link, ad label, timing, checked date) migrations, DataStore for settings and the chosen sort order |
| Background work | WorkManager every 6 hours, plus a refresh on launch when the feed is older than 6 hours |
| Notifications | One notification per new offer (never a bundled summary), carrying that offer's id; tapping it opens that offer's own detail screen, and its action button opens the venue's ad directly |
| Bundled catalogue | 6 real, sourced Dhaka offers in `app/src/main/assets/seed_offers.json`, all priced |
| Price book | 28 venues' checked buffet rates in `app/src/main/assets/venue_prices.json`, used to price bank ads that never state one |
| Venue directory | 31 venues' official Facebook pages and dining pages in `app/src/main/assets/venue_socials.json` |
| Grouping | `FeedGrouper` folds the offer list into one card per restaurant, orders venues by their best deal for the chosen sort, and keeps one copy of a deal that several banks list |
| Branding | Launcher icon, adaptive icon and in-app header logo generated from the supplied emblem by `tools/BrandIcons.java`; the Compose palette is sampled from that emblem (warm charcoal, steel blue, cream) |
| Parsing | `OfferParser` (English wording, prices, dates, serving windows), `StandardCharteredSource`, `BankPageSource` + `BankHtml` (six more banks), `NewsRssSource`, `FacebookPageSource`, `AdEnricher`, `AdLinkResolver` |

### Screens

1. **Feed** — the emblem in the header with the app name, LIVE chip and an alerts bell (counts
   offers ending within three days), search field, category chips (All / Buy 1 Get 1+ / Eat 3-4-5
   Pay 1 / 5★ Hotels), a sort menu (default **Ends soonest**, plus Newest, Price low→high, Top
   rated), and one card per restaurant: deal badges on the thumbnail's top-left, the Google rating
   on its top-right, then a row per deal with its price and unit, payment condition and countdown.
   Tapping a row opens that deal. A price filled in from the book or a search carries a small
   **typical** tag.
2. **Offer detail** — hero photo, offer text, price, rating with review count, validity and the
   serving window, payment conditions, an **ad reference card** naming the restaurant's page, the
   "Open the original ad" button (or "Search Facebook for this offer"), and "Hide this offer".
3. **Settings** — Google ratings status and key, the optional Facebook Page token with its status,
   alerts toggle, sources, manual check, reload listings, and the optional Brave key.

The feed keeps its scroll position, search text and chosen filter while an offer is open, because
that state lives above the screen switch in `AppRoot`.

## Build it

Requirements: Android Studio (Ladybug or newer) with Android SDK 35, JDK 17.

```bash
./gradlew assembleDebug        # APK at app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # JVM tests: parser, live parsers, sorting, ratings, prices, catalogue
```

Open the folder in Android Studio and press Run (Android 8.0 / API 26+).

## First run

1. Launch — the checked catalogue shows immediately, and a refresh against the keyless sources
   starts in the background.
2. Tap the refresh chip any time, or wait for the 6-hourly check.
3. Optional: paste a Places key for live ratings, a
   [Brave key](https://brave.com/search/api/) for broader search, and/or a Facebook Page token to
   read the venues' own posts through the Graph API.

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
- `BankPageSource` fetches the six other banks' dining pages concurrently (gzip, browser user agent)
  and hands each to `BankPageParser`. Because those pages share no markup, it works from the visible
  text: find the deal phrase, walk back for the venue it belongs to and forward for the card
  condition, then take the price and campaign date from the same window. Known venues are matched
  against `venue_socials.json`, so "HYATT PLACE" becomes "Hyatt Place Dhaka Uttara" and the same
  hotel from two banks is one restaurant. Pages that publish only images or PDFs (DBBL, EBL,
  Southeast, Mercantile) are deliberately not listed.
- `NewsRssSource` runs six queries (`buffet Dhaka buy 1 get 1`, `buffet offer Dhaka hotel`,
  `b1g1 buffet Dhaka`, `dine 3 pay 1 buffet Dhaka`, …), drops anything older than 60 days or without
  a recognisable deal, and keeps the rest. The RSS item is only a headline, so when no end date is
  stated the offer stays visible for 21 days and says so.
- `OfferParser` extracts the deal kind, dates, price, payment conditions, restaurant (matched
  against known Dhaka hotels) and area. Anything without a buffet word and a recognisable deal is
  dropped, as is anything already expired.

- `FacebookPageSource` reads each venue's feed through `FacebookGraph` (Graph API `v26.0`), keeps
  posts that mention a buffet deal, parses the price, end date and serving window, and stores the
  post's permalink as the ad link.
- `AdLinkResolver` settles every offer on the restaurant's own link, first from the bundled
  directory, then (only for venues whose exact post is still unknown, at most four per sync) from a
  `site:facebook.com` search restricted to that venue's page handle. A resolved post is trusted for
  seven days.
- `AdEnricher` matches a post to a discovered offer by venue and deal kind, moves the post's price,
  date, timings and photo onto the card, and drops the duplicate row so one promotion is one card.

**About Facebook:** Meta has no keyword search for ordinary public restaurant posts, and scraping is
prohibited by its Automated Data Collection Terms. What Meta does support is reading a page's own
posts through the Graph API, which this app implements; for other pages that requires the approved
**Page Public Content Access** feature, and the app reports Meta's refusal rather than hiding it.
The Ad Library API covers political/social-issue ads worldwide plus all ad types in the EU/UK — not
commercial restaurant ads in Bangladesh.

References:

- <https://developers.facebook.com/docs/features-reference/page-public-content-access/>
- <https://www.facebook.com/legal/automated_data_collection_terms>
- <https://transparency.meta.com/researchtools/ad-library-tools/>
- Google Custom Search JSON API is closed to new customers: <https://developers.google.com/custom-search/v1/overview>
- Places field/SKU mapping: <https://developers.google.com/maps/documentation/places/web-service/data-fields>

## Grouping, expiry and ordering rules

- The feed groups live offers by `nameKey(restaurant)` before display, so a hotel with two deals is
  one card. Within a card, a deal that two sources reported is kept once, preferring the copy that
  links to the restaurant's own post. Filters and search still act on individual deals, and a
  card appears when any of its deals matches.

- `validUntil` is the end of the last date found (23:59 local). A single date is the expiry; two
  dates become `validFrom`/`validUntil`; a bank page's "Valid till 31-Dec-27" is parsed as-is.
- The list flow filters `hidden == false && (validUntil == null || validUntil >= now)` and
  re-evaluates every 60 seconds, so an offer vanishes while the app is open.
- **Default order is `ENDS_SOONEST`** — restaurants whose first deal ends soonest at the top, and
  restaurants with no stated end date last. The other orders are `NEWEST` (latest deal), `PRICE_LOW_HIGH`
  (cheapest deal on the card) and `RATING_HIGH_LOW` (unrated last). The choice is stored in DataStore.
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
- Without the approved Facebook permission, posts can only be found through a search index, so an
  unindexed post is invisible; the card then links to the venue's page instead of the exact post.
- No device or emulator was available in this environment: the app compiles and its JVM tests pass,
  but the screens have not been inspected on hardware.
- Bank dining pages are whole web pages (roughly 2 MB uncompressed across the six, ~400 KB over
  gzip), fetched concurrently once per sync. They also redesign without notice: a changed page
  contributes nothing rather than half a venue name, and the snapshot tests catch the ones covered.
- Some venues (The Way Dhaka, Shinepukur Suites, Bengal Inn, Platinum Residence) print no buffet
  price anywhere readable, so until the keyless price search finds one their bank offers stay out
  of the feed under the no-price-no-card rule.

## Next steps

- Move discovery to a backend with a cron job so the phone needs no keys at all and every user
  shares one checked feed.
- Add Bangla numeral/wording support and OCR for offer posters.
- Filter by area, by card you actually hold, or by meal period.
