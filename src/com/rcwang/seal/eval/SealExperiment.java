package com.rcwang.seal.eval;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.log4j.Logger;

import com.rcwang.seal.expand.EntityList;
import com.rcwang.seal.expand.Seal;
import com.rcwang.seal.expand.SeedSelector;
import com.rcwang.seal.rank.Ranker.Feature;
import com.rcwang.seal.util.Helper;

public class SealExperiment extends IterativeExperiment {
    private static final Logger log = Logger.getLogger(SealExperiment.class);

    public SealExperiment() {
        super();
    }

    @Override
    protected List<EvalResult> evaluate(int trial) {
        SeedSelector seedSelector = new SeedSelector(policy);
        seedSelector.setFeature(feature);
        seedSelector.setNumSeeds(numTrueSeeds, numPossibleSeeds);
        EntityList initialSeeds = new EntityList(evaluator.getFirstMentions());
        
        seedSelector.setTrueSeeds(initialSeeds);
        seedSelector.setRandomSeed(trial);

        Seal seal = new Seal();
        //seal.setEvaluator(evaluator);
        //seal.setNumExpansions(numExpansions);
        //SetExpander expander = useGoogleSets() ? new GoogleSets() : new Seal();
        seal.expand(seedSelector.select(null));
        EntityList entities = seal.getEntityList();
        EvalResult evalResult = evaluator.evaluate(entities);
        List<EvalResult> evalResultList = new ArrayList<EvalResult>();
        evalResultList.add(evalResult);
        log.info(entities.toDetails(NUM_TO_PRINT, feature));
        return evalResultList;
      }

    
    /**
     * @param args
     */
    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
        SealExperiment experiment = new SealExperiment();
        experiment.setUseGoogleSets(gv.isUseGoogleSets());
        experiment.setNumTrials(gv.getNumTrials());
        experiment.setNumExpansions(gv.getNumExpansions());
        experiment.setPolicy(gv.getPolicy(), gv.getNumTrueSeeds(), gv.getNumPossibleSeeds());

//        WebManager.setFetchFromWeb(false);
        
        List<Feature> features = gv.getExpFeatures();
        if (features == null)
          features = Arrays.asList(Feature.values());
        
        for (Feature feature : features) {
          gv.setFeature(feature);
          experiment.setFeature(feature);
          experiment.run();
        }
        Helper.printElapsedTime(startTime);
    }

}
