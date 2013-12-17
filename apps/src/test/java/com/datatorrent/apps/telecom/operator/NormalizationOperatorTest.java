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
package com.datatorrent.apps.telecom.operator;

import java.util.HashMap;
import java.util.Map;

import junit.framework.Assert;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datatorrent.lib.testbench.CollectorTestSink;

/**
* 
* @since 0.9.2
*/
public class NormalizationOperatorTest
{

  private static Logger logger = LoggerFactory.getLogger(NormalizationOperatorTest.class);

  @Test
  @SuppressWarnings({ "rawtypes", "unchecked" })
  public void testNormalizationOperator()
  {

    EnrichmentOperator<String, Map<String, String>> oper = new EnrichmentOperator<String, Map<String, String>>();
    oper.setEnricher(DefaultNormalizer.class);

    Map<String, Map<String, String>> prop = new HashMap<String, Map<String, String>>();
    prop.put("age", new HashMap<String, String>());
    Map<String, String> m = prop.get("age");
    m.put("m", "Male");
    m.put("male", "Male");
    oper.setProp(prop);
    oper.setup(null);
    CollectorTestSink sortSink = new CollectorTestSink();
    oper.output.setSink(sortSink);

    HashMap<String, String> input = new HashMap<String, String>();
    input.put("age", "m");
    oper.beginWindow(0);
    oper.input.process(input);
    oper.endWindow();

    Assert.assertEquals("number emitted tuples", 1, sortSink.collectedTuples.size());
    for (Object o : sortSink.collectedTuples) {
      Assert.assertEquals("{age=Male}", o.toString());
      logger.debug(o.toString());
    }
  }
  
  @Test
  @SuppressWarnings({ "rawtypes", "unchecked" })
  public void testNormalizationOperator2()
  {

    EnrichmentOperator<String, Map<String, String>> oper = new EnrichmentOperator<String, Map<String, String>>();
    oper.setEnricher(DefaultNormalizer.class);

    Map<String, Map<String, String>> prop = new HashMap<String, Map<String, String>>();
    prop.put("age", new HashMap<String, String>());
    Map<String, String> m = prop.get("age");
    m.put("m", "Male");
    m.put("male", "Male");
    oper.setProp(prop);
    oper.setup(null);
    CollectorTestSink sortSink = new CollectorTestSink();
    oper.output.setSink(sortSink);

    HashMap<String, String> input = new HashMap<String, String>();
    input.put("age", "1");
    oper.beginWindow(0);
    oper.input.process(input);
    oper.endWindow();

    Assert.assertEquals("number emitted tuples", 1, sortSink.collectedTuples.size());
    for (Object o : sortSink.collectedTuples) {
      Assert.assertEquals("{age=1}", o.toString());
      logger.info(o.toString());
    }
  }
}