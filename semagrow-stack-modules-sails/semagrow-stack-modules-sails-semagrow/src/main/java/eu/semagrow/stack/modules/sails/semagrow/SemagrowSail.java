package eu.semagrow.stack.modules.sails.semagrow;

import eu.semagrow.stack.modules.api.decomposer.QueryDecomposer;
import eu.semagrow.stack.modules.api.evaluation.FederatedQueryEvaluation;
import eu.semagrow.stack.modules.api.source.SourceSelector;
import eu.semagrow.stack.modules.api.estimator.CardinalityEstimator;
import eu.semagrow.stack.modules.sails.semagrow.estimator.CostEstimator;
import eu.semagrow.stack.modules.querydecomp.selector.*;
import eu.semagrow.stack.modules.sails.semagrow.evaluation.QueryEvaluationImpl;
import eu.semagrow.stack.modules.sails.semagrow.evaluation.file.FileManager;
import eu.semagrow.stack.modules.sails.semagrow.evaluation.file.MaterializationManager;
import eu.semagrow.stack.modules.sails.semagrow.evaluation.monitoring.querylog.*;
import eu.semagrow.stack.modules.sails.semagrow.evaluation.monitoring.querylog.QueryLogFactory;
import eu.semagrow.stack.modules.sails.semagrow.evaluation.monitoring.querylog.rdf.RDFQueryLogFactory;
import eu.semagrow.stack.modules.sails.semagrow.planner.DPQueryDecomposer;
import org.openrdf.model.URI;
import org.openrdf.model.ValueFactory;
import org.openrdf.model.impl.ValueFactoryImpl;
import org.openrdf.query.algebra.evaluation.QueryOptimizer;
import org.openrdf.query.algebra.evaluation.impl.CompareOptimizer;
import org.openrdf.query.algebra.evaluation.impl.ConjunctiveConstraintSplitter;
import org.openrdf.query.algebra.evaluation.impl.SameTermFilterOptimizer;
import org.openrdf.query.algebra.evaluation.util.QueryOptimizerList;
import org.openrdf.query.resultio.TupleQueryResultFormat;
import org.openrdf.query.resultio.TupleQueryResultWriterFactory;
import org.openrdf.query.resultio.TupleQueryResultWriterRegistry;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFWriterFactory;
import org.openrdf.rio.RDFWriterRegistry;
import org.openrdf.sail.SailConnection;
import org.openrdf.sail.SailException;
import org.openrdf.sail.helpers.SailBase;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Semagrow Sail implementation.
 * @author acharal@iit.demokritos.gr
 *
 * TODO list and other suggestions from the plenary meeting in Wageningen
 * TODO: define clean interfaces for sourceselector
 * TODO: rethink voID descriptions
 * TODO: estimate processing cost of subqueries to the sources (some sources may contain indexes etc
 * TODO: voID and configuration as sailbase and able to be SPARQL queried.
 * TODO: do transformation
 * TODO: geosparql
 */
public class SemagrowSail extends SailBase {

    private FederatedQueryEvaluation queryEvaluation;

    private QueryLogWriter handler;

    private SourceSelector sourceSelector;
    private CostEstimator costEstimator;
    private CardinalityEstimator cardinalityEstimator;

    private ExecutorService executor = Executors.newCachedThreadPool();

    public SemagrowSail() { }

    public boolean isWritable() throws SailException {
        return false;
    }

    public ValueFactory getValueFactory() {
        return ValueFactoryImpl.getInstance();
    }

    public SailConnection getConnectionInternal() throws SailException {
        return new SemagrowSailConnection(this);
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
        CardinalityEstimator cardinalityEstimator = getCardinalityEstimator();
        return new DPQueryDecomposer(costEstimator, cardinalityEstimator, selector);
    }

    public SourceSelector getSourceSelector() { return sourceSelector; }

    public void setSourceSelector(SourceSelector selector) {
        sourceSelector = selector;
    }

    private CostEstimator getCostEstimator() { return costEstimator; }

    public void setCostEstimator(CostEstimator costEstimator) { this.costEstimator = costEstimator; }

    private CardinalityEstimator getCardinalityEstimator() {
        return this.cardinalityEstimator;
    }

    public void setCardinalityEstimator(CardinalityEstimator cardinalityEstimator) {
        this.cardinalityEstimator = cardinalityEstimator;
    }

    public FederatedQueryEvaluation getQueryEvaluation() {

        if (queryEvaluation == null) {
            MaterializationManager manager = getManager();
            handler = getRecordLog();
            queryEvaluation = new QueryEvaluationImpl(manager, handler, executor);
        }

        return queryEvaluation;
    }

    public void setQueryEvaluation(FederatedQueryEvaluation queryEvaluation) {
        this.queryEvaluation = queryEvaluation;
    }

    public MaterializationManager getManager() {
        File baseDir = new File("/var/tmp/");
        TupleQueryResultFormat resultFF = TupleQueryResultFormat.BINARY;

        TupleQueryResultWriterRegistry  registry = TupleQueryResultWriterRegistry.getInstance();
        TupleQueryResultWriterFactory writerFactory = registry.get(resultFF);
        MaterializationManager manager = new FileManager(baseDir, writerFactory);

        return manager;
    }

    public QueryLogWriter getRecordLog() {

        QueryLogWriter handler;

        File qfrLog  = new File("/var/tmp/qfr.log");
        RDFFormat rdfFF = RDFFormat.NTRIPLES;

        RDFWriterRegistry writerRegistry = RDFWriterRegistry.getInstance();
        RDFWriterFactory rdfWriterFactory = writerRegistry.get(rdfFF);
        QueryLogFactory factory = new RDFQueryLogFactory(rdfWriterFactory);
        try {
            OutputStream out = new FileOutputStream(qfrLog);
            handler = factory.getQueryLogger(out);
            return handler;
        } catch (FileNotFoundException e) {

        }
        return null;
    }

    @Override
    protected void shutDownInternal() throws SailException {
        if (handler != null) {
            try {
                handler.endQueryLog();
            } catch (QueryLogException e) {
                throw new SailException(e);
            }
        }
    }


}
