package eu.semagrow.stack.modules.sails.semagrow.optimizer;

import eu.semagrow.stack.modules.api.decomposer.QueryDecomposer;
import eu.semagrow.stack.modules.api.decomposer.QueryDecompositionException;
import eu.semagrow.stack.modules.api.estimator.CardinalityEstimator;
import eu.semagrow.stack.modules.api.source.SourceMetadata;
import eu.semagrow.stack.modules.api.source.SourceSelector;
import eu.semagrow.stack.modules.api.estimator.CostEstimator;
import eu.semagrow.stack.modules.sails.semagrow.algebra.BindJoin;
import eu.semagrow.stack.modules.sails.semagrow.algebra.HashJoin;
import eu.semagrow.stack.modules.sails.semagrow.algebra.SourceQuery;
import eu.semagrow.stack.modules.sails.semagrow.helpers.BPGCollector;
import eu.semagrow.stack.modules.sails.semagrow.helpers.CombinationIterator;
import eu.semagrow.stack.modules.sails.semagrow.helpers.FilterCollector;
import eu.semagrow.stack.modules.sails.semagrow.helpers.FilterUtils;
import org.openrdf.model.URI;
import org.openrdf.query.BindingSet;
import org.openrdf.query.Dataset;
import org.openrdf.query.algebra.*;
import org.openrdf.query.algebra.evaluation.QueryOptimizer;
import org.openrdf.query.algebra.helpers.StatementPatternCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;


/**
 * Created by angel on 3/13/14.
 */
public class DynamicProgrammingDecomposer implements QueryDecomposer {

    private CostEstimator costEstimator;
    private CardinalityEstimator cardinalityEstimator;
    private SourceSelector sourceSelector;

    final private Logger logger = LoggerFactory.getLogger(DynamicProgrammingDecomposer.class);

    public DynamicProgrammingDecomposer(CostEstimator estimator,
                                        CardinalityEstimator cardinalityEstimator,
                                        SourceSelector selector) {
        this.costEstimator = estimator;
        this.sourceSelector = selector;
        this.cardinalityEstimator = cardinalityEstimator;
    }

    /**
     * Will extract the base expressions (i.e. relations) and create alternative access plans
     * for each one. This is a different implementation from the traditional dynamic programming
     * algorithm where the base expressions are already defined. You can override accessPlans
     * to employ a heuristic to change the notion of the base expressions used.
     * @param expr
     * @return a list of access plans.
     */
    protected Collection<Plan> accessPlans(TupleExpr expr, Dataset dataset, BindingSet bindings,
                                         Collection<ValueExpr> filterConditions)
        throws QueryDecompositionException {

        Collection<Plan> plans = new LinkedList<Plan>();

        // extract the statement patterns
        List<StatementPattern> statementPatterns = StatementPatternCollector.process(expr);

        // extract the filter conditions of the query

        for (StatementPattern pattern : statementPatterns) {
            // get sources for each pattern
            Collection<SourceMetadata> sources = getSources(pattern,dataset,bindings);

            // apply filters that can be applied to the statementpattern
            TupleExpr e = FilterUtils.applyRemainingFilters(pattern, filterConditions);

            Set<TupleExpr> exprLabel =  new HashSet<TupleExpr>();
            exprLabel.add(e);

            if (sources.isEmpty())
                throw new QueryDecompositionException("No suitable sources found for statement pattern " + pattern.toString());

            // create alternative SourceQuery for each filtered-statementpattern
            List<URI> endpoints = new LinkedList<URI>();
            for (SourceMetadata sourceMetadata : sources) {
                if (sourceMetadata.getEndpoints().size() > 0)
                    endpoints.add(sourceMetadata.getEndpoints().get(0));
            }

            Plan p = createPlan(exprLabel, new SourceQuery(e.clone(), endpoints));
            plans.add(p);
        }

        // SPLENDID also cluster statementpatterns of the same source.

        return plans;
    }

