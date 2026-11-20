# Hearby: Product Spec

> *Miles apart, a heartbeat away.*

**Status:** Draft v0.2 (idea and planning stage, no code yet)
**Platform:** Android first
**Audience:** Long-distance couples first. Other relationships (family, siblings, friends) later.

---

## 1. The problem

Long distance and busy lives make it hard to stay connected with the person you love most. Even a text can feel like work when you're drained: what do I say, they'll reply, now I have to keep the conversation going. When days go by without contact, people end up feeling low, forgotten or unloved, even when their partner is thinking of them all the time.

## 2. The idea

Hearby is a **very low-effort way to tell your person "I'm thinking of you,"** and to *feel* it when they think of you.

- A cozy **home-screen widget** where a cute chibi version of the two of you lives: walking together, holding hands, peeking in to wave.
- Send a photo, a short text or a preset in a tap or two.
- Your partner's phone plays a **unique 2-beat heartbeat vibration** (*ba-dum*), different from every other notification. Even without looking at the phone, they know you're thinking of them.
- The message appears **inside their widget** with a small animation that matches its meaning (a kiss floats up and fades, a hug, a wave).

## 3. Brand and feel

- **Name:** Hearby (heart + nearby: "my heart is near you, even miles away").
- **Tone:** calm, cute and lovely. Soft pastels, rounded shapes, gentle motion. Never loud, never guilt-inducing.
- **Logo idea:** a heart worked into the wordmark (for example replacing the "ear" in *hearby*), possibly pulsing twice like the vibration.
- **Tagline options:** "Miles apart, a heartbeat away." / "Nearly there, always near."
- **Principles:**
  1. *Less but more meaningful:* every action takes seconds.
  2. *No guilt:* no streaks, no "you haven't messaged in 24 hours!"
  3. *Private by design:* everything is end-to-end encrypted. Only the two partners can ever read their messages, photos and personal details. Not Hearby, not Google, not anyone else. Things also disappear unless you choose to keep them.
  4. *Love isn't paywalled:* sending love and the heartbeat stay free forever.

> **Name check (to finish before launch):** a quick search found no app named Hearby. A "HEARBY" trademark exists in class 41 (live music information, Area4 Labs Inc.), an unrelated category. Do a formal trademark check for classes 9 and 45, and check the Play Store name and domains (hearby.app, gethearby.com) before launch.

---

## 4. Features

### 4.1 Pairing (v1)
- One partner per account.
- **Names are always the user's choice:** each person enters their own name (or nickname) during setup and can edit it anytime. The app never assumes or invents a name. Partners can also set a private nickname for each other ("my moon", "habibi") that only shows on their own phone.
- Pair by **invite link**, **QR code**, **user ID** or **short invite code**.
- Pairing is mutual: both people must accept.

### 4.2 The widget (v1), the heart of the app
- **Default size 4x4, resizable** (smaller layouts adapt gracefully).
- **Customizable:** themes, colors, a photo background, blur strength.
- Shows the **partner's latest message, photo or emoji** on a soft blurred background.
- **Quick actions:** send photo · write text · send preset · "felt it" tap-back.
- **Living couple scene:** the two chibi characters animate in short looping scenes:
  - walking together, holding hands, sitting under a tree, having fun
  - the partner's character occasionally **pops in from the side of the widget, looking for you and waving**
  - scenes rotate, with quiet idle moments in between to save battery
- **Message animations inside the widget:** the scene reacts to an incoming message with a matching moment (kiss, hug, wave, heart burst, "good night" stars). Animations vary randomly within each meaning so they never feel repetitive.
- Shows the **partner's local time**, their **status**, their **weather**, and the **meet-up countdown** (see below).

### 4.3 Sending love (v1)
- **Photo:** a quick in-app camera. Photos are **not saved to the gallery by default**, with a "save to gallery" toggle.
- **Custom text:** short, heartfelt messages.
- **Presets:** your own saved phrases plus suggested ones ("thinking of you 🤍", "drink water, cutie", "proud of you", "good night 🌙").
- **Cute custom emojis and stickers** in the Hearby style.

