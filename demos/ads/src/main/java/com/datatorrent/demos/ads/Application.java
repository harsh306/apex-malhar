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
package com.datatorrent.demos.ads;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration;

import com.datatorrent.api.AttributeMap;
import com.datatorrent.api.Context.OperatorContext;
import com.datatorrent.api.Context.PortContext;
import com.datatorrent.api.DAG;
import com.datatorrent.api.DAG.Locality;
import com.datatorrent.api.DAGContext;
import com.datatorrent.api.Operator.InputPort;
import com.datatorrent.api.annotation.ApplicationAnnotation;
import com.datatorrent.api.StreamingApplication;
import com.datatorrent.lib.io.ConsoleOutputOperator;
import com.datatorrent.lib.io.PubSubWebSocketOutputOperator;
import com.datatorrent.lib.math.MarginMap;
import com.datatorrent.lib.math.QuotientMap;
import com.datatorrent.lib.math.SumCountMap;
import com.datatorrent.lib.stream.StreamMerger;
import com.datatorrent.lib.testbench.EventClassifier;
import com.datatorrent.lib.testbench.EventGenerator;
import com.datatorrent.lib.testbench.FilteredEventClassifier;
import com.datatorrent.lib.testbench.ThroughputCounter;

/**
 * <p>
 * This demo shows live computation of cost/revenue/margin/ctr for ads for
 * various advertisers. <br>
 * <b>Functional Description : </b><br>
 * Application generate ads events data stamped by advertiser name and clicks. <br>
 * Core of application aggregates revenue/cost for clicks converted into sale
 * and total clicks for ads by advertiser name. <br>
 * Application outputs cost/revenue/margin/ctr by for every advertiser.<br>
 * <br>
 * <b>Input(s) : </b><br>
 * Random event generator and tuple stamper. <br>
 * <br>
 * <b>Output(s) : </b><br>
 * Output Adapter : <br>
 * Output values are written to console through ConsoleOutputOerator<br>
 * If needed you can use other output adapters<br>
 * <br>
 * <p>
 * Running Java Test or Main app in IDE:
 *
 * <pre>
 * LocalMode.runApp(new Application(), 600000); // 10 min run
 * </pre>
 *
 * Run Success : <br>
 * For successful deployment and run, user should see following output on
 * console: <br>
 *
 * <pre>
 * costConsole{}
 * revConsole{}
 * costConsole{}
 * revConsole{}
 * costConsole{}
 * revConsole{}
 * costConsole{sprint,finance=1050.2310000048753, nike,sports=599.9577500021693, sprint,mail=201.57719999946136, sprint,sports=611.1157500022679, etrade,mail=199.77719999948687, nike,mail=197.5589999995183, nike,home=766.9652000000992, sprint,home=780.1425500002754, etrade,home=773.4969000001865, etrade,sports=605.2130000022157, nike,finance=1028.5290000053885, etrade,finance=1038.3360000051566}
 * ctrConsole{sprint,mail=100.0, nike,sports=100.0, sprint,finance=100.0, etrade,mail=100.0, sprint,sports=100.0, nike,home=100.0, nike,mail=100.0, sprint,home=100.0, etrade,sports=100.0, etrade,home=100.0, nike,finance=100.0, etrade,finance=100.0}
 * revConsole{nike,sports=134052.0, sprint,mail=19636.0, sprint,finance=4063.0, etrade,mail=29880.0, sprint,sports=4102.0, nike,mail=55892.0, nike,home=52396.0, sprint,home=25696.0, etrade,sports=20040.0, etrade,home=21170.0, nike,finance=24552.0, etrade,finance=156545.0}
 * viewCountConsole{count=4320197, window_time=1982, tuples_per_sec=2179715, window_id=2, avg=1748380}
 * marginConsole{sprint,finance=74.15134137324944, sprint,mail=98.97343043389967, nike,sports=99.5524440142615, sprint,sports=85.10200511939864, etrade,mail=99.33140160642742, nike,home=98.53621421482536, nike,mail=99.64653438774866, sprint,home=96.96395333904002, etrade,home=96.3462593292386, etrade,sports=96.97997504988915, nike,finance=95.81081378296925, etrade,finance=99.33671723785163}
 * costConsole{}
 * revConsole{}
 * costConsole{}
 * </pre>
 * <br>
 * Scaling Options : <br>
 * User set partitions on sum operator, refer code {@link com.datatorrent.lib.math.SumCountMap} <br>
 * <br>
 * <b>Application DAG : </b><br>
 * <img src="doc-files/AdsDemo.png" width=600px > <br>
 * <b> Streaming Window Size : </b> 1000(500 seconds). <br>
 * <br>
 * <b>Operator Details: </b> <br>
 *  <ul>
 * 	<li>
 *     <b>  viewGen : </b> This is random event generator for  ads view data.
 *         This can replaced by any input stream. by user. <br>
 *     Class : {@link com.datatorrent.lib.testbench.EventGenerator}  <br>
 *     Operator Application Window Count : 1 <br>
 *     StateFull : No
 *  </li>
 *  <li>
 *  <b> adviews : </b> This is operator stamps random data with advertiser name,
 *         This can replaced by any input stream. by user. <br>
 *     Class : {@link com.datatorrent.lib.testbench.EventClassifier}  <br>
 *     Operator Application Window Count : 1 <br>
 *     StateFull : No
 *  </li>
 *  <li>
 *  <b> insertClicks : </b> This operator convert stamped stream into clicks data. <br>
 *     Class : {@link com.datatorrent.lib.testbench.FilteredEventClassifier} <br>
 *     Operator Application Window Count : 1 <br>
 *     StateFull : No <br>
 *  </li>
 *  <li>
 *  <b> viewAggregate : </b> Operator sums revenue/cost data for ads converted to sale. <br>
 *      Class : {@link com.datatorrent.lib.math.SumCountMap} <br>
 *      Operator Application Window Count : 1000<br>
 *      StateFull : Yes <br>
 *  </li>
 *  <li>
 *  <b> clicksAggregate : </b> Operator sums clicks for ads by advertiser. <br>
 *      Class : {@link com.datatorrent.lib.math.SumCountMap} <br>
 *      Operator Application Window Count : 1000<br>
 *      StateFull : Yes <br>
 *  </li>
 *  <li>
 *  <b> margin : </b> Operator computes cost/revenu margin. <br>
 *      Class : {@link import com.datatorrent.lib.math.MarginMap} <br>
 *      Operator Application Window Count : 1 <br>
 *      StateFull : No <br>
 *  </li>
 *  <li>
 *  <b> ctr : </b> Computes quotient for sales/total clicks. <br>
 *     Class : {@link import com.datatorrent.lib.math.QuotientMap} <br>
 *      Operator Application Window Count : 1 <br>
 *      StateFull : No <br>
 *  </li>
 *  </ul>
 *
 * @since 0.3.2
 */