    /**
     * Prune suboptimal plans
     * @param plans
     */
    protected void prunePlans(Collection<Plan> plans) {
        // group equivalent plans
        // get the minimum-cost plan for each equivalence class

        Collection<Plan> bestPlans = new ArrayList<Plan>();

        boolean inComparable;

        for (Plan candidatePlan : plans) {
            inComparable = true;

            for (Plan plan : bestPlans) {
                int plan_comp = comparePlan(candidatePlan, plan);

                if (plan_comp != 0)
                    inComparable = false;

                if (plan_comp == -1) {
                    bestPlans.remove(plan);
                    bestPlans.add(candidatePlan);
                }
            }
            // check if plan is incomparable with all best plans yet discovered.
            if (inComparable)
                bestPlans.add(candidatePlan);
        }

        int planSize = plans.size();
        plans.retainAll(bestPlans);
        logger.info("Pruned " + (planSize - plans.size()) + " suboptimal plans of " + planSize + " plans");
    }

    /**
     * Compare two plans; can be partial order of plans.
     * In order to be compared, both cost and properties of the plans
     * must be comparable.
     * @param plan1
     * @param plan2
     * @return 0 if plan are equal or uncomparable
     *         -1 if plan1 is better than plan2
     *         1  if plan2 is better than plan1
     */
    private int comparePlan(Plan plan1, Plan plan2) {
        if (isPlanComparable(plan1, plan2)) {
            return plan1.getCost() < plan2.getCost() ? -1 : 1;
        } else {
            return 0;
        }
    }

    private boolean isPlanComparable(Plan plan1, Plan plan2) {
        // FIXME: take plan properties into account
        return true;
    }

    /**
     * Create all the ways that to join two plans
     * @param plan1
     * @param plan2
     * @param filterConditions
     * @return
     */
    protected Collection<Plan> joinPlans(Collection<Plan> plan1, Collection<Plan> plan2,
                                              Collection<ValueExpr> filterConditions) {

        Collection<Plan> plans = new LinkedList<Plan>();

        for (Plan p1 : plan1) {
            for (Plan p2 : plan2) {

                Collection<TupleExpr> joins = createPhysicalJoins(p1, p2);
                Set<TupleExpr> s = new HashSet<TupleExpr>(p1.getPlanId());
                s.addAll(p2.getPlanId());

                for (TupleExpr plan : joins) {
                    TupleExpr e = FilterUtils.applyRemainingFilters(plan, filterConditions);
                    Plan p = createPlan(s,e);
                    plans.add(p);
                }

                TupleExpr expr = pushJoinRemote(p1, p2, filterConditions);
                if (expr != null) {
                    Plan p = createPlan(s,expr);
                    plans.add(p);
                }
            }
        }
        return plans;
    }

    /**
     * Update the properties of a plan
     * @param plan
     */
    protected void updatePlan(Plan plan) {
        TupleExpr innerExpr = plan.getArg();

        plan.setCost(costEstimator.getCost(innerExpr));
        plan.setCardinality(cardinalityEstimator.getCardinality(innerExpr));

        //FIXME: update ordering, limit, distinct, group by
    }

    protected Plan createPlan(Set<TupleExpr> planId, TupleExpr innerExpr) {
        Plan p = new Plan(planId, innerExpr);
        updatePlan(p);
        return p;
    }

    protected Collection<SourceMetadata> getSources(StatementPattern pattern, Dataset dataset, BindingSet bindings) {
        return sourceSelector.getSources(pattern,dataset,bindings);
    }

