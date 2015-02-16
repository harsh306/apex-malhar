/*
 *  Copyright (c) 2012-2015 Malhar, Inc.
 *  All Rights Reserved.
 */

package com.datatorrent.lib.appdata.qr;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Set;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Timothy Farkas: tim@datatorrent.com
 */
public class QueryDeserializerFactory
{
  private static final Logger logger = LoggerFactory.getLogger(QueryDeserializerFactory.class);

  Map<String, Class<? extends Query>> typeToClass = Maps.newHashMap();
  Map<String, CustomQueryDeserializer> typeToCustomQueryBuilder = Maps.newHashMap();

  public QueryDeserializerFactory(Class<? extends Query>... schemas)
  {
    setClasses(schemas);
  }

  private void setClasses(Class<? extends Query>[] schemas)
  {
    logger.debug("{}", schemas);
    Preconditions.checkArgument(schemas.length != 0, "No schemas provided.");

    Set<Class<? extends Query>> clazzes = Sets.newHashSet();

    for(Class<? extends Query> schema: schemas)
    {
      Preconditions.checkArgument(schema != null, "Provided schema cannot be null");
      Preconditions.checkArgument(!clazzes.contains(schema), "Schema %s was passed twice.", schema);
      clazzes.add(schema);

      Annotation[] ans = schema.getAnnotations();

      String schemaType = null;
      Class<? extends CustomQueryDeserializer> cqd = null;

      for(Annotation an: ans)
      {
        if(an instanceof QRType) {
          if(schemaType != null) {
            throw new UnsupportedOperationException("Cannot specify the " +
                                                    QRType.class +
                                                    " annotation twice on the class: " +
                                                    schema);
          }

          schemaType = ((QRType) an).type();

          logger.debug("Detected schemaType for {} is {}",
                       schema,
                       schemaType);
        }
        else if(an instanceof QueryDeserializerInfo) {
          if(cqd != null) {
            throw new UnsupportedOperationException("Cannot specify the " +
                                                    QueryDeserializerInfo.class +
                                                    " annotation twice on the class: " +
                                                    schema);
          }

          cqd = ((QueryDeserializerInfo) an).clazz();
        }
      }

      if(schemaType == null) {
        throw new UnsupportedOperationException("No " + QRType.class +
                                                " annotation found on class: " +
                                                schema);
      }

      if(cqd == null) {
        throw new UnsupportedOperationException("No " + QueryDeserializerInfo.class +
                                                " annotation found on class: " +
                                                schema);
      }

      Class<? extends Query> prevSchema = typeToClass.put(schemaType, schema);
      logger.debug("prevSchema {}:", prevSchema);

      if(prevSchema != null) {
        throw new UnsupportedOperationException("Cannot have the " +
                                                schemaType + " schemaType defined on multiple classes: " +
                                                schema + ", " + prevSchema);
      }

      try {
        CustomQueryDeserializer cqdI = cqd.newInstance();
        cqdI.setQueryClazz(schema);
        typeToCustomQueryBuilder.put(schemaType, cqdI);
      }
      catch(InstantiationException ex) {
        throw new RuntimeException(ex);
      }
      catch(IllegalAccessException ex) {
        throw new RuntimeException(ex);
      }
    }
  }

  public Query deserialize(String json)
  {
    String type;

    try
    {
      JSONObject jsonObject = new JSONObject(json);
      type = jsonObject.getString(Query.FIELD_TYPE);
    }
    catch(JSONException e)
    {
      logger.error("Error parsing", e);
      //Note faulty queries should not throw an exception and crash the operator
      //An invalid value like null should be returned and checked.
      return null;
    }

    CustomQueryDeserializer cqb = typeToCustomQueryBuilder.get(type);
    Query query = cqb.deserialize(json);
    query.setType(type);
    return query;
  }
}