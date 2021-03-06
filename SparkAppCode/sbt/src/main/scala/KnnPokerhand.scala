import org.apache.spark._
import org.apache.spark.SparkContext._
import org.apache.log4j._
import scala.collection.immutable.TreeMap
import scala.io.Source
import java.io.File
import java.io.PrintWriter

object KnnPokerhand {
  
  //Change value of K as needed (Can also been passed as an argument)
  val K = 8;
  //Stores value of Current Test Case
  var testData = new Array[Double](10)
  //Range of values for suits and ranks
  val minSuit: Double = 1.0
  val maxSuit: Double = 4.0
  val minRank: Double = 1.0
  val maxRank: Double = 13.0
  
  // Normalises the value to a scale of 0 to 1.0
  def normalisedDouble(givenVal: Double, minVal: Double, maxVal: Double ) : Double = {
    return ((givenVal - minVal) / (maxVal - minVal));
  }
  
  // Takes a double and returns its squared value.
  def squaredDistance(givenVal: Double) : Double = {
    return (givenVal*givenVal);
  }
  
  // Takes ten pairs of values (three pairs of doubles and two of strings), finds the difference between the members
  // of each pair (using nominalDistance() for strings) and returns the sum of the squared differences as a double.
  def totalSquaredDistance(trainData: Array[Double]) : Double = {

    var diffArr: Double = 0.0

    for(i <- 0 to 9)
      diffArr += squaredDistance(trainData(i) - testData(i))

    return (diffArr)
  }

  // =================
  //  Mapper Function
  // =================
  def theMapper(line: String) = {
    var trainData: Array[Double] = new Array[Double](11)

    // Split the input line
    val fields = line.split(",")
    //Array to store Suits and Ranks of Current Training Data 
    for(i <- 0 to 9)
      trainData(i) = normalisedDouble(fields(i).toDouble, minSuit, maxSuit)
    // PokerClass
    val pClass = fields(10).toInt
    //Calculate and store the Euclidian distance of current test case from current training case
    val tDist = totalSquaredDistance(trainData)
    //Pass the distance and class as a tuple
    (tDist, pClass)
  }
  
  // ==========================================================================================
  //                      Main Function
  // ==========================================================================================
  def main(args: Array[String]) {
   
    // Set the log level to only print errors
    Logger.getLogger("org").setLevel(Level.ERROR)
        
    // Create a SparkContext using every core of the local machine
    val conf = new SparkConf()
    conf.setAppName("KnnPokerhand")
    val sc = new SparkContext(conf)

    // Load each line of the source data into an RDD
    println("===================================================")
    println("\nLoading Training Data...")
    val lines = sc.textFile("s3://.../TrainDataFinal.txt") //Fix location of File as needed
    
    //Working with testFile
    println("===================================================")
    println("\nLoading Testing Data...")
    val testLines = (sc.textFile("s3://.../TestDataFinal.txt")).collect //Fix location of File as needed

    println("===================================================")
    println("Writing to File now...")
    val writer = new PrintWriter(new File("write.txt"))
    
    for (testLine <- testLines)
    {
       var testFields = testLine.split(',')
       for(i <- 0 to 9) {
          testData(i) = normalisedDouble(testFields(i).toDouble, minSuit, maxSuit)
       }
        
        // Use our theMapper function to convert to (Distance, PokerClass) tuples
        val rdd = lines.map(theMapper)
        //Sort the rdd elements in an ascending order
        val sortedRdd = rdd.sortByKey()
        // Finally take and store top K elements in an array.
        val kNearestNeighbors = sortedRdd.take(K)
        
        //New array
        var classArr = new Array[Int](K)
        
        //Store the classes in an Array
        for(i <- 0 to (K-1))
          classArr(i) = kNearestNeighbors(i)._2
          
        //Sort and store array of classes 
        val newArr = classArr.sorted
      
      //Pre-select the class at index 0
        var mostCommonClass = newArr(0)
        var freq = 1
        var currFreq = 1
        var currClass = newArr(0)
        
        //Check for class with highest frequency
        for(i <- 1 to (K-1)) {
           if(currClass == newArr(i)) {
             currFreq = currFreq + 1
           }
           else {
             if(freq < currFreq) {
               mostCommonClass = currClass
               freq = currFreq
             }
             currClass = newArr(i)
             currFreq = 1
           }
        }
        
        if(freq < currFreq) {
          mostCommonClass = currClass
          freq = currFreq
        }
        
        println("===================================================")
        println("     Writing Next Case:")

        writer.write(mostCommonClass + ",")
        println("Done!")
     }

    writer.close()
  }
}