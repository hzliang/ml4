package algo.java.online.impl;


import algo.java.online.OLAlgo;
import common.java.model.Pair;
import common.java.model.TrickStatus;
import common.java.util.MLUtil4J;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by huzuoliang on 2017/7/15.
 */
public class FTRLP extends OLAlgo {

    private final static Logger logger = LoggerFactory.getLogger(FTRLP.class);
    private final static NumberFormat nf = NumberFormat.getInstance();

    private final Map<Integer, Double> z; // 更新目标函数的梯度公式
    private final Map<Integer, Double> n; // 第i维梯度平方累加和值

    private double l1;
    private double l2;
    private double alpha;
    private double beta;

    static {
        nf.setMaximumFractionDigits(2);
    }

    private FTRLP(Map<String, Object> meta) {

        super(meta);
        z = new HashMap<>();
        n = new HashMap<>();
    }

    private FTRLP(Map<String, Object> meta, long status) {

        super(meta, status);
        z = new HashMap<>();
        n = new HashMap<>();
    }
    public FTRLP(Map<String, Object> meta, double l1, double l2,
                 double alpha, double beta){
        this(meta);
        this.l1 = l1;
        this.l2 = l2;
        this.alpha = alpha;
        this.beta = beta;
    }

    public FTRLP(Map<String, Object> meta, double l1, double l2,
                 double alpha, double beta, long status){
        this(meta,status);
        this.l1 = l1;
        this.l2 = l2;
        this.alpha = alpha;
        this.beta = beta;
    }

    @Override
    protected double probalityFunction(double wtx) {
        return 1. / (1. + Math.exp(-Math.max(Math.min(wtx, 35.), -35.)));
    }

    @Override
    protected double lossGradientFunction(double p, double y, double xi) {
        return (p - y) * xi;
    }

    @Override
    protected double lossFunction(double p, double y) {
        final double vp = Math.max(Math.min(p, 1. - 10e-15), 10e-15);
        return y == 1 ? -Math.log(vp) : -Math.log(1. - vp);
    }

    @Override
    protected void update(double y, double p, Map<Integer, Double> x, Map<Integer, Double> w) {

        x.keySet().stream().forEach(item -> {
            double g = lossGradientFunction(p, y, x.get(item));
            double s = (Math.sqrt(n.getOrDefault(item, 0.0) + g * g)
                    - Math.sqrt(n.getOrDefault(item, 0.0)))
                    / this.alpha; // 控制更新速度 s1+s2+s3+s4+...+si = learning rate(i)
            z.put(item, z.getOrDefault(item, 0.0) + g - s * w.get(item));
            n.put(item, n.getOrDefault(item, 0.0) + g * g);
        });
    }

    @Override
    protected void calc(long index, Map<Integer, Double> x, int y) {

        this.targetRatio = (1.0 * (index * this.targetRatio + y)) / (index + 1);

        final Map<Integer, Double> w = new HashMap<>();
        double wtx = wtx(x, w);
        double p = probalityFunction(wtx);
        this.logLikelihood += lossFunction(p, y);

        if ((index + 1) % TrickStatus.getPrintEveryLineNum(status) == 0) {
            logger.debug("index = " + (index + 1) + ", lossFunction = " + nf.format(logLikelihood));
        }
        this.update(y, p, x, w);
    }

    /**
     * w.transport * x
     *
     * @param x every line data
     * @return
     */
    @Override
    protected double wtx(Map<Integer, Double> x, Map<Integer, Double> w) {

        return x.keySet().stream().mapToDouble(hashIndex -> {
            if (Math.abs(z.getOrDefault(hashIndex, 0.0)) <= this.l1) {
                w.put(hashIndex, 0.0);
            } else {
                int sign = MLUtil4J.sign(z.getOrDefault(hashIndex, 0.0));
                double wi = -(z.getOrDefault(hashIndex, 0.0) - sign * this.l1)
                        / (this.l2 + (this.beta + Math.sqrt(n.getOrDefault(hashIndex, 0.0)))
                        / this.alpha);
                w.put(hashIndex, wi);
            }

            return w.get(hashIndex) * x.get(hashIndex);
        }).sum();
    }

    @Override
    public List<Pair> predictClass(String path) {
        return this.predictClass(path).stream().map(pair -> {
            double value =(Double) pair.getValue();
            pair.setValue(value >= this.targetRatio ? 1 : -1);
            return pair;
        }).collect(Collectors.toList());
    }
}
