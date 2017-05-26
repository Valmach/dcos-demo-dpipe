package com.dcos.demo.flink

import java.util.Properties

import org.apache.flink.api.common.restartstrategy.RestartStrategies
import org.apache.flink.api.scala._
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.apache.flink.streaming.api.windowing.assigners.SlidingProcessingTimeWindows
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.connectors.kafka.{FlinkKafkaConsumer010, FlinkKafkaProducer010}
import org.apache.flink.streaming.util.serialization.SimpleStringSchema

/**
  * Created by wenlock on 5/23/17.
  * We're working on a demo to calculate the current moving average, and standard deviation
  * current amounts with a stdv of 1 are non-volatile from the average and stdv > 3 are volatile and should be considered sell/buy conditions
  *
  * Lets accumulate an average over a time period and place it back on a stream to make decisions.
  */

object MovingAverage {

  /** Main program method */
  def main(args: Array[String]) : Unit = {

    try {
      var properties: SetupArguments = new SetupArguments(args)
      System.out.println("Lets calculate the moving average")
      System.out.println(properties.toString())

      // get the execution environment
      val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
          env.getConfig.disableSysoutLogging
          env.getConfig.setRestartStrategy(RestartStrategies.fixedDelayRestart(4, 10000))
          env.enableCheckpointing(5000) // create a checkpoint every 5 seconds

      val consumer = new FlinkKafkaConsumer010[String]( properties.getProperty("consumer"), new SimpleStringSchema(), properties )
      val stream = env.addSource(consumer)

      // write kafka stream to standard out.
      val results = stream
        .map {
          s =>
            val a: Long = s.split("\\s").last.toLong;
            new MovingAverageRecord(properties.getProperty("consumer"), a, List(a));
        }
        .keyBy("label")
        .window(SlidingProcessingTimeWindows.of(Time.minutes(5),Time.seconds(30)))
        .reduce((t1, t2) => {
          new MovingAverageRecord(t1.label, t2.amount, t1.amounts ++ t2.amounts )
        })

      // print the results with a single thread, rather than in parallel
      results.print().setParallelism(1)
//      stream.print().setParallelism(1)  // raw output

      env.execute("Read from Kafka example")

    } catch {
    case e: Exception =>
      System.err.println("Problem with calculating moving average.")
      e.printStackTrace()
    }

  }

  /*** Helper functions ***/

  /**
    * Manage the arguments
    */
  class SetupArguments() extends Properties {
    private var configresource = "/application.conf"
    private var a: Array[String] = _

    @throws[Exception]
    def this(args:Array[String]) {
      this()
      this.a = args
      val in = getClass().getResourceAsStream(this.configresource)
      this.load(in)
      in.close()
      val params = ParameterTool.fromArgs(this.a)
      // setup connection properties
      if (params.has("kafka_broker")) this.setProperty("bootstrap.servers", params.get("kafka_broker"))
      if (params.has("kafka_zookeeper")) this.setProperty("zookeeper.connect", params.get("kafka_zookeeper"))
      if (params.has("kafka_groupid")) this.setProperty("group.id", params.get("kafka_groupid"))
      if (params.has("kafka_producer_topic")) this.setProperty("producer", params.get("kafka_producer_topic"))
      if (params.has("kafka_consumer_topic")) this.setProperty("consumer", params.get("kafka_consumer_topic"))
    }

    @throws[Exception]
    def getParams(): ParameterTool = {
      return ParameterTool.fromArgs(this.a)
    }
  }

}