### 4.4 Receiving love (v1)
- **Signature 2-beat heartbeat vibration** (*ba-dum*) on its own notification channel.
- The message shows in the widget with its animation.
- **"Felt it" tap-back** 💗: one tap tells the sender their love landed, so they know it was felt and not just delivered.

### 4.5 Avatars (v1)
- The app **creates a cute default chibi pair** automatically, so it works right away.
- An **optional avatar builder**, all mix-and-match:
  - **Style:** man, woman in hijab, woman with hair showing.
  - **Hijab styles:** classic, khimar (long), turban wrap, with niqab.
  - **Hair:** short, buzz cut, side part, quiff, short curls, man bun, shoulder length, wolf cut, long and free, bun, ponytail, two braids, side braid, bob, big curls, bald.
  - **Beards and moustaches:** full natural beard, short boxed beard, short beard, 3-day stubble, goatee, Van Dyke, anchor, circle beard, classic, chevron, handlebar and pencil moustaches, clean shaven.
  - **Men's clothing:** shirt with rolled sleeves, full-sleeve tee, t-shirt, jumper, hoodie, jacket over tee, kurta, thobe; trousers, baggy jeans, joggers, shorts.
  - **Women's clothing:** abaya, gown, churidar with dupatta, saree, burqa (parda), top and jeans, top and long skirt, jumper dress.
  - **Shoes:** sneakers, boots, loafers, sandals, flats, heels.
  - **Colors:** skin tone, eyes, hair or hijab, main outfit color and a second color; plus glasses.
- Modest and cultural styles (hijab, modest dress, beards, and more over time) are first-class options, not afterthoughts.
- Built from **layered sprites** so every customization works with every animation.
- *Later:* generate an avatar from a selfie.

### 4.6 Privacy and disappearing messages (v1)
- **Everything is end-to-end encrypted** (see section 5, a hard requirement).
- Messages and photos **auto-disappear after 24 hours by default**, on both phones and on the server.
- Per-message **"keep"** option. Kept items go to the Memory Jar and stay encrypted.
- A global setting to change the default.
- **Discreet mode:** blur the widget's message and photo until you tap, for when other people can see your home screen.
- **App lock:** optional fingerprint, face or PIN lock.
- **Lock-screen notifications never show content.** They show "💗 Hearby" at most.

### 4.7 Gentle reminders (v1)
- A few soft nudges a day that mix two kinds of reminders:
  - **Connect:** "Mia might love hearing from you 💌", "Check on Mia: how's the day going? 👀", "Send Mia a little kiss 😘".
  - **Pray for each other:** "Take a moment to make dua for Mia and for you both 🤲", "Make dua that Allah keeps you two together and brings you close soon 🤲", "After your next prayer, make a little dua for Mia 🤲".
- Each person chooses the style: **love and dua** (Islamic wording), **love and prayer** (general wording), **love only**, or **off**, and how often or at what times.
- *Later:* optionally time the dua reminders right after the five daily prayers, using prayer times calculated for the chosen city (no GPS needed).
- Tone stays warm, never guilt-inducing. Reminders skip quiet hours, and love nudges skip if you've just messaged each other.

### 4.8 Time zones and sleep-aware delivery (v1)
- The widget shows the partner's **local time**.
- The partner's chibi **sleeps during their night**.
- Messages sent during their quiet hours **arrive silently**. The heartbeat vibration waits until they wake (quiet hours are user-set, with a default of 11pm to 7am in their time zone).
- The **receiving phone** decides when to vibrate, based on its own quiet hours, so the server never needs to know anyone's sleep schedule.

### 4.9 Tiny status (v1)
- Set a status: *at work · studying · sleepy · busy · free to talk · missing you*.
- The partner's character reflects it (holding a laptop, yawning, hugging a pillow).
- It answers "why haven't they replied?" without anyone typing a word.

