package eu.semagrow.stack.modules.sails.semagrow.evaluation;

import eu.semagrow.stack.modules.sails.semagrow.evaluation.iteration.InsertValuesBindingsIteration;
import info.aduna.iteration.CloseableIteration;
import info.aduna.iteration.EmptyIteration;
import info.aduna.iteration.Iterations;
import org.openrdf.model.Literal;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.query.*;
import org.openrdf.query.algebra.TupleExpr;
import org.openrdf.query.algebra.Var;
import org.openrdf.query.algebra.evaluation.federation.JoinExecutorBase;
import org.openrdf.query.algebra.helpers.QueryModelVisitorBase;
import org.openrdf.query.impl.EmptyBindingSet;
import org.openrdf.query.parser.ParsedTupleQuery;
import org.openrdf.queryrender.sparql.SPARQLQueryRenderer;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.sparql.query.InsertBindingSetCursor;

import java.util.*;

/**
 * Created by angel on 6/6/14.
 */
//FIXME: Shutdown connections and repositories properly
public class QueryExecutorImpl implements QueryExecutor {

    public RepositoryConnection getConnection(URI endpoint) {
        return null;
    }

    public CloseableIteration<BindingSet, QueryEvaluationException>
        evaluate(URI endpoint, TupleExpr expr, BindingSet bindings)
            throws QueryEvaluationException {

        CloseableIteration<BindingSet,QueryEvaluationException> result = null;
        try {

            String sparqlQuery = buildSPARQLQuery(expr);

            Set<String> freeVars = computeVars(expr);
            freeVars.removeAll(bindings.getBindingNames());

            // FIXME: check if no free vars in the query. If so, send an ASK query.
            // if (freeVars.isEmpty())

            result = sendQuery(endpoint, sparqlQuery, bindings);

            result = new InsertBindingSetCursor(result, bindings);

            return result;
        } catch (QueryEvaluationException e) {
            Iterations.closeCloseable(result);
            throw e;
        } catch (Exception e) {
            Iterations.closeCloseable(result);
            throw new QueryEvaluationException(e);
        }
    }

    public CloseableIteration<BindingSet, QueryEvaluationException>
        evaluate(URI endpoint, TupleExpr expr,
                 CloseableIteration<BindingSet, QueryEvaluationException> bindingIter)
            throws QueryEvaluationException {

        CloseableIteration<BindingSet, QueryEvaluationException> result = null;
        try {
            List<BindingSet> bindings = Iterations.asList(bindingIter);

            if (bindings.isEmpty()) {
                return new EmptyIteration<BindingSet, QueryEvaluationException>();
            }

            if (bindings.size() == 1) {
                result = evaluate(endpoint, expr, bindings.get(0));
                return result;
            }

            try {
                result = evaluateInternal(endpoint, expr, bindings);
                return result;
            } catch(QueryEvaluationException e) {
                return new SequentialQueryIteration(endpoint, expr, bindings);
            }

        } catch (MalformedQueryException e) {
                // this exception must not be silenced, bug in our code
                throw new QueryEvaluationException(e);
        }
        catch (QueryEvaluationException e) {
            Iterations.closeCloseable(result);
            throw e;
        } catch (Exception e) {
            Iterations.closeCloseable(result);
            throw new QueryEvaluationException(e);
        }
    }


    protected CloseableIteration<BindingSet, QueryEvaluationException>
        evaluateInternal(URI endpoint, TupleExpr expr, List<BindingSet> bindings)
            throws Exception {

        CloseableIteration<BindingSet, QueryEvaluationException> result = null;

        Set<String> exprVars = computeVars(expr);

        List<String> relevant = getRelevantBindingNames(bindings, exprVars);

        String sparqlQuery = buildSPARQLQueryVALUES(expr, bindings, relevant);

        result = sendQuery(endpoint, sparqlQuery, EmptyBindingSet.getInstance());

        result = new InsertValuesBindingsIteration(result, bindings);

        return result;
    }

    private List<String> getRelevantBindingNames(List<BindingSet> bindings, Set<String> exprVars) {

        List<String> relevantBindingNames = new ArrayList<String>(5);
        for (String bName : bindings.get(0).getBindingNames()) {
            if (exprVars.contains(bName))
                relevantBindingNames.add(bName);
        }

        return relevantBindingNames;
    }

    /**
     * Compute the variable names occurring in the service expression using tree
     * traversal, since these are necessary for building the SPARQL query.
     *
     * @return the set of variable names in the given service expression
     */
    private Set<String> computeVars(TupleExpr serviceExpression) {
        final Set<String> res = new HashSet<String>();
        serviceExpression.visit(new QueryModelVisitorBase<RuntimeException>() {

            @Override
            public void meet(Var node)
                    throws RuntimeException
            {
                // take only real vars, i.e. ignore blank nodes
                if (!node.hasValue() && !node.isAnonymous())
                    res.add(node.getName());
            }
            // TODO maybe stop tree traversal in nested SERVICE?
            // TODO special case handling for BIND
        });
        return res;
    }

