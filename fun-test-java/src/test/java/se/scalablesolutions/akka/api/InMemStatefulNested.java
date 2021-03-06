package se.scalablesolutions.akka.api;

import se.scalablesolutions.akka.annotation.transactionrequired;
import se.scalablesolutions.akka.kernel.state.*;

@transactionrequired
public class InMemStatefulNested {
  private TransactionalState factory = new TransactionalState();
  private TransactionalMap<String, String> mapState = factory.newInMemoryMap();
  private TransactionalVector<String> vectorState = factory.newInMemoryVector();
  private TransactionalRef<String> refState = factory.newInMemoryRef();

  
  public String getMapState(String key) {
    return (String)mapState.get(key).get();
  }

  
  public String getVectorState() {
    return (String)vectorState.last();
  }

  
  public String getRefState() {
    return (String)refState.get().get();
  }

  
  public void setMapState(String key, String msg) {
    mapState.put(key, msg);
  }

  
  public void setVectorState(String msg) {
    vectorState.add(msg);
  }

  
  public void setRefState(String msg) {
    refState.swap(msg);
  }

  
  public void success(String key, String msg) {
    mapState.put(key, msg);
    vectorState.add(msg);
    refState.swap(msg);
  }

  
  public String failure(String key, String msg, InMemFailer failer) {
    mapState.put(key, msg);
    vectorState.add(msg);
    refState.swap(msg);
    failer.fail();
    return msg;
  }

  
  public void thisMethodHangs(String key, String msg, InMemFailer failer) {
    setMapState(key, msg);
  }

  /*
  public void clashOk(String key, String msg, InMemClasher clasher) {
    mapState.put(key, msg);
    clasher.clash();
  }

  public void clashNotOk(String key, String msg, InMemClasher clasher) {
    mapState.put(key, msg);
    clasher.clash();
    this.success("clash", "clash");
  }
  */
}