### 4.10 Meet-up countdown (v1)
- Tap the ✈️ chip to **set, change or remove** the next date you'll be together, with an optional note ("Leo flies to Toronto").
- **Date not set yet?** Choose "We don't know the date yet" and the widget shows "✈️ soon", or **turn the countdown off** entirely.
- Both partners share one countdown; either can edit it, and the other gets a gentle heads-up when it changes.
- The widget shows *"12 days"*, the characters get more excited as the day gets closer, a playful tease comes the day before ("Guess who you're seeing tomorrow? 😍"), and something special plays on the day.

### 4.10a Widget states: small and expanded (v1)
- **At rest** the widget is small and only plays the softly blurred, painted couple scene: no buttons.
- **Tapping it expands** it to the full view with the message, partner info and all buttons. Tapping outside or ✕ shrinks it back.
- **Partner teasing scenes** rotate with the sweet ones: the partner knocks on your screen, pokes your character's cheek, squishes their face against the glass, plays hide and seek from a bush, or blows bubbles at you. Each phone shows the *other* person teasing.

### 4.10b Photo slideshow (v1)
- Send one photo or **several photos from the gallery** (up to 10). With two or more, the partner's widget plays a **slideshow**.
- The sender picks a transition, or a random mix: **fade in/out, blur in/out, swipe left/right/up/down, dip to white, dip to black, zoom**. A slow zoom drifts on each photo while it shows.
- The partner can switch the widget between the slideshow and the couple scene (🎞️).

