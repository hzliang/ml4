package algo.java.online;

import common.java.model.Pair;
import common.java.model.TrickStatus;
import common.java.util.MLUtil4J;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Created by huzuoliang on 2017/7/15.
 * after using hash trick,the zero xi do need care
 */
public abstract class OLAlgo {
    private final static Logger logger = LoggerFactory.getLogger(OLAlgo.class);

    protected String targetColumn;
    protected String idColumn;
    protected String[] numberColumns;
    protected String[] categoricalColumns;

    protected double targetRatio = 0;
    protected double logLikelihood = 0;

    protected long status;

    protected OLAlgo(Map<String, Object> meta) {

        this.targetColumn = meta.get("targetColumn").toString();
        this.idColumn = meta.get("idColumn").toString();
        this.numberColumns = (String[]) meta.get("numberColumns");
        this.categoricalColumns = (String[]) meta.get("categoricalColumns");
        this.status = TrickStatus.toLong(1, 1, 1, 100000, 4000000, 2);
    }

    public OLAlgo(Map<String, Object> meta, long status) {

        this(meta);
        this.status = status;
    }


    /**
     * you should get the predict probality or value from wtx
     * which means weight vector w multiply data vector x
     *
     * @param wtx weight vector w multiply data vector x
     * @return
     */
    protected abstract double probalityFunction(double wtx);

    /**
     * the grandient of the loss function
     *
     * @param p  predict value
     * @param y  really value
     * @param xi the value of data in certain dimension
     * @return
     */
    protected abstract double lossGradientFunction(double p, double y, double xi);

    /**
     * cost function
     *
     * @param p predict value
     * @param y really value
     * @return
     */
    protected abstract double lossFunction(double p, double y);

    /**
     * calc will compute wtx(w.transport * x) and probality p
     * and then call update function to udpate weight
     *
     * @param index
     * @param x
     * @param target
     */
    protected abstract void calc(long index, Map<Integer, Double> x, int target);

    /**
     * function to update something param
     *
     * @param y
     * @param p
     * @param x
     * @param w
     */
    protected abstract void update(double y, double p, Map<Integer, Double> x, Map<Integer, Double> w);

    /**
     * weight vector w multiply data vector x
     *
     * @param x
     * @param w
     * @return
     */
    protected abstract double wtx(Map<Integer, Double> x, Map<Integer, Double> w);

    /**
     * predict the probality of a record to be positive
     *
     * @param path
     * @return
     */
    public List<Pair> predictProbability(String path) throws IOException {
        logger.info("predict probability start...");
        Reader in = new FileReader(path);
        Iterable<CSVRecord> records = CSVFormat.RFC4180.withFirstRecordAsHeader().parse(in);
        return StreamSupport.stream(records.spliterator(), true).map(item -> {
            final String id = item.get(this.idColumn);
            final Map<Integer, Double> x = getLine(item);
            final double wtx = wtx(x, new HashMap<>());
            return new Pair(id, probalityFunction(wtx));
        }).collect(Collectors.toList());
    }

    public String forKaggle(String path) throws IOException {
        logger.info("predict probability start...");
        Reader in = new FileReader(path);
        Iterable<CSVRecord> records = CSVFormat.RFC4180.withFirstRecordAsHeader().parse(in);
        StringBuffer sb = new StringBuffer(1000000);
        sb.append("id,click\n");
        StreamSupport.stream(records.spliterator(), false).forEach(item -> {
            final String id = item.get(this.idColumn);
            final Map<Integer, Double> x = getLine(item);
            final double wtx = wtx(x, new HashMap<>());
            sb.append(id + "," + probalityFunction(wtx) + "\n");
        });
        return sb.toString();
    }

    public String forFile(String path) throws IOException {
        logger.info("predict probability start...");
        FileReader fr = new FileReader(path);
        BufferedReader br = new BufferedReader(fr);
        StringBuffer sb = new StringBuffer(1000000);
        sb.append("id,click\n");
        String line = "";
        double p = 0;
        int c = 0;
        List<Integer> truth = new ArrayList<>(1000);
        List<Double> predict = new ArrayList<>(1000);
        double slogloss=0;
        while ((line = br.readLine()) != null) {
            String[] strs = line.split(",");
            final int label = Integer.parseInt(strs[Integer.parseInt(targetColumn)]);
            final Map<Integer, Double> x = getLine(strs);
            final double wtx = wtx(x, new HashMap<>());
            double a = probalityFunction(wtx);
            truth.add(label);
            predict.add(a);
            if (a >= this.targetRatio && label == 1) {
                p++;
                c++;

            } else if (a <= this.targetRatio && label == 0) {
                p++;
                c++;
            } else {
                c++;
            }
            slogloss += logloss(label,a);

//            sb.append("id,click\n");
        }
        double auc = MLUtil4J.calcAUC(truth.toArray(new Integer[truth.size()]),
                predict.toArray(new Double[predict.size()]));
        System.out.println("the auc = " +auc);
        System.out.println("the accurate = " + p / c);
        System.out.println("the logloss = " + slogloss/c);
        return sb.toString();
    }

