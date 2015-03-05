/*
 * Copyright (c) 2013 DataTorrent, Inc. ALL Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datatorrent.contrib.kafka;

import com.datatorrent.api.Attribute;
import com.datatorrent.api.BaseOperator;
import com.datatorrent.api.Context;
import com.datatorrent.api.DAG;
import com.datatorrent.api.DAG.Locality;
import com.datatorrent.api.DefaultInputPort;
import com.datatorrent.api.LocalMode;
import com.datatorrent.api.Operator;
import com.datatorrent.lib.helper.OperatorContextTestHelper;
import com.datatorrent.lib.io.IdempotentStorageManager;
import com.datatorrent.lib.testbench.CollectorTestSink;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.LoggerFactory;

public class KafkaInputOperatorTest extends KafkaOperatorTestBase
{
  static final org.slf4j.Logger logger = LoggerFactory.getLogger(KafkaInputOperatorTest.class);
  static AtomicInteger tupleCount = new AtomicInteger();
  static CountDownLatch latch;

  /**
   * Test Operator to collect tuples from KafkaSingleInputStringOperator.
   * 
   * @param <T>
   */
  public static class CollectorModule<T> extends BaseOperator
  {
    public final transient CollectorInputPort<T> inputPort = new CollectorInputPort<T>("myInput", this);
  }

  public static class CollectorInputPort<T> extends DefaultInputPort<T>
  {

    public CollectorInputPort(String id, Operator module)
    {
      super();
    }

    @Override
    public void process(T tuple)
    {
      if (tuple.equals(KafkaOperatorTestBase.END_TUPLE)) {
        if (latch != null) {
          latch.countDown();
        }
        return;
      }
      tupleCount.incrementAndGet();
    }

    @Override
    public void setConnected(boolean flag)
    {
      tupleCount.set(0);
    }
  }

  /**
   * Test AbstractKafkaSinglePortInputOperator (i.e. an input adapter for
   * Kafka, aka consumer). This module receives data from an outside test
   * generator through Kafka message bus and feed that data into Malhar
   * streaming platform.
   * 
   * [Generate message and send that to Kafka message bus] ==> [Receive that
   * message through Kafka input adapter(i.e. consumer) and send using
   * emitTuples() interface on output port during onMessage call]
   * 
   * 
   * @throws Exception
   */
  public void testKafkaInputOperator(int sleepTime, final int totalCount, KafkaConsumer consumer, boolean isValid) throws Exception
  {
    // initial the latch for this test
    latch = new CountDownLatch(1);
    
    
 // Start producer
    KafkaTestProducer p = new KafkaTestProducer(TEST_TOPIC);
    p.setSendCount(totalCount);
    new Thread(p).start();

    // Create DAG for testing.
    LocalMode lma = LocalMode.newInstance();
    DAG dag = lma.getDAG();



    // Create KafkaSinglePortStringInputOperator
    KafkaSinglePortStringInputOperator node = dag.addOperator("Kafka message consumer", KafkaSinglePortStringInputOperator.class);
    consumer.setTopic(TEST_TOPIC);

    node.setConsumer(consumer);
    
    if (isValid) {
      node.setZookeeper("localhost:" + KafkaOperatorTestBase.TEST_ZOOKEEPER_PORT[0]);
    }
    
    // Create Test tuple collector
    CollectorModule<String> collector = dag.addOperator("TestMessageCollector", new CollectorModule<String>());

    // Connect ports
    dag.addStream("Kafka message", node.outputPort, collector.inputPort).setLocality(Locality.CONTAINER_LOCAL);

    // Create local cluster
    final LocalMode.Controller lc = lma.getController();
    lc.setHeartbeatMonitoringEnabled(false);

    lc.runAsync();
    
    // Wait 30s for consumer finish consuming all the messages
    Assert.assertTrue("TIMEOUT: 30s ", latch.await(300000, TimeUnit.MILLISECONDS));
    
    // Check results
    Assert.assertEquals("Tuple count", totalCount, tupleCount.intValue());
    logger.debug(String.format("Number of emitted tuples: %d", tupleCount.intValue()));
    
    p.close();
    lc.shutdown();
  }

  @Test
  public void testKafkaInputOperator_Highleverl() throws Exception
  {
    int totalCount = 10000;
    Properties props = new Properties();
    props.put("group.id", "group1");
    // This damn property waste me 2 days! It's a 0.8 new property. "smallest" means
    // reset the consumer to the beginning of the message that is not consumed yet
    // otherwise it wont get any of those the produced before!
    KafkaConsumer k = new HighlevelKafkaConsumer(props);
    k.setInitialOffset("earliest");
    testKafkaInputOperator(1000, totalCount, k, true);
  }
  
  @Test
  public void testKafkaInputOperator_Simple() throws Exception
  {
    int totalCount = 10000;
    KafkaConsumer k = new SimpleKafkaConsumer();
    k.setInitialOffset("earliest");
    testKafkaInputOperator(1000, totalCount, k, true);
  }
  
  @Test
  public void testKafkaInputOperator_Invalid() throws Exception
  {
    int totalCount = 10000;
    SimpleKafkaConsumer consumer = new SimpleKafkaConsumer();
    try{
      testKafkaInputOperator(1000, totalCount,consumer, false);
    }catch(Exception e){
      // invalid host setup expect to fail here
      Assert.assertEquals("Error creating local cluster", e.getMessage());
    }
  }

  @Override
  @After
  public void afterTest()
  {
    tupleCount.set(0);
    super.afterTest();
  }

  public static class TestMeta extends TestWatcher
  {
    String baseDir;
    String recoveryDir;
    KafkaSinglePortStringInputOperator operator;
    CollectorTestSink<Object> sink;
    Context.OperatorContext context;

    @Override
    protected void starting(Description description)
    {
      String methodName = description.getMethodName();
      String className = description.getClassName();
      baseDir = "target/" + className + "/" + methodName;
      recoveryDir = baseDir + "/" + "recovery";
    }
  }

  @Rule
  public TestMeta testMeta = new TestMeta();

  @Test
  public void testRecoveryAndIdempotency() throws Exception
  {
    int totalCount = 1500;

    // initial the latch for this test
    latch = new CountDownLatch(50);

    // Start producer
    KafkaTestProducer p = new KafkaTestProducer(TEST_TOPIC);
    p.setSendCount(totalCount);
    new Thread(p).start();

    Attribute.AttributeMap attributeMap = new Attribute.AttributeMap.DefaultAttributeMap();
    attributeMap.put(Context.OperatorContext.SPIN_MILLIS, 500);

    testMeta.context = new OperatorContextTestHelper.TestIdOperatorContext(1, attributeMap);
    testMeta.operator = new KafkaSinglePortStringInputOperator();
    ((IdempotentStorageManager.FSIdempotentStorageManager) testMeta.operator.getIdempotentStorageManager()).setRecoveryPath(testMeta.recoveryDir);

    KafkaConsumer consumer = new SimpleKafkaConsumer();
    consumer.setTopic(TEST_TOPIC);
    consumer.setInitialOffset("earliest");

    testMeta.operator.setConsumer(consumer);
    testMeta.operator.setZookeeper("localhost:" + KafkaOperatorTestBase.TEST_ZOOKEEPER_PORT[0]);
    testMeta.operator.setMaxTuplesPerWindow(500);
    testMeta.sink = new CollectorTestSink<Object>();
    testMeta.operator.outputPort.setSink(testMeta.sink);

    testMeta.operator.setup(testMeta.context);
    testMeta.operator.activate(testMeta.context);
    latch.await(4000, TimeUnit.MILLISECONDS);
    testMeta.operator.beginWindow(1);
    testMeta.operator.emitTuples();
    testMeta.operator.endWindow();
    testMeta.operator.beginWindow(2);
    testMeta.operator.emitTuples();
    testMeta.operator.endWindow();

    //failure and then re-deployment of operator
    testMeta.sink.collectedTuples.clear();
    testMeta.operator.setup(testMeta.context);
    testMeta.operator.activate(testMeta.context);

    Assert.assertEquals("largest recovery window", 2, testMeta.operator.getIdempotentStorageManager().getLargestRecoveryWindow());

    testMeta.operator.beginWindow(1);
    testMeta.operator.emitTuples();
    testMeta.operator.endWindow();
    testMeta.operator.beginWindow(2);
    testMeta.operator.emitTuples();
    testMeta.operator.endWindow();
    latch.await(3000, TimeUnit.MILLISECONDS);
    // Emiting data after all recovery windows are replayed
    testMeta.operator.beginWindow(3);
    testMeta.operator.emitTuples();
    testMeta.operator.endWindow();

    Assert.assertEquals("Total messages collected ", totalCount, testMeta.sink.collectedTuples.size());
    testMeta.sink.collectedTuples.clear();
  }
}
