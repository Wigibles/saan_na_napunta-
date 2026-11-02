# SaanNaPunta: Design specification

Version 0.2 · Android · Light theme · Filipino users of all ages

---

## What changed in 0.2

- Reconciled the spec with the 11 mockup screens. Where they disagreed, the calm principles in this spec win, and Appendix A lists what the mockups need to change.
- Added Chito (the mascot) as a defined part of the system, with limits on where he appears (section 5.3).
- Added section 6, "Money definitions", so every number on every screen means one thing.
- Updated Home, Budgets, and Insights to match what the mockups actually contain, minus the duplication.
- Added the screens that were missing: add or review transaction, permissions, settings, empty states.
- Rewrote the copy rules: plain words instead of finance jargon, and no wording that claims more than the app does (no "sync", "link", or "connected" for things that are only read from the phone).
- Added section 12, private release notes (signing, backup, permissions on sideloaded installs).

---

## 1. Design intent

SaanNaPunta helps people in the Philippines see where their money goes, using the banks and e-wallets they already use. The design should feel like a calm, well-made banking product: quiet surfaces, confident numbers, and very little decoration.

**Who it is for**

Filipinos of all ages: employees paid on the 15th and 30th, people paid monthly, freelancers with irregular income, and students on baon or allowance. The first release is a private build for the owner and close friends.

**What the design must do**

- Make the peso amount the hero of every screen.
- Let people recognise their own accounts instantly (GCash, Maya, BDO, and so on) by name and logo.
- Feel trustworthy and private, because the app works on the device and never sends data out.
- Work in the person's pay cycle (sahod or baon), not only the calendar month.

**What the design must not do**

- Look like an AI-generated template: no emoji, no decorative illustrations other than Chito (section 5.3), no gradient washes, no icon on every row, no "motivational" copy.

**One memorable moment:** the balance card on Home. Everything else stays quiet so that card and the numbers carry the identity.

---

## 2. Color

White and green, with tinted neutrals so the page never feels cold or grey.

### Core palette

| Token | Hex | Use |
|---|---|---|
| `canvas` | `#F4F7F5` | App background behind cards |
| `surface` | `#FFFFFF` | Cards, sheets, bottom bar |
| `ink` | `#0E1A14` | Primary text, amounts |
| `ink-muted` | `#4B5A52` | Secondary text |
| `ink-faint` | `#7C8A82` | Captions, placeholders (large text only) |
| `line` | `#E3EAE5` | 1 px card borders, dividers |

### Green scale

| Token | Hex | Use |
|---|---|---|
| `green-900` | `#0A3D29` | Hero balance card background, highlighted chart bar |
| `green-700` | `#06653D` | Pressed state, selected chip text |
| `green-600` | `#09804D` | Primary buttons, links, active nav, progress fill |
| `green-500` | `#14A869` | Chart bars and large graphics only |
| `green-100` | `#DDF3E7` | Selected chips, soft badges |
| `green-50` | `#EEF8F2` | Subtle highlights, income rows, monogram tiles |

### Status

| Token | Hex | Use |
|---|---|---|
| `warn` | `#B45309` | Nearing budget limit |
| `over` | `#C0392B` | Over budget, errors |

### On the hero card

| Token | Hex | Use |
|---|---|---|
| `on-hero` | `#FFFFFF` | Balance and primary text on `green-900` |
| `on-hero-muted` | `#B7D6C5` | Captions on `green-900` (roughly 7:1) |

### Color rules

- Green means "action" or "healthy". Do not use it as decoration.
- Spending is shown in `ink`, not red. This includes day totals in the transaction list and the Expense quick action. Red is reserved for over-budget and errors, otherwise every screen turns alarming.
- Income is `green-600` with a plus sign. Never rely on color alone: always show `+` or `−`.
- Near-limit and over-limit budgets keep a white card. Only the progress fill, the caption under it, and a text label ("Near limit", "Over by ₱310") change to `warn` or `over`. No pink or orange card fills.
- Provider brand colors (GCash blue, Maya green, and so on) appear only inside the logo tile. They never fill a card or a row.
- Charts use one hue. Bars are `green-500`; the highlighted bar (today, or the highest day) is `green-900`. Chart marks must reach 3:1 against their background. Do not use a five-shade green legend: lists of categories use labels and percentages, not color keys.
- Check contrast before shipping. `green-600` on white is roughly 5:1; `ink-faint` is for captions at 13sp and above only.
- No gradients anywhere, and no decorative circles or blobs inside cards. Depth comes from the white-on-tinted-canvas contrast and 1 px borders.

