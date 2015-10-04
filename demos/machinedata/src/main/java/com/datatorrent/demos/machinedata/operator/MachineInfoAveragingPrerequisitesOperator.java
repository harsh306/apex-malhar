/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.datatorrent.demos.machinedata.operator;

import com.datatorrent.common.util.BaseOperator;
import com.datatorrent.api.DefaultInputPort;
import com.datatorrent.api.DefaultOutputPort;

import com.datatorrent.demos.machinedata.data.MachineKey;
import com.datatorrent.demos.machinedata.data.MachineInfo;
import com.datatorrent.demos.machinedata.data.AverageData;
import com.datatorrent.lib.util.KeyHashValPair;


import java.util.HashMap;
import java.util.Map;

/**
 * This class calculates the partial sum and count for tuples generated by upstream Operator
 * <p> MachineInfoAveragingPrerequisitesOperator class. </p>
 *
 * @since 0.3.5
 */
public class MachineInfoAveragingPrerequisitesOperator extends BaseOperator
{

  // Aggregate sum of all values seen for a key.
  private Map<MachineKey, AverageData> sums = new HashMap<>();

  public final transient DefaultOutputPort<KeyHashValPair<MachineKey, AverageData>> outputPort = new DefaultOutputPort<KeyHashValPair<MachineKey, AverageData>>()
  {
    @Override
    public Unifier<KeyHashValPair<MachineKey, AverageData>> getUnifier()
    {
      MachineInfoAveragingUnifier unifier = new MachineInfoAveragingUnifier();
      return unifier;
    }

    ;
  };

  public transient DefaultInputPort<MachineInfo> inputPort = new DefaultInputPort<MachineInfo>()
  {

    @Override
    public void process(MachineInfo tuple)
    {
      MachineKey key = tuple.getMachineKey();
      AverageData averageData = sums.get(key);
      if (averageData == null) {
        averageData = new AverageData(tuple.getCpu(), tuple.getHdd(), tuple.getRam(), 1);
        sums.put(key, averageData);
      }
      else {
        averageData.setCpu(averageData.getCpu() + tuple.getCpu());
        averageData.setRam(averageData.getRam() + tuple.getRam());
        averageData.setHdd(averageData.getHdd() + tuple.getHdd());
        averageData.setCount(averageData.getCount() + 1);
      }
    }
  };

  @Override
  public void endWindow()
  {

    for (Map.Entry<MachineKey, AverageData> entry : sums.entrySet()) {
      if (outputPort.isConnected()) {
        outputPort.emit(new KeyHashValPair<>(entry.getKey(), entry.getValue()));
      }
    }
    sums.clear();
  }

}
