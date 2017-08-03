package algo.scala.util

import org.apache.spark.rdd.RDD

/**
  * Created by root on 17-8-3.
  */
object MLUtil4S {

  /**
    * predict sample auc
    * auc: The occure probality of positive samples more than negative samples
    *
    * @param sl
    * @return
    */
   def aucCal(sl: RDD[(Double, Double)]): Double = {
    //得到得分后zip 上 label 并排序 ，index 就是rank 值
    val rankByProbs: RDD[((Double, Double), Long)]
    = sl.sortBy(_._1).zipWithIndex()

    //正例返回rank 负例返回o ，最后sum 的值就是正例的rank 值之和
    val rankSum = rankByProbs.map(ps =>
      if (ps._1._2 == 1.0) {
        ps._2
      } else {
        0.0
      }).sum()

    val M = sl.map(_._2).sum()
    val N = sl.map(_._2).count() - M

    //计算auc
    (rankSum - M * (M + 1) / 2) / (M * N)
  }
}
