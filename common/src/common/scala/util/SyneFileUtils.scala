package common.scala.util

import java.net.URI

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileSystem, Path}

/**
  * Created by root on 17-6-15.
  */
object SyneFileUtils {


  def deleteDirectory(directory:String): Boolean ={
    val fs=FileSystem.get(new URI(directory),new Configuration())
    fs.delete(new Path(directory),true)
  }
}
