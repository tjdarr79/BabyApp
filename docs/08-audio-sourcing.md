# 08 — Audio Library Sourcing

**Status:** research for owner decision · **Researched:** 2026-11-19
**Limitation:** this environment couldn't load the vendor sites directly. Findings come from search-result excerpts. **Every licence term below must be confirmed on the live page, ideally in writing from the vendor, before purchase.**

## 1. Requirements (brief audio addendum + `01` A4/A5)

| Requirement | Implication |
|---|---|
| Authentic, natural recordings, not synthetic | **Excludes AI-generated SFX** (e.g. [ElevenLabs sound effects](https://elevenlabs.io/sound-effects/baby-crying)) even where licensed |
| Categorized by age, type, intensity, duration, state | Need many clips across **newborn → 12 months**, with fussing, crying (3 intensities), coos, babble, giggles, burps, sleep sounds and settling |
| Avoid repetition | Plan **≥ 6–10 clips per category per age group** |
| Paid app, offline, bundled in APK | Licence must allow **embedding in distributed software**, **perpetually**, without per-download fees |
| Asset manifest | Record filename, source URL, creator, licence, attribution and download date per clip |

## 2. Licence screen: which sources are usable

| Source type | Verdict | Why |
|---|---|---|
| **One-time-purchase pro SFX libraries** (A Sound Effect, BOOM Library, Sound Ideas) | ✅ **Preferred** | Perpetual, royalty-free, one-time cost. Fits a one-time-purchase app. *Confirm the app-embedding clause* |
| **Freesound CC0 clips** | ✅ Supplement | Free, no attribution. Licence is per-sound, so each clip must be checked. CC BY-NC is unusable ([cinevva guide](https://app.cinevva.com/guides/free-sound-effects-music)). Candidate infant clips exist (e.g. [josephvm #442655](https://freesound.org/people/josephvm/sounds/442655), [Duke Sonic Dictionary on panikko #144473](https://sonicdictionary.duke.edu/items/baby-crying.html)), licences unverified |
| **Pixabay** | ✅ Supplement | Commercial use allowed, no attribution. Bars standalone redistribution, so in-app embedding is fine ([Pixabay FAQ](https://pixabay.com/service/faq/)). Search didn't surface Pixabay infant clips. Needs a manual search |
| **BigSoundBank** | ⚠ Possible | Has infant recordings (e.g. ["Two months baby crying"](https://bigsoundbank.com/two-months-baby-crying-s0883.html)). Licence page frames use as permissive with optional credit/donation, but commercial terms weren't clear in the excerpt ([licence](https://bigsoundbank.com/droit.html)). Get written confirmation |
| **ZapSplat** | ⚠ Avoid as primary | Licence forbids sounds being the **"main value"** of an app and redistribution through apps ([ZapSplat FAQ](https://www.zapsplat.com/faq-category/license-and-usage/), [licence](https://www.zapsplat.com/license-agreement)). Baby audio is central to this app, so this is arguable. Free tier needs attribution |
| **Subscription libraries** (Epidemic Sound, Artlist, Soundsnap) | ❌ Avoid | Epidemic: content published after cancellation isn't cleared ([Epidemic help](https://help.epidemicsound.com/hc/en-us/articles/26254650213266-What-happens-when-a-subscription-is-canceled)), so every app update becomes a licensing question. Artlist: assets can't be used in new projects after expiry, and apps/games are routed to Enterprise ([Artlist help](https://help.artlist.io/hc/en-us/articles/29490991524253)). Soundsnap: covers apps but the grant is "revocable" and post-cancellation status is unclear ([Soundsnap licence](https://www.soundsnap.com/licence)). A recurring cost for a one-time-purchase app is a structural mismatch |

## 3. Candidate libraries

| Library | Contents (per excerpts) | Ages | Price | Fit |
|---|---|---|---|---|
| **Sound Ex Machina — "Baby Sounds"** (sold on A Sound Effect) | **160 recordings**, ~85 min, 24-bit/96 kHz WAV: cries, whimpers, screams, laughs, giggles, sighs, plus breastfeeding, sneezing, breathing. Soundminer metadata. "Stealth" (natural, uncontrolled) recording ([KVR](https://www.kvraudio.com/news/sound-ex-machina-releases-baby-sounds-library-45229), [A Sound Effect](https://asoundeffect.com/sound-library/Baby-Sounds)) | Newborn, infant, toddler. Track names include newborn, 3 months and 4 months ([Artlist track list](https://artlist.io/sfx/pack/baby-sounds/3527)) | **~$39** (royalty-free; user count selected at checkout) | **Best primary candidate** for 0–6 m. Natural recordings match the brief's "authentic" requirement |
| **Sound Ideas — "Baby Boom"** | 152 files, 96 kHz/24-bit ([Sound Ideas](https://sound-ideas.com/products/baby-boom-sound-effects)) | **3–24 months** | **~$65 CAD** | **Fills 7–12 m** (babble, older cries) and the future toddler band |
| **A Sound Effect — "Babies & Kids"** | Breathing, cooing, fussing, grunting, squealing, laughing, babbling, whimpering, whining and more ([A Sound Effect](https://asoundeffect.com/sound-library/Babies-Kids)) | Infants/kids (ages to confirm) | Not shown | Alternative/complement for coos and babble |
| **BOOM Library** infant pack | Mentioned in comparison results at ~€49 ([BOOM Library](https://www.boomlibrary.com/?p=5316)) | Not confirmed | ~€49 | Check contents before considering |

**Licence gap to close:** A Sound Effect's EULA "covers all Sound Library purchases… unless explicitly stated" ([A Sound Effect licence](https://asoundeffect.com/license-agreement)), but its app/software clauses couldn't be read here. Comparable pro-library licences commonly grant perpetual single-user rights including mobile apps and games (e.g. [Soundholder EULA](https://blogs.ed.ac.uk/dmsp-perception23/wp-content/uploads/sites/7848/2023/04/1-Soundholder-EULA-1.pdf)). That is evidence only, not confirmation.

## 4. Recommendation

**Primary:** buy **Sound Ex Machina "Baby Sounds" (~$39)** and **Sound Ideas "Baby Boom" (~$65 CAD)**: ~$90 USD one-time, perpetual, no subscription. Supplement with Freesound CC0 and Pixabay clips for variety, each verified individually.

Before buying, email each vendor this question and keep the reply as licence evidence:
> "We are building a paid Android app (one-time purchase) that simulates infant care. Your recordings would be edited, compressed and bundled inside the app as non-extractable assets, triggered by the app's simulation. The audio enhances the app; the app is not a sound library or soundboard. Does your standard single-user licence permit this, perpetually, including future app updates and an iOS version?"

**Also confirm:** the recordings were made with the parent's consent (the "stealth recording" description makes this worth asking; see `01` A5).

**Fallback** if licences don't cover the app or quality is too low: commission original recordings from a family with a written release. Cost and logistics TBD.

## 5. Coverage plan (clips needed per age group)

| Category | 0–4 wk | 2–3 m | 4–6 m | 7–9 m | 10–12 m | Likely source |
|---|---|---|---|---|---|---|
| Light fussing | 8 | 8 | 8 | 8 | 8 | SEM / Baby Boom |
| Crying (moderate) | 8 | 8 | 8 | 8 | 8 | SEM / Baby Boom |
| Crying (intense) | 6 | 6 | 6 | 6 | 6 | SEM / Baby Boom |
| Coos / content sounds | 4 (grunts) | 8 | 8 | 6 | 6 | SEM / Babies & Kids |
| Babbling | — | — | 4 | 8 | 8 | Baby Boom / Babies & Kids |
| Giggles / laughs | — | 2 | 6 | 6 | 6 | SEM / Baby Boom |
| Burps / hiccups | 4 | 4 | 4 | 4 | 4 | Freesound CC0 / libraries |
| Sleep sounds / breathing | 6 | 6 | 6 | 6 | 6 | SEM |
| Settling / whimper-to-calm | 6 | 6 | 6 | 6 | 6 | SEM |

~650 clip slots. Some clips are shared between adjacent bands where developmentally plausible (reviewer decides).

## 6. Processing pipeline (M6.3)
Trim → loudness-normalize (consistent perceived level per intensity tier) → mono → encode **Opus/OGG ~48 kbps** for APK size → generate `manifest.json` entries (filename, source URL, creator, licence, attribution, download date, age bands, type, intensity, duration, states) → `AudioManifestLicenseGateTest` rejects any entry without licence evidence or with NC/ND terms.