@ApplicationAnnotation(name="AdsDemo")
public class Application implements StreamingApplication
{
 
  public static final String P_allInline = Application.class.getName() + ".allInline";
  public static final String P_enableHdfs = Application.class.getName() + ".enableHdfs";
  private Locality locality = null;
 
  private void configure(DAG dag, Configuration conf)
  {

    if (StreamingApplication.Environment.CLUSTER == conf.getEnum(StreamingApplication.ENVIRONMENT, StreamingApplication.Environment.LOCAL)) {
     
      // settings only affect distributed mode
      AttributeMap attributes = dag.getAttributes();
      if (attributes.get(DAGContext.CONTAINER_MEMORY_MB) == null) {
        attributes.put(DAGContext.CONTAINER_MEMORY_MB, 2048);
      }
      if (attributes.get(DAGContext.MASTER_MEMORY_MB) == null) {
        attributes.put(DAGContext.MASTER_MEMORY_MB, 1024);
      }
      if (attributes.get(DAGContext.CONTAINERS_MAX_COUNT) == null) {
        attributes.put(DAGContext.CONTAINERS_MAX_COUNT, 1);
      }
    }
    else if (StreamingApplication.Environment.LOCAL == conf.getEnum(StreamingApplication.ENVIRONMENT, StreamingApplication.Environment.CLUSTER)) {
     
    }
    this.locality = conf.getBoolean(P_allInline, false) ? Locality.CONTAINER_LOCAL : null;

  }

  private InputPort<Object> getConsolePort(DAG b, String name, boolean silent)
  {
    // output to HTTP server when specified in environment setting
    String gatewayAddress = b.getValue(DAG.GATEWAY_CONNECT_ADDRESS);
    if (!StringUtils.isEmpty(gatewayAddress)) {
      URI uri = URI.create("ws://" + gatewayAddress + "/pubsub");
      String topic = "demos.ads." + name;
      PubSubWebSocketOutputOperator<Object> wsOut = b.addOperator(name, new PubSubWebSocketOutputOperator<Object>());
      wsOut.setUri(uri);
      wsOut.setTopic(topic);
      return wsOut.input;
    }
    ConsoleOutputOperator oper = b.addOperator(name, new ConsoleOutputOperator());
    oper.setStringFormat(name + "%s");
    oper.silent = silent;
    return oper.input;
  }

  public EventClassifier getAdViewsStampOperator(String name, DAG b)
  {
    EventClassifier oper = b.addOperator(name, EventClassifier.class);
    HashMap<String, Double> kmap = new HashMap<String, Double>();
    kmap.put("sprint", null);
    kmap.put("etrade", null);
    kmap.put("nike", null);
    oper.setKeyMap(kmap);
    return oper;
  }

