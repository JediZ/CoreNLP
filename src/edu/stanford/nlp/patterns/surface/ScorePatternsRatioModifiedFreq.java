package edu.stanford.nlp.patterns.surface;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;

import edu.stanford.nlp.patterns.surface.ConstantsAndVariables.ScorePhraseMeasures;
import edu.stanford.nlp.patterns.surface.GetPatternsFromDataMultiClass.PatternScoring;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.util.Execution;
import edu.stanford.nlp.util.Pair;

public class ScorePatternsRatioModifiedFreq<E> extends ScorePatterns<E> {

  public ScorePatternsRatioModifiedFreq(
      ConstantsAndVariables constVars,
      PatternScoring patternScoring,
      String label, Set<String> allCandidatePhrases,
      TwoDimensionalCounter<E, String> patternsandWords4Label,
      TwoDimensionalCounter<E, String> negPatternsandWords4Label,
      TwoDimensionalCounter<E, String> unLabeledPatternsandWords4Label,
      TwoDimensionalCounter<String, ScorePhraseMeasures> phInPatScores,
      ScorePhrases scorePhrases, Properties props) {
    super(constVars, patternScoring, label, allCandidatePhrases,  patternsandWords4Label,
        negPatternsandWords4Label, unLabeledPatternsandWords4Label,
        props);
    this.phInPatScores = phInPatScores;
    this.scorePhrases = scorePhrases;
  }

  // cached values
  private TwoDimensionalCounter<String, ScorePhraseMeasures> phInPatScores;

  private ScorePhrases scorePhrases;

  @Override
  public void setUp(Properties props) {
  }

  @Override
  Counter<E> score() throws IOException, ClassNotFoundException {

    Counter<String> externalWordWeightsNormalized = null;
    if (constVars.dictOddsWeights.containsKey(label))
      externalWordWeightsNormalized = GetPatternsFromDataMultiClass
          .normalizeSoftMaxMinMaxScores(constVars.dictOddsWeights.get(label),
              true, true, false);

    Counter<E> currentPatternWeights4Label = new ClassicCounter<E>();

    boolean useFreqPhraseExtractedByPat = false;
    if (patternScoring.equals(PatternScoring.SqrtAllRatio))
      useFreqPhraseExtractedByPat = true;
    Function<Pair<E, String>, Double> numeratorScore = x -> patternsandWords4Label.getCount(x.first(), x.second());

    Counter<E> numeratorPatWt = this.convert2OneDim(label,
        numeratorScore, allCandidatePhrases, patternsandWords4Label, constVars.sqrtPatScore, false, null,
        useFreqPhraseExtractedByPat);

    Counter<E> denominatorPatWt = null;

    Function<Pair<E, String>, Double> denoScore;
    if (patternScoring.equals(PatternScoring.PosNegUnlabOdds)) {
      denoScore = x -> negPatternsandWords4Label.getCount(x.first(), x.second()) + unLabeledPatternsandWords4Label.getCount(x.first(), x.second());

      denominatorPatWt = this.convert2OneDim(label,
          denoScore, allCandidatePhrases, patternsandWords4Label, constVars.sqrtPatScore, false,
          externalWordWeightsNormalized, useFreqPhraseExtractedByPat);
    } else if (patternScoring.equals(PatternScoring.RatioAll)) {
      denoScore = x -> negPatternsandWords4Label.getCount(x.first(), x.second()) + unLabeledPatternsandWords4Label.getCount(x.first(), x.second()) +
        patternsandWords4Label.getCount(x.first(), x.second());
      denominatorPatWt = this.convert2OneDim(label, denoScore,allCandidatePhrases, patternsandWords4Label,
          constVars.sqrtPatScore, false, externalWordWeightsNormalized,
          useFreqPhraseExtractedByPat);
    } else if (patternScoring.equals(PatternScoring.PosNegOdds)) {
      denoScore = x -> negPatternsandWords4Label.getCount(x.first(), x.second());
      denominatorPatWt = this.convert2OneDim(label, denoScore, allCandidatePhrases, patternsandWords4Label,
          constVars.sqrtPatScore, false, externalWordWeightsNormalized,
          useFreqPhraseExtractedByPat);
    } else if (patternScoring.equals(PatternScoring.PhEvalInPat)
        || patternScoring.equals(PatternScoring.PhEvalInPatLogP)
        || patternScoring.equals(PatternScoring.LOGREG)
        || patternScoring.equals(PatternScoring.LOGREGlogP)) {
      denoScore = x -> negPatternsandWords4Label.getCount(x.first(), x.second()) + unLabeledPatternsandWords4Label.getCount(x.first(), x.second());
      denominatorPatWt = this.convert2OneDim(label,
        denoScore, allCandidatePhrases, patternsandWords4Label, constVars.sqrtPatScore, true,
          externalWordWeightsNormalized, useFreqPhraseExtractedByPat);
    } else if (patternScoring.equals(PatternScoring.SqrtAllRatio)) {
      denoScore = x -> negPatternsandWords4Label.getCount(x.first(), x.second()) + unLabeledPatternsandWords4Label.getCount(x.first(), x.second());

      denominatorPatWt = this.convert2OneDim(label,
        denoScore, allCandidatePhrases, patternsandWords4Label, true, false,
          externalWordWeightsNormalized, useFreqPhraseExtractedByPat);
    } else
      throw new RuntimeException("Cannot understand patterns scoring");

    currentPatternWeights4Label = Counters.divisionNonNaN(numeratorPatWt,
        denominatorPatWt);

    //Multiplying by logP
    if (patternScoring.equals(PatternScoring.PhEvalInPatLogP) || patternScoring.equals(PatternScoring.LOGREGlogP)) {
      Counter<E> logpos_i = new ClassicCounter<E>();
      for (Entry<E, ClassicCounter<String>> en : patternsandWords4Label
          .entrySet()) {
        logpos_i.setCount(en.getKey(), Math.log(en.getValue().size()));
      }
      Counters.multiplyInPlace(currentPatternWeights4Label, logpos_i);
    }
    Counters.retainNonZeros(currentPatternWeights4Label);
    return currentPatternWeights4Label;
  }

