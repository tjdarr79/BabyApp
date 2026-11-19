# Behavior report: attentive caregiver, 2 days

Engine schema v1, parameter set v1. 3 seeds per combination; the caregiver checks every 2 min. Day = 07:00–19:00, night = 19:00–07:00. Day 1 = the first 24 h; median, p10 and p90 are across seeds over all 2 days.

Parameter values stay `UNREVIEWED` until the childcare reviewer signs off (counts below). Targets come from docs/06.
A metric marked **OUT** is outside its target; *Levers* names the parameters that move it.

## Summary

| Band | Temperament | Out of range | Metrics out of range |
|---|---|---|---|
| M2_3 | AVERAGE | 6 of 13 | Care requests per day, Longest daytime gap with no care request, Longest awake gap with no audible activity, Night wakings per night, Fussing and crying per 24 h, Median time between feeds |

## Parameters in use

201 values, 184 `UNREVIEWED`. By evidence: PEER_REVIEWED 6, GUIDANCE 13, SECONDARY 7, DESIGN_ASSUMPTION 175.

`DESIGN_ASSUMPTION` values by file (no adequate source; the reviewer decides these):

- `age/M10_12.json`: feedIntervalMinutes, feedDurationMinutes, feedsPer24h, feedAcceptMin, wakeWindowMinutes, sleepCycleMinutes, fussCryMinutesPer24h, eveningFussClustering, circadianStrength, wetDiapersPerDay, soiledDiapersPerDay, vocalRatePerAwakeHour, boredAfterMinutes, careSustainMinutes, crySegmentMaxMinutes, selfSettleProbNight
- `age/M2_3.json`: feedDurationMinutes, feedAcceptMin, napsPerDay, wakeWindowMinutes, fussCryMinutesPer24h, circadianStrength, vocalRatePerAwakeHour, boredAfterMinutes, careSustainMinutes, crySegmentMaxMinutes, selfSettleProbNight
- `age/M4_6.json`: feedDurationMinutes, feedAcceptMin, napsPerDay, wakeWindowMinutes, sleepCycleMinutes, fussCryMinutesPer24h, eveningFussClustering, circadianStrength, wetDiapersPerDay, vocalRatePerAwakeHour, boredAfterMinutes, careSustainMinutes, crySegmentMaxMinutes, selfSettleProbNight
- `age/M7_9.json`: feedIntervalMinutes, feedDurationMinutes, feedsPer24h, feedAcceptMin, napsPerDay, wakeWindowMinutes, sleepCycleMinutes, fussCryMinutesPer24h, eveningFussClustering, circadianStrength, wetDiapersPerDay, soiledDiapersPerDay, vocalRatePerAwakeHour, boredAfterMinutes, careSustainMinutes, crySegmentMaxMinutes, selfSettleProbNight
- `age/NEWBORN.json`: feedDurationMinutes, feedsPer24h, feedAcceptMin, totalSleepHours24, napsPerDay, wakeWindowMinutes, nightWakings, fussCryMinutesPer24h, circadianStrength, wetDiapersPerDay, soiledDiapersPerDay, vocalRatePerAwakeHour, boredAfterMinutes, careSustainMinutes, crySegmentMaxMinutes, selfSettleProbNight
- `model.json`: distressRisePerMinute, distressFallPerMinute, fussThreshold, cryThreshold, intenseCryThreshold, calmThreshold, intensityBiasStep, escalateAfterMinutes, maxEscalationLevel, hungerCauseWeight, hungerOverdueGain, tiredCauseWeight, overtiredMargin, overtiredCauseWeight, diaperCauseWeight, diaperCueAt, diaperDiscomfortPerMinuteWet, diaperDiscomfortPerMinuteSoiled, wetnessPerVoid, postFeedVoidMultiplier, postFeedVoidWindowMinutes, unexplainedFussCauseWeight, unexplainedFussPerAwakeHour, eveningFussMultiplier, eveningStartLocalMinutes, eveningEndLocalMinutes, fussSpellMinutes, boredCauseWeight, wantsContactCauseWeight, causeStackBonus, drowsyToSleepMinutes, restedSleepPressure, nightHungerRateScale, nightSleepPressureDecayScale, nightSleepPressureRiseBoost, sleepNoiseProbPerArousal, wakeTransitionDistress, moodRatePerMinute, moodVocalGain, autoPauseUnansweredRequests, autoPauseHoursWithoutResponse, actionEffects, partialReliefFactor, worsenFactor, sootheFallMultiplier, hungerComfortMinutes, satiatedHunger, nonNutritiveComfort, gasPerFeedMinute, gasDecayPerMinute, gasDiscomfortAt, gasCauseWeight, burpableGas, burpSuccessProb, gasPerBurp, spontaneousBurpPerMinute, playStimulationPerMinute, stimulationDecayPerMinute, overstimAt, overstimCauseWeight, settleMinutes, rockDrowsyFactor, transferStayAsleepProb, changeTransitionDistress, putDownTransitionDistress
- `temperament/AVERAGE.json`: rhythmicitySigmaScale, sootheTimeScale, unexplainedFussHazardScale, careSustainScale, overstimThresholdScale, transitionDistress, selfSettleScale, contentVocalRateScale, cryIntensityBias
- `temperament/DEMANDING.json`: rhythmicitySigmaScale, sootheTimeScale, unexplainedFussHazardScale, careSustainScale, overstimThresholdScale, transitionDistress, selfSettleScale, contentVocalRateScale, cryIntensityBias
- `temperament/EASYGOING.json`: rhythmicitySigmaScale, sootheTimeScale, unexplainedFussHazardScale, careSustainScale, overstimThresholdScale, transitionDistress, selfSettleScale, contentVocalRateScale, cryIntensityBias
- `temperament/HIGHLY_SENSITIVE.json`: rhythmicitySigmaScale, sootheTimeScale, unexplainedFussHazardScale, careSustainScale, overstimThresholdScale, transitionDistress, selfSettleScale, contentVocalRateScale, cryIntensityBias