    private TupleExpr pushJoinRemote(Plan e1, Plan e2, Collection<ValueExpr> filterConditions) {

        if (e1.getArg() instanceof SourceQuery &&
            e2.getArg() instanceof SourceQuery) {

            SourceQuery q1 = (SourceQuery) e1.getArg();
            SourceQuery q2 = (SourceQuery) e2.getArg();
            //List<URI> sources = commonSources(q1, q2);

            if (q1.getSources().size() == 1 && q2.getSources().size() == 1 &&
                q1.getSources().containsAll(q2.getSources())) {

                TupleExpr expr = FilterUtils.applyRemainingFilters(new Join(q1.getArg(), q2.getArg()), filterConditions);
                return new SourceQuery(expr, q1.getSources());
            }

            //FIXME: push down joins if we can guarantee that datasets are not joinable.
        }

        return null;
    }

    private Collection<TupleExpr> createPhysicalJoins(Plan e1, Plan e2) {
        Collection<TupleExpr> plans = new LinkedList<TupleExpr>();

        //TupleExpr expr = new Join(e1, e2);
        TupleExpr expr = new BindJoin(e1,e2);
        plans.add(expr);

        expr = new HashJoin(e1,e2);
        plans.add(expr);

        //expr = new Join(e2, e1);
        //plans.add(expr);

        return plans;
    }

    protected void finalizePlans(Collection<Plan> plans) {

    }

    public void decompose(TupleExpr tupleExpr, Dataset dataset, BindingSet bindings)
        throws QueryDecompositionException {

        Collection<TupleExpr> basicGraphPatterns = BPGCollector.process(tupleExpr);

        for (TupleExpr bgp : basicGraphPatterns)
            decomposebgp(bgp, dataset, bindings);

        QueryOptimizer finalizeOptimizers = new LimitPushDownOptimizer();
        finalizeOptimizers.optimize(tupleExpr, dataset, bindings);
    }

    public void decomposebgp(TupleExpr bgp, Dataset dataset, BindingSet bindings)
            throws QueryDecompositionException {

        Collection<ValueExpr> filterConditions = FilterCollector.process(bgp);

        // optPlans is a function from (Set of Expressions) to (Set of Plans)
        PlanCollection optPlans = new PlanCollection();

        Collection<Plan> accessPlans = accessPlans(bgp, dataset, bindings, filterConditions);

        optPlans.addPlan(accessPlans);

        // plans.getExpressions() get basic expressions
        // subsets S of size i
        //
        Set<TupleExpr> r = optPlans.getExpressions();
        int count = r.size();

        // bottom-up starting for subplans of size "k"
        for (int k = 2; k <= count; k++) {

            // enumerate all subsets of r of size k
            for (Set<TupleExpr> s : subsetsOf(r, k)) {

                for (int i = 1; i < k; i++) {

                    // let disjoint sets o1 and o2 such that s = o1 union o2
                    for (Set<TupleExpr> o1 : subsetsOf(s, i)) {

                        Set<TupleExpr> o2 = new HashSet<TupleExpr>(s);
                        o2.removeAll(o1);

                        Collection<Plan> plans1 = optPlans.get(o1);
                        Collection<Plan> plans2 = optPlans.get(o2);
                        Collection<Plan> newPlans = joinPlans(plans1, plans2, filterConditions);

                        optPlans.addPlan(newPlans);
                    }
                }
                prunePlans(optPlans.get(s));
            }
        }
        Collection<Plan> fullPlans = optPlans.get(r);
        finalizePlans(fullPlans);

        if (!fullPlans.isEmpty()) {
            logger.info("Found " + fullPlans.size()+" complete optimal plans");
            TupleExpr bestPlan = getBestPlan(fullPlans);
            bgp.replaceWith(bestPlan);
        }
    }

    /**
     * Choose one of the plans as best
     * @param plans
     * @return
     */
    private TupleExpr getBestPlan(Collection<Plan> plans) {
        if (plans.isEmpty())
            return null;

        Plan bestPlan = plans.iterator().next();

        for (Plan p : plans)
            if (p.getCost() < bestPlan.getCost())
                bestPlan = p;

        return bestPlan;
    }


    private static <T> Iterable<Set<T>> subsetsOf(Set<T> s, int k) {
        return new CombinationIterator<T>(k, s);
    }

}
