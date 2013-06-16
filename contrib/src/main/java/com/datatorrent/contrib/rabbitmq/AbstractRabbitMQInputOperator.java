/*
 *  Copyright (c) 2012 Malhar, Inc.
 *  All Rights Reserved.
 */
package com.malhartech.contrib.rabbitmq;

import com.malhartech.annotation.InjectConfig;
import com.malhartech.api.*;
import com.malhartech.api.Context.OperatorContext;
import com.rabbitmq.client.*;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RabbitMQ input adapter operator, which consume data from RabbitMQ message bus.<p><br>
 *
 * <br>
 * Ports:<br>
 * <b>Input</b>: No input port<br>
 * <b>Output</b>: Can have any number of output ports<br>
 * <br>
 * Properties:<br>
 * <b>tuple_blast</b>: Number of tuples emitted in each burst<br>
 * <b>bufferSize</b>: Size of holding buffer<br>
 * <b>host</b>:the address for the consumer to connect to rabbitMQ producer<br>
 * <br>
 * Compile time checks:<br>
 * Class derived from this has to implement the abstract method emitTuple() <br>
 * <br>
 * Run time checks:<br>
 * None<br>
 * <br>
 * <b>Benchmarks</b>: Blast as many tuples as possible in inline mode<br>
 * <table border="1" cellspacing=1 cellpadding=1 summary="Benchmark table for AbstractRabbitMQInputOperator&lt;K,V extends Number&gt; operator template">
 * <tr><th>In-Bound</th><th>Out-bound</th><th>Comments</th></tr>
 * <tr><td><b>10 thousand K,V pairs/s</td><td>One tuple per key per window per port</td><td>In-bound rate is the main determinant of performance. Operator can emit about 10 thousand unique (k,v immutable pairs) tuples/sec as RabbitMQ DAG. Tuples are assumed to be
 * immutable. If you use mutable tuples and have lots of keys, the benchmarks may differ</td></tr>
 * </table><br>
 * <br>
 * @author Zhongjian Wang <zhongjian@malhar-inc.com>
 */
public abstract class AbstractRabbitMQInputOperator<T>
    implements InputOperator,
ActivationListener<OperatorContext>
{
  private static final Logger logger = LoggerFactory.getLogger(AbstractRabbitMQInputOperator.class);
  @InjectConfig(key = "host")
  private String host = "localhost";
  @InjectConfig(key = "exchange")
  private String exchange;
  transient ConnectionFactory connFactory;
//  QueueingConsumer consumer = null;

  private static final int DEFAULT_BLAST_SIZE = 1000;
  private static final int DEFAULT_BUFFER_SIZE = 1024*1024;
  private int tuple_blast = DEFAULT_BLAST_SIZE;
  private int bufferSize = DEFAULT_BUFFER_SIZE;

  transient Connection connection = null;
  transient Channel channel = null;
  transient TracingConsumer tracingConsumer = null;
  transient String cTag;
  transient String queueName="testQ";
  transient ArrayBlockingQueue<byte[]> holdingBuffer;

/**
 * define a consumer which can asynchronously receive data,
 * and added to holdingBuffer
 */
 public class TracingConsumer extends DefaultConsumer
  {
    public TracingConsumer(Channel ch)
    {
      super(ch);
    }

    @Override
    public void handleConsumeOk(String c)
    {
      logger.debug(this + ".handleConsumeOk(" + c + ")");
      super.handleConsumeOk(c);
    }

    @Override
    public void handleCancelOk(String c)
    {
      logger.debug(this + ".handleCancelOk(" + c + ")");
      super.handleCancelOk(c);
    }

    @Override
    public void handleShutdownSignal(String c, ShutdownSignalException sig)
    {
      logger.debug(this + ".handleShutdownSignal(" + c + ", " + sig + ")");
      super.handleShutdownSignal(c, sig);
    }

    @Override
    public void handleDelivery(String consumer_Tag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException
    {
      holdingBuffer.add(body);
//      logger.debug("Received Async message:" + new String(body)+" buffersize:"+holdingBuffer.size());
    }
  }

  @Override
  public void emitTuples()
  {
    int ntuples = tuple_blast;
    if (ntuples > holdingBuffer.size()) {
      ntuples = holdingBuffer.size();
    }
    for (int i = ntuples; i-- > 0;) {
      emitTuple(holdingBuffer.poll());
    }
  }

  public abstract void emitTuple(byte[] message);

  @Override
  public void beginWindow(long windowId)
  {
  }

  @Override
  public void endWindow()
  {
  }

  @Override
  public void setup(OperatorContext context)
  {
    holdingBuffer = new ArrayBlockingQueue<byte[]>(bufferSize);
  }

  @Override
  public void teardown()
  {
  }

  @Override
  public void activate(OperatorContext ctx)
  {
    try {
      connFactory = new ConnectionFactory();
      connFactory.setHost(host);
      connection = connFactory.newConnection();
      channel = connection.createChannel();

      channel.exchangeDeclare(exchange, "fanout");
      queueName = channel.queueDeclare().getQueue();
      channel.queueBind(queueName, exchange, "");

//      consumer = new QueueingConsumer(channel);
//      channel.basicConsume(queueName, true, consumer);
      tracingConsumer = new TracingConsumer(channel);
      cTag = channel.basicConsume(queueName, true, tracingConsumer);
    }
    catch (IOException ex) {
      logger.debug(ex.toString());
    }
  }

//  public void addBuffer() {
//      holdingBuffer.add("aaa".getBytes());
//      holdingBuffer.add("bbb".getBytes());
//      holdingBuffer.add("ccc".getBytes());
//  }
  @Override
  public void deactivate()
  {
    try {
      channel.close();
      connection.close();
    }
    catch (IOException ex) {
      logger.debug(ex.toString());
    }
  }
  public void setTupleBlast(int i)
  {
    this.tuple_blast = i;
  }

  public void setHost(String host)
  {
    this.host = host;
  }
  public void setExchange(String exchange)
  {
    this.exchange = exchange;
  }

  public String getQueueName()
  {
    return queueName;
  }

}