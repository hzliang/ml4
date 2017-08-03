package algo.scala.online.ensemble

import algo.scala.util.{MLUtil4S, SCtx}
import common.java.util.MLUtil4J
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.mllib.linalg.{DenseVector, Vector, Vectors}
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.mllib.tree.GradientBoostedTrees
import org.apache.spark.mllib.tree.configuration.{BoostingStrategy, FeatureType}
import org.apache.spark.mllib.tree.model.{GradientBoostedTreesModel, Node}

object CTRGbdtLR {

  lazy val featuresName = "id,click,hour,C1,banner_pos,site_id,site_domain,site_category," +
    "app_id,app_domain,app_category,device_id,device_ip,device_model,device_type,device_conn_type," +
    "C14,C15,C16,C17,C18,C19,C20,C21".split(",")

  def main(args: Array[String]): Unit = {

    val clickLogs = SCtx.sc.textFile("/root/te.csv", 2)

    val learningWithCount = clickLogs.filter(!_.contains("id"))
      .flatMap(line => {
        val items = line.split(",")
        val label = items(1).toDouble
        for (i <- 0 until items.length if i != 1) yield (featuresName(i) + "@" + items(i), (label, 1.0))
      }).reduceByKey((l, r) => (l._1 + r._1, l._2 + r._2))
      .filter(item => item._2._2 >= 10)
      // field countClass1,countClass0
      .map(item => (item._1, ((item._2._1) / item._2._2,
      (item._2._2 - item._2._1) / item._2._2))).collect().toMap

    //    learningWithCount.take(10).foreach(item => println(item._1.toString + "-" + item._2._1 + "-" + item._2._2))
    val lwc = SCtx.sc.broadcast[Map[String, (Double, Double)]](learningWithCount)
    val data = clickLogs.filter(!_.contains("id"))
      .map(line => {
        val items = line.split(",")
        val continueFeatures = transportToContinueFeatures(items, lwc)
        val label = items(1).toDouble
        LabeledPoint(label, Vectors.dense(continueFeatures))
      })

    // Split the data into training and test sets (30% held out for testing)
    val splits = data.randomSplit(Array(0.7, 0.3))
    val (trainingData, testData) = (splits(0), splits(1))

    // init model
    val boostingStrategy = BoostingStrategy.defaultParams("Classification")
    boostingStrategy.setNumIterations(30)
    boostingStrategy.treeStrategy.setNumClasses(2)
    boostingStrategy.treeStrategy.setMaxDepth(7)
    boostingStrategy.setLearningRate(0.01)
    boostingStrategy.treeStrategy.setMaxBins(120)

    val gbdtModel = GradientBoostedTrees.train(trainingData, boostingStrategy)

    // 用测试数据评价模型
    val labelAndPreds = testData.map { point =>
      val predProb = MLUtil4J.positiveProb(predictBySumming(point.features, gbdtModel))
      val predLabel = gbdtModel.predict(point.features)
      (point.label, predLabel, predProb)
    }

    // log loss
    val losslog = labelAndPreds.map(item => {
      MLUtil4J.logloss(item._1, item._3)
    }).sum()

    val testErr = labelAndPreds.filter(r => r._1 != r._2).count.toDouble / testData.count()
    val auc = MLUtil4S.aucCal(labelAndPreds.map(item => (item._3, item._1)))

    println("the auc = " + auc)
    println("the accurate = " + (1 - testErr))
    println("the logloss = " + losslog / testData.count())

    //    println("Learned classification GBT model:\n" + gbdtModel.toDebugString)
    val numTrees = gbdtModel.trees.length
    val treesLeaies = new Array[Array[Int]](numTrees)
    for (i <- 0 until numTrees) {
      treesLeaies(i) = getLeafNodes(gbdtModel.trees(i).topNode)
    }
    val sumLeaf = treesLeaies.map(_.length).sum

    val featuresTrainingForLR = trainingData.map(point => {
      var newFeature = Array.fill(sumLeaf)(0)
      var fIndex = 0
      for (i <- 0 until numTrees) {
        val treePredict = predictNodeId(gbdtModel.trees(i).topNode, point.features.toDense)
        //gbdt tree is binary tree
        val treeLeaf = treesLeaies(i)
        newFeature(fIndex + treeLeaf.indexOf(treePredict)) = 1
        fIndex += treeLeaf.length
      }
      point.label.toInt + "," + newFeature.mkString(",")
    })
    featuresTrainingForLR.repartition(1).saveAsTextFile("/root/ss/s1")

    val featuresTestForLR = testData.map(point => {
      var newFeature = Array.fill(sumLeaf)(0)
      var fIndex = 0
      for (i <- 0 until numTrees) {
        val treePredict = predictNodeId(gbdtModel.trees(i).topNode, point.features.toDense)
        //gbdt tree is binary tree
        val treeLeaf = treesLeaies(i)
        newFeature(fIndex + treeLeaf.indexOf(treePredict)) = 1
        fIndex += treeLeaf.length
      }
      point.label.toInt + "," + newFeature.mkString(",")
    })
    featuresTestForLR.repartition(1).saveAsTextFile("/root/ss/s2")

    FTRLPForFile.predict(sumLeaf)
  }


