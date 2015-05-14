package eu.semagrow.stack.modules.sails.semagrow.evaluation.monitoring.querylog.rdf;

import eu.semagrow.stack.modules.sails.semagrow.evaluation.monitoring.querylog.*;
import org.openrdf.rio.RDFWriter;
import org.openrdf.rio.RDFWriterFactory;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * Created by angel on 10/21/14.
 */
public class RDFQueryLogFactory implements QueryLogFactory {

    private RDFWriterFactory writerFactory;

    public RDFQueryLogFactory(RDFWriterFactory writerFactory) {
        this.writerFactory = writerFactory;
    }

    @Override
    public QueryLogWriter getQueryRecordLogger(QueryLogConfig config) throws QueryLogConfigException
    {
        if (config instanceof FileQueryLogConfig) {
            FileQueryLogConfig fileConfig = (FileQueryLogConfig)config;
            try {

                FileOutputStream fileStream = new FileOutputStream(fileConfig.getFilename());

                return getQueryRecordLogger(fileStream);

            } catch (FileNotFoundException e) {
                throw new QueryLogConfigException(e);
            }
        }
        throw new QueryLogConfigException("Wrong query log config instance");
    }


    public QueryLogWriter getQueryRecordLogger(OutputStream out) {

        RDFWriter writer = writerFactory.getWriter(out);

        QueryLogWriter handler = new RDFQueryLogWriter(writer);
        try {
            handler.startQueryLog();
        } catch (QueryLogException e) {
            e.printStackTrace();
        }
        return handler;
    }

}