### 4.10c Pop-up text (v1)
- A text mode where the message **types out like a typewriter in huge letters** (about 3/4 of the widget's height) and slides across like a news ticker.
- The background behind it is always **blurred**, so the text stands out.

### 4.10d Flying kiss and heart (v1)
- 💋 and ❤️ buttons in the expanded widget. The partner sees a kiss or heart **fly toward them, grow past the size of the widget and fade**.

### 4.10e "Only when they're looking" rule (v1)
- Pop-up text, flying kisses and hearts **only play while the partner is looking at their home screen**. They never play to a dark screen.
- The heartbeat vibration still happens on arrival, so the partner feels it in their pocket. The visual waits and plays as soon as they unlock and look.
- Several waiting surprises play one after another.

### 4.10f Special days (v1)
- A **shared calendar** of birthdays, anniversaries and other moments ("the day we met", "Nikah day", graduation), managed from the widget (📅). Either partner can add, edit or delete days, and the list syncs end-to-end encrypted.
- Each day has a type, who it's for (one person or both), a date, an optional "count the years" setting (age or years together) and a celebration style.
- **The day before:** a cheeky tease only (see "Teasing reminders" below). A personal day such as a birthday teases **only the partner**, to keep the surprise; a shared day such as an anniversary teases **both**.
- **On the day:**
  - The widget pops a message such as "Happy 23rd Birthday, Mia!" on Mia's phone, while Leo's says "It's Mia's 23rd birthday! Send Mia a wish 💌". Anniversaries say "Happy 3rd Anniversary!" to both.
  - A ribbon stays on the widget all day, and tapping it replays the celebration.
- **Celebrations** play across the widget: confetti, balloons, fireworks, a light show, or a **drone show** (lights forming a heart, then the age or the couple's initials in the night sky). "Surprise me" picks one that fits the occasion.
- Celebrations follow the "only when they're looking" rule.
- **Memory:** every celebrated day is logged in Memories (and the Memory Jar). Ages and year counts update automatically each year.
- **Teasing reminders:** reminders are playful, never nagging.
  - The day before, the app **only teases** ("What day is tomorrow 😉?", "Don't forget what day it is tomorrow 🤨", "Hint: 🎂 + Mia = ?") and gives no answer, so they can work it out themselves.
  - On the day itself, a **clear notification** says what day it is ("It's Mia's 23rd birthday! Send Mia a wish 💌"), alongside the celebration.
  - On the day, if a partner hasn't sent a wish by midday, it nudges them with humor ("Have you wished Mia yet? 👀 Just checking…", "Still no wish for Mia? Bold move 😬").
  - Teases never spoil a surprise: personal days only tease the partner.
- **Repeats:** each day can repeat every year, every month (for example a monthly "monthversary" on the 9th, the day you united), every week on a chosen weekday, every day, or just once. Monthly days can count months ("Happy 15th Monthversary").


- If both partners send love within the same minute, a special animation plays: the characters run to each other and hug, with sparkles. It's rare, so it feels magical.

### 4.12 Memory Jar (later)
- Kept messages and photos collect in a little jar.
- "Open the jar" on lonely days to pull out a random memory.

### 4.13 Their weather (later)
- The partner's character reflects the weather in **their city** (umbrella in the rain, scarf in the cold, sunglasses in the sun).
- Uses a city the user picks, **never live GPS**. The city is shared with the partner end-to-end encrypted, and each phone fetches the weather itself (no account, no user ID sent to the weather service).

### 4.14 Days together and anniversaries (later)
- A days-together counter and anniversary reminders, with special anniversary scenes.

### 4.15 Wear OS (later)
- The heartbeat vibration on your wrist, plus a tiny watch tile to send love back.

### 4.16 Unlockables (later)
- New outfits, scenes and widget themes unlock gently over time and through use.
- **No streaks** and nothing that punishes a missed day.

### 4.17 Expanding to other relationships (later)
- Parent and child, siblings, best friends: the same core with relationship-appropriate themes and wording.
- Possibly family circles (more than one connection) once the 1:1 experience is polished.

---

## 4b. The app (beyond the widget)

Opening Hearby feels like the widget opened up: the same calm painted scenery, glassy cards and your two characters. Four tabs: Home, Together, Memories and Me.

### 🏡 Home (v1)
- A large version of the living scene (today's place and season).
- Partner's time, mood and status, and the meet-up countdown.
- A quick-send bar: 💋 kiss, ❤️ heart, ✏️ message, 📷 photos, ✨ pop-up.
- **Mood check-in:** tap how you feel (happy, loved, sleepy, a bit down, stressed, unwell, excited, grateful); your partner sees it and gets a gentle heads-up.
- **Today's question:** daily couple questions in seven categories; answers unlock only once you've both replied.
- The next special day and the next planned date night.
- **This week:** a soft count of kisses, hearts, messages, photos and "felt it" taps from each of you ("You've thought of each other 28 times this week 💞"). It resets weekly and never shames anyone for a low number.
- **Between us:** the distance between your cities, the time difference and your partner's local time ("Different skies, same moon").
- A small "moment for you" (a love nudge or a dua reminder).

### 💞 Together
- (Daily questions moved to Home.)
- **Sleep together (later):** both screens dim to the same starry sky with the two characters asleep side by side, a "together for…" timer and optional sleep sounds (rain, ocean, soft noise). Heartbeats go quiet until either wakes up.
- **Plan a date night (later):** when, virtual or in person, where to go, food to try, an event or activity, and a movie to watch, with suggestions and a "Surprise me" button. Both see the plan and get a reminder on the day.
- **Our dua list:** things you're both praying for, ticked with "alhamdulillah" when answered.

### 📖 Memories (v1 core)
- **Memory Jar:** kept messages, photos and celebrated days; "open the jar" pulls a random memory.
- **Albums:** make albums from received photos and your own; play any album as a full-screen **slideshow** with transitions, show it on your own widget, or send it to your partner's widget.
- **Timeline** of celebrated days, visits and milestones.
- **"Open when…" letters:** sealed letters that unlock in a moment (you miss me, you can't sleep, a hard day, our next monthversary).
- **Bucket list with scenery:** dream places shown as little scenery pictures in the season you'd love to visit (or your own named spot that looks like one). Preview a place on your widget; ticked places join the timeline.

### ⚙️ Me (v1)
- Your character with the full wardrobe.
- **Secret gift ideas** for your partner, saved by occasion (birthday, anniversary, monthversary, Eid, just because) and marked as bought, never visible to them.
- Privacy: end-to-end encryption status, the safety code to verify with your partner, discreet mode, screenshot protection.
- Settings: reminders, quiet hours, scenery, status, pairing.

## 5. Privacy and security (hard requirement)

**Promise to users:** *only you and your partner can ever see what you share.* Every user has their own privacy, with no data leakage. Hearby's servers only relay and temporarily store **encrypted data they cannot read**.

### 5.1 End-to-end encryption (v1, non-negotiable)
- **Everything personal is encrypted on the sender's phone and only decrypted on the partner's phone:**
  - messages, presets sent, emojis, "felt it" tap-backs
  - photos (encrypted before upload, with the key sent inside an encrypted message)
  - status, meet-up countdown date, chosen city, time zone, avatar choices, widget scene state
  - Memory Jar items
- **Protocol:** use a proven, audited protocol. **Never invent our own crypto.** The candidate is the **Signal Protocol** (X3DH or PQXDH key agreement + Double Ratchet), which gives forward secrecy (old messages stay safe even if a key leaks later) and post-compromise security.
  - Option A: **libsignal** (Signal's official library). Most battle-tested. It's licensed under **AGPL-3.0**, which has implications for how the app's source code must be shared, so this needs a licensing decision.
  - Option B: a permissively licensed, audited Double Ratchet implementation built on Google Tink or libsodium primitives. This needs extra care and an audit.
- **Keys live only on the device**, in the Android Keystore (hardware-backed where available). Private keys never leave the phone and are never sent to the server.
- **Key verification at pairing:** QR-code pairing doubles as key verification (each phone checks the other's key fingerprint), which stops anyone from impersonating a partner. Link and code pairing show a "safety code" both partners can compare.
- **Key changes are visible:** if a partner's key changes (new phone, reinstall), the app clearly shows it.

### 5.2 What the server can and cannot see
| Server **cannot** see | Server **can** see (kept to a minimum) |
|---|---|
| Message text, photos, emojis, presets | That two accounts are paired |
| Status, countdown, city, time zone, avatar | That an encrypted message was sent, and roughly when |
| Memory Jar contents | Encrypted blob size (padded to fixed sizes to hide length) |
| Quiet hours and sleep schedule | Push-notification tokens |

- **Metadata minimization:** pad message sizes, keep minimal server logs with short retention, never log IP addresses with user IDs, and evaluate "sealed sender"-style techniques that hide the sender from the server.
- **Push notifications:** FCM payloads carry only an encrypted blob or a content-free "wake up" ping. Google sees nothing readable.

### 5.3 No data leakage, by design
- **No third-party advertising, tracking or analytics SDKs.** Ever.
- Crash reporting (if any) is scrubbed of all personal content and is opt-in.
- **Data minimization:** sign-up asks only for what's needed. No contacts access, no location, no phone-number requirement if it can be avoided.
- Encrypted ciphertext on the server is **deleted after 24 hours** (or right after delivery, whichever comes first) unless it's a kept item in an encrypted backup.
- **Screenshot protection** (optional) on the message view, and photos stay in app-private storage unless the user saves them.
- Each user's data is isolated, with strict server-side access rules so no account can ever read another account's records.

### 5.4 Backup, new phones and recovery
- Because keys live only on the device, **losing a phone means losing the keys.** With 24-hour disappearing messages this is mostly fine, and the couple simply re-pairs and verifies keys again.
- **Kept memories:** an optional **encrypted backup** protected by a user-chosen passphrase or recovery key that Hearby never knows (later version). If the passphrase is lost, the backup can't be recovered. That's the trade-off for real privacy.

### 5.5 Unpair and account deletion
- **Unpairing** deletes the session keys on both sides and wipes shared data from the server immediately.
- **Account deletion** removes everything from the server immediately and wipes local data.

### 5.6 Verification and compliance
- An **independent security audit** of the encryption and backend before public launch.
- A publicly documented security model ("how Hearby protects you"), in plain words.
- Accurate **Google Play Data Safety** disclosures.
- Compliance with applicable privacy laws for the launch region (e.g. GDPR, India's DPDP Act).
- A responsible-disclosure contact for security researchers.

---

## 6. Things to design carefully

- **Unpairing:** relationships sometimes end. Unpairing must be simple, respectful and immediate, with clear choices about deleting shared history. Either person can unpair without the other's consent.
- **No location tracking:** live location in couple apps can enable controlling behavior. Time zone and weather come from a chosen city only.
- **Abuse and safety:** easy unpair and block, and no features that pressure someone to respond (no read-receipt guilt, no streaks).
- **Battery:** widget animation must be light (short loops, idle periods, only animating while visible).
- **Notifications:** respect Do Not Disturb and the user's own system settings.

## 7. Making money (later)

- **Free forever:** pairing, sending love, the heartbeat, the widget, default avatars, disappearing messages.
- **Optional premium ("Hearby+"):** extra outfits, scenes, widget themes, animation packs, a bigger Memory Jar, Wear OS extras.
- One-time cosmetic purchases, possibly alongside a small subscription.

---

## 8. v1 scope summary

| Area | v1 | Later |
|---|---|---|
| Pairing (link, QR, ID, code), 1 partner | ✅ | Family circles, other relationships |
| App tabs: Home (questions, mood, weekly stats, distance), Memories (jar, timeline, open-when letters), Me | ✅ | |
| Sleep together, date night planner, albums, bucket list with scenery | | ✅ |
| End-to-end encryption of everything personal | ✅ | Encrypted passphrase backup |
| Discreet mode, app lock, content-free notifications | ✅ | |
| 4x4 resizable, customizable widget | ✅ | More layouts and themes |
| Small widget that expands on tap, partner teasing scenes | ✅ | More scenes |
| Photo slideshow with transitions | ✅ (basic transitions first) | Blur, dip and zoom transitions |
| Pop-up typewriter text, flying kiss and heart, shown only while looking | ✅ (needs device prototype) | |
| Living chibi couple scenes + partner peek-in | ✅ | More scenes |
| Photo (no gallery save by default), text, presets, emojis | ✅ | Voice notes, doodles |
| 2-beat heartbeat vibration | ✅ | Wear OS |
| In-widget message animations (meaning-based, randomized) | ✅ (keyword/emoji mapping) | AI mood detection |
| "Felt it" tap-back | ✅ | |
| Default avatars + avatar builder | ✅ | Selfie-to-avatar |
| 24-hour disappearing messages + keep option | ✅ | |
| Gentle daily reminders | ✅ | |
| Time zones + sleep-aware delivery | ✅ | |
| Tiny status on character | ✅ | |
| Meet-up countdown | ✅ | |
| "In sync" moments | | ✅ |
| Memory Jar | | ✅ |
| Partner's weather | | ✅ |
| Days together and anniversaries | | ✅ |
| Unlockables (no streaks) | | ✅ |
| Premium cosmetics | | ✅ |

---

## 9. Key screens

1. **Welcome and sign-up:** a short, cute intro to the heartbeat idea.
2. **Pairing:** share link, show QR code, scan QR code, enter ID or code.
3. **Avatar:** see the default chibi pair → "Make it look like me" (builder) or "Keep it cute."
4. **Widget setup:** a guided "add Hearby to your home screen" step, then theme customization.
5. **Home (in app):** the live couple scene, partner status, local time, countdown, latest messages.
6. **Send:** camera · text · presets · emojis.
7. **Message view:** the message with its animation, a "felt it" button and a "keep" toggle.
8. **Memory Jar** (later).
9. **Settings:** status, quiet hours, reminders, disappearing-message default, vibration test, the partner's city, unpair, account.

---

## 10. Technical approach (initial thinking)

| Concern | Approach |
|---|---|
| Language and UI | Kotlin, Jetpack Compose |
| Widget | App widget built on RemoteViews (Jetpack Glance where it fits). Frame animations via `ViewFlipper`-style flipbook loops of sprite frames. |
| Art style | Chibi or pixel-art characters at a low frame rate, which looks intentionally cute within widget limits. Layered sprites (body, hair, outfit, accessories). |
| Heartbeat vibration | A dedicated notification channel with a custom vibration pattern (a "lub-dub," roughly `[0, 120, 90, 200]` ms), fine-tuned by testing on real devices. Note that a channel's pattern is fixed once created and users can override it in system settings. |
| Real-time delivery | Firebase Cloud Messaging (high-priority data messages carrying only ciphertext or a content-free ping) to wake the app, which decrypts and updates the widget locally. |
| Backend | Firebase Auth, Firestore (pairing records and encrypted message blobs), Cloud Storage (encrypted photos), Cloud Functions (delivery fan-out, 24-hour deletion). Strict security rules isolate every account. Firebase is a starting point; a self-hosted relay is an option if it reduces metadata exposure. |
| Disappearing messages | A server-side scheduled delete (Firestore TTL + a Storage cleanup function), plus local cleanup on the device. |
| Camera | CameraX. Captures go to app-private storage, are encrypted before upload, and are only written to the gallery if the user chooses. |
| Sleep-aware delivery | Handled on the receiving phone: messages always arrive as silent data, and the phone plays the heartbeat now or at the end of its own quiet hours. The server never learns anyone's schedule. |
| Weather (later) | A weather API queried by the chosen city. |
| Security | **End-to-end encryption from v1** with the Signal Protocol (see section 5). Keys in the Android Keystore. TLS in transit. The server stores only ciphertext. |

### Known platform limits
- Widgets can only play **short flipbook-style loops**, not smooth free-form animation. The art style is designed around this.
- Android launchers may throttle frequent widget updates, so animation and updates must stay lightweight.
- **iOS (future):** custom vibration patterns aren't possible for push notifications while the app is closed. A heartbeat *sound* would stand in for the vibration there.
- **Slideshow:** Android widgets support photo flipping with simple built-in transitions (fade, slide) through `AdapterViewFlipper`. Blur, dip-to-white/black and zoom transitions would need pre-rendered frames or the overlay approach below.
- **Pop-ups and flying kisses:** a standard widget can't draw large free-form animations or tell whether someone is looking at the home screen. The planned approach:
  - Detect "the person is looking" with the screen-on / user-unlocked system events (`ACTION_USER_PRESENT`).
  - Play the animation in a short-lived, transparent **overlay positioned exactly over the widget** (needs the "display over other apps" permission, which Google Play reviews carefully), or else a brief full-screen moment when the app opens.
  - This needs a prototype on real devices early, because it's the riskiest technical part of the app.

### Biggest cost: the art
Scenes × animation frames × customization options multiply quickly. Layered sprites keep this manageable. We'll need either an illustrator or a carefully chosen asset pipeline.

---

## 11. Competitors to study

- **Couple and widget apps:** Milo (Couple Widget), Couples Widget, Locket, Widgetable (animated pets in widgets).
- **Couple apps:** Paired, Between, Lovewick, Nearish, Cupla.
- **Hardware:** Bond Touch bracelets (the tap-to-vibrate feeling).

**Hearby's edge:** the signature heartbeat vibration + a living chibi couple in the widget + a calm, guilt-free, low-effort design + true end-to-end encrypted privacy.

---

## 12. Open questions

1. Which art style exactly: chibi illustration or pixel art? Who draws it?
2. Which animation "meanings" ship in v1 (kiss, hug, wave, miss you, good night, proud of you, ...)?
3. Maximum length of a custom text?
4. Should "felt it" be automatic (on viewing the widget) or always a deliberate tap? *Current lean: a deliberate tap.*
5. Launch region and languages?
6. Exact heartbeat vibration pattern (to be tuned on real devices).
7. E2EE library: libsignal (AGPL-3.0, affects source-code licensing) or a permissively licensed audited alternative?
8. Backend: Firebase, or a self-hosted relay to reduce metadata exposure?