    private double logloss(int label,double prob){
        if(label == 1){
            return -Math.log(prob);
        }
        return -Math.log(1-prob);
    }
    protected abstract List<Pair> predictClass(String path);

    /**
     * get input
     * judge if use hash trick for save memeory
     *
     * @param record
     * @return
     */
    private Map<Integer, Double> getLine(CSVRecord record) {

        Map<Integer, Double> x = new HashMap<>();
        final int numberColumnsLen = numberColumns.length;
        for (int i = 0; i < numberColumnsLen; i++) {
            double value = Double.parseDouble(record.get(numberColumns[i]));
            x.put(i, value);
        }
        if (TrickStatus.useHashTrick(status)) {
            Stream.of(categoricalColumns).forEach(item -> {
                String value = record.get(item);
                int hashIndex = hash(item, value) + numberColumnsLen;
                x.put(hashIndex, x.getOrDefault(hashIndex, 0.0) + 1);
            });
            return x;
        }
        for (int i = 0; i < categoricalColumns.length; i++) {
            x.put(i + numberColumnsLen, x.getOrDefault(i, 0.0) + 1);
        }
        return x;
    }

    private Map<Integer, Double> getLine(String[] featrue) {

        Map<Integer, Double> x = new HashMap<>();
        final int numberColumnsLen = numberColumns.length;
        for (int i = 0; i < numberColumnsLen; i++) {
            double value = Double.parseDouble(featrue[Integer.parseInt(numberColumns[i])]);
            if (value > 0) {
                x.put(i, value+19);
            }
        }
        if (TrickStatus.useHashTrick(status)) {
            Stream.of(categoricalColumns).forEach(item -> {
                String value = featrue[Integer.parseInt(item)];
                if(Integer.parseInt(value) != 0) {
                    int hashIndex = hash(item, value) + numberColumnsLen;
                    x.put(hashIndex, x.getOrDefault(hashIndex, 0.0) + 1.0);
                }
            });
            return x;
        }
        for (int i = 0; i < categoricalColumns.length; i++) {
            x.put(i + numberColumnsLen, x.getOrDefault(i, 0.0) + 1);
        }
        return x;
    }

    /**
     * hash for key+"_"+value
     *
     * @param key
     * @param value
     * @return
     */
    private int hash(String key, String value) {
        return Math.abs((key + "_" + value).hashCode()) % TrickStatus.getMaxFeatures(status);
    }

    /**
     * the fucntion to expose to user for call
     * which really calc on the calc dataset
     *
     * @param path
     * @throws IOException
     */
    public void fitForCSV(String path) throws IOException {

        int sampleIndex = 0;
        final int epochs = TrickStatus.getEpochs(status);
        // iter times equal epochs
        for (int i = 1; i <= epochs; i++) {
            logger.info("the {} time calc starting",
                    i == 1 ? "first" : (i == epochs ? "last" : i));
            Reader in = new FileReader(path);
            Iterable<CSVRecord> records = CSVFormat.RFC4180.withFirstRecordAsHeader().parse(in);
            for (CSVRecord record : records) {
                final int y = Integer.parseInt(record.get(this.targetColumn));
                Map<Integer, Double> x = getLine(record);
                calc(sampleIndex++, x, y);
            }
        }
    }

    public void fitForFile(String path) throws IOException {
        int sampleIndex = 0;
        final int epochs = TrickStatus.getEpochs(status);
        // iter times equal epochs
        for (int i = 1; i <= epochs; i++) {
            logger.info("the {} time calc starting",
                    i == 1 ? "first" : (i == epochs ? "last" : i));
            FileReader fr = new FileReader(path);
            BufferedReader br = new BufferedReader(fr);
            String line = "";
            while ((line = br.readLine()) != null) {
                String[] strs = line.split(",");
                int label = Integer.parseInt(strs[Integer.parseInt(targetColumn)]);
                Map<Integer, Double> x = getLine(strs);
                calc(sampleIndex++, x, label);
            }
        }
    }
}