---

## 3. Typography

### Typeface

**Plus Jakarta Sans** (Google Fonts, free, bundled in the app). It has rounded, open letterforms, a friendly but sharp feel, and is widely used in fintech products. Use one family across the whole app and let weight and size create hierarchy.

Fallback if you want an alternative: **Manrope** or **Figtree**.

Before committing, confirm the font renders the peso sign (₱, U+20B1) and has tabular (fixed-width) numerals. If either is missing, use Manrope for amounts only.

### Scale

| Role | Size / line | Weight | Notes |
|---|---|---|---|
| Balance | 36 / 42 sp | 700 | Tight tracking (−0.5%), tabular numerals |
| Screen title | 24 / 30 sp | 700 | |
| Section title | 17 / 24 sp | 600 | |
| Amount (row) | 16 / 22 sp | 600 | Right-aligned, tabular numerals |
| Body | 15 / 22 sp | 400 | |
| Label (buttons, chips) | 14 / 20 sp | 600 | |
| Caption | 13 / 18 sp | 500 | Minimum size in the app is 12 sp |

### Typography rules

- Sentence case everywhere. No all-caps labels ("NET WORTH", "SPENT TODAY", "7-DAY LIQUIDITY" become "Net worth", "Spent today", "Last 7 days"), and no letter-spaced eyebrows above headings.
- Do not highlight a single word in a headline with a different color or italics.
- No monospace. Masked account numbers ("•••• 7183") use Plus Jakarta Sans with tabular numerals at 13 sp in `ink-muted`.
- Peso format: `₱12,480.50`. Always two decimals for transactions; whole pesos are fine for large summary figures (`₱48,200`). Negative amounts use a true minus sign: `−₱289.00`.
- Show the peso sign slightly smaller (about 70% of the digit size) on the balance for a refined look.
- Amounts always use tabular numerals so columns line up.
- Keep lines under about 60 characters on a phone.

---

## 4. Shape, spacing, elevation

### Corner radius

Use different radii for different roles. A single radius on everything looks templated.

| Element | Radius |
|---|---|
| Hero balance card | 28 dp (the only surface at this radius) |
| Standard cards, bottom sheets (top corners) | 20 dp |
| Inputs, buttons | 14 dp |
| Chips, badges, progress bars | Fully rounded |
| Bank/e-wallet logo tile | 12 dp |

### Spacing

8 dp base grid: 4, 8, 12, 16, 24, 32. Screen side padding is 20 dp. Gap between cards is 12 dp. Card inner padding is 16 dp (20 dp on the hero card).

### Elevation

- Cards sit on `canvas` with a 1 px `line` border and no shadow.
- Only floating elements (bottom sheets, menus) get a shadow: a single soft one, `0 8 24` at 8% `ink`.
- Do not add shadows to every card.

---

## 5. Iconography, logos, and Chito

### 5.1 UI icons

- One outline icon set, 1.5 px stroke, 24 dp (Phosphor or Lucide are good fits). Never mix sets.
- Use icons only where they replace a word the user must learn once (navigation, search, filter, add, the quick actions on Home).
- Rows in a list do not get an icon: no category icon on transactions, no category icon on budget cards. Category is text.
- No emoji. Empty states are text, Chito, and one action.

### 5.2 Banks and e-wallets

Users recognise accounts by logo first, name second. Show both.

**Logo tile:** 40 × 40 dp, 12 dp radius, white fill with a 1 px `line` border, logo centered with 6 dp padding. In dense lists use 32 dp.

**Fallback tile:** if a logo is missing, show a monogram (first one or two letters of the name, 600 weight) on `green-50`. Never leave a blank gray box.