  Counter<E> convert2OneDim(String label,
      Function<Pair<E, String>, Double> scoringFunction, Set<String> allCandidatePhrases, TwoDimensionalCounter<E, String> positivePatternsAndWords,
      boolean sqrtPatScore, boolean scorePhrasesInPatSelection,
      Counter<String> dictOddsWordWeights, boolean useFreqPhraseExtractedByPat) throws IOException, ClassNotFoundException {

    if (Data.googleNGram.size() == 0 && Data.googleNGramsFile != null) {
      Data.loadGoogleNGrams();
    }

    Counter<E> patterns = new ClassicCounter<E>();

    Counter<String> googleNgramNormScores = new ClassicCounter<String>();
    Counter<String> domainNgramNormScores = new ClassicCounter<String>();

    Counter<String> externalFeatWtsNormalized = new ClassicCounter<String>();
    Counter<String> editDistanceFromOtherSemanticBinaryScores = new ClassicCounter<String>();
    Counter<String> editDistanceFromAlreadyExtractedBinaryScores = new ClassicCounter<String>();
    double externalWtsDefault = 0.5;
    Counter<String> classifierScores = null;

    if ((patternScoring.equals(PatternScoring.PhEvalInPat) || patternScoring
        .equals(PatternScoring.PhEvalInPatLogP)) && scorePhrasesInPatSelection) {

      for (String g : allCandidatePhrases) {
        if (constVars.usePatternEvalEditDistOther) {

          editDistanceFromOtherSemanticBinaryScores.setCount(g,
              constVars.getEditDistanceScoresOtherClassThreshold(g));
        }
        if (constVars.usePatternEvalEditDistSame) {
          editDistanceFromAlreadyExtractedBinaryScores.setCount(g,
              1 - constVars.getEditDistanceScoresThisClassThreshold(label, g));
        }

        if (constVars.usePatternEvalGoogleNgram) {
          if (Data.googleNGram.containsKey(g)) {
            assert (Data.rawFreq.containsKey(g));
            googleNgramNormScores
                .setCount(
                    g,
                    ((1 + Data.rawFreq.getCount(g)
                        * Math.sqrt(Data.ratioGoogleNgramFreqWithDataFreq)) / Data.googleNGram
                        .getCount(g)));
          }
        }
        if (constVars.usePatternEvalDomainNgram) {
          // calculate domain-ngram wts
          if (Data.domainNGramRawFreq.containsKey(g)) {
            assert (Data.rawFreq.containsKey(g));
            domainNgramNormScores.setCount(g,
                scorePhrases.phraseScorer.getDomainNgramScore(g));
          }
        }

        if (constVars.usePatternEvalWordClass) {
          Integer num = constVars.getWordClassClusters().get(g);
          if (num != null
              && constVars.distSimWeights.get(label).containsKey(num)) {
            externalFeatWtsNormalized.setCount(g,
                constVars.distSimWeights.get(label).getCount(num));
          } else
            externalFeatWtsNormalized.setCount(g, externalWtsDefault);
        }
      }
      if (constVars.usePatternEvalGoogleNgram)
        googleNgramNormScores = GetPatternsFromDataMultiClass
            .normalizeSoftMaxMinMaxScores(googleNgramNormScores, true, true,
                false);
      if (constVars.usePatternEvalDomainNgram)
        domainNgramNormScores = GetPatternsFromDataMultiClass
            .normalizeSoftMaxMinMaxScores(domainNgramNormScores, true, true,
                false);
      if (constVars.usePatternEvalWordClass)
        externalFeatWtsNormalized = GetPatternsFromDataMultiClass
            .normalizeSoftMaxMinMaxScores(externalFeatWtsNormalized, true,
                true, false);
    }

    else if ((patternScoring.equals(PatternScoring.LOGREG) || patternScoring.equals(PatternScoring.LOGREGlogP))
        && scorePhrasesInPatSelection) {
      Properties props2 = new Properties();
      props2.putAll(props);
      props2.setProperty("phraseScorerClass", "edu.stanford.nlp.patterns.surface.ScorePhrasesLearnFeatWt");
      ScorePhrases scoreclassifier = new ScorePhrases(props2, constVars);
      System.out.println("file is " + props.getProperty("domainNGramsFile"));
      Execution.fillOptions(Data.class, props2);
      classifierScores = scoreclassifier.phraseScorer.scorePhrases(label, allCandidatePhrases,  true);
      // scorePhrases(Data.sents, label, true,
      // constVars.perSelectRand, constVars.perSelectNeg, null, null,
      // dictOddsWordWeights);
      // throw new RuntimeException("Not implemented currently");
    }

    Counter<String> cachedScoresForThisIter = new ClassicCounter<String>();

    for (Map.Entry<E, ClassicCounter<String>> en: positivePatternsAndWords.entrySet()) {

        for(Entry<String, Double> en2: en.getValue().entrySet()) {
          String word = en2.getKey();
          Counter<ScorePhraseMeasures> scoreslist = new ClassicCounter<ScorePhraseMeasures>();
          double score = 1;
          if ((patternScoring.equals(PatternScoring.PhEvalInPat) || patternScoring
            .equals(PatternScoring.PhEvalInPatLogP))
            && scorePhrasesInPatSelection) {
            if (cachedScoresForThisIter.containsKey(word)) {
              score = cachedScoresForThisIter.getCount(word);
            } else {
              if (constVars.getOtherSemanticClassesWords().contains(word)
                || constVars.getCommonEngWords().contains(word))
                score = 1;
              else {

                if (constVars.usePatternEvalSemanticOdds) {
                  double semanticClassOdds = 1;
                  if (dictOddsWordWeights.containsKey(word))
                    semanticClassOdds = 1 - dictOddsWordWeights.getCount(word);
                  scoreslist.setCount(ScorePhraseMeasures.SEMANTICODDS,
                    semanticClassOdds);
                }

                if (constVars.usePatternEvalGoogleNgram) {
                  double gscore = 0;
                  if (googleNgramNormScores.containsKey(word)) {
                    gscore = 1 - googleNgramNormScores.getCount(word);
                  }
                  scoreslist.setCount(ScorePhraseMeasures.GOOGLENGRAM, gscore);
                }

                if (constVars.usePatternEvalDomainNgram) {
                  double domainscore;
                  if (domainNgramNormScores.containsKey(word)) {
                    domainscore = 1 - domainNgramNormScores.getCount(word);
                  } else
                    domainscore = 1 - scorePhrases.phraseScorer
                      .getPhraseWeightFromWords(domainNgramNormScores, word,
                        scorePhrases.phraseScorer.OOVDomainNgramScore);
                  scoreslist.setCount(ScorePhraseMeasures.DOMAINNGRAM,
                    domainscore);
                }
                if (constVars.usePatternEvalWordClass) {
                  double externalFeatureWt = externalWtsDefault;
                  if (externalFeatWtsNormalized.containsKey(word))
                    externalFeatureWt = 1 - externalFeatWtsNormalized.getCount(word);
                  scoreslist.setCount(ScorePhraseMeasures.DISTSIM,
                    externalFeatureWt);
                }

                if (constVars.usePatternEvalEditDistOther) {
                  assert editDistanceFromOtherSemanticBinaryScores.containsKey(word) : "How come no edit distance info?";
                  scoreslist.setCount(ScorePhraseMeasures.EDITDISTOTHER,
                    editDistanceFromOtherSemanticBinaryScores.getCount(word));
                }
                if (constVars.usePatternEvalEditDistSame) {
                  scoreslist.setCount(ScorePhraseMeasures.EDITDISTSAME,
                    editDistanceFromAlreadyExtractedBinaryScores.getCount(word));
                }

                // taking average
                score = Counters.mean(scoreslist);

                phInPatScores.setCounter(word, scoreslist);
              }

              cachedScoresForThisIter.setCount(word, score);
            }
          } else if ((patternScoring.equals(PatternScoring.LOGREG) || patternScoring.equals(PatternScoring.LOGREGlogP))
            && scorePhrasesInPatSelection) {
            score = 1 - classifierScores.getCount(word);
            // score = 1 - scorePhrases.scoreUsingClassifer(classifier,
            // e.getKey(), label, true, null, null, dictOddsWordWeights);
            // throw new RuntimeException("not implemented yet");
          }

          if (useFreqPhraseExtractedByPat)
            score = score * scoringFunction.apply(new Pair(en.getKey(), word));
          if (constVars.sqrtPatScore)
            patterns.incrementCount(en.getKey(), Math.sqrt(score));
          else
            patterns.incrementCount(en.getKey(), score);
        }
    }



    return patterns;
  }

}