    private CloseableIteration<BindingSet, QueryEvaluationException>
        sendQuery(URI endpoint, String sparqlQuery, BindingSet bindings)
            throws QueryEvaluationException, MalformedQueryException, RepositoryException {

        RepositoryConnection conn = getConnection(endpoint);
        TupleQuery query = conn.prepareTupleQuery(QueryLanguage.SPARQL, sparqlQuery);

        for (Binding b : bindings)
            query.setBinding(b.getName(), b.getValue());

        return query.evaluate();
    }

    /**
     * Computes the VALUES clause for the set of relevant input bindings. The
     * VALUES clause is attached to a subquery for block-nested-loop evaluation.
     * Implementation note: we use a special binding to mark the rowIndex of the
     * input binding.
     *
     * @param bindings
     * @param relevantBindingNames
     * @return a string with the VALUES clause for the given set of relevant
     *         input bindings
     * @throws QueryEvaluationException
     */
    private String buildVALUESClause(List<BindingSet> bindings, List<String> relevantBindingNames)
            throws QueryEvaluationException
    {

        StringBuilder sb = new StringBuilder();
        sb.append(" VALUES (?"+ InsertValuesBindingsIteration.INDEX_BINDING_NAME +")");

        for (String bName : relevantBindingNames) {
            sb.append(" ?").append(bName);
        }

        sb.append(") { ");

        int rowIdx = 0;
        for (BindingSet b : bindings) {
            sb.append(" (");
            sb.append("\"").append(rowIdx++).append("\" "); // identification of the row for post processing
            for (String bName : relevantBindingNames) {
                appendValueAsString(sb, b.getValue(bName)).append(" ");
            }
            sb.append(")");
        }

        sb.append(" }");
        return sb.toString();
    }

    private String buildSPARQLQuery(TupleExpr expr) throws Exception {
        SPARQLQueryRenderer renderer = new SPARQLQueryRenderer();
        ParsedTupleQuery query = new ParsedTupleQuery(expr);
        return renderer.render(query);
    }

    private String buildSPARQLQueryVALUES(TupleExpr expr, List<BindingSet> bindings, List<String> relevantBindingNames)
            throws Exception {

        return buildSPARQLQuery(expr) + buildVALUESClause(bindings,relevantBindingNames);
    }

    private String buildSPARQLQueryUNION(TupleExpr expr, List<BindingSet> bindings, List<String> relevantBindingNames)
            throws Exception {

        return null;
    }

    protected StringBuilder appendValueAsString(StringBuilder sb, Value value) {

        // TODO check if there is some convenient method in Sesame!

        if (value == null)
            return sb.append("UNDEF"); // see grammar for BINDINGs def

        else if (value instanceof URI)
            return appendURI(sb, (URI)value);

        else if (value instanceof Literal)
            return appendLiteral(sb, (Literal)value);

        // XXX check for other types ? BNode ?
        throw new RuntimeException("Type not supported: " + value.getClass().getCanonicalName());
    }

    /**
     * Append the uri to the stringbuilder, i.e. <uri.stringValue>.
     *
     * @param sb
     * @param uri
     * @return the StringBuilder, for convenience
     */
    protected static StringBuilder appendURI(StringBuilder sb, URI uri) {
        sb.append("<").append(uri.stringValue()).append(">");
        return sb;
    }

    /**
     * Append the literal to the stringbuilder: "myLiteral"^^<dataType>
     *
     * @param sb
     * @param lit
     * @return the StringBuilder, for convenience
     */
    protected static StringBuilder appendLiteral(StringBuilder sb, Literal lit) {
        sb.append('"');
        sb.append(lit.getLabel().replace("\"", "\\\""));
        sb.append('"');

        //if (Literals.isLanguageLiteral(lit)) {
        //    sb.append('@');
        //    sb.append(lit.getLanguage());
       // }
        //else {
            sb.append("^^<");
            sb.append(lit.getDatatype().stringValue());
            sb.append('>');
        //}
        return sb;
    }

    protected class SequentialQueryIteration extends JoinExecutorBase<BindingSet> {

        private TupleExpr expr;
        private URI endpoint;
        private Collection<BindingSet> bindings;

        public SequentialQueryIteration(URI endpoint, TupleExpr expr, Collection<BindingSet> bindings)
                throws QueryEvaluationException {
            super(null, null, EmptyBindingSet.getInstance());
            this.endpoint = endpoint;
            this.expr = expr;
            this.bindings = bindings;
            run();
        }

        @Override
        protected void handleBindings() throws QueryEvaluationException {
            for (BindingSet b : bindings) {
                CloseableIteration<BindingSet,QueryEvaluationException> result = evaluate(endpoint, expr, b);
                addResult(result);
            }
        }
    }
}
