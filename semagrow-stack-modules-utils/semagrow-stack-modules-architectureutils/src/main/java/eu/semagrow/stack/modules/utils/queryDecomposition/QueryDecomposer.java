/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package eu.semagrow.stack.modules.utils.queryDecomposition;

import eu.semagrow.stack.modules.utils.endpoint.SPARQLEndpoint;
import java.util.List;
import java.util.UUID;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.algebra.StatementPattern;
import org.openrdf.query.parser.ParsedQuery;

/**
 *
 * @author ggianna
 */
public interface QueryDecomposer {
    public List<StatementPattern> getPatterns(SPARQLEndpoint caller, 
            UUID uQueryID, ParsedQuery sQuery);
}