  import com.github.fommil.netlib.BLAS.{getInstance => blas}

  private def predictBySumming(features: Vector, model: GradientBoostedTreesModel): Double = {
    val treePredictions = model.trees.map(_.predict(features))
    blas.ddot(model.numTrees, treePredictions, 1, model.treeWeights, 1)
  }

  /**
    * 分类特征转成连续特征
    * 使用learn with counting策略
    *
    * @param items
    * @param bv
    * @return
    */
  private def transportToContinueFeatures(items: Array[String], bv: Broadcast[Map[String, (Double, Double)]]) = {
    val learningWithCount: Map[String, (Double, Double)] = bv.value
    val continueFeatures = Array.fill[Double](featuresName.length * 2 - 2)(0.0)
    val key = featuresName(0) + "@" + items(0)
    val cf = learningWithCount.get(key)
    if (cf != None) {
      continueFeatures(0) = cf.get._1
      continueFeatures(1) = cf.get._2
    }
    for (i <- 2 until items.length) {
      val key = featuresName(i) + "@" + items(i)
      val cf = learningWithCount.get(key)
      if (cf != None) {
        continueFeatures((i - 1) * 2) = cf.get._1
        continueFeatures((i - 1) * 2 + 1) = cf.get._2
      }
    }
    continueFeatures
  }


  //get decision tree leaf's nodes
  def getLeafNodes(node: Node): Array[Int] = {
    var treeLeafNodes = new Array[Int](0)
    if (node.isLeaf) {
      treeLeafNodes = treeLeafNodes.:+(node.id)
    } else {
      treeLeafNodes = treeLeafNodes ++ getLeafNodes(node.leftNode.get)
      treeLeafNodes = treeLeafNodes ++ getLeafNodes(node.rightNode.get)
    }
    treeLeafNodes
  }

  /**
    * 在哪个node.id上进行预测
    *
    * @param node
    * @param features
    * @return
    */
  def predictNodeId(node: Node, features: DenseVector): Int = {
    val split = node.split
    if (node.isLeaf) {
      node.id
    } else {
      if (split.get.featureType == FeatureType.Continuous) {
        if (features(split.get.feature) <= split.get.threshold) {
          predictNodeId(node.leftNode.get, features)
        } else {
          predictNodeId(node.rightNode.get, features)
        }
      } else {
        if (split.get.categories.contains(features(split.get.feature))) {
          predictNodeId(node.leftNode.get, features)
        } else {
          predictNodeId(node.rightNode.get, features)
        }
      }
    }
  }
}
