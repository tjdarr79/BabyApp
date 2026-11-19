# Behavior report: attentive caregiver, 2 days

Engine schema v1, parameter set v1. 3 seeds per combination; the caregiver checks every 2 min. Day = 07:00–19:00, night = 19:00–07:00. Day 1 = the first 24 h; median, p10 and p90 are across seeds over all 2 days.

Parameter values stay `UNREVIEWED` until the childcare reviewer signs off (counts below). Targets come from docs/06.
A metric marked **OUT** is outside its target; *Levers* names the parameters that move it.

## Summary

| Band | Temperament | Out of range | Metrics out of range |
|---|---|---|---|
| M2_3 | AVERAGE | 5 of 13 | Care requests per day, Longest daytime gap with no care request, Longest awake gap with no audible activity, Night wakings per night, Fussing and crying per 24 h |

## Parameters in use

212 values, 188 `UNREVIEWED`. By evidence: PEER_REVIEWED 6, GUIDANCE 13, SECONDARY 7, DESIGN_ASSUMPTION 186.

`DESIGN_ASSUMPTION` values by file (no adequate source; the reviewer decides these):

- `age/M10_12.json`: feedIntervalMinutes, feedDurationMinutes, feedsPer24h, feedAcceptMin, wakeWindowMinutes, sleepCycleMinutes, dayNapMinutes, catnapProb, fussCryMinutesPer24h, eveningFussClustering, circadianStrength, wetDiapersPerDay, soiledDiapersPerDay, vocalRatePerAwakeHour, boredAfterMinutes, careSustainMinutes, crySegmentMaxMinutes, selfSettleProbNight
- `age/M2_3.json`: feedDurationMinutes, feedAcceptMin, napsPerDay, wakeWindowMinutes, dayNapMinutes, catnapProb, fussCryMinutesPer24h, circadianStrength, vocalRatePerAwakeHour, boredAfterMinutes, careSustainMinutes, crySegmentMaxMinutes, selfSettleProbNight
- `age/M4_6.json`: feedDurationMinutes, feedAcceptMin, napsPerDay, wakeWindowMinutes, sleepCycleMinutes, dayNapMinutes, catnapProb, fussCryMinutesPer24h, eveningFussClustering, circadianStrength, wetDiapersPerDay, vocalRatePerAwakeHour, boredAfterMinutes, careSustainMinutes, crySegmentMaxMinutes, selfSettleProbNight
- `age/M7_9.json`: feedIntervalMinutes, feedDurationMinutes, feedsPer24h, feedAcceptMin, napsPerDay, wakeWindowMinutes, sleepCycleMinutes, dayNapMinutes, catnapProb, fussCryMinutesPer24h, eveningFussClustering, circadianStrength, wetDiapersPerDay, soiledDiapersPerDay, vocalRatePerAwakeHour, boredAfterMinutes, careSustainMinutes, crySegmentMaxMinutes, selfSettleProbNight
- `age/NEWBORN.json`: feedDurationMinutes, feedsPer24h, feedAcceptMin, totalSleepHours24, napsPerDay, wakeWindowMinutes, dayNapMinutes, catnapProb, nightWakings, fussCryMinutesPer24h, circadianStrength, wetDiapersPerDay, soiledDiapersPerDay, vocalRatePerAwakeHour, boredAfterMinutes, careSustainMinutes, crySegmentMaxMinutes, selfSettleProbNight
- `model.json`: distressRisePerMinute, distressFallPerMinute, fussThreshold, cryThreshold, intenseCryThreshold, calmThreshold, intensityBiasStep, escalateAfterMinutes, maxEscalationLevel, hungerCauseWeight, hungerOverdueGain, tiredCauseWeight, overtiredMargin, overtiredCauseWeight, diaperCauseWeight, diaperCueAt, diaperDiscomfortPerMinuteWet, diaperDiscomfortPerMinuteSoiled, wetnessPerVoid, postFeedVoidMultiplier, postFeedVoidWindowMinutes, unexplainedFussCauseWeight, unexplainedFussPerAwakeHour, eveningFussMultiplier, eveningStartLocalMinutes, eveningEndLocalMinutes, fussSpellMinutes, boredCauseWeight, wantsContactCauseWeight, causeStackBonus, drowsyToSleepMinutes, catnapMinutes, restedSleepPressure, nightHungerRateScale, nightSleepPressureDecayScale, nightSleepPressureRiseBoost, sleepNoiseProbPerArousal, wakeTransitionDistress, moodRatePerMinute, moodVocalGain, autoPauseUnansweredRequests, autoPauseHoursWithoutResponse, actionEffects, partialReliefFactor, worsenFactor, sootheFallMultiplier, hungerComfortMinutes, satiatedHunger, nonNutritiveComfort, gasPerFeedMinute, gasDecayPerMinute, gasDiscomfortAt, gasCauseWeight, burpableGas, burpSuccessProb, gasPerBurp, spontaneousBurpPerMinute, playStimulationPerMinute, stimulationDecayPerMinute, overstimAt, overstimCauseWeight, settleMinutes, rockDrowsyFactor, transferStayAsleepProb, changeTransitionDistress, putDownTransitionDistress
- `temperament/AVERAGE.json`: rhythmicitySigmaScale, sootheTimeScale, unexplainedFussHazardScale, careSustainScale, overstimThresholdScale, transitionDistress, selfSettleScale, contentVocalRateScale, cryIntensityBias
- `temperament/DEMANDING.json`: rhythmicitySigmaScale, sootheTimeScale, unexplainedFussHazardScale, careSustainScale, overstimThresholdScale, transitionDistress, selfSettleScale, contentVocalRateScale, cryIntensityBias
- `temperament/EASYGOING.json`: rhythmicitySigmaScale, sootheTimeScale, unexplainedFussHazardScale, careSustainScale, overstimThresholdScale, transitionDistress, selfSettleScale, contentVocalRateScale, cryIntensityBias
- `temperament/HIGHLY_SENSITIVE.json`: rhythmicitySigmaScale, sootheTimeScale, unexplainedFussHazardScale, careSustainScale, overstimThresholdScale, transitionDistress, selfSettleScale, contentVocalRateScale, cryIntensityBias

