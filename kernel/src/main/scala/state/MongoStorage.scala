package se.scalablesolutions.akka.kernel.state

import com.mongodb._
import se.scalablesolutions.akka.kernel.util.Logging
import serialization.{Serializer}
import kernel.Kernel.config
import sjson.json.Serializer._

import java.util.{Map=>JMap, List=>JList, ArrayList=>JArrayList}

/**
 * A module for supporting MongoDB based persistence.
 * <p/>
 * The module offers functionality for:
 * <li>Persistent Maps</li>
 * <li>Persistent Vectors</li>
 * <li>Persistent Refs</li>
 * <p/>
 * @author <a href="http://debasishg.blogspot.com">Debasish Ghosh</a>
 */
object MongoStorage extends MapStorage 
    with VectorStorage with RefStorage with Logging {
      
  // enrich with null safe findOne
  class RichDBCollection(value: DBCollection) {
    def findOneNS(o: DBObject): Option[DBObject] = {
      value.findOne(o) match {
        case null => None
        case x => Some(x)
      }
    }
  }
  
  implicit def enrichDBCollection(c: DBCollection) = new RichDBCollection(c)
  
  val KEY = "key"
  val VALUE = "value"
  val COLLECTION = "akka_coll"
  val MONGODB_SERVER_HOSTNAME = 
    config.getString("akka.storage.mongodb.hostname", "127.0.0.1")
  val MONGODB_SERVER_DBNAME = 
    config.getString("akka.storage.mongodb.dbname", "testdb")
  val MONGODB_SERVER_PORT = 
    config.getInt("akka.storage.mongodb.port", 27017)

  val db = new Mongo(MONGODB_SERVER_HOSTNAME,
    MONGODB_SERVER_PORT, MONGODB_SERVER_DBNAME)
  val coll = db.getCollection(COLLECTION)

  // @fixme: make this pluggable
  private[this] val serializer = SJSON
  
  override def insertMapStorageEntryFor(name: String, 
    key: AnyRef, value: AnyRef) {
    insertMapStorageEntriesFor(name, List((key, value)))
  }

  override def insertMapStorageEntriesFor(name: String, 
    entries: List[Tuple2[AnyRef, AnyRef]]) {
    import java.util.{Map, HashMap}
    
    val m: Map[AnyRef, AnyRef] = new HashMap
    for ((k, v) <- entries) {
      m.put(k, serializer.out(v))
    }
    
    nullSafeFindOne(name) match {
      case None => 
        coll.insert(new BasicDBObject().append(KEY, name).append(VALUE, m))
      case Some(dbo) => {
        // collate the maps
        val o = dbo.get(VALUE).asInstanceOf[Map[AnyRef, AnyRef]]
        o.putAll(m)
        
        // remove existing reference
        removeMapStorageFor(name)
        // and insert
        coll.insert(new BasicDBObject().append(KEY, name).append(VALUE, o))
      }
    }
  }
  
  override def removeMapStorageFor(name: String) = {
    val q = new BasicDBObject
    q.put(KEY, name)
    coll.remove(q)
  }

  override def removeMapStorageFor(name: String, key: AnyRef) = {
    nullSafeFindOne(name) match {
      case None => 
      case Some(dbo) => {
        val orig = dbo.get(VALUE).asInstanceOf[DBObject].toMap
        orig.remove(key.asInstanceOf[String])

        // remove existing reference
        removeMapStorageFor(name)
        // and insert
        coll.insert(new BasicDBObject().append(KEY, name).append(VALUE, orig))
      }
    }
  }
  
  override def getMapStorageEntryFor(name: String, 
    key: AnyRef): Option[AnyRef] = {
    getValueForKey(name, key.asInstanceOf[String])
  }
  
  override def getMapStorageSizeFor(name: String): Int = {
    nullSafeFindOne(name) match {
      case None => 0
      case Some(dbo) =>
        dbo.get(VALUE).asInstanceOf[JMap[String, AnyRef]].keySet.size
    }
  }
  
  override def getMapStorageFor(name: String): List[Tuple2[AnyRef, AnyRef]]  = {
    val m = 
      nullSafeFindOne(name) match {
        case None => 
          throw new Predef.NoSuchElementException(name + " not present")
        case Some(dbo) =>
          dbo.get(VALUE).asInstanceOf[JMap[String, AnyRef]]
      }
    val n = 
      List(m.keySet.toArray: _*).asInstanceOf[List[String]]
    val vals = 
      for(s <- n) 
        yield (s, serializer.in[AnyRef](m.get(s).asInstanceOf[Array[Byte]]))
    vals.asInstanceOf[List[Tuple2[String, AnyRef]]]
  }
  
  override def getMapStorageRangeFor(name: String, start: Option[AnyRef], 
                            finish: Option[AnyRef], 
                            count: Int): List[Tuple2[AnyRef, AnyRef]] = {
    val m = 
      nullSafeFindOne(name) match {
        case None => 
          throw new Predef.NoSuchElementException(name + " not present")
        case Some(dbo) =>
          dbo.get(VALUE).asInstanceOf[JMap[String, AnyRef]]
      }

    /**
     * <tt>count</tt> is the max number of results to return. Start with 
     * <tt>start</tt> or 0 (if <tt>start</tt> is not defined) and go until
     * you hit <tt>finish</tt> or <tt>count</tt>.
     */
    val s = if (start.isDefined) start.get.asInstanceOf[Int] else 0
    val cnt = 
      if (finish.isDefined) {
        val f = finish.get.asInstanceOf[Int]
        if (f >= s) Math.min(count, (f - s)) else count
      }
      else count

    val n = 
      List(m.keySet.toArray: _*).asInstanceOf[List[String]].sort((e1, e2) => (e1 compareTo e2) < 0).slice(s, s + cnt)
    val vals = 
      for(s <- n) 
        yield (s, serializer.in[AnyRef](m.get(s).asInstanceOf[Array[Byte]]))
    vals.asInstanceOf[List[Tuple2[String, AnyRef]]]
  }
  
  private def getValueForKey(name: String, key: String): Option[AnyRef] = {
    try {
      nullSafeFindOne(name) match {
        case None => None
        case Some(dbo) =>
          Some(serializer.in[AnyRef](
            dbo.get(VALUE)
               .asInstanceOf[JMap[String, AnyRef]]
               .get(key).asInstanceOf[Array[Byte]]))
      }
    } catch {
      case e =>
        throw new Predef.NoSuchElementException(e.getMessage)
    }
  }
  
  override def insertVectorStorageEntriesFor(name: String, elements: List[AnyRef]) = {
    val q = new BasicDBObject
    q.put(KEY, name)
    
    val currentList =
      coll.findOneNS(q) match {
        case None => 
          new JArrayList[AnyRef]
        case Some(dbo) => 
          dbo.get(VALUE).asInstanceOf[JArrayList[AnyRef]]
      }
    if (!currentList.isEmpty) {
      // record exists
      // remove before adding
      coll.remove(q)
    }
    
    // add to the current list
    elements.map(serializer.out(_)).foreach(currentList.add(_))
    
    coll.insert(
      new BasicDBObject()
        .append(KEY, name)
        .append(VALUE, currentList)
    )
  }
  
  override def insertVectorStorageEntryFor(name: String, element: AnyRef) = {
    insertVectorStorageEntriesFor(name, List(element))
  }
  
  override def getVectorStorageEntryFor(name: String, index: Int): AnyRef = {
    try {
      val o =
      nullSafeFindOne(name) match {
        case None => 
          throw new Predef.NoSuchElementException(name + " not present")

        case Some(dbo) =>
          dbo.get(VALUE).asInstanceOf[JList[AnyRef]]
      }
      serializer.in[AnyRef](
        o.get(index).asInstanceOf[Array[Byte]])
    } catch {
      case e => 
        throw new Predef.NoSuchElementException(e.getMessage)
    }
  }
  
  override def getVectorStorageRangeFor(name: String, 
    start: Option[Int], finish: Option[Int], count: Int): List[AnyRef] = {
    try {
      val o =
      nullSafeFindOne(name) match {
        case None => 
          throw new Predef.NoSuchElementException(name + " not present")

        case Some(dbo) =>
          dbo.get(VALUE).asInstanceOf[JList[AnyRef]]
      }

      // pick the subrange and make a Scala list
      val l = 
        List(o.subList(start.get, start.get + count).toArray: _*)

      for(e <- l) 
        yield serializer.in[AnyRef](e.asInstanceOf[Array[Byte]])
    } catch {
      case e => 
        throw new Predef.NoSuchElementException(e.getMessage)
    }
  }
  
  override def getVectorStorageSizeFor(name: String): Int = {
    nullSafeFindOne(name) match {
      case None => 0
      case Some(dbo) => 
        dbo.get(VALUE).asInstanceOf[JList[AnyRef]].size
    }
  }

  private def nullSafeFindOne(name: String): Option[DBObject] = {
    val o = new BasicDBObject
    o.put(KEY, name)
    coll.findOneNS(o)
  }

  override def insertRefStorageFor(name: String, element: AnyRef) = {
    nullSafeFindOne(name) match {
      case None =>
      case Some(dbo) => {
        val q = new BasicDBObject
        q.put(KEY, name)
        coll.remove(q)
      }
    }
    coll.insert(
      new BasicDBObject()
        .append(KEY, name)
        .append(VALUE, serializer.out(element)))
  }

  override def getRefStorageFor(name: String): Option[AnyRef] = {
    nullSafeFindOne(name) match {
      case None => None
      case Some(dbo) =>
        Some(serializer.in[AnyRef](dbo.get(VALUE).asInstanceOf[Array[Byte]]))
    }
  }
}