**Private build:** ship with monogram tiles first. Add official logos only from each provider's brand assets, and review each provider's usage guidelines before any public release.

**Suggested launch list**

| Type | Providers |
|---|---|
| E-wallets | GCash, Maya, GoTyme, ShopeePay, GrabPay, Coins.ph |
| Traditional banks | BDO, BPI, Metrobank, Landbank, Security Bank, PNB, RCBC, UnionBank, China Bank, EastWest, PSBank |
| Digital banks | Maya Bank, GoTyme Bank, UnionDigital, SeaBank, CIMB, Tonik, Ownbank, MariBank |
| Other | Cash (generic wallet), Other bank (monogram) |

The onboarding account list needs the full list above with a search field, not six fixed rows.

**Brand and policy notes**

- Logos are trademarks. Use them only to identify the account, never to suggest the provider endorses or built the app.
- Add a short line in Settings > About: "Bank and e-wallet names and logos belong to their owners. SaanNaPunta is not affiliated with them."
- Ship logos as vector (SVG or Android vector drawable) so they stay sharp on every density.
- Keep logos in their original colors. Do not tint them green.

### 5.3 Chito

Chito is the app's mascot: a pixel-art monkey. He has one name everywhere: Chito. (The Accounts mockup says "Tarsi"; change it.)

**Where Chito appears (revised in 0.3)**

Chito is a companion on every screen and sheet, not only onboarding. He sits at the top of the content with one short line about what the user is looking at or doing, and he reacts to it (`ui/chito/ChitoCompanion`, lines in `ui/chito/ChitoLines`):

- Home: today's safe amount, over-budget warnings, a sleeping pose late at night. Tapping him cycles through tips.
- Add or review transaction: reacts as the amount is typed, for example "That's ₱250 over what's safe for today."
- Budgets, Accounts, Transactions, Insights, Settings, and every add/edit sheet: one line about that screen's numbers or task.
- The permissions step of onboarding stays without Chito, so the request reads as serious.

**Pose follows the numbers**