## M2_3 / AVERAGE

| Metric | Day 1 | Median | p10 | p90 | Target | Status | Levers |
|---|---|---|---|---|---|---|---|
| Care requests per day | 2.00 | 2.50 | 2.50 | 2.90 | 8.00–13.00 | **OUT** (median 2.50) | careSustainMinutes, model.cryThreshold, feedIntervalMinutes, wakeWindowMinutes |
| Night care requests per night | 0.00 | 1.00 | 1.00 | 1.40 | 1.00–3.00 | pass | feedIntervalMinutes, selfSettleProbNight, circadianStrength, model.nightHungerRateScale |
| Longest daytime gap with no care request h | 7.66 | 10.17 | 8.16 | 11.63 | ≤ 3.50 | **OUT** (p90 11.63) | careSustainMinutes, model.cryThreshold, feedIntervalMinutes, wakeWindowMinutes |
| Longest awake gap with no audible activity min | 26.00 | 30.00 | 26.80 | 42.80 | ≤ 15.00 | **OUT** (p90 42.80) | vocalRatePerAwakeHour, model.moodVocalGain |
| Audible events per hour | 2.46 | 2.67 | 2.27 | 2.80 | — | — |  |
| Total sleep per 24 h h | 16.22 | 16.46 | 16.26 | 16.56 | 14.00–17.00 | pass | wakeWindowMinutes, totalSleepHours24, sleepCycleMinutes, circadianStrength, model.restedSleepPressure |
| Naps per day | 4.00 | 3.50 | 3.10 | 3.90 | 3.00–5.00 | pass | wakeWindowMinutes, totalSleepHours24, sleepCycleMinutes, circadianStrength, model.restedSleepPressure |
| Median daytime nap length min | 167.00 | 164.50 | 144.30 | 165.30 | — | — |  |
| Night wakings per night | 4.00 | 3.50 | 3.50 | 4.70 | 1.00–3.00 | **OUT** (median 3.50) | feedIntervalMinutes, selfSettleProbNight, circadianStrength, model.nightHungerRateScale |
| Fussing and crying per 24 h min | 75.00 | 89.75 | 76.35 | 95.35 | 35.00–80.00 | **OUT** (median 89.75) | model.unexplainedFussPerAwakeHour, model.distressRisePerMinute, model.distressFallPerMinute, model.cryThreshold |
| Feeds per day | 5.00 | 6.00 | 5.60 | 6.00 | 6.00–8.00 | pass | feedIntervalMinutes, feedAcceptMin, feedDurationMinutes |
| Median time between feeds min | 238.00 | 238.00 | 234.80 | 253.20 | 180.00–240.00 | pass | feedIntervalMinutes, feedAcceptMin, feedDurationMinutes |
| Wet diapers per day | 7.00 | 8.00 | 7.60 | 10.40 | 6.00–8.00 | pass | wetDiapersPerDay, model.postFeedVoidMultiplier |
| Diaper changes per day | 5.00 | 6.00 | 4.80 | 6.80 | — | — |  |
| Day-to-day schedule similarity | n/a | 0.00 | 0.00 | 0.20 | ≤ 0.60 | pass | rhythmicitySigmaScale |
| Auto-pauses | 0.00 | 0.00 | 0.00 | 0.00 | 0 | pass | careSustainMinutes, model.cryThreshold, feedIntervalMinutes, wakeWindowMinutes |