  public FilteredEventClassifier<Double> getInsertClicksOperator(String name, DAG b)
  {
    FilteredEventClassifier<Double> oper = b.addOperator(name, new FilteredEventClassifier<Double>());
    HashMap<String, Double> kmap = new HashMap<String, Double>();
    // Getting $1,$5,$4 per click respectively
    kmap.put("sprint", 1.0);
    kmap.put("etrade", 5.0);
    kmap.put("nike", 4.0);
    oper.setKeyMap(kmap);

    HashMap<String, ArrayList<Integer>> wmap = new HashMap<String, ArrayList<Integer>>();
    ArrayList<Integer> alist = new ArrayList<Integer>(3);
    alist.add(60);
    alist.add(10);
    alist.add(30);
    wmap.put("home", alist);
    alist = new ArrayList<Integer>(3);
    alist.add(10);
    alist.add(75);
    alist.add(15);
    wmap.put("finance", alist);
    alist = new ArrayList<Integer>(3);
    alist.add(10);
    alist.add(10);
    alist.add(80);
    wmap.put("sports", alist);
    alist = new ArrayList<Integer>(3);
    alist.add(50);
    alist.add(15);
    alist.add(35);
    wmap.put("mail", alist);
    oper.setKeyWeights(wmap);

    return oper;
  }

  @Override
  public void populateDAG(DAG dag, Configuration conf)
  {
	
    configure(dag, conf);
    //dag.setAttribute(DAG.APPLICATION_NAME, "AdsApplication");
    EventGenerator viewGen =dag.addOperator("viewGen", EventGenerator.class);
    EventClassifier adviews = getAdViewsStampOperator("adviews", dag);
    FilteredEventClassifier<Double> insertclicks = getInsertClicksOperator("insertclicks", dag);
    SumCountMap<String, Double> viewAggregate=dag.addOperator("viewAggr", new SumCountMap<String, Double>());
    SumCountMap<String, Double> clickAggregate=dag.addOperator("clickAggr", new SumCountMap<String, Double>());
    dag.addStream("views", viewGen.hash_data, adviews.event);
    DAG.StreamMeta viewsAggStream = dag.addStream("viewsaggregate", adviews.data, insertclicks.data, viewAggregate.data).setLocality(Locality.CONTAINER_LOCAL);

    if (conf.getBoolean(P_enableHdfs, false)) {
      HdfsHashMapOutputOperator viewsToHdfs = dag.addOperator("viewsToHdfs", new HdfsHashMapOutputOperator());
      viewsToHdfs.setAppend(false);
      viewsToHdfs.setCloseCurrentFile(true);
      viewsToHdfs.setFilePath("file:///tmp/adsdemo/views-%(operatorId)-part%(partIndex)");
      dag.setInputPortAttribute(viewsToHdfs.input, PortContext.PARTITION_PARALLEL, true);
      viewsAggStream.addSink(viewsToHdfs.input);
    }

    dag.addStream("clicksaggregate", insertclicks.filter, clickAggregate.data);
    QuotientMap<String, Integer> ctr = dag.addOperator("ctr", new QuotientMap<String, Integer>());
    SumCountMap<String, Double> cost = dag.addOperator("cost", new SumCountMap<String, Double>());
    SumCountMap<String, Double> revenue = dag.addOperator("rev", new SumCountMap<String, Double>());
    MarginMap<String, Double> margin =dag.addOperator("margin", new MarginMap<String, Double>());
    StreamMerger<HashMap<String, Integer>> merge =dag.addOperator("countmerge", new StreamMerger<HashMap<String, Integer>>());
    ThroughputCounter<String, Integer> tuple_counter =dag.addOperator("tuple_counter", new ThroughputCounter<String, Integer>());

    dag.addStream("adviewsdata", viewAggregate.sum, cost.data);
    dag.addStream("clicksdata", clickAggregate.sum, revenue.data);
    dag.addStream("viewtuplecount", viewAggregate.count, ctr.denominator, merge.data1).setLocality(locality);
    dag.addStream("clicktuplecount", clickAggregate.count, ctr.numerator, merge.data2).setLocality(locality);
    dag.addStream("total count", merge.out, tuple_counter.data).setLocality(locality);

    InputPort<Object> revconsole = getConsolePort(dag, "revConsole", false);
    InputPort<Object> costconsole = getConsolePort(dag, "costConsole", false);
    InputPort<Object> marginconsole = getConsolePort(dag, "marginConsole", false);
    InputPort<Object> ctrconsole = getConsolePort(dag, "ctrConsole", false);
    InputPort<Object> viewcountconsole = getConsolePort(dag, "viewCountConsole", false);

    dag.addStream("revenuedata", revenue.sum, margin.denominator, revconsole).setLocality(locality);
    dag.addStream("costdata", cost.sum, margin.numerator, costconsole).setLocality(locality);
    dag.addStream("margindata", margin.margin, marginconsole).setLocality(locality);
    dag.addStream("ctrdata", ctr.quotient, ctrconsole).setLocality(locality);
    dag.addStream("tuplecount", tuple_counter.count, viewcountconsole).setLocality(locality);

  }

}