Waving (welcome, empty), thinking (asking for input, near a limit), default (neutral), thumbs up (on track), rich (money in, nothing spent), confused (over today's safe amount), broke (over budget), celebrate (income, backfill found), sleeping (late night).

**Motion**

Gentle idle breathing; a short hop and tilt when his line changes; a hop when tapped. All of it follows the system "remove animations" setting.

**Style**

- Pick the pose from the user's numbers (above). Still no sparkles or confetti.
- Chito speaks like the rest of the app: plain, short, active. Taglish is fine. No exclamation marks and no praise ("Galing mo!", "Great job!"). Say what happened: "You spent ₱1,420 less than last month at this point in the cycle."
- Sizes: up to 160 dp in onboarding, 64 to 76 dp on main screens, 48 to 56 dp in sheets.

---

## 6. Money definitions

Every number in the app comes from one of these. If a screen shows a different number for the same idea, it is a bug.

| Term | Definition | Shown on |
|---|---|---|
| Total balance | Sum of balances of all spendable accounts (e-wallets, bank accounts, cash). Savings accounts marked "savings" are excluded. | Home hero card |
| Net worth | Assets minus liabilities, including manually added assets and debts. | Accounts |
| Spent | Money out that is an expense. Excludes transfers between your own accounts and deposits to a savings goal. Includes fees. | Home, Budgets, Insights |
| Transfer | Money moved between your own accounts (BDO to GCash). Never counts as spending or income. Shown with both account logos. | Transactions |
| Savings goal | A named target you set money aside for (Emergency Fund, Ipon Jar). Deposits count as saved, not spent. Shown separately from category budgets. | Budgets |
| Cycle | The period from one payday to the day before the next (for semi-monthly pay: Nov 15 to Nov 29, then Nov 30 to Dec 14). Monthly and flexible cycles are also supported. | Home, Budgets, Insights |
| Cycle budget | Sum of the category budgets for the cycle, plus any savings goal contribution. | Budgets |
| Left this cycle | Sum of category budgets minus spent in those categories. Savings goals are not part of it. | Home, Budgets |
| Safe to spend per day | Left this cycle divided by days remaining in the cycle, including today. | Home, Budgets, Transactions |
| Available now | Balances of accounts marked spendable. | Insights |
| Upcoming | Recurring bills and expected income dated before the next payday. | Home, Insights |

**Pay date rules**

- If payday falls on a Saturday or Sunday, ask once: "Do you get paid on the Friday before?" Save the answer per cycle setting.
- Let the user edit a single cycle's payday, because real paydays move.
- "Days left" always counts to the same date everywhere on the same day.

---

## 7. Components

### Hero balance card (Home)

- Solid `green-900` background, 28 dp radius, `on-hero` text. This is the one dark, saturated surface in the app. The Budgets summary is a white card, not a second green card.
- Contents: "Total balance" caption, balance figure, then one line: "₱4,930 left this cycle · ₱410 a day".
- Optional hide-balance toggle in the screen header, not inside the card.
- No pattern, no gradient, no illustration, no "Live sync" badge.

### Standard card

White, 20 dp radius, 1 px `line` border, 16 dp padding. A card holds one idea. Do not nest cards inside cards; use dividers inside instead. This applies to Upcoming (list of dues) and category rows too.

### Account card

White, 20 dp radius, 1 px `line` border. Logo tile (40 dp), provider name, optional nickname, masked number, balance in 16 sp 600. Status line in 13 sp `ink-muted`: "Last message read 2m ago" or "Manual account". Group on the Accounts screen by type: E-wallets, Banks, Cash. On Home, accounts are a horizontal strip of compact versions (logo tile, name, balance).

### Transaction row

```
[logo]  Jollibee Gapan                  −₱289.00
        GCash · Food & Dining           12:45 PM
```

- Left: logo tile of the account used.
- Title in 15 sp 500, subtitle in 13 sp `ink-muted`.
- Amount right-aligned, 16 sp 600. Income in `green-600` with `+`. Transfers in `ink`, no sign, with both logos.
- No category icon. Category is text.
- Rows are 64 dp tall. Hairline divider inset to align with the text, not the logo.
- A row that was read from a message shows "From GCash message" in the detail view, not on the row.

### Budget card

- Category name and "₱2,100 of ₱5,000".
- Thin progress bar, 8 dp tall, fully rounded. Fill `green-600`; switches to `warn` at 85% and `over` past 100%.
- Caption underneath: "₱2,900 left · ₱116 a day". Near or over limit: add the label ("Near limit", "Over by ₱310") in `warn` or `over`.
- Subtitle lists examples in `ink-muted` ("Meralco, water, fiber"). No icon.

### Savings goal card

Same layout as the budget card, but the caption reads "₱2,570 saved of ₱4,000" and the fill stays `green-600` (never `warn` or `over`). Label: "Savings goal". Placed under the category budgets, with its own section title.

### Segmented control

Used for List view / Calendar view, and All / Assets / Liabilities. Track `green-50`, selected segment white with a 1 px `line` border. Text only, no icons.

### Calendar heatmap (Transactions)

- Each day is a 40 dp tile; the selected day is `green-600` with white text; paydays have a small ring.
- Intensity is relative to the safe-to-spend-per-day figure, not fixed peso amounts: no spending, under the daily amount, over the daily amount. Use `green-50`, `green-100`, and `warn` text on the date number (not a fill).
- Legend below the grid, with labels in words.
- Tapping a day shows its total and the transactions under the grid.

### Buttons

| Type | Style |
|---|---|
| Primary | `green-600` fill, white label, 14 dp radius, 52 dp tall, full width on forms |
| Secondary | White fill, 1 px `line` border, `ink` label |
| Text | `green-600` label, no container |

One primary button per screen. Labels say exactly what happens: "Save budget", "Add account", "Add 4 accounts".

### Chips and filters

Fully rounded. Unselected: white with `line` border. Selected: `green-100` fill, `green-700` text, no border.

### Bottom navigation

Four items: Home, Transactions, Budgets, Insights. White bar with a 1 px top `line`, labels always visible, active item in `green-600`. No pill indicator animation and no floating action button. Accounts is reached from Home ("See all") and from the avatar menu. "Add transaction" is a primary button placed within Home and Transactions.

### Inputs

White, 14 dp radius, 1 px `line` border, 56 dp tall, label above the field. Focus: 2 px `green-600` border. Amount inputs use the balance type style and a peso prefix.

### Bottom sheets

20 dp top radius, a small drag handle, one clear title, one primary action.

---

## 8. Screens

Status key: **Designed** means a mockup exists. **To design** means the spec describes it but there is no mockup yet.

| Screen | Status |
|---|---|
| Splash | Designed |
| Onboarding 1 to 4 and setup complete | Designed |
| Home | Designed, revise per below |
| Accounts | Designed, revise |
| Transactions (list and calendar) | Designed, revise |
| Budgets | Designed, revise |
| Insights | Designed, revise |
| Add or review transaction | To design |
| Permissions and privacy | To design |
| Settings | To design |
| Empty states | To design |

### Splash and onboarding

Splash: Chito, one line of what the app does, three short facts, one primary button "Get started". Keep the facts to what is true and checkable (section 9).

Onboarding is four steps with a progress line and Skip at the top right:

1. **Meet Chito.** What the app does and what it reads. One primary button.
2. **Pay cycle.** Tabs: May sahod / Baon or allowance. Options: semi-monthly (15th and 30th), monthly (end of month), flexible. Shows the estimated next payday with a Change action and the weekend rule from section 6.
3. **Accounts.** Searchable list of providers (section 5.2). The primary button names the count: "Add 4 accounts". After this step comes the permissions screen. Offer a way to skip: "I'll add transactions myself".
4. **First budget.** A cycle target with the safe amount per day, a starter template (Starting sahod or Student baon) and the category allocation. Student baon template: Food and baon meals 40%, Pamasahe 20%, School supplies and projects 13%, Ipon 27% (as a savings goal).

Setup complete: Chito (calm pose), a summary of pay cycle, accounts added, cycle budget, and where data is stored, and one primary button "Go to Home". No confetti.

### Home

```
┌────────────────────────────┐
│ Good evening, Juan     Hide│
│                            │
│ ┌────────────────────────┐ │
│ │ Total balance          │ │  green-900 card
│ │ ₱148,200.50            │ │
│ │ ₱4,930 left this cycle │ │
│ │ · ₱410 a day           │ │
│ └────────────────────────┘ │
│ [Income] [Expense] [Transfer]│  text + icon, all neutral
│                            │
│ Accounts              See all│
│ [GCash][Maya][BDO] →       │
│                            │
│ ┌────────────────────────┐ │
│ │ This cycle  Nov 15–29  │ │
│ │ ₱16,070 of ₱21,000     │ │
│ │ ▓▓▓▓▓▓▓▓░░░░           │ │
│ │ Payday in 12 days · Nov 30│
│ └────────────────────────┘ │
│                            │
│ Upcoming                   │
│ Converge Fiber  Nov 28 −₱1,500│
│                            │
│ Recent                See all│
│ [logo] Jollibee   −₱289.00 │
│ [logo] Salary    +₱24,000  │
│ [logo] Meralco   −₱2,310   │
├────────────────────────────┤
│ Home  Trans.  Budgets  Insights│
└────────────────────────────┘
```

- Optional Chito note (two lines, dismissible) between the greeting and the hero card.
- "Last 7 days" and "Spent today" cards move to Insights. "Spent today" survives as one caption in the cycle card: "₱289 spent today · under ₱410".
- The separate "Days until payday" card is gone; payday is the last line of the cycle card.
- Quick actions are Income, Expense, Transfer. Budget is in the bottom nav, so it does not repeat here.

### Accounts

Header: "Accounts" with a text action "Add account". Net worth card (white, not green): net worth figure, a one-line change vs last month, and a segmented control: All / Assets / Liabilities. Below, account cards grouped by type with a subtotal on each group header. A filter chip row (All, E-wallets, Banks) is fine. Primary button at the bottom: "Add account".

Status lines say what actually happens ("Last message read 2m ago", "Manual account"). The Chito insight card and the large decorative circle on the net worth card are removed. The "Stored on this phone" line appears once, at the bottom.

### Transactions

Segmented control: List / Calendar. Calendar view per section 7. Search field, filter chips (Account, Category, Date), then rows grouped by day (Today, Yesterday, then dates). Day headers show the daily total on the right in `ink`, not red. Payday groups get a plain "Payday" badge. If some transactions have no category, show one plain card: "3 transactions need a category" with an arrow. Primary button at the bottom: "Add transaction".

### Budgets

Period chips: current cycle, Monthly overall, Custom. Summary card (white, 20 dp): cycle budget spent versus total, a progress bar, and two figures side by side: "Safe to spend per day" and "Next payday". Then:

- **Upcoming bills** before payday (one card, divider rows).
- **Category budgets**: one budget card per category.
- **Savings goals**: one savings goal card per goal (Emergency Fund, Ipon Jar).

Primary button: "Add category budget". Support monthly and sahod-cycle periods (15th and 30th paydays).

### Insights

Keep to four cards, each with one sentence of plain-language summary:

1. **Spending pace.** This cycle vs last cycle at the same point, in pesos and percent.
2. **Where it went.** A horizontal bar list by category with amount and percentage. No pie, no segmented strip, no color legend.
3. **Before next payday.** Available now, bills due before payday, and what is left after them. This replaces "Liquidity and sahod run-rate".
4. **Top merchants.** Merchant, number of transactions, total, and share of spending.

Daily pattern (the weekday bars) lives here too, as a compact chart inside card 1. A Chito note sits at the top (two lines). One optional action card at the bottom: "Set a cap for dining", with one button.

### Add or review a transaction

A bottom sheet. Amount first and large, then account (with logo), category, date, note. Transfers show From and To account fields instead of a category. When a transaction was read from an SMS or notification, show a small "From GCash message" line so the user knows where it came from and can correct it. Primary button: "Save transaction". Secondary: "Delete". If the amount could not be read: "Couldn't read this message. Enter the amount yourself."

### Permissions and privacy

Because SMS and notification access is sensitive, give it its own calm screen before the system dialog: one sentence on what is read, one on what is not (nothing leaves the phone), and a primary button. No scary red, no marketing language, no Chito.

If the build is installed outside the Play Store, add a step explaining Android's "restricted settings" (section 12), with the exact taps to allow it. Always offer "I'll add transactions myself".

### Settings

- Stored on this phone: one line, and the date of the last backup.
- Back up and restore: export to an encrypted file, import from a file.
- Messages and notifications: permission status, and help for keeping the app running on phones with aggressive battery savers.
- Pay cycle: payday dates and the weekend rule.
- Language: English now, Filipino later.
- About: version, the logo disclaimer from section 5.2.

### Empty states

Text, Chito, and one action. Example: "No transactions yet. Add one, or turn on message reading for GCash."

---

## 9. Voice and copy

- Plain, short, active. Name things the way users do.
- Default language: English with natural Filipino terms where people actually use them (sahod, baon, utang, ipon, cash-in). Offer a Filipino language option later.
- Buttons name the action: "Save budget", not "Submit". The confirmation uses the same word: "Budget saved".
- Errors say what happened and what to do: "Couldn't read this message. Enter the amount yourself."
- Empty states invite one action: "No transactions yet. Add one or turn on message reading for GCash."
- No exclamation marks, no emoji, no praise like "Great job!" or "Galing mo!".
- Do not claim more than the app does. The app reads messages already on the phone; it does not connect to GCash, Maya, or any bank.

### Plain words

| Instead of | Use |
|---|---|
| Liquidity, Available liquid | Available now |
| Safe burn rate, Daily safe allowance | Safe to spend per day |
| Envelope, tracked envelopes | Category budget |
| Cutoff charge, Upcoming cutoff charge | Upcoming bill |
| Sahod run-rate | Before next payday |
| Estimated safety buffer | Safe to save |
| Link account, Connected, Live sync, Synced | Add account, Added, Last message read 2m ago |
| Auto-read SMS (as a badge) | Reads GCash messages |

Keep the terms people already know: sahod, baon, utang, ipon, net worth.

### Claims that must stay true

Use only claims you can verify in the shipped build.

| Do not write | Write instead |
|---|---|
| Bank-grade isolation | (remove) |
| Zero cloud telemetry | This app has no internet access (only if the build has no internet permission) |
| Encrypted and stored locally in the Philippines | Stored on this phone |
| Data Privacy Act | (remove; do not imply compliance) |
| Encrypted on device | Only if the database is actually encrypted |

"Stored on this phone" appears on the permissions screen, in Settings, and at the bottom of Accounts. Not on every screen.

---

## 10. Motion

- Motion responds to what the user does: a sheet slides up, a row confirms with a short fade, a progress bar fills to its new value.
- No entrance animations on every card, no animated gradients, no confetti, no sparkles.
- One exception worth considering: the balance figure counts up once when Home first opens.
- Durations 150 to 250 ms, standard ease-out. Respect the system "remove animations" setting.

---

## 11. Android and accessibility

- Build with Material 3 components as the base but restyle them to these tokens. Stock Material defaults (purple tint, default shapes) are the quickest way to look generic.
- Edge-to-edge layout with proper system bar insets.
- Touch targets at least 48 × 48 dp.
- Support system font scaling up to 200% without clipping amounts. Let rows grow in height. The app is for all ages, so test the whole app at the largest setting.
- Provide content descriptions for logos ("GCash account").
- Amount changes are described with words for screen readers: "minus 289 pesos".
- Never rely on color alone: budget states have text labels, charts have values.
- Plan a dark theme from the start by using tokens, but ship light first.

---

## 12. Private release notes

For the first build, shared with the owner and close friends outside the Play Store.

**Build**

- Kotlin, Jetpack Compose, Room. Material 3 restyled to the tokens above.
- Store money as whole centavos (integers), never floating point.
- Keep the message and notification parsers in their own module, with one unit test per provider format. Messages that cannot be parsed go to a "needs review" list.
- Merge duplicates when the same transaction arrives by SMS and by notification.

**Privacy**

- Leave the internet permission out of the manifest. Anyone can then verify in App info that the app cannot send data, and the copy can say so. The cost: no crash reporting and no in-app updates.
- Turn off Android automatic backup so data does not go to the cloud by accident. Offer an encrypted export and import instead (Settings).

**Install**

- Distribute a signed release APK. Create one signing key, and back it up in two places. If it is lost, friends must uninstall (and lose their data) to receive updates.
- On Android 13 and later, apps installed outside a store cannot receive SMS or notification access until the user allows restricted settings (App info, the ⋮ menu, "Allow restricted settings"). The exact wording varies by phone. Show this step on the permissions screen.
- Many phones stop background apps to save battery. Add a step that asks the user to set the app's battery use to unrestricted.
- Google is rolling out developer verification for sideloaded apps, starting in Brazil, Indonesia, Singapore, and Thailand and expanding globally in 2027. Register the app in the Android Developer Console before it reaches the Philippines.

**iOS later**

iOS apps cannot read SMS or other apps' notifications. An iOS version would rely on manual entry, quick add, and statement import, so make those fast and first-class from the start.

---

## 13. Suggestions

1. **Sahod-aware periods.** Many Filipinos are paid on the 15th and 30th. Let budgets run by pay cycle, not only by calendar month.
2. **Cash-in and transfer fees.** Show InstaPay and PESONet fees and e-wallet cash-in charges as their own line, since they are a real hidden cost.
3. **Utang tracker.** A simple "owed to me / I owe" list is a feature local users will recognise immediately.
4. **Transfers are not spending.** Moving money from BDO to GCash must not count as an expense. Show it as a transfer with both logos. The same goes for savings goal deposits.
5. **Trust cue.** A quiet "Stored on this phone" line in Settings and on the permissions screen is more convincing than a badge or lock icon.
6. **Low-end devices.** Many users are on budget Android phones. Avoid blur, heavy shadows, and large images; this design is already light.
7. **Fonts and glyphs.** Test the ₱ sign and tabular numbers on the real font file before you build every screen.
8. **Start with five screens.** Home, Transactions, Add transaction, Budgets, Accounts. Insights can follow once there is real data.
9. **Build the message parser from real messages.** Collect samples from your own phone and your friends' phones before writing parsers, and keep them as test fixtures.
10. **Backup before sharing.** Finish export and import before the first friend installs the app, because the data exists only on their phone.

---

## 14. Quick reference

| Property | Value |
|---|---|
| Theme | Light |
| Background / cards | `#F4F7F5` / `#FFFFFF` |
| Primary | `#09804D` |
| Hero card | `#0A3D29` (only one per app) |
| Text | `#0E1A14` |
| Font | Plus Jakarta Sans (no monospace) |
| Radii | 28 / 20 / 14 / 12 / pill |
| Borders | 1 px `#E3EAE5`, no card shadows |
| Icons | One outline set, 1.5 px, used sparingly, none in list rows |
| Account identity | Logo tile plus name, monogram fallback, brand color only inside the tile |
| Mascot | Chito, calm poses, limited screens |
| Spending color | `ink` (never red) |
| Cycle | Payday to the day before the next payday |

---

## Appendix A: Mockup fixes for the next design pass

| Screen | Fix |
|---|---|
| All | Pick one sample "today" (for example Mon, Nov 18) and use it everywhere. The mockups show Nov 23 on Transactions, 12 days left on Home and Budgets, and 6 days left on Insights. |
| All | Change all-caps labels, exclamation marks, and praise lines to the rules in sections 3 and 9. Remove the repeated lock-icon privacy footers. |
| All | Remove the monospace font (account numbers on Accounts). |
| Home | Total balance (₱48,200) must equal the sum of the accounts shown. On the Accounts screen the accounts add up to ₱148,200.50. |
| Home, Budgets | "Left to spend" must be one number. Home shows ₱3,420; Budgets shows ₱6,360. Recalculate per section 6 (reference data below). |
| Home | Remove the "Last 7 days" and "Spent today" cards and the separate payday card. Make quick actions neutral (no red Expense). Remove the nested cards in Upcoming. |
| Accounts | Replace the full-color GCash, Maya, and GoTyme cards with white account cards and logo tiles. Rename "Tarsi insight" or remove it. Remove the decorative circle. |
| Transactions | Daily target says ₱750; use the safe-to-spend-per-day number. Day totals in `ink`, not red. Make "Log new transaction" a full 52 dp primary button named "Add transaction". Remove "Stored in the Philippines" and "Data Privacy Act". |
| Budgets | Make the active cycle card white, not a second green card. Move Emergency Fund out of category budgets into Savings goals and out of the "spent" total. Remove category icons. Use one cycle range (Nov 15 to Nov 29). Remove the pink Utilities card fill. |
| Insights | Category list: drop the five-shade segmented bar; use bars with labels. Savings goal deposits should not appear as "Others and Utang repay". Rename Liquidity and Run-rate. |
| Onboarding | Setup complete says 3 accounts; the previous step added 4. Remove confetti, sparkles, and the celebration pose. Change "Link 4 Accounts" to "Add 4 accounts". Replace "Bank-grade isolation" and "Zero cloud telemetry". |

**Reference sample data**

| Item | Value |
|---|---|
| GCash / Maya / GoTyme / BPI / BDO | ₱14,850.50 / ₱8,420.00 / ₱83,000.00 / ₱19,950.00 / ₱21,980.00 |
| Total balance | ₱148,200.50 |
| Cycle | Nov 15 to Nov 29, payday Nov 30, today Nov 18 (12 days left) |
| Category budgets | Food ₱8,000, Utilities ₱4,500, Transportation ₱3,500, Personal ₱5,000 (₱21,000) |
| Spent in categories | ₱6,200 + ₱4,370 + ₱2,100 + ₱3,400 = ₱16,070 |
| Left this cycle | ₱4,930 |
| Safe to spend per day | ₱410 |
| Savings goal | Emergency Fund, ₱2,570 saved of ₱4,000 |
| Cycle budget | ₱25,000 (₱21,000 categories + ₱4,000 goal) |
