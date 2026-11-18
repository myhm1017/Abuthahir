# Hearby: Product Spec

> *Miles apart, a heartbeat away.*

**Status:** Draft v0.1 (idea and planning stage, no code yet)
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
  3. *Private by default:* things disappear unless you choose to keep them.
  4. *Love isn't paywalled:* sending love and the heartbeat stay free forever.

> **Name check (to finish before launch):** a quick search found no app named Hearby. A "HEARBY" trademark exists in class 41 (live music information, Area4 Labs Inc.), an unrelated category. Do a formal trademark check for classes 9 and 45, and check the Play Store name and domains (hearby.app, gethearby.com) before launch.

---

## 4. Features

### 4.1 Pairing (v1)
- One partner per account.
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
- An **optional avatar builder**: hair, skin tone, eyes, outfit and accessories.
- Built from **layered sprites** so every customization works with every animation.
- *Later:* generate an avatar from a selfie.

### 4.6 Privacy and disappearing messages (v1)
- Messages and photos **auto-disappear after 24 hours by default**.
- Per-message **"keep"** option. Kept items go to the Memory Jar.
- A global setting to change the default.

### 4.7 Gentle reminders (v1)
- If you haven't sent anything in a day, a soft nudge: *"Your person might love hearing from you today 🌙"*.
- Users choose the reminder time, or turn reminders off.

### 4.8 Time zones and sleep-aware delivery (v1)
- The widget shows the partner's **local time**.
- The partner's chibi **sleeps during their night**.
- Messages sent during their quiet hours **arrive silently**. The heartbeat vibration waits until they wake (quiet hours are user-set, with a default of 11pm to 7am in their time zone).

### 4.9 Tiny status (v1)
- Set a status: *at work · studying · sleepy · busy · free to talk · missing you*.
- The partner's character reflects it (holding a laptop, yawning, hugging a pillow).
- It answers "why haven't they replied?" without anyone typing a word.

### 4.10 Meet-up countdown (v1)
- Set the next date you'll be together.
- The widget shows *"12 days until we're together ✈️"*. The characters get more excited as the day gets closer, and something special plays on the day.

### 4.11 "In sync" moments (later)
- If both partners send love within the same minute, a special animation plays: the characters run to each other and hug, with sparkles. It's rare, so it feels magical.

### 4.12 Memory Jar (later)
- Kept messages and photos collect in a little jar.
- "Open the jar" on lonely days to pull out a random memory.

### 4.13 Their weather (later)
- The partner's character reflects the weather in **their city** (umbrella in the rain, scarf in the cold, sunglasses in the sun).
- Uses a city the user picks, **never live GPS**.

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

## 5. Things to design carefully

- **Unpairing:** relationships sometimes end. Unpairing must be simple, respectful and immediate, with clear choices about deleting shared history. Either person can unpair without the other's consent.
- **No location tracking:** live location in couple apps can enable controlling behavior. Time zone and weather come from a chosen city only.
- **Abuse and safety:** easy unpair and block, and no features that pressure someone to respond (no read-receipt guilt, no streaks).
- **Battery:** widget animation must be light (short loops, idle periods, only animating while visible).
- **Notifications:** respect Do Not Disturb and the user's own system settings.

## 6. Making money (later)

- **Free forever:** pairing, sending love, the heartbeat, the widget, default avatars, disappearing messages.
- **Optional premium ("Hearby+"):** extra outfits, scenes, widget themes, animation packs, a bigger Memory Jar, Wear OS extras.
- One-time cosmetic purchases, possibly alongside a small subscription.

---

## 7. v1 scope summary

| Area | v1 | Later |
|---|---|---|
| Pairing (link, QR, ID, code), 1 partner | ✅ | Family circles, other relationships |
| 4x4 resizable, customizable widget | ✅ | More layouts and themes |
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

## 8. Key screens

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

## 9. Technical approach (initial thinking)

| Concern | Approach |
|---|---|
| Language and UI | Kotlin, Jetpack Compose |
| Widget | App widget built on RemoteViews (Jetpack Glance where it fits). Frame animations via `ViewFlipper`-style flipbook loops of sprite frames. |
| Art style | Chibi or pixel-art characters at a low frame rate, which looks intentionally cute within widget limits. Layered sprites (body, hair, outfit, accessories). |
| Heartbeat vibration | A dedicated notification channel with a custom vibration pattern (a "lub-dub," roughly `[0, 120, 90, 200]` ms), fine-tuned by testing on real devices. Note that a channel's pattern is fixed once created and users can override it in system settings. |
| Real-time delivery | Firebase Cloud Messaging (high-priority data messages) to wake the app and update the widget. |
| Backend | Firebase Auth, Firestore (pairs, messages, status), Cloud Storage (photos), Cloud Functions (fan-out, quiet-hours scheduling, 24-hour deletion). |
| Disappearing messages | A server-side scheduled delete (Firestore TTL + a Storage cleanup function), plus local cleanup on the device. |
| Camera | CameraX. Captures go to app-private storage and are only written to the gallery if the user chooses. |
| Sleep-aware delivery | The partner's time zone and quiet hours are stored server-side. Messages sent during quiet hours are delivered silently, and the heartbeat is scheduled for wake-up time. |
| Weather (later) | A weather API queried by the chosen city. |
| Security | Encrypted in transit and at rest. Consider end-to-end encryption for messages and photos in a later version. |

### Known platform limits
- Widgets can only play **short flipbook-style loops**, not smooth free-form animation. The art style is designed around this.
- Android launchers may throttle frequent widget updates, so animation and updates must stay lightweight.
- **iOS (future):** custom vibration patterns aren't possible for push notifications while the app is closed. A heartbeat *sound* would stand in for the vibration there.

### Biggest cost: the art
Scenes × animation frames × customization options multiply quickly. Layered sprites keep this manageable. We'll need either an illustrator or a carefully chosen asset pipeline.

---

## 10. Competitors to study

- **Couple and widget apps:** Milo (Couple Widget), Couples Widget, Locket, Widgetable (animated pets in widgets).
- **Couple apps:** Paired, Between, Lovewick, Nearish, Cupla.
- **Hardware:** Bond Touch bracelets (the tap-to-vibrate feeling).

**Hearby's edge:** the signature heartbeat vibration + a living chibi couple in the widget + a calm, guilt-free, low-effort design.

---

## 11. Open questions

1. Which art style exactly: chibi illustration or pixel art? Who draws it?
2. Which animation "meanings" ship in v1 (kiss, hug, wave, miss you, good night, proud of you, ...)?
3. Maximum length of a custom text?
4. Should "felt it" be automatic (on viewing the widget) or always a deliberate tap? *Current lean: a deliberate tap.*
5. Launch region and languages?
6. Exact heartbeat vibration pattern (to be tuned on real devices).