## M2_3 / AVERAGE

| Metric | Day 1 | Median | p10 | p90 | Target | Status | Levers |
|---|---|---|---|---|---|---|---|
| Care requests per day | 4.00 | 6.00 | 4.40 | 6.80 | 8.00–13.00 | **OUT** (median 6.00) | careSustainMinutes, model.cryThreshold, feedIntervalMinutes, wakeWindowMinutes |
| Night care requests per night | 2.00 | 3.00 | 1.80 | 3.40 | 1.00–3.00 | pass | feedIntervalMinutes, selfSettleProbNight, circadianStrength, model.nightHungerRateScale |
| Longest daytime gap with no care request h | 7.02 | 7.02 | 6.66 | 7.91 | ≤ 3.50 | **OUT** (p90 7.91) | careSustainMinutes, model.cryThreshold, feedIntervalMinutes, wakeWindowMinutes |
| Longest awake gap with no audible activity min | 31.50 | 31.50 | 25.10 | 40.30 | ≤ 15.00 | **OUT** (p90 40.30) | vocalRatePerAwakeHour, model.moodVocalGain |
| Audible events per hour | 2.42 | 2.48 | 2.21 | 2.61 | — | — |  |
| Total sleep per 24 h h | 16.08 | 16.39 | 16.38 | 16.50 | 14.00–17.00 | pass | wakeWindowMinutes, totalSleepHours24, sleepCycleMinutes, circadianStrength, model.restedSleepPressure |
| Naps per day | 3.00 | 3.00 | 3.00 | 3.80 | 3.00–5.00 | pass | wakeWindowMinutes, totalSleepHours24, sleepCycleMinutes, circadianStrength, model.restedSleepPressure |
| Night wakings per night | 3.00 | 3.50 | 3.10 | 4.30 | 1.00–3.00 | **OUT** (median 3.50) | feedIntervalMinutes, selfSettleProbNight, circadianStrength, model.nightHungerRateScale |
| Fussing and crying per 24 h min | 95.00 | 102.00 | 98.40 | 128.20 | 35.00–80.00 | **OUT** (median 102.00) | model.unexplainedFussPerAwakeHour, model.distressRisePerMinute, model.distressFallPerMinute, model.cryThreshold |
| Feeds per day | 6.00 | 6.00 | 6.00 | 6.00 | 6.00–8.00 | pass | feedIntervalMinutes, feedAcceptMin, feedDurationMinutes |
| Median time between feeds min | 244.00 | 244.00 | 239.20 | 244.00 | 180.00–240.00 | **OUT** (median 244.00) | feedIntervalMinutes, feedAcceptMin, feedDurationMinutes |
| Wet diapers per day | 7.00 | 8.00 | 7.60 | 10.40 | 6.00–8.00 | pass | wetDiapersPerDay, model.postFeedVoidMultiplier |
| Diaper changes per day | 4.00 | 6.50 | 4.90 | 6.50 | — | — |  |
| Day-to-day schedule similarity | n/a | 0.08 | 0.02 | 0.18 | ≤ 0.60 | pass | rhythmicitySigmaScale |
| Auto-pauses | 0.00 | 0.00 | 0.00 | 0.00 | 0 | pass | careSustainMinutes, model.cryThreshold, feedIntervalMinutes, wakeWindowMinutes |

