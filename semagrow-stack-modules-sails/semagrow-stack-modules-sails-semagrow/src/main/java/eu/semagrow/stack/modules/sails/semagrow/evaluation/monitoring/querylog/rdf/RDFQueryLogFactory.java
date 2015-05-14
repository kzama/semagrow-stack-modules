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

            if (fileConfig.rotate()) {

                QueryLogWriter handler = null;
                try {
                    handler = new RotatingQueryLogWriter(fileConfig);
                    return handler;
                } catch (QueryLogException e) {
                    throw new QueryLogConfigException(e);
                }

            } else {

                try {
                    QueryLogWriter handler = getQueryRecordLogger(fileConfig.getFilename());
                }
                catch (FileNotFoundException e) {
                    throw new QueryLogConfigException(e);
                }
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

    public QueryLogWriter getQueryRecordLogger(String out) throws FileNotFoundException {

        FileOutputStream fileStream = new FileOutputStream(out);

        return getQueryRecordLogger(fileStream);
    }



    private class RotatingQueryLogWriter implements QueryLogWriter
    {
        private QueryLogWriter actualWriter;

        private FileQueryLogConfig config;

        private int counter = 0;

        private int rotation = 0;

        public RotatingQueryLogWriter(FileQueryLogConfig config) throws QueryLogException {
            this.config = config;
            actualWriter = newWriter();
        }

        public RotatingQueryLogWriter(QueryLogWriter writer, FileQueryLogConfig config) {
            actualWriter = writer;
            this.config = config;
        }

        @Override
        public void startQueryLog() throws QueryLogException {
            actualWriter.startQueryLog();
        }

        @Override
        public void endQueryLog() throws QueryLogException {
            actualWriter.endQueryLog();
        }

        @Override
        public void handleQueryRecord(QueryLogRecord queryLogRecord) throws QueryLogException {

            actualWriter.handleQueryRecord(queryLogRecord);
            counter++;
            checkRotate();
        }

        private void checkRotate() throws QueryLogException {
            boolean mustRotate = false;

            mustRotate = counter > 100;

            if (mustRotate) {
                actualWriter.endQueryLog();
                initWriter();
            }
        }

        private void initWriter() throws QueryLogException {
            rotation++;
            actualWriter = newWriter();
            counter = 0;
        }


        private QueryLogWriter newWriter() throws QueryLogException
        {
            String filename = computeNextFilename();

            FileOutputStream fileStream = null;

            try {
                QueryLogWriter handler = getQueryRecordLogger(filename);
                return handler;
            }
            catch (FileNotFoundException e) {
                throw new QueryLogException(e);
            }
        }

        private String computeNextFilename() {
            return config.getFilename() + "." + rotation;
        }
    }

}
