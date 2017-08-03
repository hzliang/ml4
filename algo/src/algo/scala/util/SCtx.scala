package algo.scala.util

import org.apache.spark.sql.SparkSession
import org.apache.spark.{SparkConf, SparkContext}

/**
  * Created by root on 17-6-16.
  */
object SCtx {

  private lazy val conf = new SparkConf()
    .setAppName("A test class by hzliang")
    .setMaster("local[2]")

  lazy val sc = new SparkContext(conf)
  lazy val spark=SparkSession.builder().getOrCreate()
}