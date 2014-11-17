package eu.semagrow.stack.modules.sails.semagrow.evaluation;

import eu.semagrow.stack.modules.api.evaluation.EvaluationStrategy;
import eu.semagrow.stack.modules.api.evaluation.QueryExecutor;
import eu.semagrow.stack.modules.sails.semagrow.algebra.*;
import eu.semagrow.stack.modules.sails.semagrow.evaluation.iteration.*;
import eu.semagrow.stack.modules.sails.semagrow.optimizer.Plan;
import info.aduna.iteration.*;
import org.openrdf.model.*;
import org.openrdf.model.impl.ValueFactoryImpl;
import org.openrdf.query.BindingSet;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.algebra.*;
import org.openrdf.query.algebra.evaluation.TripleSource;
import org.openrdf.query.algebra.evaluation.federation.JoinExecutorBase;
import org.openrdf.query.algebra.evaluation.iterator.CollectionIteration;
import org.openrdf.query.impl.EmptyBindingSet;

import javax.management.QueryEval;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Overrides the behavior of the default evaluation strategy implementation.
 * Functionality will be added for (potential) custom operators of the execution plan.
 * @author acharal@iit.demokritos.gr
 */
public class EvaluationStrategyImpl extends org.openrdf.query.algebra.evaluation.impl.EvaluationStrategyImpl
    implements EvaluationStrategy {

    private int batchSize = 10;

    private boolean includeProvenance = false;

    public static String provenanceField = "__endpoint";

    private QueryExecutor queryExecutor;

    public EvaluationStrategyImpl(QueryExecutor queryExecutor, final ValueFactory vf) {
        super(new TripleSource() {
            public CloseableIteration<? extends Statement, QueryEvaluationException>
            getStatements(Resource resource, URI uri, Value value, Resource... resources) throws QueryEvaluationException {
                throw new UnsupportedOperationException("Statement retrieval is not supported");
            }

            public ValueFactory getValueFactory() {
                return vf;
            }
        });
        this.queryExecutor = queryExecutor;
    }

    public EvaluationStrategyImpl(QueryExecutor queryExecutor)
    {

        this(queryExecutor,ValueFactoryImpl.getInstance());
    }

    public void setIncludeProvenance(boolean includeProvenance) { this.includeProvenance = includeProvenance; }

    public boolean getIncludeProvenance() { return this.includeProvenance; }

    @Override
    public CloseableIteration<BindingSet, QueryEvaluationException>
        evaluate(UnaryTupleOperator expr, BindingSet bindings) throws QueryEvaluationException {
        if (expr instanceof SourceQuery) {
            return this.evaluate((SourceQuery) expr, bindings);
        } else if (expr instanceof Transform) {
            return this.evaluate((Transform) expr, bindings);
        } else if (expr instanceof Plan) {
            return this.evaluate(((Plan)expr).getArg(), bindings);
        } else {
            return super.evaluate(expr, bindings);
        }
    }

    @Override
    public CloseableIteration<BindingSet, QueryEvaluationException>
        evaluate(Projection projection, BindingSet bindings) throws QueryEvaluationException {

        CloseableIteration<BindingSet, QueryEvaluationException> result;

        result = this.evaluate(projection.getArg(), bindings);
        result = new ProjectionIteration(projection, result, bindings);
        return result;
    }

    public CloseableIteration<BindingSet,QueryEvaluationException>
        evaluate(SourceQuery expr, BindingSet bindings) throws QueryEvaluationException {

        TupleExpr innerExpr = expr.getArg();

        List<CloseableIteration<BindingSet,QueryEvaluationException>> results =
                new LinkedList<CloseableIteration<BindingSet, QueryEvaluationException>>();

        if (expr.getSources().size() == 0)
            return new EmptyIteration<BindingSet, QueryEvaluationException>();

        if (expr.getSources().size() == 1)
            return evaluateSource(expr.getSources().get(0), innerExpr, bindings);

        for (URI endpoint : expr.getSources()) {
            CloseableIteration<BindingSet,QueryEvaluationException> iter =
                    evaluateSourceDelayed(endpoint, innerExpr, bindings);
             results.add(iter);
        }

        return new UnionIteration<BindingSet, QueryEvaluationException>(results);
    }

    private CloseableIteration<BindingSet,QueryEvaluationException>
        evaluateSourceDelayed(final URI endpoint, final TupleExpr expr, final BindingSet bindings)
            throws QueryEvaluationException {

        return new DelayedIteration<BindingSet, QueryEvaluationException>() {
            @Override
            protected Iteration<? extends BindingSet, ? extends QueryEvaluationException> createIteration()
                    throws QueryEvaluationException {
                return evaluateSource(endpoint, expr, bindings);
            }
        };
    }

    private CloseableIteration<BindingSet,QueryEvaluationException>
        evaluateSource(URI endpoint, TupleExpr expr, BindingSet bindings)
            throws QueryEvaluationException {

        CloseableIteration<BindingSet,QueryEvaluationException> result =
                queryExecutor.evaluate(endpoint, expr, bindings);

        if (getIncludeProvenance()) {
            ProvenanceValue provenance = new ProvenanceValue(endpoint);
            result = new InsertProvenanceIteration(result, provenance);
        }
        return result;
    }

    public CloseableIteration<BindingSet,QueryEvaluationException>
        evaluate(Transform expr, BindingSet bindings) throws QueryEvaluationException {

        // transform bindings to evaluate expr and then transform back the result.
        BindingSet bindingsT = bindings;
        return new TransformIteration(this.evaluate(expr.getArg(), bindingsT));
    }

    public CloseableIteration<BindingSet,QueryEvaluationException>
        evaluate(Join join, BindingSet bindings) throws QueryEvaluationException {

        if (join instanceof BindJoin)
            return evaluate((BindJoin)join, bindings);
        else if (join instanceof HashJoin)
            return evaluate((HashJoin)join, bindings);
        else
            return super.evaluate(join, bindings);
    }

    public CloseableIteration<BindingSet,QueryEvaluationException>
        evaluate(HashJoin join, BindingSet bindings) throws QueryEvaluationException {

        return new HashJoinIteration(this, join, bindings);
    }

    public CloseableIteration<BindingSet,QueryEvaluationException>
        evaluate(BindJoin join, BindingSet bindings) throws QueryEvaluationException {

        CloseableIteration<BindingSet,QueryEvaluationException> leftIter =
                evaluate(join.getLeftArg(), bindings);
        return new BindJoinIteration(leftIter, join.getRightArg(), bindings, this);
    }

    public CloseableIteration<BindingSet,QueryEvaluationException>
        evaluate(TupleExpr expr, CloseableIteration<BindingSet, QueryEvaluationException> bIter)
            throws QueryEvaluationException
    {
        return new BatchingIteration(bIter, expr, batchSize);
    }


    protected CloseableIteration<BindingSet, QueryEvaluationException>
        evaluateInternal(TupleExpr expr, Iterable<BindingSet> iterable)
        throws QueryEvaluationException
    {
        if (expr instanceof Union) {
            return evaluateInternal((Union) expr, iterable);
        } else if (expr instanceof Plan) {
            return evaluateInternal((Plan) expr, iterable);
        } else {

            CloseableIteration<BindingSet, QueryEvaluationException> bIter =
                    new IterationWrapper<BindingSet, QueryEvaluationException>(
                        new IteratorIteration<BindingSet, QueryEvaluationException>(iterable.iterator()));

            return evaluateInternal(expr, bIter);
        }
    }

    protected CloseableIteration<BindingSet, QueryEvaluationException>
        evaluateInternal(final Union union, final Iterable<BindingSet> iterable)
            throws QueryEvaluationException
    {

        Iteration<BindingSet, QueryEvaluationException> leftArg, rightArg;

        leftArg = new DelayedIteration<BindingSet, QueryEvaluationException>() {

            @Override
            protected Iteration<BindingSet, QueryEvaluationException> createIteration()
                    throws QueryEvaluationException
            {
                return evaluateInternal(union.getLeftArg(), iterable);
            }
        };

        rightArg = new DelayedIteration<BindingSet, QueryEvaluationException>() {

            @Override
            protected Iteration<BindingSet, QueryEvaluationException> createIteration()
                    throws QueryEvaluationException
            {
                return evaluateInternal(union.getRightArg(), iterable);
            }
        };

        return new UnionIteration<BindingSet, QueryEvaluationException>(leftArg, rightArg);
    }

    protected CloseableIteration<BindingSet, QueryEvaluationException>
        evaluateInternal(final Plan plan, final Iterable<BindingSet> iterable)
            throws QueryEvaluationException
    {
        return evaluateInternal(plan.getArg(), iterable);
    }

    protected CloseableIteration<BindingSet,QueryEvaluationException>
        evaluateInternal(TupleExpr expr, CloseableIteration<BindingSet, QueryEvaluationException> bIter)
            throws QueryEvaluationException
    {
        if (expr instanceof SourceQuery)
            return evaluateInternal((SourceQuery)expr, bIter);
        else if (expr instanceof Transform)
            return evaluateInternal((Transform)expr, bIter);
        else
            return evaluateInternalDefault(expr, bIter);
    }

    protected CloseableIteration<BindingSet,QueryEvaluationException>
        evaluateInternal(SourceQuery expr, CloseableIteration<BindingSet, QueryEvaluationException> bIter)
            throws QueryEvaluationException {

        URI endpoint = expr.getSources().get(0);

        CloseableIteration<BindingSet,QueryEvaluationException> result =
                queryExecutor.evaluate(endpoint, expr.getArg(), bIter);

        if (getIncludeProvenance()) {
            ProvenanceValue provenance = new ProvenanceValue(endpoint);
            result = new InsertProvenanceIteration(result, provenance);
        }

        return result;
    }

    protected CloseableIteration<BindingSet,QueryEvaluationException>
        evaluateInternal(Transform transform, CloseableIteration<BindingSet,QueryEvaluationException> bIter)
            throws QueryEvaluationException {

        CloseableIteration<BindingSet,QueryEvaluationException> bIterT =
                new TransformIteration(bIter);

        return new TransformIteration(evaluateInternal(transform.getArg(), bIterT));
    }

    protected CloseableIteration<BindingSet,QueryEvaluationException>
        evaluateInternalDefault(TupleExpr expr, CloseableIteration<BindingSet, QueryEvaluationException> bIter)
            throws QueryEvaluationException {

        return new BatchingIteration(bIter, expr, 20);
    }

    protected class BatchingIteration extends JoinExecutorBase<BindingSet> {

        private final int blockSize;
        private TupleExpr expr;

        public BatchingIteration(CloseableIteration<BindingSet,QueryEvaluationException> leftIter,
                                 TupleExpr expr, int blockSize)
                throws QueryEvaluationException {
            super(leftIter, expr, EmptyBindingSet.getInstance());

            this.expr = expr;
            this.blockSize = blockSize;
            run();
        }

        @Override
        protected void handleBindings() throws Exception {
            while (!closed && leftIter.hasNext()) {

                if (blockSize == 1) {
                    addResult(evaluate(expr, leftIter.next()));
                    continue;
                } else {
                    //CloseableIteration<BindingSet, QueryEvaluationException>
                    //        materializedIter = createBatchIter(leftIter, blockSize);
                    Iterable<BindingSet> iterable = createIterable(leftIter, blockSize);
                    addResult(evaluateInternal(expr,iterable));
                }
            }
        }

        protected CloseableIteration<BindingSet, QueryEvaluationException>
            createBatchIter(CloseableIteration<BindingSet, QueryEvaluationException> iter, int blockSize)
                throws QueryEvaluationException {


            ArrayList<BindingSet> blockBindings = new ArrayList<BindingSet>(blockSize);
            for (int i = 0; i < blockSize; i++) {
                if (!iter.hasNext())
                    break;
                blockBindings.add(iter.next());
            }

            CloseableIteration<BindingSet, QueryEvaluationException> materializedIter =
                    new CollectionIteration<BindingSet, QueryEvaluationException>(blockBindings);

            return materializedIter;
        }

        protected Iterable<BindingSet> createIterable(CloseableIteration<BindingSet, QueryEvaluationException> iter, int blockSize)
            throws QueryEvaluationException
        {
            ArrayList<BindingSet> blockBindings = new ArrayList<BindingSet>(blockSize);
            for (int i = 0; i < blockSize; i++) {
                if (!iter.hasNext())
                    break;
                blockBindings.add(iter.next());
            }
            return blockBindings;
        }

    }

}
