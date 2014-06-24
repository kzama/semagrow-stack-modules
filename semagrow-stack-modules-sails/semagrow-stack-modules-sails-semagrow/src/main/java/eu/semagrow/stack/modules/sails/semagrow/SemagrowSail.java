package eu.semagrow.stack.modules.sails.semagrow;

import eu.semagrow.stack.modules.api.decomposer.QueryDecomposer;
import eu.semagrow.stack.modules.api.evaluation.QueryEvaluation;
import eu.semagrow.stack.modules.api.source.SourceSelector;
import eu.semagrow.stack.modules.api.statistics.Statistics;
import eu.semagrow.stack.modules.api.estimator.CardinalityEstimator;
import eu.semagrow.stack.modules.api.estimator.CostEstimator;
import eu.semagrow.stack.modules.querydecomp.selector.*;
import eu.semagrow.stack.modules.sails.semagrow.estimator.CardinalityEstimatorImpl;
import eu.semagrow.stack.modules.sails.semagrow.estimator.CostEstimatorImpl;
import eu.semagrow.stack.modules.sails.semagrow.evaluation.QueryEvaluationImpl;
import eu.semagrow.stack.modules.sails.semagrow.optimizer.DynamicProgrammingDecomposer;
import org.openrdf.model.URI;
import org.openrdf.model.ValueFactory;
import org.openrdf.model.impl.ValueFactoryImpl;
import org.openrdf.query.algebra.evaluation.QueryOptimizer;
import org.openrdf.query.algebra.evaluation.impl.CompareOptimizer;
import org.openrdf.query.algebra.evaluation.impl.ConjunctiveConstraintSplitter;
import org.openrdf.query.algebra.evaluation.impl.SameTermFilterOptimizer;
import org.openrdf.query.algebra.evaluation.util.QueryOptimizerList;
import org.openrdf.repository.Repository;
import org.openrdf.repository.sail.SailRepository;
import org.openrdf.sail.Sail;
import org.openrdf.sail.SailConnection;
import org.openrdf.sail.SailException;
import org.openrdf.sail.StackableSail;
import org.openrdf.sail.helpers.SailBase;

import java.util.Collection;

/**
 * Semagrow Sail implementation.
 * @author acharal@iit.demokritos.gr
 *
 * TODO list and other suggestions from the plenary meeting in Wageningen
 * TODO: lineage of evaluation (track the sources of the produced tuples)
 * TODO: define clean interfaces for sourceselector
 * TODO: rethink voID descriptions
 * TODO: estimate processing cost of subqueries to the sources (some sources may contain indexes etc
 * TODO: order-by and sort-merge-join
 * TODO: voID and configuration as sailbase and able to be SPARQL queried.
 * TODO: do transformation
 * TODO: geosparql
 */
public class SemagrowSail extends SailBase implements StackableSail {

    private Sail metadataSail;
    private QueryEvaluation queryEvaluation;

    public SemagrowSail() { }

    public SemagrowSail(Sail metadataSail) { setBaseSail(metadataSail); }

    public boolean isWritable() throws SailException {
        return false;
    }

    @Override
    protected void initializeInternal() throws SailException {
        // TODO: uncomment initialization to work properly!
        //if (metadataSail != null && metadataSail.)
        //   metadataSail.initialize();
    }

    @Override
    protected void shutDownInternal() throws SailException {
        metadataSail.shutDown();
    }

    public void setBaseSail(Sail sail) {
        metadataSail = sail;
    }

    public Sail getBaseSail() {
        return metadataSail;
    }

    public ValueFactory getValueFactory() {
        return ValueFactoryImpl.getInstance();
    }

    public SailConnection getConnectionInternal() throws SailException {
        return new SemagrowSailConnection(this, this.getBaseSail().getConnection());
    }

    public QueryOptimizer getOptimizer() {
        SourceSelector selector = getSourceSelector();
        CostEstimator costEstimator = getCostEstimator();

        QueryOptimizerList optimizer = new QueryOptimizerList(
                new ConjunctiveConstraintSplitter(),
                new CompareOptimizer(),
                new SameTermFilterOptimizer()
        );

        return optimizer;
    }

    public QueryDecomposer getDecomposer(Collection<URI> includeOnly, Collection<URI> exclude) {
        SourceSelector selector = getSourceSelector();
        selector = new RestrictiveSourceSelector(selector, includeOnly, exclude);
        CostEstimator costEstimator = getCostEstimator();
        return new DynamicProgrammingDecomposer(costEstimator,selector);
    }

    private SourceSelector getSourceSelector() {
        //VOIDResourceSelector resourceSelector = new VOIDResourceSelector();
        //resourceSelector.setRepository(getMetadataAsRepository());
        //return new SourceSelectorAdapter(resourceSelector);
        return new VOIDSourceSelector(getMetadataAsRepository());
    }

    private CostEstimator getCostEstimator() {
        CardinalityEstimator cardinalityEstimator = getCardinalityEstimator();
        return new CostEstimatorImpl(cardinalityEstimator);
    }

    private CardinalityEstimator getCardinalityEstimator() {
        return new CardinalityEstimatorImpl(getStatistics());
    }

    private Statistics getStatistics() {
        return new VOIDStatistics(getMetadataAsRepository());
    }

    private Repository getMetadataAsRepository() {
        assert metadataSail != null;
        return new SailRepository(metadataSail);
    }

    public QueryEvaluation getQueryEvaluation() {

        if (queryEvaluation == null)
            queryEvaluation = new QueryEvaluationImpl();

        return queryEvaluation;
    }
}
